"""Owner proposal/admin inventory review, scoped fixtures in verified hotel clone only."""
import concurrent.futures
import importlib.util
import json
import os
import secrets
import subprocess
import uuid
from pathlib import Path

root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
 h.verify_running_clone('hotel-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 secret=config['services']['hotel-service']['environment']['SECRET_KEY']
 hotel,owner,other,admin,room,unknown=[str(uuid.uuid4()) for _ in range(6)]
 marker='stockreview_'+secrets.token_hex(10);fault=marker+'_commit';count=0
 tokens={role:h.token(secret,identity,role) for role,identity in [('OWNER',owner),('ADMIN',admin),('CUSTOMER',other)]}
 tokens['OTHER']=h.token(secret,other,'OWNER')
 tokens['SERVICE']=h.token(secret,'booking-service','BOOKING_SERVICE')
 def db(query):return h.sql('checkinn_hotel',query)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def api(method,path,body=None,role='OWNER'):
  settings='request = '+json.dumps(method)+'\nheader = "Content-Type: application/json"\n'
  if role:settings+='header = '+json.dumps('Authorization: Bearer '+tokens[role])+'\n'
  if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
  raw=h.run(h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','25','--config','-','-w','\n%{http_code}','http://localhost:8083'+path],settings)
  text,_,status=raw.rpartition('\n')
  try:value=json.loads(text)
  except ValueError:value=None
  return int(status),value
 def path(id=room):return '/api/v1/'+id+'/inventory-review'
 def propose(qty,rev=0,id=room):return api('POST',path(id),{'quantity':qty,'revision':rev,'source':'Synthetic fixture: counted against room register'})
 def review(request,rev,approve=True):return api('POST','/admin/inventory-reviews/'+request+'/review',{'revision':rev,'approve':approve,'reason':'Synthetic administrator evidence review'},'ADMIN')
 def stored(id=room):return db("SELECT total_rooms::text||':'||base_price::text FROM room_types WHERE id='"+id+"'")
 def fingerprint(table):return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM "+table+' r')
 before={table:fingerprint(table) for table in ['hotels','room_types','room_availability','room_holds']}
 try:
  check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','"+marker+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','scoped owned property fixture')
  for id,quantity in [(room,10),(unknown,None)]:
   check(db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+id+"','hotel_id','"+hotel+"','name','Synthetic stock review','base_price',200000,'total_rooms',"+('NULL' if quantity is None else str(quantity))+",'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a")=='1','scoped room fixture')
  status,state=api('GET',path());check(status==200 and state['revision']==0 and state['managed'] is False and state['verified'] is False and state['currentQuantity']==10,'existing ten remains unreviewed, never assumed invalid or approved')
  check(api('GET',path(),role=None)[0]==401,'anonymous cannot read inventory review')
  for role in ['OTHER','CUSTOMER']:
   check(api('GET',path(),role=role)[0]==403,'foreign/customer cannot read private evidence '+role)
   check(api('POST',path(),{'quantity':2,'revision':0,'source':'Synthetic source'},role)[0]==403,'foreign/customer cannot propose '+role)
  check(api('POST',path(),{'quantity':2,'revision':0,'source':'Synthetic source'},'ADMIN')[0]==403,'admin cannot manufacture owner proposal')
  for body in [{'quantity':0,'revision':0,'source':'fixture'},{'quantity':2,'revision':-1,'source':'fixture'},{'quantity':2,'revision':0,'source':' '},{'quantity':1.5,'revision':0,'source':'fixture'}]:
   check(api('POST',path(),body)[0]==400,'proposal strict validation')
  check(api('GET','/admin/inventory-reviews',role='OWNER')[0]==403,'owner cannot read administrator queue')
  status,request=propose(2);check(status==201,'owner may propose correction without changing stored stock');request_id=request['id']
  check(stored()=='10:200000.00','proposal preserves old count and price')
  check(propose(3)[0]==409,'stale owner proposal rejected')
  check(api('PUT','/api/v1/'+room,{'roomAmount':11})[0]==409,'managed count cannot bypass administrator review via legacy update')
  check(api('PUT','/api/v1/'+room,{'description':'Synthetic partial edit','roomAmount':10})[0]==200,'unchanged count and unrelated owner edits remain compatible')
  state=api('GET',path())[1];check(state['revision']==1 and state['managed'] and not state['verified'],'pending proposal is not verified stock')
  queue=api('GET','/admin/inventory-reviews',role='ADMIN');check(queue[0]==200 and any(r['id']==request_id for r in queue[1]),'admin sees pending evidence')
  check(api('POST','/admin/inventory-reviews/'+request_id+'/review',{'approve':True,'revision':1,'reason':'Synthetic review'},'OWNER')[0]==403,'owner cannot approve own stock')
  # Two nights, peak combined load3 (not sum6 or max-held+max-booked6).
  db("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+room+"',current_date+500,3,0,now(),now()),('"+str(uuid.uuid4())+"','"+room+"',current_date+501,0,3,now(),now())")
  check(review(request_id,1)[0]==409 and stored()=='10:200000.00','review cannot reduce below existing held/booked peak')
  check(api('GET',path())[1]['revision']==1,'rejected unsafe approval leaves request pending and revision unchanged')
  status,new=propose(3,1);check(status==201,'owner may supersede pending proposal');new_id=new['id']
  check(review(request_id,2)[0]==409,'superseded evidence cannot be approved')
  check(review(new_id,1)[0]==409,'stale administrator revision cannot approve')
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
   replies=list(pool.map(lambda _:review(new_id,2),range(2)))
  check(sorted(r[0] for r in replies)==[200,409],'competing administrators serialize; only one approval commits')
  state=api('GET',path())[1];check(state['verified'] and state['currentQuantity']==3 and state['revision']==3,'approved source establishes current count with revision')
  check(stored()=='3:200000.00','only configured count changes on approval')
  check(review(new_id,3)[0]==409,'approval replay cannot double apply')
  status,request=propose(4,3);check(status==201,'next pending review retains previous approved count')
  check(api('GET',path())[1]['verified'] is True,'previous approved count remains verified while proposed change is pending')
  check(review(request['id'],4,False)[0]==200 and stored()=='3:200000.00','rejection records reason without changing stock')
  status,request=propose(4,5);check(status==201,'new proposal records current snapshot')
  db("UPDATE room_types SET total_rooms=5 WHERE id='"+room+"'")
  check(review(request['id'],6)[0]==409 and api('GET',path())[1]['verified'] is False,'out-of-band stock change invalidates snapshot and verified flag')
  check(review(request['id'],6,False)[0]==200,'admin may reject stale evidence without reverting count')
  status,request=propose(2,0,unknown);check(status==201,'unknown count accepts sourced owner proposal')
  db("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+unknown+"',current_date+500,-1,2,now(),now())")
  check(review(request['id'],1)[0]==409 and api('GET',path(unknown))[1]['currentQuantity'] is None,'malformed counters block approval even with positive combined load')
  db("DELETE FROM room_availability WHERE room_type_id='"+unknown+"'")
  db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic review commit fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON room_inventory_review_requests DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id='"+request['id']+"'::uuid AND NEW.status='APPROVED') EXECUTE FUNCTION "+fault+"()")
  status,_=review(request['id'],1);state=api('GET',path(unknown))[1]
  check(status==500 and state['currentQuantity'] is None and state['revision']==1 and state['latestRequest']['status']=='PENDING' and state['approvedRequest'] is None,'deferred commit failure rolls back stock, decision and revision together')
  db('DROP TRIGGER '+fault+' ON room_inventory_review_requests; DROP FUNCTION '+fault+'()')
  check(review(request['id'],1)[0]==200 and api('GET',path(unknown))[1]['verified'],'administrator may establish unknown stock without guessed defaults')
  # Real frontend SDK traverses the gateway; credentials only enter the child environment.
  gateway=h.run(h.COMPOSE+['ps','-q','api-gateway'])
  assert h.run(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',gateway])=='checkinn-test'
  bindings=json.loads(h.run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}',gateway]))['8080/tcp']
  port=next(b['HostPort'] for b in bindings if b['HostIp'] in ['127.0.0.1','0.0.0.0',''])
  env=dict(os.environ);env.update({'NEXT_PUBLIC_API_BASE_URL':'http://127.0.0.1:'+port,'CHECKINN_OWNER_TOKEN':tokens['OWNER'],'CHECKINN_ADMIN_TOKEN':tokens['ADMIN'],'CHECKINN_REVIEW_ROOM':unknown})
  sdk="""import assert from 'node:assert/strict';
import {inventoryReviewApi} from './lib/api/inventory-review.ts';
let token=process.env.CHECKINN_OWNER_TOKEN;globalThis.window={};globalThis.localStorage={getItem:()=>token};
const room=process.env.CHECKINN_REVIEW_ROOM;const before=await inventoryReviewApi.state(room);assert.equal(before.currentQuantity,2);assert.equal(before.verified,true);
const proposal=await inventoryReviewApi.propose(room,2,before.revision,'Synthetic SDK source');assert.equal(proposal.status,'PENDING');
const pending=await inventoryReviewApi.state(room);assert.equal(pending.currentQuantity,2);assert.equal(pending.verified,true);
token=process.env.CHECKINN_ADMIN_TOKEN;const reviewed=await inventoryReviewApi.review(proposal,false,'Synthetic SDK rejection');assert.equal(reviewed.status,'REJECTED');
"""
  result=subprocess.run(['node','--experimental-strip-types','--input-type=module','-'],input=sdk,cwd=root.parent.parent/'checkinn-frontend',env=env,capture_output=True,text=True,encoding='utf-8',timeout=90)
  check(result.returncode==0,'actual typed frontend SDK owner/admin calls round-trip through gateway without changing stock')
  state=api('GET',path(unknown))[1]
  status,request=propose(1,state['revision'],unknown);check(status==201,'sourced decrease may be reviewed against concurrent hold')
  payload={'roomTypeId':unknown,'checkInDate':'2028-06-01','checkOutDate':'2028-06-02','quantity':2}
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
   approving=pool.submit(review,request['id'],request['revision'])
   holding=pool.submit(api,'POST','/api/v1/availability/hold',payload,'SERVICE')
   replies=[approving.result(),holding.result()]
  check(sorted(r[0] for r in replies)==[200,409],'approval-versus-hold race allows only one incompatible operation')
  check(db("SELECT COALESCE(MAX(held::bigint+booked::bigint),0)<=(SELECT total_rooms FROM room_types WHERE id='"+unknown+"') FROM room_availability WHERE room_type_id='"+unknown+"'")=='t','concurrent review never reduces stock below a committed hold')
  for hold in db("SELECT id FROM room_holds WHERE room_type_id='"+unknown+"' AND status='HELD'").splitlines():
   check(api('POST','/api/v1/availability/hold/'+str(uuid.UUID(hold))+'/release',{},'SERVICE')[0]==200,'review race hold can be released through authorized API')
  history=api('GET','/admin/inventory-reviews/history',role='ADMIN');check(history[0]==200 and any(r['id']==new_id and r['status']=='APPROVED' for r in history[1]),'immutable evidence and reviewer audit retained')
 finally:
  if db("SELECT to_regclass('room_inventory_review_states') IS NOT NULL")=='t':
   db('DROP TRIGGER IF EXISTS '+fault+' ON room_inventory_review_requests; DROP FUNCTION IF EXISTS '+fault+'()')
   db("DELETE FROM room_inventory_review_states WHERE room_id IN ('"+room+"','"+unknown+"'); DELETE FROM room_inventory_review_requests WHERE room_id IN ('"+room+"','"+unknown+"')")
  db("DELETE FROM room_holds WHERE room_type_id IN ('"+room+"','"+unknown+"'); DELETE FROM room_availability WHERE room_type_id IN ('"+room+"','"+unknown+"'); DELETE FROM room_types WHERE id IN ('"+room+"','"+unknown+"') AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='"+marker+"'")
  check(all(fingerprint(t)==v for t,v in before.items()),'scoped cleanup leaves original hotel/room/counter/hold fingerprints unchanged')
 print(json.dumps({'verified_cases':count}),flush=True)

if __name__=='__main__':main()
