"""Opt-in Batch8C owned Windows lifecycle. No forced termination or remote JMX."""
import contextlib,datetime,importlib.util,json,msvcrt,socket,subprocess,sys,time,urllib.request
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('diag',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
h=d.h; stack=h.stack
SERVICES=('eureka','user','hotel','booking','auth','gateway')

def process(pid):
 r=subprocess.run(['powershell','-NoProfile','-Command',f'Get-CimInstance Win32_Process -Filter "ProcessId={int(pid)}" | Select-Object ProcessId,CreationDate,CommandLine | ConvertTo-Json -Compress'],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=15)
 return json.loads(r.stdout) if r.stdout.strip() else None

def assert_owned(name,p):
 observed=process(p['pid'])
 if not observed:return None
 command=observed['CommandLine']
 assert str(stack.PRIVATE) in command and '--server.port='+str(p['port']) in command and 'batch8c-owned' in command,'Unverified PID ownership'
 assert observed['CreationDate']==p['creationDate'],'PID reused'
 return observed

def assert_port_free(port):
 with socket.socket() as sock:
  sock.setsockopt(socket.SOL_SOCKET,socket.SO_EXCLUSIVEADDRUSE,1)
  sock.bind(('127.0.0.1',port))

def assert_no_duplicate(name):
 module=stack.MODULES[name]
 result=subprocess.run(['powershell','-NoProfile','-Command',"Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | Select-Object -ExpandProperty CommandLine | ConvertTo-Json -Compress"],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=15)
 assert result.returncode==0,'Cannot inventory existing JVMs'
 commands=json.loads(result.stdout) if result.stdout.strip() else []
 if isinstance(commands,str):commands=[commands]
 assert not any(str(R/module/'target') in command for command in commands if command),'Existing module JVM requires ownership review'

def connection_headroom(name):
 if name not in stack.OWNERS:return
 v=stack.values();maximum=int(v[name.upper()+'_DB_POOL_MAX']);samples=[]
 for attempt in range(6):
  result=d.diagnostic(name)
  if result['status']!='PASS':raise RuntimeError('DATABASE_DIAGNOSTIC_BLOCKED')
  cap=result['runtimeRoleCap']
  observed=sum(row['backendRows'] for row in result['backendActivity'] if row['role']=='checkinn_'+name+'_app')
  samples.append(dict(observedRuntimeBackends=observed,roleCap=cap,poolMax=maximum,attempt=attempt))
  d.write(name+'-prestart-headroom.json',samples)
  if cap-observed-maximum>=1:return
  if attempt<5:time.sleep(10)
 raise RuntimeError('INSUFFICIENT_ROLE_HEADROOM_NO_RESTART')

def invalidate_readiness(reason):
 gate=d.OUT/'benchmark-readiness-gate.json'
 if gate.exists():
  value=json.loads(gate.read_text());value.update(passed=False,reason=reason);d.write(gate.name,value)

def start(name):
 assert_no_duplicate(name)
 connection_headroom(name)
 state=stack.state();assert name not in state['processes'],'Recorded process requires review/stop'
 assert_port_free(state['ports'][name])
 assert_port_free(stack.GRPC.get(name,9199)) if name in stack.GRPC else None
 invalidate_readiness('Service start invalidates prior chain readiness: '+name)
 original_env=stack.clean_parent_env;original_values=stack.values;original_popen=stack.subprocess.Popen
 def env():
  e=original_env();e['SPRING_APPLICATION_JSON']=json.dumps({'spring':{'jpa':{'properties':{'hibernate.generate_statistics':True}}}});e.update(MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE='health,info,metrics',SPRING_JPA_PROPERTIES_HIBERNATE_GENERATE_STATISTICS='true',LOGGING_LEVEL_ORG_HIBERNATE_ENGINE_INTERNAL_STATISTICALLOGGINGSESSIONEVENTLISTENER='INFO')
  return e
 def values():
  v=original_values();v.update(SUPABASE_DEV_VNPAY_MODE='simulated',SUPABASE_DEV_STORAGE_MODE='disabled');return v
 def popen(command,**kw):
  if command[0]=='java':command=[arg for arg in command if not arg.startswith('--logging.level.com.zaxxer.hikari=')]+['--spring.application.admin.enabled=true','--server.shutdown=graceful','--spring.lifecycle.timeout-per-shutdown-phase=20s','--batch8c-owned=true','--logging.level.com.zaxxer.hikari=INFO','--logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=INFO']
  return original_popen(command,**kw)
 stack.clean_parent_env=env;stack.values=values;stack.subprocess.Popen=popen
 try:stack.start([name])
 finally:stack.clean_parent_env=original_env;stack.values=original_values;stack.subprocess.Popen=original_popen
 state=stack.state();p=state['processes'][name];p['creationDate']=process(p['pid'])['CreationDate'];p['batch8cOwned']=True;stack.save(state)
 deadline=time.monotonic()+120
 while time.monotonic()<deadline:
  if not assert_owned(name,p):raise RuntimeError('STARTUP_PROCESS_EXITED')
  try:
   with urllib.request.urlopen('http://127.0.0.1:'+str(p['port'])+'/actuator/health',timeout=3) as response:
    if json.load(response).get('status')=='UP':print(name+' HEALTH_UP',flush=True);return
  except Exception:pass
  time.sleep(2)
 raise RuntimeError('BOUNDED_HEALTH_TIMEOUT')

def stop(name):
 state=stack.state();p=state['processes'][name];assert p.get('batch8cOwned')
 if assert_owned(name,p):
  r=subprocess.run(['java','--add-modules','jdk.attach',str(R/'tools/supabase-runner/acceptance/java/OwnedGracefulShutdown.java'),str(p['pid'])],capture_output=True,text=True,timeout=40)
  # A JMX disconnect is possible once shutdown closes the connector; actual exit is authoritative.
  for _ in range(25):
   if not process(p['pid']):break
   time.sleep(1)
  else:raise RuntimeError('GRACEFUL_EXIT_FAILED_NO_FORCE_FALLBACK')
 del state['processes'][name];stack.save(state);print(name+' GRACEFUL_EXIT_VERIFIED',flush=True)

def start_core(label):
 assert not stack.state()['processes'],'A full restart requires all prior owned JVMs to have exited'
 gate=d.OUT/'benchmark-readiness-gate.json'
 if gate.exists():
  value=json.loads(gate.read_text());value.update(passed=False,reason='Restart invalidates prior chain readiness');d.write(gate.name,value)
 for name in SERVICES:start(name)
 spec=importlib.util.spec_from_file_location('smoke',R/'tools/batch8c-smoke.py');smoke=importlib.util.module_from_spec(spec);spec.loader.exec_module(smoke)
 for attempt in range(6):
  if smoke.smoke(label+'-attempt-'+str(attempt)):
   smoke.metrics(label);print('FULL_CHAIN_READY',flush=True);return
  if attempt<5:time.sleep(10)
 raise RuntimeError('CHAIN_READINESS_FAILED_NO_BENCHMARK')

def main():
 with (stack.PRIVATE/'batch8c-lifecycle.lock').open('a+b') as lock:
  lock.seek(0);lock.write(b'0');lock.flush();lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
  try:
   action=sys.argv[1]
   if action=='start':start(sys.argv[2])
   elif action=='start-core':start_core(sys.argv[2] if len(sys.argv)>2 else 'core')
   elif action=='stop':
    for name in sys.argv[2:] or [name for name in reversed(SERVICES) if name in stack.state()['processes']]:stop(name)
   elif action=='start-deps':stack.dependencies(True)
  finally:lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
if __name__=='__main__':main()

