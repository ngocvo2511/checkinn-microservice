"""Unpaid synthetic booking lifecycle. No provider calls or expires_at manipulation."""
import concurrent.futures,datetime,importlib.util,json,sys,time,uuid
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('current',R/'tools/batch8c-acceptance.py');c=importlib.util.module_from_spec(s);s.loader.exec_module(c);a=c.a

def context():
 ds=json.loads((a.PRIVATE/'dataset.json').read_text());customers=[v for v in json.loads((a.PRIVATE/'accounts.json').read_text()) if v['role']=='CUSTOMER'];return ds['rooms'][0],customers

def request(customer,room,date):
 req=a.booking_request(customer,room)
 req.update(checkInDate=date,checkOutDate=(datetime.date.fromisoformat(date)+datetime.timedelta(days=1)).isoformat())
 for item in req['items']:item.update(checkInDate=req['checkInDate'],checkOutDate=req['checkOutDate'])
 return req

def observe(room,date):
 return a.sql('hotel',f"SELECT json_build_object('held',coalesce(sum(held),0),'booked',coalesce(sum(booked),0))::text FROM room_availability WHERE room_type_id='{uuid.UUID(room['id'])}' AND date='{datetime.date.fromisoformat(date)}'")[0]

def start_expiry():
 room,customers=context();req=request(customers[0],room,'2027-03-15');code,b=a.api('/api/bookings','POST',req,customers[0]['token']);a.check(code==201,'Natural expiry unpaid booking created',code)
 a.record('booking','bookings','id',b['id']);a.discover()
 state=a.sql('booking',f"SELECT json_build_object('id',id,'status',status,'holdExpiresAt',hold_expires_at)::text FROM bookings WHERE id='{uuid.UUID(b['id'])}'")[0]
 (a.PRIVATE/'expiry-request.private.json').write_text(json.dumps(dict(request=req,customerId=customers[0]['id'],bookingId=b['id'])))
 a.write('natural-expiry-start.json',dict(booking=state,observedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),counters=observe(room,req['checkInDate']),method='Application default expiry unchanged; no expires_at updates; no provider call'))

def verify_expiry():
 room,customers=context();x=json.loads((a.PRIVATE/'expiry-request.private.json').read_text());bid=str(uuid.UUID(x['bookingId']));req=x['request']
 state=a.sql('booking',f"SELECT json_build_object('status',status,'holdExpiresAt',hold_expires_at)::text FROM bookings WHERE id='{bid}'")[0]
 holds=a.sql('hotel',f"SELECT json_build_object('status',status,'expiresAt',expires_at)::text FROM room_holds WHERE room_type_id='{uuid.UUID(room['id'])}' AND check_in_date='{req['checkInDate']}'")
 counters=observe(room,req['checkInDate']);ok=state['status']=='CANCELLED' and bool(holds) and all(v['status']=='RELEASED' for v in holds) and counters==dict(held=0,booked=0)
 a.write('natural-expiry-result.json',dict(status='PASS' if ok else 'PENDING_OR_FAIL',booking=state,holds=holds,counters=counters,observedAt=datetime.datetime.now(datetime.timezone.utc).isoformat()))
 print('NATURAL_EXPIRY',ok,state['status'],holds,counters,flush=True)
 if ok:
  customer=next(v for v in customers if v['id']==x['customerId']);code,replay=a.api('/api/bookings','POST',req,customer['token']);a.check(code==201 and replay['id']==bid,'Natural expiry idempotent booking replay',code)
  a.discover()
 return ok

def concurrency():
 room,customers=context();date='2027-04-15';attempts=[]
 requests=[request(customers[i%2],room,date) for i in range(3)]
 def attempt(i):
  t=time.perf_counter();code,body=a.api('/api/bookings','POST',requests[i],customers[i%2]['token']);return dict(index=i,httpStatus=code,ms=(time.perf_counter()-t)*1000),body
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(attempt,range(3)))
 for row,body in results:
  if row['httpStatus']==201:a.record('booking','bookings','id',body['id'])
  attempts.append(row)
 a.discover();counters=observe(room,date)
 ok=sum(row['httpStatus']==201 for row in attempts)==2 and all(row['httpStatus'] in (201,409) for row in attempts) and counters==dict(held=2,booked=0)
 a.write('concurrency-results.json',dict(status='IN_PROGRESS' if ok else 'FAIL',stock=2,attempts=attempts,counters=counters))
 print('CONCURRENCY',ok,attempts,counters,flush=True)
 if not ok:return False
 for row,body in results:
  if row['httpStatus']!=201:continue
  i=row['index'];customer=customers[i%2];req=requests[i]
  code,retry=a.api('/api/bookings','POST',req,customer['token']);a.check(code==201 and retry['id']==body['id'],'Concurrent successful booking idempotent retry',code)
  code,_=a.api('/api/bookings/'+body['id'],'DELETE',token=customer['token']);a.check(code==204,'Concurrent fixture cancel',code)
 a.discover();a.check(observe(room,date)==dict(held=0,booked=0),'Concurrent counters released after cancellation')
 duplicate=request(customers[0],room,date)
 def replay(_):
  started=time.perf_counter();code,body=a.api('/api/bookings','POST',duplicate,customers[0]['token']);return dict(httpStatus=code,ms=(time.perf_counter()-started)*1000),body
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:duplicates=list(pool.map(replay,range(2)))
 for row,body in duplicates:
  if row['httpStatus']==201:a.record('booking','bookings','id',body['id'])
 a.discover();counter=observe(room,date)
 duplicate_ok=all(row['httpStatus']==201 for row,body in duplicates) and len({body.get('id') for row,body in duplicates})==1 and counter==dict(held=1,booked=0)
 result=json.loads((a.OUT/'concurrency-results.json').read_text());result.update(concurrentSameKeyAttempts=[row for row,body in duplicates],concurrentSameKeyOneBooking=duplicate_ok,concurrentSameKeyCounters=counter,status='IN_PROGRESS' if duplicate_ok else 'FAIL');a.write('concurrency-results.json',result)
 a.check(duplicate_ok,'Concurrent same-key retries create one booking and one hold')
 code,_=a.api('/api/bookings/'+duplicates[0][1]['id'],'DELETE',token=customers[0]['token']);a.check(code==204,'Cancel same-key fixture booking',code)
 a.discover();a.check(observe(room,date)==dict(held=0,booked=0),'Same-key cancellation releases exact one hold')
 result.update(status='PASS',finalCounters=observe(room,date));a.write('concurrency-results.json',result)
 return True
if __name__=='__main__':
 try:
  action=sys.argv[1]
  if action=='expiry-start':start_expiry()
  elif action=='expiry-check':sys.exit(0 if verify_expiry() else 1)
  elif action=='concurrency':sys.exit(0 if concurrency() else 1)
 finally:
  for conn in a.READ_CONNECTIONS.values():conn.close()
