"""Canonical text and JSONB serialization round-trip; synthetic clone fixtures only."""
import importlib.util,json,os,secrets,subprocess,time,uuid
from pathlib import Path
from urllib.parse import urlencode
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('hotel-service');config=json.loads(h.run(h.COMPOSE+['config','--format','json']));hotel,room,owner,cat=[str(uuid.uuid4()) for _ in range(4)];marker='textcheck_'+secrets.token_hex(12);count=0
 credential=h.token(config['services']['hotel-service']['environment']['SECRET_KEY'],owner,'OWNER')
 def db(q):return h.sql('checkinn_hotel',q)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def fingerprint(table):return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM "+table+' r')
 before_hotels=fingerprint('hotels');before_rooms=fingerprint('room_types')
 def api(method,path,body=None,gateway=False,authorized=False):
  service='api-gateway' if gateway else 'hotel-service';port='8080' if gateway else '8083';base='/api/hotel' if gateway else ''
  settings='request = '+json.dumps(method)+'\nheader = "Content-Type: application/json"\n'
  if authorized:settings+='header = '+json.dumps('Authorization: Bearer '+credential)+'\n'
  if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
  raw=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','--config','-','-w','\n%{http_code}','http://localhost:'+port+base+path],settings)
  value,_,status=raw.rpartition('\n')
  try:result=json.loads(value)
  except ValueError:result=None
  return int(status),result
 try:
  # Application health does not guarantee the gateway's discovery cache is warm.
  deadline=time.monotonic()+120
  while True:
   status,value=api('GET','/hotels/offers/page?'+urlencode({'hotelName':marker}),gateway=True)
   if status==200 and isinstance(value,dict) and value.get('totalElements')==0:break
   if status not in [502,503,504] or time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: gateway discovery status='+str(status))
   time.sleep(1)
  check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','"+marker+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','create owned synthetic hotel')
  check(db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic text room','base_price',200000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','create synthetic room with known inventory')
  db("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES ('"+cat+"','"+hotel+"','Synthetic literal category',true,now(),now())")
  for text in ['"Quoted amenity"','null','undefined']:
   db("INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES ('"+str(uuid.uuid4())+"','"+cat+"','"+text+"',true,now())")
  name='"'+marker+'"';description='"Tiếng Việt 🌊"\nLiteral null and undefined';address={'street':'"Quoted street"','city':'Synthetic city','country':'VN'}
  status,_=api('PUT','/hotels/'+hotel,{'name':name,'description':description,'address':address},authorized=True);check(status==200,'authorized update accepts ordinary Unicode and quoted text')
  room_name='"Quoted room"';room_description='"Literal room description"\nTiếng Việt 🌊';labels=['"Quoted room amenity"','null','undefined']
  status,_=api('PUT','/api/v1/'+room,{'name':room_name,'description':room_description,'capacity':{'adults':2,'children':0},'amenities':labels},authorized=True);check(status==200,'room text and structured fields update through normal validated API')
  check(db("SELECT jsonb_typeof(address) FROM hotels WHERE id='"+hotel+"'")=='object','real Hibernate address write remains one JSONB object')
  check(db("SELECT jsonb_typeof(capacity)||'|'||jsonb_typeof(amenities) FROM room_types WHERE id='"+room+"'")=='object|array','real Hibernate room write remains object and array, not JSON strings')
  for gateway in [False,True]:
   status,value=api('GET','/hotels/'+hotel,gateway=gateway);check(status==200,'public canonical hotel read '+str(gateway)+' status='+str(status))
   check(value.get('textEncoding')=='PLAIN_V1','hotel API explicitly identifies canonical scalar encoding')
   check(value['name']==name,'hotel scalar name retains literal quotes in actual API serialization')
   check(value['description']==description,'Unicode multiline description survives actual API serialization')
   check(isinstance(value['address'],dict) and value['address']['street']==address['street'],'address object retains quoted text inside, without another decode')
   actual={item['title'] for category in value['amenityCategories'] for item in category['items']};check(actual=={'"Quoted amenity"','null','undefined'},'legacy property labels retain exact literal values')
   nested=next(r for r in value['roomTypes'] if r['id']==room);check(nested['name']==room_name and nested['description']==room_description and nested['amenities']==labels,'nested room text/list values stay canonical and literal')
   status,standalone=api('GET','/api/v1/'+room,gateway=gateway);check(status==200 and standalone.get('textEncoding')=='PLAIN_V1' and standalone['name']==room_name and standalone['amenities']==labels,'standalone room API shares the same versioned literal contract')
   status,page=api('GET','/hotels/offers/page?'+urlencode({'hotelName':marker,'rooms':1,'adults':2}),gateway=gateway);check(status==200 and page['totalElements']==1 and page['content'][0]['hotel']['name']==name,'paged search retains canonical quoted name')
   check(set(page['availableAmenities'])==actual,'search facets preserve quoted/null-like labels exactly')
  # Resolve the host port from this verified test container, never a guessed endpoint.
  gateway=h.run(h.COMPOSE+['ps','-q','api-gateway'])
  assert h.run(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',gateway])=='checkinn-test'
  bindings=json.loads(h.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}',gateway]))['8080/tcp']
  port=next(b['HostPort'] for b in bindings if b['HostIp'] in ['127.0.0.1','0.0.0.0',''])
  env=dict(os.environ);env['NEXT_PUBLIC_API_BASE_URL']='http://127.0.0.1:'+port
  env['CHECKINN_TEXT_EXPECTED']=json.dumps({'hotel':hotel,'room':room,'name':name,'description':description,'roomName':room_name,'roomDescription':room_description,'marker':marker})
  script="""import assert from 'node:assert/strict';
import {hotelApi} from './lib/api/hotels.ts';
const e=JSON.parse(process.env.CHECKINN_TEXT_EXPECTED);
const hotel=await hotelApi.getHotelById(e.hotel);assert.equal(hotel.textEncoding,'PLAIN_V1');assert.equal(hotel.name,e.name);assert.equal(hotel.description,e.description);
const room=await hotelApi.getRoomTypeById(e.room);assert.equal(room.name,e.roomName);assert.equal(room.description,e.roomDescription);
const page=await hotelApi.searchHotelOfferPage({hotelName:e.marker,rooms:1,adults:2,size:1});assert.equal(page.totalElements,1);assert.equal(page.content[0].name,e.name);assert.equal(page.content[0].roomTypes[0].name,e.roomName);
assert.deepEqual(new Set(page.availableAmenities),new Set(['"Quoted amenity"','null','undefined']));
"""
  result=subprocess.run(['node','--experimental-strip-types','--input-type=module','-'],input=script,cwd=root.parent.parent/'checkinn-frontend',env=env,capture_output=True,text=True,encoding='utf-8',timeout=45)
  check(result.returncode==0,'actual frontend adapters consume real clone gateway hotel/room/page responses')
  for literal in ['null','undefined']:
   status,_=api('PUT','/hotels/'+hotel,{'description':literal},authorized=True);check(status==200,'literal null-like text remains valid optional description')
   status,value=api('GET','/hotels/'+hotel);check(status==200 and value['description']==literal,'literal null-like description is not SQL/JSON null')
  check(db("SELECT base_price::int||'|'||total_rooms FROM room_types WHERE id='"+room+"'")=='200000|2','text round-trip does not change price/inventory')
 finally:
  db("DELETE FROM hotel_amenities WHERE category_id='"+cat+"'; DELETE FROM hotel_amenity_categories WHERE id='"+cat+"' AND hotel_id='"+hotel+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
  check(fingerprint('hotels')==before_hotels and fingerprint('room_types')==before_rooms,'exact fixture cleanup and unchanged pre-existing hotel/room fingerprints')
 print(json.dumps({'verified_cases':count}),flush=True)
if __name__=='__main__':main()
