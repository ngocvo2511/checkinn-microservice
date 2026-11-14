"""Synthetic positive-points Booking/Hotel/Payment/Refund/Revenue workflow on owned local runtime."""
from pathlib import Path
import json,os,subprocess,time,uuid,urllib.request,urllib.error,base64,hmac,hashlib,datetime
from concurrent.futures import ThreadPoolExecutor
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway';state=json.loads((private/'runtime.private.json').read_text());cfg=json.loads((private/'connections.private.json').read_text());out=r/'Docs/evidence/batch-5b-canonical-flyway/http-workflow-results.json'
checks=[];guest=str(uuid.uuid4());admin=str(uuid.uuid4());hotel=str(uuid.uuid4());room=str(uuid.uuid4())
def check(ok,name,detail=None):
 checks.append({'case':name,'status':'PASS' if ok else 'FAIL','detail':detail})
 out.write_text(json.dumps({'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':checks},indent=2))
 print(name,checks[-1]['status'],flush=True)
 if not ok:raise AssertionError(name+': '+str(detail))
def token(role,subject):
 enc=lambda data:base64.urlsafe_b64encode(data).rstrip(b'=')
 data=enc(b'{"alg":"HS256","typ":"JWT"}')+b'.'+enc(json.dumps({'sub':subject,'role':role,'iat':int(time.time()),'exp':int(time.time())+900}).encode())
 return (data+b'.'+enc(hmac.new(state['secret'].encode(),data,hashlib.sha256).digest())).decode()
def api(owner,path,method='GET',body=None,role='CUSTOMER',subject=None):
 headers={'Authorization':'Bearer '+token(role,subject or guest),'Content-Type':'application/json'}
 request=urllib.request.Request('http://127.0.0.1:'+str(state['ports'][owner])+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:
  with urllib.request.urlopen(request,timeout=20) as response:raw=response.read();code=response.status
 except urllib.error.HTTPError as error:raw=error.read();code=error.code
 try:result=json.loads(raw) if raw else None
 except ValueError:result=raw.decode()[:200]
 return code,result

def sql(owner,statement):
 c=cfg[owner];db=state['processes'][owner]['database'];assert db.startswith('batch5b_'+owner+'_')
 env=os.environ.copy();env['PGPASSWORD']=c['appPassword']
 proc=subprocess.run(['C:/Program Files/PostgreSQL/18/bin/psql.exe','-X','-At','-h','127.0.0.1','-p','55486','-U',c['app'],'-d',db,'-v','ON_ERROR_STOP=1','-c',statement],env=env,capture_output=True,text=True)
 if proc.returncode:raise RuntimeError(proc.stderr)
 return proc.stdout.strip()
def wait(predicate,label,seconds=45):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  if predicate():check(True,label);return
  time.sleep(.5)
 check(False,label,'Timed out')
for owner in state['ports']:
 code,body=api(owner,'/actuator/health');check(code==200 and body.get('status')=='UP',owner+' full application startup',{'code':code,'body':body})
# Test-only fixtures are written with runtime DML credentials. No schema mutations, real users or imported data.
city=sql('hotel','SELECT id FROM cities ORDER BY name LIMIT 1');uuid.UUID(city)
sql('hotel',f"INSERT INTO hotels(id,owner_id,city_id,name,address,business_license_number,is_active,approved_status,accommodation_type,revision,created_at,updated_at) VALUES ('{hotel}','{admin}','{city}','Batch5B HTTP hotel','{{}}','synthetic',true,'APPROVED','HOTEL',0,now(),now()); INSERT INTO room_types(id,hotel_id,name,base_price,capacity,amenities,is_active,total_rooms,created_at,updated_at) VALUES ('{room}','{hotel}','Batch5B HTTP room',1000000,'{{\"adults\":2,\"children\":0}}','[]',true,2,now(),now());")
sql('user',f"INSERT INTO loyalty_points(id,user_id,total_points,used_points,available_points,created_at,updated_at) VALUES ('{uuid.uuid4()}','{guest}',100,0,100,now(),now())")
code,policy=api('booking','/api/bookings/cancellation-policy');check(code==200,'cancellation policy lookup')
request={'idempotencyKey':str(uuid.uuid4()),'userId':guest,'hotelId':hotel,'checkInDate':'2027-02-10','checkOutDate':'2027-02-11','adults':2,'children':0,'contactName':'Synthetic','contactEmail':'synthetic@example.test','contactPhone':'0900000000','pointsToUse':1,'acceptedCancellationPolicyVersion':policy['version'],'items':[{'roomTypeId':room,'quantity':1,'checkInDate':'2027-02-10','checkOutDate':'2027-02-11','unitPrice':1000000}]}
code,booking=api('booking','/api/bookings','POST',request);check(code==201,'Booking to Hotel hold and User Loyalty reservation',{'code':code,'body':booking if code!=201 else None});bid=booking['id'];check(uuid.UUID(bid).version==3,'deterministic Booking UUIDv3')
check(str(booking['totalAmount']) in ['999500','999500.0','999500.00'],'server pricing minus authoritative points quote',booking.get('totalAmount'))
code,retry=api('booking','/api/bookings','POST',request);check(code==201 and retry['id']==bid,'creation retry returns same booking')
check(sql('user',f"SELECT state FROM points_reservations WHERE booking_id='{bid}'")=='HELD','UUID loyalty reservation persisted')
check(sql('booking',f"SELECT count(*) FROM booking_items WHERE booking_id='{bid}'")=='1','one booking item after retry')
code,payment=api('booking','/api/payments','POST',{'bookingId':bid,'method':'HOTEL','amount':999500});check(code==201,'onsite payment attempt creation',{'code':code,'body':payment if code!=201 else None});pid=payment['id']
wait(lambda:sql('booking',f"SELECT onsite_reservation_status FROM bookings WHERE id='{bid}'")=='V1_CONFIRMED','Booking to Hotel inventory commit')
code,paid=api('booking','/api/payments/'+bid+'/confirm','POST',role='ADMIN',subject=admin);check(code==200,'onsite payment receipt',{'code':code,'body':paid if code!=200 else None})
code,_=api('booking','/api/payments/'+bid+'/confirm','POST',role='ADMIN',subject=admin);check(code==200,'duplicate onsite confirmation idempotent')
wait(lambda:sql('revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{pid}' AND kind='CREDIT'")=='1','Payment outbox through real RabbitMQ to Revenue CREDIT')
wait(lambda:sql('user',f"SELECT state FROM points_reservations WHERE booking_id='{bid}'")=='COMMITTED','Booking to Loyalty commit recovery')
code,_=api('booking','/api/bookings/'+bid+'/cancel-reviewed','POST',{'reason':'Synthetic runtime acceptance'},role='ADMIN',subject=admin);check(code==204,'reviewed cancellation')
wait(lambda:sql('hotel',f"SELECT COALESCE(sum(held+booked),0) FROM room_availability WHERE room_type_id='{room}'")=='0','Booking to Hotel release after cancellation')
receipt_query=f"SELECT json_build_object('transaction_id',transaction_id,'vnpay_order_id',vnpay_order_id,'vnpay_transaction_no',vnpay_transaction_no,'amount',amount,'paid_at',paid_at,'transaction_occurred_at',transaction_occurred_at) FROM payments WHERE id='{pid}'"
original_receipt=sql('booking',receipt_query)
code,refund=api('booking','/api/payments/'+pid+'/refund-request','POST',{'reason':'Synthetic internal acceptance'});check(code==201,'audited refund request',code)
code,_=api('booking','/api/payments/'+pid+'/refund-request/approve','POST',role='ADMIN',subject=admin);check(code==200,'refund ADMIN approval',code)
with ThreadPoolExecutor(max_workers=4) as pool:
 codes=list(pool.map(lambda _:api('booking','/api/payments/'+pid+'/refund','POST',role='ADMIN',subject=admin)[0],range(4)))
check(all(code==200 for code in codes),'concurrent internal demo executions',codes)
code,_=api('booking','/api/payments/'+pid+'/refund','POST',role='ADMIN',subject=admin);check(code==200,'duplicate internal execution idempotent',code)
wait(lambda:sql('revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{pid}' AND kind='DEBIT'")=='1','Refund through real RabbitMQ to Revenue DEBIT')
check(sql('revenue',f"SELECT sum(CASE WHEN kind='CREDIT' THEN amount ELSE -amount END) FROM revenue_cash_transactions WHERE payment_id='{pid}'") in ['0','0.00'],'cash ledger balances after refund')
check(sql('booking',f"SELECT count(*) FROM manual_refund_executions WHERE payment_id='{pid}'")=='1','one execution per payment')
check(sql('booking',receipt_query)==original_receipt,'original payment receipt fields preserved exactly')
wait(lambda:sql('user',f"SELECT state FROM points_reservations WHERE booking_id='{bid}'")=='CANCELLED','Loyalty cancellation recovery')
check(sql('user',f"SELECT used_points FROM loyalty_points WHERE user_id='{guest}'")=='0','Loyalty USE reversal exactly once')
(private/'http-fixture-identifiers.json').write_text(json.dumps({'booking':bid,'payment':pid,'user':guest,'room':room,'hotel':hotel},indent=2))
print('Full selected five-service workflow passed',flush=True)
