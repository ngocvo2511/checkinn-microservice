"""Public review aggregate contract and payload observations; isolated test clones only."""
import importlib.util,json,secrets,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('hotel-service');prefix='summarycheck_'+secrets.token_hex(8);ids=[str(uuid.uuid4()) for _ in range(4)];missing=str(uuid.uuid4());count=0
 def db(query):return h.sql('checkinn_hotel',query)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def api(path,gateway=False):
  service='api-gateway' if gateway else 'hotel-service';port='8080' if gateway else '8083';base='/api/hotel' if gateway else ''
  start=time.monotonic();raw=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-w','\n%{http_code}','http://localhost:'+port+base+'/api/v1/reviews/'+path]);elapsed=time.monotonic()-start
  text,_,status=raw.rpartition('\n')
  try:body=json.loads(text)
  except ValueError:body=None
  return int(status),body,len(text.encode()),elapsed
 def query(values):return 'summaries?'+__import__('urllib.parse',fromlist=['urlencode']).urlencode([('hotelIds',id) for id in values])
 try:
  sizes=[int(v) for v in db("SELECT (SELECT count(*) FROM hotels)||','||(SELECT count(*) FROM hotel_reviews)").split(',')]
  for i,id in enumerate(ids):
   state='PENDING' if i==3 else 'APPROVED';active='false' if i==2 else 'true'
   check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+id+"','name','"+prefix+str(i)+"','approved_status','"+state+"','is_active',"+active+"))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','create scoped property with explicit publication state')
  for hotel,status,rating in [(ids[0],'PUBLISHED','8.55'),(ids[0],'PUBLISHED','8.56'),(ids[0],'PENDING','10'),(ids[0],'REJECTED','10'),(ids[2],'PUBLISHED','10'),(ids[3],'PUBLISHED','10')]:
   db("INSERT INTO hotel_reviews(id,hotel_id,guest_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+hotel+"','"+str(uuid.uuid4())+"',"+rating+",'Synthetic summary fixture','Synthetic private review body',0,0,'"+status+"',now(),now())")
  for gateway in [False,True]:
   status,rows,_,_=api(query([ids[1],ids[0],ids[0],ids[2],ids[3],missing]),gateway)
   check(status==200 and isinstance(rows,list),'public bounded summary request succeeds '+str(gateway))
   check([r['hotelId'] for r in rows]==[ids[1],ids[0]],'deduplicate and preserve request order; omit inactive/pending/missing')
   check(rows[0]['totalReviews']==0 and rows[0]['averageRating'] is None,'known no reviews has null score and explicit zero count')
   check(rows[1]['totalReviews']==2 and rows[1]['averageRating']==8.56,'published-only aggregate uses existing two-decimal rounding')
   check(all(set(r)=={'hotelId','totalReviews','averageRating'} for r in rows),'no review content, guest identity or feedback in search facts')
  for path in ['summaries','summaries?hotelIds=not-a-uuid','summaries?hotelIds=','summaries?hotelIds='+ids[0]+'&hotelIds=',query([str(uuid.uuid4()) for _ in range(101)])]:
   check(api(path)[0]==400,'invalid/missing/oversized request rejected before summary')
  check(api(query([ids[0]]*100))[0]==200,'documented maximum raw input size accepted')
  old_bytes=0;old_seconds=0
  for id in ids[:2]:
   status,row,size,elapsed=api('stats/hotel/'+id);check(status==200,'legacy detail statistics retain their full contract');old_bytes+=size;old_seconds+=elapsed
   check(row['totalReviews']==(2 if id==ids[0] else 0),'legacy and summary agree on published counts')
   check(row['averageRating']==(8.56 if id==ids[0] else 0) and len(row['recentReviews'])==(2 if id==ids[0] else 0),'legacy average and recent review contents remain available for detail')
  status,rows,new_bytes,new_seconds=api(query(ids[:2]));check(status==200 and len(rows)==2,'summary measures the same two properties in one request')
  print(json.dumps({'observation':{'environment':'checkinn-test Docker clone, one sequential client, no external provider','existing_hotel_rows':sizes[0],'existing_review_rows':sizes[1],'fixture_hotels':4,'fixture_reviews':6,'searched_hotels':2,'legacy_requests':2,'summary_requests':1,'legacy_json_bytes':old_bytes,'summary_json_bytes':new_bytes,'legacy_total_client_seconds':round(old_seconds,3),'summary_client_seconds':round(new_seconds,3),'includes_compose_curl_overhead':True,'not_a_latency_SLO':True}}),flush=True)
 finally:
  literals=','.join("'"+id+"'" for id in ids)
  db('DELETE FROM hotel_reviews WHERE hotel_id IN ('+literals+"); DELETE FROM hotels WHERE id IN ("+literals+") AND name LIKE '"+prefix+"%'")
  check(db('SELECT count(*) FROM hotels WHERE id IN ('+literals+')')=='0','remove exact synthetic fixtures')
 print(json.dumps({'verified_cases':count}),flush=True)
if __name__=='__main__':main()
