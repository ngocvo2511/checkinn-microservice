"""Real regulation authorization, concurrency, commit failure and broker restart on verified clones.

Run alone. Restarts ONLY the isolated test RabbitMQ/regulations containers. Never edits the
four business rates or copied snapshots; all writes/cleanup use generated synthetic keys.
"""
import base64
import concurrent.futures
import datetime
import importlib.util
import json
from pathlib import Path
import time
import urllib.parse
import urllib.request
import urllib.error
import uuid

ROOT=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)

def main():
 h.verify_running_clone('regulations-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 env=config['services']['regulations-service']['environment'];key='TEST_'+uuid.uuid4().hex
 actor=str(uuid.uuid4());queue='reg-probe-'+uuid.uuid4().hex;exchange=queue+'-exchange'
 fault='reg_probe_'+uuid.uuid4().hex;count=0;stopped=False;source_stopped=False;extra=str(uuid.uuid4())
 since=datetime.datetime.now(datetime.timezone.utc).isoformat()
 marker='synthetic-private-'+uuid.uuid4().hex
 def db(sql):return h.sql('checkinn_regulations',sql)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def wait(predicate,name,seconds=90):
  end=time.monotonic()+seconds
  while time.monotonic()<end:
   if predicate():return
   time.sleep(.5)
  raise RuntimeError('Timed out: '+name)
 def api(path='/api/regulations/snapshots',body=None,role='ADMIN'):
  options='header = "Content-Type: application/json"\n'
  if role:options+='header = '+json.dumps('Authorization: Bearer '+h.token(env['SECRET_KEY'],actor,role))+'\n'
  if body is not None:options+='request = "PUT"\ndata-binary = '+json.dumps(json.dumps(body))+'\n'
  raw=h.run(h.COMPOSE+['exec','-T','regulations-service','curl','-sS','--max-time','25','--config','-','-w','\n%{http_code}','http://localhost:8091'+path],options)
  text,_,code=raw.rpartition('\n')
  try:data=json.loads(text)
  except ValueError:data=None
  return int(code),data
 def body(version,value='2'):return {'regulationKey':key,'name':'Synthetic "quoted"\nname','value':value,'description':marker+'\\line\n','active':True,'version':version}
 def put(version,value='2'):return api('/api/regulations/'+key,body(version,value))
 def healthy(service):
  container=h.run(h.COMPOSE+['ps','-q',service]);return bool(container) and h.run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
 rabbit=config['services']['rabbitmq']['environment']
 auth='Basic '+base64.b64encode((rabbit['RABBITMQ_DEFAULT_USER']+':'+rabbit['RABBITMQ_DEFAULT_PASS']).encode()).decode()
 def broker(method,path,data=None):
  request=urllib.request.Request('http://127.0.0.1:15672/api/'+path,method=method,
    headers={'Authorization':auth,'Content-Type':'application/json'},data=None if data is None else json.dumps(data).encode())
  with urllib.request.urlopen(request,timeout=10) as response:
   raw=response.read();return json.loads(raw) if raw else None
 def messages():return broker('POST','queues/checkinn/'+queue+'/get',{'count':100,'ackmode':'ack_requeue_false','encoding':'auto','truncate':50000})
 def drop():db('DROP TRIGGER IF EXISTS '+fault+' ON regulation_event_outbox; DROP FUNCTION IF EXISTS '+fault+'()')
 try:
  broker('PUT','queues/checkinn/'+queue,{'durable':True,'auto_delete':False,'arguments':{}})
  broker('POST','bindings/checkinn/e/hotel.events/q/'+queue,{'routing_key':'regulation.updated','arguments':{}})
  check(api(role=None)[0]==401,'anonymous audit history denied')
  check(all(api(role=role)[0]==403 for role in ['CUSTOMER','OWNER']),'customer and host audit history denied')
  check(api()[0]==200,'administrator audit history allowed')
  check(api('/api/regulations/typed/points',role=None)[0]==200,'public typed business rates remain readable')
  status,first=put(0)
  check(status==200 and first['version']==1,'new synthetic regulation and source version committed')
  check(db("SELECT source_user='"+actor+"' FROM regulation_snapshots WHERE regulation_key='"+key+"'")=='t','audit actor comes from authenticated principal')
  snapshot=json.loads(db("SELECT snapshot_data FROM regulation_snapshots WHERE regulation_key='"+key+"'"))
  check(snapshot['name']==body(0)['name'] and snapshot['description']==body(0)['description'],'actual audit JSON round trips quoted Unicode/control characters')
  check(api('/api/regulations/'+key,{**body(1),'regulationKey':key+'_OTHER'})[0]==400,'path and body mismatch cannot mutate a different key')
  check(put(0)[0]==409,'stale form is rejected before overwrite')
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:results=list(pool.map(lambda n:put(1,str(n+3))[0],range(8)))
  check(results.count(200)==1 and results.count(409)==7,'eight competing updates have one winner and seven conflicts')
  check(db("SELECT version FROM regulations WHERE regulation_key='"+key+"'")=='2','winning source version advances exactly once')
  check(db("SELECT count(*) FROM regulation_snapshots WHERE regulation_key='"+key+"'")=='2' and db("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key='"+key+"'")=='2','only committed versions produce audit and durable events')
  db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic private commit fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON regulation_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.regulation_key='"+key+"') EXECUTE FUNCTION "+fault+"()")
  status,error=put(2,'99')
  check(status==503 and error.get('code')=='DEPENDENCY_UNAVAILABLE','deferred database commit failure returns a safe unavailable response')
  drop()
  check(db("SELECT version FROM regulations WHERE regulation_key='"+key+"'")=='2' and db("SELECT count(*) FROM regulation_snapshots WHERE regulation_key='"+key+"'")=='2','failed source commit leaves no phantom source or audit revision')
  h.run(h.COMPOSE+['stop','rabbitmq']);stopped=True
  check(put(2,'4')[0]==200,'broker outage cannot lose a committed source update')
  check(db("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key='"+key+"' AND source_version=3 AND state='PENDING'")=='1','broker outage leaves a durable pending event')
  h.run(h.COMPOSE+['stop','regulations-service']);source_stopped=True
  h.run(h.COMPOSE+['start','rabbitmq']);stopped=False
  h.run(h.COMPOSE+['start','regulations-service']);source_stopped=False
  wait(lambda:healthy('rabbitmq') and healthy('regulations-service'),'broker/source healthy',120)
  wait(lambda:db("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key='"+key+"' AND state='PENDING'")=='0','source restart outbox drain')
  check(True,'source restart recovers committed events after broker restart')
  collected=[]
  def collected_versions():
   collected.extend(messages());return {json.loads(m['payload']).get('version') for m in collected if json.loads(m['payload']).get('regulationKey')==key}>={1,2,3}
  wait(collected_versions,'actual routed regulation versions')
  check(not any(json.loads(m['payload']).get('regulationKey')==key and json.loads(m['payload']).get('value')=='99' for m in collected),
        'actual broker receives all committed versions and no rolled-back value')
  db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic delivery acknowledgement fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON regulation_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.regulation_key='"+key+"' AND NEW.source_version=4 AND NEW.state='DELIVERED') EXECUTE FUNCTION "+fault+"()")
  check(put(3,'5')[0]==200,'new committed update survives broker acknowledgement commit failure')
  replays=[]
  def replayed():
   replays.extend(m for m in messages() if json.loads(m['payload']).get('regulationKey')==key and json.loads(m['payload']).get('version')==4)
   return len(replays)>=2
  wait(replayed,'actual broker duplicate delivery')
  check(len({m['properties']['message_id'] for m in replays})==1,'broker accepted and local commit failed replays the same event identity')
  check(db("SELECT state FROM regulation_event_outbox WHERE regulation_key='"+key+"' AND source_version=4")=='PENDING','unknown local completion never falsely marks delivered')
  drop()
  wait(lambda:db("SELECT count(*) FROM regulation_event_outbox WHERE regulation_key='"+key+"' AND state='PENDING'")=='0','acknowledgement replay completion')
  broker('PUT','exchanges/checkinn/'+exchange,{'type':'topic','durable':False,'auto_delete':False,'arguments':{}})
  db("INSERT INTO regulation_event_outbox(id,regulation_key,source_version,exchange_name,payload) SELECT '"+extra+"','"+key+"_UNROUTABLE',1,'"+exchange+"',payload FROM regulation_event_outbox WHERE regulation_key='"+key+"' AND source_version=4")
  wait(lambda:db("SELECT last_error FROM regulation_event_outbox WHERE id='"+extra+"'")=='UNROUTABLE_EVENT','mandatory broker return')
  check(db("SELECT state FROM regulation_event_outbox WHERE id='"+extra+"'")=='PENDING','unroutable publisher confirmation keeps event pending')
  container=h.run(h.COMPOSE+['ps','-q','regulations-service'])
  logs=h.run(['docker','logs','--since',since,container])
  check(marker not in logs and actor not in logs,'application logs omit regulation content and audit actor')
 finally:
  if stopped:h.run(h.COMPOSE+['start','rabbitmq'])
  if source_stopped:h.run(h.COMPOSE+['start','regulations-service'])
  if stopped:wait(lambda:healthy('rabbitmq'),'broker cleanup readiness',120)
  drop()
  db("DELETE FROM regulation_event_outbox WHERE regulation_key='"+key+"' OR id='"+extra+"'; DELETE FROM regulation_snapshots WHERE regulation_key='"+key+"'; DELETE FROM regulations WHERE regulation_key='"+key+"'")
  for resource in ['queues/checkinn/'+queue,'exchanges/checkinn/'+exchange]:
   try:broker('DELETE',resource)
   except urllib.error.HTTPError as error:
    if error.code!=404:raise
  check(db("SELECT count(*) FROM regulations WHERE regulation_key='"+key+"'")=='0','exact synthetic key audit event and broker resource cleanup')
 print(json.dumps({'verified_cases':count,'failures':0}),flush=True)

if __name__=='__main__':
 try:main()
 except Exception as error:
  print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True);raise SystemExit(1)
