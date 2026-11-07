"""Actual global offer filters/sort/offset pages on exact UUID-scoped clone fixtures."""
import datetime,importlib.util,json,secrets,time,uuid
from pathlib import Path
from urllib.parse import urlencode
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('hotel-service');prefix='pagecheck_'+secrets.token_hex(8);ids=[str(uuid.uuid4()) for _ in range(6)];rooms=[str(uuid.uuid4()) for _ in ids];categories=[str(uuid.uuid4()) for _ in ids];extra_room=str(uuid.uuid4());count=0
 arrival=datetime.date.today()+datetime.timedelta(days=40);departure=arrival+datetime.timedelta(days=2)
 def db(q):return h.sql('checkinn_hotel',q)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def request(params=None,gateway=False,legacy=False):
  query={'hotelName':prefix,'checkIn':arrival.isoformat(),'checkOut':departure.isoformat(),'rooms':1,'adults':2,'page':0,'size':1,'sort':'DEFAULT'};query.update(params or {})
  service='api-gateway' if gateway else 'hotel-service';port='8080' if gateway else '8083';base='/api/hotel' if gateway else '';path='/hotels/offers'+('' if legacy else '/page')
  if legacy:
   for k in ['page','size','sort']:query.pop(k,None)
  raw=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-w','\n%{http_code}','http://localhost:'+port+base+path+'?'+urlencode(query,doseq=True)])
  body,_,status=raw.rpartition('\n');return int(status),json.loads(body),len(body.encode())
 def page(params=None,gateway=False):
  status,body,_=request(params,gateway);check(status==200,'successful paged public request '+str(gateway));return body
 def content_ids(body):return [r['hotel']['id'] for r in body['content']]
 try:
  for i,(id,room,cat) in enumerate(zip(ids,rooms,categories)):
   active='false' if i==4 else 'true';state='PENDING' if i==5 else 'APPROVED';price=[200000,100000,100000,50000,1,1][i];star=5 if i in [2,3] else 4
   check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+id+"','name','"+prefix+str(i)+"','is_active',"+active+",'approved_status','"+state+"','star_rating',"+str(star)+",'accommodation_type','HOTEL'))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','create controlled publication fixture')
   check(db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+id+"','name','Synthetic paged room','base_price',"+str(price)+",'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','create authoritative room offer fixture')
   label='Pool, Indoor' if i in [1,2] else 'Wi-Fi'
   db("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES ('"+cat+"','"+id+"','Synthetic page category',true,now(),now()); INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES ('"+str(uuid.uuid4())+"','"+cat+"','"+label+"',true,now())")
  # Cheap capacity-ineligible option must not set the qualifying hotel's sorted price.
  db("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+extra_room+"','hotel_id','"+ids[0]+"','name','Synthetic ineligible cheap room','base_price',1,'capacity',jsonb_build_object('adults',1,'children',0)))).* FROM room_types r WHERE id='"+rooms[0]+"'")
  db("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+rooms[3]+"','"+arrival.isoformat()+"',0,2,now(),now())")
  for i,score,status in [(0,'8.5','PUBLISHED'),(1,'9.5','PUBLISHED'),(1,'1','PENDING'),(3,'10','PUBLISHED')]:
   db("INSERT INTO hotel_reviews(id,hotel_id,guest_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+ids[i]+"','"+str(uuid.uuid4())+"',"+score+",'Synthetic page review','Synthetic body',0,0,'"+status+"',now(),now())")
  fingerprint=db("SELECT md5(to_jsonb(a)::text) FROM room_availability a WHERE room_type_id='"+rooms[3]+"'")
  deadline=time.monotonic()+120
  while request(gateway=True)[0]!=200:
   if time.monotonic()>deadline:raise RuntimeError('Gateway discovery is not ready')
   time.sleep(1)
  ascending=sorted(ids[1:3])+[ids[0]];descending=[ids[0]]+sorted(ids[1:3])
  for gateway in [False,True]:
   observed=[]
   for n in range(3):
    result=page({'page':n,'sort':'PRICE_ASC'},gateway);observed+=content_ids(result)
    check(result['page']==n and result['size']==1 and result['totalElements']==3 and result['totalPages']==3 and result['sort']=='PRICE_ASC','correct global totals and page metadata')
    check(result['content'][0]['offer']['availableRooms']==2 and result['content'][0]['offer']['checkedAt'] is not None,'dated inventory stays attached to matched room')
   check(observed==ascending and len(set(observed))==3,'global ascending price pages have stable ID ties and no missing/duplicate fixtures')
   result=page({'size':50,'sort':'PRICE_DESC'},gateway);check(content_ids(result)==descending,'descending prices keep ID ties ascending and never use the ineligible cheap option')
   result=page({'size':50,'sort':'RATING_DESC'},gateway);check(content_ids(result)==[ids[1],ids[0],ids[2]] and result['content'][-1]['reviews']['averageRating'] is None,'published ratings sort globally with unrated last; sold-out score10 excluded')
   result=page({'starRatings':5},gateway);check(content_ids(result)==[ids[2]] and result['totalElements']==1,'star filter precedes page and count')
   result=page({'guestRatings':'9'},gateway);check(content_ids(result)==[ids[1]] and result['totalElements']==1,'guest threshold uses published aggregate before page')
   result=page({'amenities':['Pool, Indoor'],'size':1},gateway);check(result['totalElements']==2 and content_ids(result)==sorted(ids[1:3])[:1],'literal comma label is one filter before pagination')
   check(set(result['availableAmenities'])=={'Pool, Indoor','Wi-Fi'},'global observed amenity options do not shrink with current page')
   result=page({'starRatings':5,'guestRatings':8},gateway);check(result['totalElements']==0 and result['content']==[] and result['totalPages']==0,'unrated cannot satisfy a guest threshold or borrow sold-out reviews')
   result=page({'minPrice':150000,'maxPrice':250000},gateway);check(content_ids(result)==[ids[0]] and result['content'][0]['offer']['unitPrice']==200000,'price filter and order use the same capacity-qualified room')
   result=page({'accommodationTypes':'VILLA'},gateway);check(result['totalElements']==0,'type filter applied globally')
   result=page({'page':99},gateway);check(result['content']==[] and result['totalElements']==3 and result['totalPages']==3,'out-of-range page preserves truthful total')
  for params in [{'page':-1},{'page':'1.5'},{'size':0},{'size':51},{'sort':'POPULAR'},{'starRatings':6},{'guestRatings':11},{'amenities':''},{'rooms':0},{'checkIn':'bad-date'}]:check(request(params)[0]==400,'invalid request remains400 without unbounded fallback')
  status,legacy,old_bytes=request(legacy=True);check(status==200 and isinstance(legacy,list) and len(legacy)==3,'legacy list shape and eligibility remain compatible')
  status,result,new_bytes=request();check(status==200 and len(result['content'])==1,'new API bounds returned hotel content')
  check(db("SELECT md5(to_jsonb(a)::text) FROM room_availability a WHERE room_type_id='"+rooms[3]+"'")==fingerprint,'all searches leave inventory ledger untouched')
  print(json.dumps({'observation':{'fixture_hotels':6,'fixture_rooms':7,'fixture_review_rows':4,'eligible_hotels':3,'page_size':1,'legacy_json_bytes':old_bytes,'paged_json_bytes':new_bytes,'not_a_throughput_or_latency_claim':True}}),flush=True)
 finally:
  for id,room,cat in zip(ids,rooms,categories):
   db("DELETE FROM hotel_reviews WHERE hotel_id='"+id+"'; DELETE FROM hotel_amenities WHERE category_id='"+cat+"'; DELETE FROM hotel_amenity_categories WHERE id='"+cat+"' AND hotel_id='"+id+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+id+"'")
  db("DELETE FROM room_types WHERE id='"+extra_room+"' AND hotel_id='"+ids[0]+"'")
  for id in ids:db("DELETE FROM hotels WHERE id='"+id+"' AND name LIKE '"+prefix+"%'")
  check(db("SELECT count(*) FROM hotels WHERE name LIKE '"+prefix+"%'")=='0','exact synthetic page fixtures removed')
 print(json.dumps({'verified_cases':count}),flush=True)
if __name__=='__main__':main()
