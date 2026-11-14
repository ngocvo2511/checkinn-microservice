"""Run guarded opt-in canonical migration/validation tests using private local credentials."""
from pathlib import Path
import json,os,re,subprocess,datetime,sys,time,importlib.util
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway'
cfg=json.loads((private/'connections.private.json').read_text())
spec=importlib.util.spec_from_file_location('cluster',r/'docker/batch5b-local-cluster.py');cluster=importlib.util.module_from_spec(spec);spec.loader.exec_module(cluster)
adminenv=os.environ.copy();adminenv['PGPASSWORD']=(private/'postgres-password.private').read_text()
def sql(db,statement):return cluster.run('psql','-X','-At','-h','127.0.0.1','-p',55486,'-U','batch5b_admin','-d',db,'-v','ON_ERROR_STOP=1','-c',statement,env=adminenv)
assert Path(sql('postgres','SHOW data_directory').strip()).resolve()==cluster.DATA.resolve()
runid=str(int(time.time()))+'_'+str(os.getpid());results=[]
for owner in (sys.argv[1:] or list(cfg)):
 c=cfg[owner];env=os.environ.copy()
 db='batch5b_'+owner+'_'+runid
 sql('postgres',f"CREATE DATABASE {db} OWNER {c['migrator']}")
 sql('postgres',f"REVOKE ALL ON DATABASE {db} FROM PUBLIC; GRANT CONNECT ON DATABASE {db} TO {c['app']}")
 sql(db,f"REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO {c['app']}")
 c['url']=f'jdbc:postgresql://127.0.0.1:55486/{db}?currentSchema=public'
 manifest=private/'cluster-owner.json';owned=json.loads(manifest.read_text());owned['databases'].append(db);manifest.write_text(json.dumps(owned,indent=2))
 env['BATCH5B_DATABASE']=db
 env.update(BATCH5B_ACCEPTANCE='true',BATCH5B_URL=c['url'],BATCH5B_MIGRATION_PASSWORD=c['migrationPassword'],BATCH5B_APP_PASSWORD=c['appPassword'],BATCH5B_DATA_DIR=c['data'])
 log=private/(owner+'-acceptance-'+runid+'.log')
 command=['mvn.cmd','-o','-B','-ntp','-pl',owner+'-service','-Dtest=CanonicalPostgresAcceptanceTest','test']
 with log.open('w',encoding='utf-8') as f:p=subprocess.run(command,cwd=r,env=env,stdout=f,stderr=subprocess.STDOUT)
 summary=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',log.read_text(encoding='utf-8'))
 passed=p.returncode==0 and 'BUILD SUCCESS' in log.read_text(encoding='utf-8') and bool(summary) and tuple(map(int,summary[-1]))==(1,0,0,0)
 results.append({'passed':passed,'owner':owner,'database':db,'log':log.name,'port':55486,'exit_code':p.returncode,'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'command':' '.join(command)})
 print(owner,p.returncode,flush=True)
(private/('acceptance-run-results-'+runid+'.json')).write_text(json.dumps(results,indent=2))

if not results or not all(row['passed'] for row in results):
 raise SystemExit(1)
