from pathlib import Path
from importlib.machinery import SourceFileLoader
import json
h=SourceFileLoader('h','docker/schema-smoke.py').load_module()
assert json.loads(h.run(['docker','inspect',h.PG]).stdout)[0]['Config']['Labels']['com.docker.compose.project']==h.PROJECT
r=[];snap={}
for svc in ['user','hotel','booking','revenue','regulations']:
 db='migration_final_'+svc;h.sql('postgres','CREATE DATABASE '+db)
 files=sorted((h.ROOT/(svc+'-service/src/main/resources/db/migration')).glob('*.sql'))
 if svc=='booking':files=[h.ROOT/'booking-service/src/main/resources/schema.sql']+files
 if svc=='revenue':files=[h.ROOT/'docker/sql/revenue-event-receipts-v1-20261107.sql']
 if svc=='regulations':files=[h.ROOT/'docker/sql/regulation-event-outbox-v1-20261107.sql']
 for f in files:
  p=h.sql(db,f.read_text(encoding='utf-8-sig'),False)
  (h.RAW/('migration-final-'+svc+'-'+f.name+'.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
  r.append({'service':svc,'database':db,'file':f.relative_to(h.ROOT).as_posix(),'status':'FAIL' if p.returncode else 'PASS','error':p.stderr.strip(),'scope':'published SQL entrypoint only; no JPA','complete_service_schema':False})
  if p.returncode:break
 snap[db]=h.snapshot(db)
h.save('migration-only-results.json',r);h.save('migration-only-schema-manifest.json',snap)
print(json.dumps(r,indent=2))
