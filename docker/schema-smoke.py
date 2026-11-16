"""Batch 3: disposable-only verification. Never connects to a host/shared database.
Run once on an unused checkinn-schema-smoke project; preserves resources/evidence.
"""
from pathlib import Path
import subprocess, json, time, re, hashlib
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'Docs/evidence/batch-3-schema'; RAW=ROOT/'.local/evidence/batch-3-schema'
PROJECT='checkinn-schema-smoke'; PG=PROJECT+'-postgres-1'
COMPOSE=['docker','compose','-p',PROJECT,'-f',str(ROOT/'docker/docker-compose.schema-smoke.yml')]
def run(args, data=None, check=True):
 p=subprocess.run(args,input=None if data is None else data.encode('utf-8'),capture_output=True,timeout=180)
 p.stdout=p.stdout.decode('utf-8');p.stderr=p.stderr.decode('utf-8')
 if check and p.returncode: raise RuntimeError(p.stderr)
 return p

def save(name,value): (OUT/name).write_text(json.dumps(value,ensure_ascii=False,sort_keys=True,indent=2)+'\n',encoding='utf-8')
def sql(db,s,check=True):
 p=run(['docker','exec','-i',PG,'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','schema_runner','-d',db],s,check)
 return p

def snapshot(db):
 queries={
 'columns':"SELECT table_name,column_name,ordinal_position,data_type,udt_name,is_nullable,column_default,numeric_precision,numeric_scale,datetime_precision FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position",
 'constraints':"SELECT c.relname AS table_name,p.conname,p.contype,p.convalidated,pg_get_constraintdef(p.oid,true) AS definition FROM pg_constraint p JOIN pg_class c ON c.oid=p.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' ORDER BY c.relname,p.conname",
 'indexes':"SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname",
 'sequences':"SELECT sequencename,data_type,start_value,min_value,max_value,increment_by,cycle FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename",
 'tables':"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename",
 'extensions':"SELECT extname,extversion FROM pg_extension ORDER BY extname"}
 return {k:json.loads(sql(db,'SELECT coalesce(json_agg(q),\'[]\'::json) FROM ('+v+') q;').stdout) for k,v in queries.items()}

def main():
 OUT.mkdir(parents=True,exist_ok=True);RAW.mkdir(parents=True,exist_ok=True)
 initial={}
 for typ,args in [('containers',['docker','ps','-a','--format','{{.Names}}|{{.Label "com.docker.compose.project"}}']),('volumes',['docker','volume','ls','--format','{{.Name}}']),('networks',['docker','network','ls','--format','{{.Name}}'])]:
  initial[typ]=run(args).stdout.splitlines()
  assert not any(PROJECT in x for x in initial[typ]), 'Project collision; refusing reuse'
 save('docker-initial-ownership.json',initial)
 run(COMPOSE+['up','-d'])
 for _ in range(60):
  if run(['docker','exec',PG,'pg_isready','-U','schema_runner','-d','postgres'],check=False).returncode==0: break
  time.sleep(1)
 info=json.loads(run(['docker','inspect',PG]).stdout)[0]
 assert info['Config']['Labels']['com.docker.compose.project']==PROJECT
 assert not info['HostConfig']['PortBindings']
 assert all(m['Type']=='volume' and m['Name']==PROJECT+'_schema-data' for m in info['Mounts'])
 vol=json.loads(run(['docker','volume','inspect',PROJECT+'_schema-data']).stdout)[0]
 assert vol['Labels']['com.docker.compose.project']==PROJECT
 save('environment.json',{'project':PROJECT,'image':info['Config']['Image'],'image_id':info['Image'],'mounts':[{k:m[k] for k in ('Type','Name','Destination')} for m in info['Mounts']],'ports':info['HostConfig']['PortBindings'],'network':PROJECT+'_schema','shared_mounts':False})
 results=[];snaps={}
 # Real empty PostgreSQL tests, stop each chain at first failure.
 for service in ['user','hotel','booking']:
  db='migration_'+service;sql('postgres','CREATE DATABASE '+db)
  files=sorted((ROOT/(service+'-service/src/main/resources/db/migration')).glob('*.sql'))
  if service=='booking': files=[ROOT/'booking-service/src/main/resources/schema.sql']+files
  for f in files:
   p=sql(db,f.read_text(encoding='utf-8-sig'),False)
   (RAW/('migration-'+service+'-'+f.name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
   results.append({'test':'migration-only','service':service,'file':str(f.relative_to(ROOT)).replace('\\','/'),'status':'PASS' if p.returncode==0 else 'FAIL','error':p.stderr.strip() if p.returncode else None})
   if p.returncode: break
  snaps[db]=snapshot(db)
 save('execution-results.json',results);save('clean-schema-manifest.json',snaps)
 # Isolated current JPA bootstrap with existing images: no credentials or external services.
 for service,image in [('hotel','checkinn/booking-smoke-hotel:batch2'),('booking','checkinn/booking-smoke-booking:batch2'),('user','checkinn/user-service:local'),('revenue','checkinn/revenue-service:local'),('regulations','checkinn/regulations-service:local')]:
  db='current_'+service+('' if service in ['hotel','booking'] else '_service');sql('postgres','CREATE DATABASE '+db)
  name=PROJECT+'-'+service
  args=['docker','run','-d','--name',name,'--label','com.docker.compose.project='+PROJECT,'--network',PROJECT+'_schema']
  env={'SPRING_DATASOURCE_URL':'jdbc:postgresql://'+PG+':5432/'+db,'SPRING_DATASOURCE_USERNAME':'schema_runner','SPRING_DATASOURCE_PASSWORD':'','EUREKA_CLIENT_ENABLED':'false','SPRING_CLOUD_CONFIG_ENABLED':'false','SPRING_BOOT_ADMIN_CLIENT_ENABLED':'false','SECRET_KEY':'batch3-disposable-only-secret-key-012345678901234567890123456789','SPRING_JPA_SHOW_SQL':'false','SPRING_RABBITMQ_HOST':'no-broker','SPRING_DATA_REDIS_HOST':'no-redis'}
  for k,v in env.items(): args+=['-e',k+'='+v]
  run(args+[image]); log=''
  for _ in range(50):
   log=run(['docker','logs',name]).stdout+run(['docker','logs',name]).stderr
   state=run(['docker','inspect','--format','{{.State.Running}}',name]).stdout.strip()
   if 'Started ' in log:
    time.sleep(5)
    p=run(['docker','logs',name]);log=p.stdout+p.stderr
   if 'APPLICATION FAILED TO START' in log or 'Application run failed' in log or 'Started ' in log or state=='false': break
   time.sleep(1)
  (RAW/('current-'+service+'.log')).write_text(log,encoding='utf-8')
  run(['docker','stop','-t','3',name])
  imageid=run(['docker','inspect','--format','{{.Image}}',name]).stdout.strip()
  results.append({'test':'current-bootstrap','service':service,'image':image,'image_id':imageid,'started':bool(re.search(r'Started \w+Application',log)),'status':'PARTIAL' if re.search(r'Started \w+Application',log) and 'Application run failed' not in log else 'BLOCKED','failure_lines':[x for x in log.splitlines() if any(z in x for z in ['Caused by:','Schema','schema','ERROR:'])][-12:]})
  snaps[db]=snapshot(db)
  save('execution-results.json',results);save('clean-schema-manifest.json',snaps)
 save('clean-schema-manifest.json',snaps);save('execution-results.json',results)
 print(json.dumps(results,ensure_ascii=False,indent=2))
if __name__=='__main__': main()
