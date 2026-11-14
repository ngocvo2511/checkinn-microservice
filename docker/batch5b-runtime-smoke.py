"""Isolated local dependencies and actual canonical application startup/restart smoke."""
from pathlib import Path
import json,subprocess,os,secrets,socket,sys,time,urllib.request,urllib.error,base64
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway';statefile=private/'runtime.private.json'
def docker(*args):
 p=subprocess.run(['docker',*args],capture_output=True,text=True)
 if p.returncode:raise RuntimeError('Docker command failed: '+p.stderr)
 return p.stdout.strip()
def main():
 action=sys.argv[1]
 if action=='dependencies':
  assert not statefile.exists(),'Refuse existing runtime state'
  for port in [55672,55673,55679,*range(55820,55825),*range(55920,55925)]:
   with socket.socket() as s:s.bind(('127.0.0.1',port))
  names=['checkinn-batch5b-rabbit','checkinn-batch5b-redis'];existing=docker('ps','-a','--format','{{.Names}}').splitlines();assert not set(names)&set(existing)
  state={'secret':secrets.token_urlsafe(48),'brokerPassword':secrets.token_urlsafe(32),'containers':names,'processes':{},'ports':{o:55820+i for i,o in enumerate(['user','hotel','booking','revenue','regulations'])}}
  statefile.write_text(json.dumps(state,indent=2))
  docker('run','-d','--name',names[0],'--label','checkinn.owner=batch5b','-p','127.0.0.1:55672:5672','-p','127.0.0.1:55673:15672','-e','RABBITMQ_DEFAULT_USER=batch5b','-e','RABBITMQ_DEFAULT_PASS='+state['brokerPassword'],'rabbitmq:4.1-management')
  docker('run','-d','--name',names[1],'--label','checkinn.owner=batch5b','-p','127.0.0.1:55679:6379','redis:7-alpine')
  print('Started two Batch5B-owned containers with loopback ports; no shared broker/volume.');return
 state=json.loads(statefile.read_text())
 if action=='restart-dependencies':
  for name in state['containers']:
   assert docker('inspect','--format','{{index .Config.Labels "checkinn.owner"}}',name)=='batch5b'
   docker('start',name)
  print('Restarted only owned dependency containers');return
 if action=='bind':
  credentials=base64.b64encode(('batch5b:'+state['brokerPassword']).encode()).decode()
  request=urllib.request.Request('http://127.0.0.1:55673/api/bindings/%2F/e/batch5b.isolated/e/hotel.events',data=json.dumps({'routing_key':'regulation.updated','arguments':{}}).encode(),headers={'Authorization':'Basic '+credentials,'Content-Type':'application/json'},method='POST')
  with urllib.request.urlopen(request,timeout=10) as response:assert response.status in [201,204]
  print('Bound owned test exchange alias to actual Regulation cache subscribers');return
 if action in ['stop','stop-apps']:
  for o,proc in list(state['processes'].items()):
   if len(sys.argv)>2 and o not in sys.argv[2:]:continue
   # Only process IDs started by this harness, with recorded command and isolated ports.
   observed=subprocess.run(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter \"ProcessId={proc['pid']}\").CommandLine"],capture_output=True,text=True).stdout
   if observed.strip():
    assert '--server.port='+str(proc['port']) in observed and 'batch-5b-canonical-flyway' in observed,'PID identity changed'
    subprocess.run(['taskkill','/PID',str(proc['pid']),'/T','/F'],capture_output=True)
   state['processes'].pop(o)
  statefile.write_text(json.dumps(state,indent=2))
  if action=='stop-apps':print('Stopped only owned JVMs');return
  for name in state['containers']:
   label=docker('inspect','--format','{{index .Config.Labels "checkinn.owner"}}',name);assert label=='batch5b'
   docker('stop',name)
  print('Stopped owned JVMs and dependency containers; data/evidence retained.');return
 if action=='start':
  cfg=json.loads((private/'connections.private.json').read_text());latest={}
  for p in sorted(private.glob('acceptance-run-results-*.json'),key=lambda p:p.stat().st_mtime):
   for x in json.loads(p.read_text()):latest[x['owner']]=x
  for i,(owner,port) in enumerate(state['ports'].items()):
   if len(sys.argv)>2 and owner not in sys.argv[2:]:continue
   assert latest[owner]['exit_code']==0 and latest[owner].get('passed',True),owner+' acceptance failed'
   jars=[p for p in (r/(owner+'-service')/'target').glob('*.jar') if not p.name.endswith('.original')];assert len(jars)==1
   c=cfg[owner];env=os.environ.copy();env.update(SECRET_KEY=state['secret'],CANONICAL_DB_URL=f"jdbc:postgresql://127.0.0.1:55486/{latest[owner]['database']}?currentSchema=public",CANONICAL_APP_USER=c['app'],CANONICAL_APP_PASSWORD=c['appPassword'],SPRING_RABBITMQ_HOST='127.0.0.1',SPRING_RABBITMQ_PORT='55672',SPRING_RABBITMQ_USERNAME='batch5b',SPRING_RABBITMQ_PASSWORD=state['brokerPassword'],SPRING_DATA_REDIS_HOST='127.0.0.1',SPRING_DATA_REDIS_PORT='55679',SPRING_CLOUD_CONFIG_ENABLED='false',EUREKA_CLIENT_ENABLED='false',SPRING_BOOT_ADMIN_CLIENT_ENABLED='false',USER_SERVICE_URL='http://127.0.0.1:55820',HOTEL_SERVICE_URL='http://hotel-service',BOOKING_GRPC_ADDRESS='127.0.0.1:55922',REFUND_INTERNAL_ENABLED='true')
   args=['java','-Xmx256m','-Duser.timezone=UTC','-jar',str(jars[0]),'--spring.profiles.active=canonical','--server.address=127.0.0.1','--server.port='+str(port),'--grpc.server.address=127.0.0.1','--grpc.server.port='+str(55920+i),'--spring.jpa.show-sql=false','--spring.cloud.discovery.client.simple.instances.hotel-service[0].uri=http://127.0.0.1:55821','--spring.cloud.discovery.client.simple.instances.user-service[0].uri=http://127.0.0.1:55820','--regulations.service.url=http://127.0.0.1:55824','--logging.file.name='+str(private/(owner+'-runtime-app.log')),'--grpc.client.booking-service.address=static://127.0.0.1:55922','--grpc.client.user-service.address=static://127.0.0.1:55920','--grpc.client.hotel-service.address=static://127.0.0.1:55921','--app.rabbit.exchange=batch5b.isolated','--app.rabbit.payment-queue=batch5b.revenue.payment','--app.rabbit.booking-queue=batch5b.revenue.booking']
   logfile=private/(owner+'-runtime-console.log')
   with logfile.open('a',encoding='utf-8') as log:proc=subprocess.Popen(args,cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
   state['processes'][owner]={'pid':proc.pid,'port':port,'database':latest[owner]['database'],'command':'java -jar '+jars[0].name+' --spring.profiles.active=canonical'}
   statefile.write_text(json.dumps(state,indent=2))
  print('Started selected owned JVMs; use check to observe startup.');return
 if action=='check':
  results=[]
  for owner,port in state['ports'].items():
   try:
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=5) as res:body=json.load(res);code=res.status
   except urllib.error.HTTPError as error:code=error.code;body=error.read().decode()
   except Exception as error:code=0;body=type(error).__name__
   results.append({'owner':owner,'port':port,'status':code,'health':body})
  (r/'Docs/evidence/batch-5b-canonical-flyway/runtime-startup-results.json').write_text(json.dumps(results,indent=2))
  print(json.dumps(results,indent=2));return
 raise ValueError(action)
if __name__=='__main__':main()
