"""Legacy unknown room facts, partial owner edits and existing six-field API contract."""
import importlib.util,json,os,secrets,subprocess,time,uuid
from pathlib import Path
from urllib.parse import urlencode
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 h.verify_running_clone('hotel-service');config=json.loads(h.run(h.COMPOSE+['config','--format','json']));hotel,owner=[str(uuid.uuid4()) for _ in range(2)];rooms=[str(uuid.uuid4()) for _ in range(2)];marker='unknowncheck_'+secrets.token_hex(12);count=0
 token=h.token(config['services']['hotel-service']['environment']['SECRET_KEY'],owner,'OWNER')
 def db(q):return h.sql('checkinn_hotel',q)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def fingerprint(table):return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM "+table+' r')
 hotels_before=fingerprint('hotels');rooms_before=fingerprint('room_types')
 def api(method,path,body=None,authorized=False):
  settings='request = '+json.dumps(method)+'\nheader = "Content-Type: application/json"\n'
  if authorized:settings+='header = '+json.dumps('Authorization: Bearer '+token)+'\n'
  if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
  raw=h.run(h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','20','--config','-','-w','\n%{http_code}','http://localhost:8083'+path],settings)
  text,_,status=raw.rpartition('\n')
  try:value=json.loads(text)
  except ValueError:value=None
  return int(status),value
 try:
  check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','"+marker+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','owned synthetic hotel exists')
  for room in rooms:
   check(db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic unknown room','description','Original','base_price',200000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','scoped legacy-room fixture created')
  db("UPDATE room_types SET capacity='null'::jsonb WHERE id='"+rooms[0]+"'; UPDATE room_types SET total_rooms=NULL WHERE id='"+rooms[1]+"'")
  status,value=api('GET','/api/v1/'+rooms[0]);check(status==200 and value.get('capacity') is None,'actual public API can expose a legacy unknown capacity without inventing2/0')
  status,value=api('GET','/api/v1/'+rooms[1]);check(status==200 and value.get('totalRooms') is None and value['capacity']['adults']==2,'unknown configured stock stays distinct from known capacity')
  # Booking already rejects either unknown count. Search must not unbox null or
  # invent a capacity before it reaches inventory; isolate this option by price.
  db("UPDATE room_types SET base_price=100000,capacity='{\"adults\":2,\"children\":null}'::jsonb WHERE id='"+rooms[0]+"'")
  for endpoint in ['/hotels/offers','/hotels/offers/page']:
   status,result=api('GET',endpoint+'?'+urlencode({'hotelName':marker,'minPrice':100000,'maxPrice':100000}))
   check(status==200 and (result==[] if endpoint.endswith('/offers') else result.get('totalElements')==0),'partial legacy child count does not crash search or produce guessed offers '+endpoint+' status='+str(status))
  db("UPDATE room_types SET base_price=200000,capacity='null'::jsonb WHERE id='"+rooms[0]+"'")
  gateway=h.run(h.COMPOSE+['ps','-q','api-gateway']);assert h.run(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',gateway])=='checkinn-test'
  bindings=json.loads(h.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}',gateway]))['8080/tcp'];port=next(b['HostPort'] for b in bindings if b['HostIp'] in ['127.0.0.1','0.0.0.0',''])
  env=dict(os.environ);env['NEXT_PUBLIC_API_BASE_URL']='http://127.0.0.1:'+port;env['CHECKINN_ROOM_OWNER_TOKEN']=token;env['CHECKINN_ROOM_FIXTURES']=json.dumps(rooms)
  script="""import assert from 'node:assert/strict';
import {hotelApi} from './lib/api/hotels.ts';
import {roomFacts,roomEditState,roomUpdatePatch} from './lib/search/room-facts.ts';
globalThis.window={};globalThis.localStorage={getItem:key=>key==='token'?process.env.CHECKINN_ROOM_OWNER_TOKEN:null};
for(const id of JSON.parse(process.env.CHECKINN_ROOM_FIXTURES)){
 const before=await hotelApi.getRoomTypePreview(id,process.env.CHECKINN_ROOM_OWNER_TOKEN);assert.equal(roomFacts(before).hasRequiredDetails,false);
 const edit=roomEditState(before);edit.description='Actual partial owner edit';const patch=roomUpdatePatch(before,edit,before.amenities||[]);assert.deepEqual(patch,{description:'Actual partial owner edit'});
 await hotelApi.updateRoomType(id,patch);const after=await hotelApi.getRoomTypePreview(id,process.env.CHECKINN_ROOM_OWNER_TOKEN);
 assert.equal(after.description,'Actual partial owner edit');assert.deepEqual(after.capacity,before.capacity);assert.equal(after.totalRooms,before.totalRooms);assert.equal(after.basePrice,before.basePrice);
}
"""
  result=subprocess.run(['node','--experimental-strip-types','--input-type=module','-'],input=script,cwd=root.parent.parent/'checkinn-frontend',env=env,capture_output=True,text=True,encoding='utf-8',timeout=60)
  check(result.returncode==0,'real frontend partial-update helper and authorized SDK preserve unknown facts through clone gateway')
  check(db("SELECT capacity::text FROM room_types WHERE id='"+rooms[0]+"'")=='null','unknown capacity remains JSON null after unrelated edit')
  check(db("SELECT total_rooms IS NULL FROM room_types WHERE id='"+rooms[1]+"'")=='t','unknown configured inventory remains SQL null after unrelated edit')
  metadata={'adults':3,'children':1,'bedType':'"Queen bed"','roomSize':24,'breakfastIncluded':True,'breakfastQuantity':2}
  status,_=api('PUT','/api/v1/'+rooms[0],{'capacity':metadata},True);check(status==200,'existing owner API accepts explicit supported six-field capacity review')
  status,value=api('GET','/api/v1/'+rooms[0]);check(status==200 and value['capacity']==metadata,'all six metadata fields already round-trip; unsupported-API suspicion is NOT_AN_ISSUE')
  check(value['basePrice']==200000 and value['totalRooms']==2,'bed/area/breakfast metadata does not silently alter price or configured stock')
  for body in [{'capacity':{'adults':0,'children':0}},{'capacity':{'adults':2}},{'capacity':{'adults':2,'children':0,'roomSize':1.5}},{'capacity':{'adults':2,'children':0,'breakfastQuantity':-1}}]:check(api('PUT','/api/v1/'+rooms[0],body,True)[0]==400,'existing required/minimum/strict-integer validation remains enforced')
  check(api('PUT','/api/v1/'+rooms[1],{'roomAmount':5},True)[0]==409 and db("SELECT total_rooms IS NULL FROM room_types WHERE id='"+rooms[1]+"'")=='t','owner cannot turn unknown stock into a guessed physical count')
  status,value=api('GET','/api/v1/'+rooms[0]);check(status==200 and value['capacity']==metadata,'invalid attempts do not rewrite reviewed metadata')
 finally:
  for room in rooms:db("DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'")
  db("DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='"+marker+"'")
  check(fingerprint('hotels')==hotels_before and fingerprint('room_types')==rooms_before,'exact cleanup leaves all pre-existing hotel/room fingerprints unchanged')
 print(json.dumps({'verified_cases':count}),flush=True)
if __name__=='__main__':main()
