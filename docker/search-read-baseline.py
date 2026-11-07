"""Measured dated/paged READ baseline on isolated PostgreSQL fixtures; no latency SLO is invented."""
import concurrent.futures
import datetime
import importlib.util
import json
from pathlib import Path
import statistics
import time
import uuid

ROOT=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)

def main():
 h.verify_running_clone('hotel-service')
 hotels=[str(uuid.uuid4()) for _ in range(24)];rooms=[str(uuid.uuid4()) for _ in range(48)]
 prefix='searchprobe'+uuid.uuid4().hex
 day=(datetime.date.today()+datetime.timedelta(days=30));end=day+datetime.timedelta(days=2)
 def db(sql):return h.sql('checkinn_hotel',sql)
 def read():
  path='/hotels/offers/page?hotelName='+prefix+'&checkIn='+day.isoformat()+'&checkOut='+end.isoformat()+'&adults=2&rooms=1&page=0&size=5&sort=PRICE_ASC'
  started=time.perf_counter()
  raw=h.run(h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','20','-w','\n%{http_code}','http://localhost:8083'+path])
  text,_,status=raw.rpartition('\n');assert status=='200','Read baseline requires successful HTTP responses'
  body=json.loads(text);assert body['totalElements']==24 and len(body['content'])==5
  assert [row['offer']['unitPrice'] for row in body['content']]==[100000+i*1000 for i in range(5)]
  assert all(row['offer']['availableRooms']==5 and row['offer']['checkIn']==day.isoformat() and row['offer']['checkOut']==end.isoformat() for row in body['content'])
  return (time.perf_counter()-started)*1000
 try:
  for i,hotel in enumerate(hotels):
   db("INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','name','"+prefix+str(i)+"','is_active',true,'approved_status','APPROVED'))).* FROM hotels h LIMIT 1")
   for j in range(2):db("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+rooms[i*2+j]+"','hotel_id','"+hotel+"','is_active',true,'total_rooms',5,'base_price',"+str(100000+i*1000+j*5000)+",'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
  warmup=read();sequential=[read() for _ in range(10)]
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:parallel=list(pool.map(lambda _:read(),range(8)))
  values=sorted(sequential+parallel)
  assert db("SELECT count(*) FROM room_availability WHERE room_type_id IN ('"+"','".join(rooms)+"')")=='0','Dated READ must not materialize inventory rows'
  print(json.dumps({'result':'PASS','fixture_hotels':24,'fixture_room_types':48,'stay_nights':2,'page_size':5,'total_matches':24,
      'measured_requests':18,'sequential_requests':10,'parallel_requests':8,'concurrency':4,'warmup_ms':round(warmup,1),
      'inclusive_cli_http_ms':{'p50':round(statistics.median(values),1),'p95':round(values[-1],1),'min':round(values[0],1),'max':round(values[-1],1)},
      'environment':'Windows host -> Docker exec/curl -> local hotel image -> PostgreSQL18 test clone','latency_slo':'NOT_DEFINED'}),flush=True)
 finally:
  db("DELETE FROM room_types WHERE id IN ('"+"','".join(rooms)+"'); DELETE FROM hotels WHERE id IN ('"+"','".join(hotels)+"')")
  print(json.dumps({'exact_fixture_cleanup':True}),flush=True)

if __name__=='__main__':
 try:main()
 except Exception as error:
  print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True);raise SystemExit(1)
