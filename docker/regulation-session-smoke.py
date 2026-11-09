"""Regulation session outage classification on the isolated clone; read-only HTTP.

Run alone: restarts ONLY test Redis and preserves all keys/volumes. No tokens or bodies printed.
"""
import importlib.util
import json
from pathlib import Path
import time
import uuid

ROOT=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)

def main():
 h.verify_running_clone('regulations-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 env=config['services']['regulations-service']['environment'];assert env['SPRING_DATA_REDIS_HOST']=='redis'
 actor=str(uuid.uuid4());token=h.token(env['SECRET_KEY'],actor,'ADMIN');stopped=False;count=0
 def check(ok,name):
  nonlocal count
  print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
  if not ok:raise AssertionError(name)
  count+=1
 def api(credential=token,path='/api/regulations/snapshots',method='GET'):
  headers='Authorization: Bearer '+credential+'\n' if credential else ''
  raw=h.run(h.COMPOSE+['exec','-T','regulations-service','curl','-sS','--max-time','20','-X',method,'-H','@-','-w','\n%{http_code}','http://localhost:8091'+path],headers)
  body,_,code=raw.rpartition('\n')
  try:data=json.loads(body)
  except ValueError:data=None
  return int(code),data
 def redis_healthy():
  container=h.run(h.COMPOSE+['ps','-q','redis'])
  return bool(container) and h.run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
 def ready():
  end=time.monotonic()+60
  while time.monotonic()<end:
   if redis_healthy():return
   time.sleep(.5)
  raise RuntimeError('Redis readiness timeout')
 try:
  check(api()[0]==200,'valid administrator session works before Redis outage')
  status,body=api(None,'/api/regulations/synthetic/missing')
  check(status==404 and body.get('code')=='NOT_FOUND','unknown route remains a safe404')
  status,body=api(path='/api/regulations/SYNTHETIC',method='POST')
  check(status==405 and body.get('code')=='METHOD_NOT_ALLOWED','unsupported method remains a safe405')
  malformed=h.token(env['SECRET_KEY'],'synthetic-not-a-user-uuid','ADMIN')
  check(api(malformed)[0]==401,'signed malformed principal cannot become a server error or admin identity')
  h.run(h.COMPOSE+['stop','redis']);stopped=True
  status,body=api()
  check(status==503 and body.get('code')=='SESSION_STORE_UNAVAILABLE','session infrastructure outage is503 and never bypasses authorization')
  check(api(None,'/api/regulations/typed/points')[0]==200,'anonymous public rate reads do not require session infrastructure')
 finally:
  if stopped:h.run(h.COMPOSE+['start','redis']);ready()
 check(api()[0]==200,'the same valid session works after Redis recovery without re-login')
 print(json.dumps({'verified_cases':count,'failures':0}),flush=True)

if __name__=='__main__':
 try:main()
 except Exception as error:
  print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True);raise SystemExit(1)
