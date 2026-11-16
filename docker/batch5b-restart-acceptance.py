"""Broker outage + durable outbox + real five-process restart and event replay on owned fixtures."""
from pathlib import Path
import json,os,subprocess,time,uuid,urllib.request,urllib.error,base64,hmac,hashlib,datetime
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway';state=json.loads((private/'runtime.private.json').read_text());cfg=json.loads((private/'connections.private.json').read_text());checks=[]
out=r/'Docs/evidence/batch-5b-canonical-flyway/restart-results.json'
def check(ok,label):
 checks.append({'case':label,'status':'PASS' if ok else 'FAIL'});out.write_text(json.dumps({'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'checks':checks},indent=2));print(label,checks[-1]['status'],flush=True)
 if not ok:raise AssertionError(label)
def sql(owner,query):
 c=cfg[owner];env=os.environ.copy();env['PGPASSWORD']=c['appPassword'];db=state['processes'][owner]['database'];assert db.startswith('batch5b_'+owner+'_')
 p=subprocess.run(['C:/Program Files/PostgreSQL/18/bin/psql.exe','-X','-At','-h','127.0.0.1','-p','55486','-U',c['app'],'-d',db,'-v','ON_ERROR_STOP=1','-c',query],env=env,capture_output=True,text=True);assert p.returncode==0,p.stderr;return p.stdout.strip()
def runtime(action):subprocess.run([sys.executable,str(r/'docker/batch5b-runtime-smoke.py'),action],check=True)
def token():
 enc=lambda b:base64.urlsafe_b64encode(b).rstrip(b'=');data=enc(b'{"alg":"HS256","typ":"JWT"}')+b'.'+enc(json.dumps({'sub':str(uuid.uuid4()),'role':'ADMIN','iat':int(time.time()),'exp':int(time.time())+300}).encode());return (data+b'.'+enc(hmac.new(state['secret'].encode(),data,hashlib.sha256).digest())).decode()
def wait(predicate,label,seconds=100):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  try:
   if predicate():check(True,label);return
  except (OSError,urllib.error.URLError):pass
  time.sleep(1)
 check(False,label)
def healthy(owner):
 with urllib.request.urlopen('http://127.0.0.1:'+str(state['ports'][owner])+'/actuator/health',timeout=2) as response:return json.load(response).get('status')=='UP'
import sys
broker='checkinn-batch5b-rabbit'
label=subprocess.check_output(['docker','inspect','--format','{{index .Config.Labels "checkinn.owner"}}',broker],text=True).strip();assert label=='batch5b'
key='BATCH5B_RESTART_'+uuid.uuid4().hex.upper();before=sql('regulations','SELECT count(*) FROM regulations')
subprocess.run(['docker','stop',broker],check=True,stdout=subprocess.DEVNULL)
try:
 body={'regulationKey':key,'name':'Synthetic restart probe','value':'1','description':'Disposable acceptance fixture','active':True,'version':0}
 request=urllib.request.Request('http://127.0.0.1:55824/api/regulations/'+key,data=json.dumps(body).encode(),headers={'Authorization':'Bearer '+token(),'Content-Type':'application/json'},method='PUT')
 with urllib.request.urlopen(request,timeout=15) as response:check(response.status==200,'Regulation write commits while broker is unavailable')
 check(sql('regulations',f"SELECT count(*) FROM regulation_snapshots WHERE regulation_key='{key}'")=='1','Snapshot and source transaction committed once')
 check(sql('regulations',f"SELECT state FROM regulation_event_outbox WHERE regulation_key='{key}'")=='PENDING','Outbox survives broker outage as PENDING')
 runtime('stop-apps')
finally:subprocess.run(['docker','start',broker],check=True,stdout=subprocess.DEVNULL)
runtime('start')
for owner in state['ports']:wait(lambda owner=owner:healthy(owner),owner+' full application restart')
runtime('bind')
wait(lambda:sql('regulations',f"SELECT state FROM regulation_event_outbox WHERE regulation_key='{key}'")=='DELIVERED','Pending outbox delivered after process and broker restart')
check(int(sql('regulations','SELECT count(*) FROM regulations'))==int(before)+1,'Java bootstrap does not duplicate policies after restart')
# Replay original immutable event through the real broker, retaining event ID and payload.
ids=json.loads((private/'http-fixture-identifiers.json').read_text());pid=ids['payment'];bid=ids['booking']
payload=sql('booking',f"SELECT payload FROM booking_event_outbox WHERE booking_id='{bid}' AND routing_key='payment.refunded'")
assert payload;event=json.loads(payload)
credentials=base64.b64encode(('batch5b:'+state['brokerPassword']).encode()).decode()
for _ in range(2):
 data={'properties':{'delivery_mode':2,'content_type':'application/json','message_id':event['eventId']},'routing_key':'payment.refunded','payload':payload,'payload_encoding':'string'}
 request=urllib.request.Request('http://127.0.0.1:55673/api/exchanges/%2F/batch5b.isolated/publish',data=json.dumps(data).encode(),headers={'Authorization':'Basic '+credentials,'Content-Type':'application/json'},method='POST')
 with urllib.request.urlopen(request,timeout=10) as response:check(json.load(response)['routed'],'Duplicate event routed through isolated RabbitMQ')
time.sleep(3)
check(sql('revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{pid}' AND kind='DEBIT'")=='1','Duplicate event after restart creates no second DEBIT')
check(sql('booking',f"SELECT count(*) FROM manual_refund_executions WHERE payment_id='{pid}'")=='1','Refund execution retained exactly once after restart')
print('Restart and broker recovery accepted',flush=True)
