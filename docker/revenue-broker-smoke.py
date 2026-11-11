"""Real bounded retries, commit rollback, DLQ/conversion and private logs in a disposable vhost."""
import argparse
import base64
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--baseline',action='store_true');args=parser.parse_args()
 h.verify_running_clone('revenue-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 env=config['services']['rabbitmq']['environment'];user=env['RABBITMQ_DEFAULT_USER']
 auth='Basic '+base64.b64encode((user+':'+env['RABBITMQ_DEFAULT_PASS']).encode()).decode()
 vhost='revenueprobe'+uuid.uuid4().hex;worker=vhost+'-worker';id=str(uuid.uuid4());marker='synthetic-private-'+uuid.uuid4().hex
 fault='revenue_probe_'+uuid.uuid4().hex;count=0;started=False
 def api(path,body=None,method=None):
  request=urllib.request.Request('http://127.0.0.1:15672'+path,headers={'Authorization':auth,'Content-Type':'application/json'},data=None if body is None else json.dumps(body).encode(),method=method)
  with urllib.request.urlopen(request,timeout=10) as response:
   raw=response.read();return json.loads(raw) if raw and 'json' in response.headers.get('Content-Type','') else None
 def db(query):return h.sql('checkinn_revenue',query)
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def wait(predicate,name):
  deadline=time.monotonic()+90
  while time.monotonic()<deadline:
   result=predicate()
   if result:return result
   time.sleep(.5)
  raise RuntimeError('Timed out: '+name)
 def queue(name):
  try:return api('/api/queues/'+vhost+'/'+name)
  except urllib.error.HTTPError as error:
   if error.code!=404:raise
   return {}
 def take():return api('/api/queues/'+vhost+'/revenue.events.dlq/get',{'count':10,'ackmode':'ack_requeue_false','encoding':'auto'})
 def publish(payload):
  body=payload if isinstance(payload,str) else json.dumps(payload)
  assert api('/api/exchanges/'+vhost+'/hotel.events/publish',{'properties':{'content_type':'application/json','headers':{'private':marker}},'routing_key':'booking.status.changed','payload':body,'payload_encoding':'string'})['routed']
  return body
 def logs():
  result=subprocess.run(['docker','logs',worker],capture_output=True,text=True,encoding='utf-8',timeout=20)
  assert result.returncode==0;return result.stdout+result.stderr
 def drop():db('DROP TRIGGER IF EXISTS '+fault+' ON booking_status_records; DROP FUNCTION IF EXISTS '+fault+'()')
 try:
  api('/api/vhosts/'+vhost,{},'PUT')
  api('/api/permissions/'+vhost+'/'+user,{'configure':'.*','write':'.*','read':'.*'},'PUT')
  api('/api/policies/'+vhost+'/revenue-quarantine-v1',{'pattern':r'^revenue\.(payment|booking)\.queue$','definition':{'dead-letter-exchange':'revenue.dlx','dead-letter-routing-key':'revenue.dead'},'priority':10,'apply-to':'queues'},'PUT')
  db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '"+marker+"'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON booking_status_records DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.booking_id='"+id+"') EXECUTE FUNCTION "+fault+"()")
  h.run(h.COMPOSE+['run','--no-deps','-d','--name',worker,'-e','SPRING_BOOT_ADMIN_CLIENT_ENABLED=false','-e','EUREKA_CLIENT_ENABLED=false','revenue-service','--spring.rabbitmq.virtual-host='+vhost]);started=True
  wait(lambda:queue('revenue.booking.queue').get('consumers',0)==1,'isolated worker consumer')
  event={'bookingId':id,'hotelId':str(uuid.uuid4()),'checkInDate':'2027-01-10','checkOutDate':'2027-01-11','nights':1,'rooms':1,'bookingStatus':'CONFIRMED','eventAt':'2027-01-01T12:00:00','eventVersion':1,'eventId':str(uuid.uuid4())}
  original=publish(event)
  messages=wait(take,'commit failure quarantine')
  check(len(messages)==1 and messages[0]['payload']==original,'database commit failure retains original event in broker DLQ')
  check(logs().count('[REVENUE_BOOKING_INGEST_DEFERRED]')==3,'actual database failure retries exactly three times')
  check(db("SELECT count(*) FROM booking_status_records WHERE booking_id='"+id+"'")=='0','failed source projection commits no false state')
  check(db("SELECT count(*) FROM revenue_event_receipts WHERE booking_id='"+id+"'")=='0','failed projection receipt rolls back with its state')
  leaked=marker in logs()
  if args.baseline:print(json.dumps({'baseline_private_log_leak':leaked,'baseline_precommit_applied_log':'[REVENUE_EVENT_APPLIED]' in logs()}),flush=True)
  else:check(not leaked,'database retry logs exclude raw cause body and private headers')
  if not args.baseline:check('[REVENUE_EVENT_APPLIED]' not in logs(),'staged receipt logs never claim the failed transaction committed')
  drop()
  publish({**event,'eventVersion':2,'eventId':str(uuid.uuid4())})
  wait(lambda:db("SELECT count(*) FROM booking_status_records WHERE booking_id='"+id+"'")=='1','successful projection')
  check(True,'healthy database accepts the next event after quarantine')
  malformed='{"private":"'+marker+'"'
  publish(malformed)
  messages=wait(take,'malformed event quarantine');check(len(messages)==1 and messages[0]['payload']==malformed,'JSON conversion failure is quarantined intact')
  if not args.baseline:check(marker not in logs(),'conversion and success logs also exclude private payload')
 finally:
  if started:h.run(['docker','rm','-f',worker])
  drop();db("DELETE FROM revenue_event_receipts WHERE booking_id='"+id+"'; DELETE FROM booking_status_records WHERE booking_id='"+id+"'")
  api('/api/vhosts/'+vhost,method='DELETE')
 print(json.dumps({'verified_cases':count,'baseline':args.baseline,'synthetic_resources_cleaned':True}),flush=True)
if __name__=='__main__':
 try:main()
 except Exception as error:
  print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True);raise SystemExit(1)
