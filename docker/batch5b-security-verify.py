"""Negative CONNECT tests using each local runtime role, never administrator fallback."""
from pathlib import Path
import json,os,subprocess,datetime
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway';out=r/'Docs/evidence/batch-5b-canonical-flyway'
cfg=json.loads((private/'connections.private.json').read_text());latest={}
for p in sorted(private.glob('acceptance-run-results-*.json'),key=lambda p:p.stat().st_mtime):
 for x in json.loads(p.read_text()):latest[x['owner']]=x
psql='C:/Program Files/PostgreSQL/18/bin/psql.exe';results=[]
for owner,c in cfg.items():
 env=os.environ.copy();env['PGPASSWORD']=c['appPassword'];checks=[]
 def run(db,sql):return subprocess.run([psql,'-X','-At','-h','127.0.0.1','-p','55486','-U',c['app'],'-d',db,'-v','ON_ERROR_STOP=1','-v','VERBOSITY=verbose','-c',sql],capture_output=True,text=True,env=env)
 own=latest[owner]['database']
 role=run(own,"SELECT json_build_object('user',current_user,'superuser',rolsuper,'create_database',rolcreatedb,'create_role',rolcreaterole,'bypass_rls',rolbypassrls) FROM pg_roles WHERE rolname=current_user")
 assert role.returncode==0;roles=json.loads(role.stdout)
 assert not any(roles[k] for k in ['superuser','create_database','create_role','bypass_rls'])
 targets=[x['database'] for other,x in latest.items() if other!=owner]+['postgres','template1']
 probeenv=env.copy();probeenv.update(BATCH5B_SECURITY_USER=c['app'],BATCH5B_SECURITY_PASSWORD=c['appPassword'])
 driver=sorted((Path.home()/'.m2/repository/org/postgresql/postgresql').glob('*/*.jar'))[-1]
 probe=subprocess.run(['java','--class-path',str(driver),str(r/'docker/Batch5bConnectProbe.java'),*targets],env=probeenv,capture_output=True,text=True)
 assert probe.returncode==0,probe.stderr
 states=dict(line.split('|') for line in probe.stdout.strip().splitlines())
 for target in targets:checks.append({'case':'CONNECT denied','database':target,'sqlstate':states[target],'pass':states[target]=='42501'})
 schema=run(own,"SELECT has_schema_privilege(current_user,'public','CREATE')");assert schema.stdout.strip()=='f'
 history=run(own,'SELECT * FROM flyway_schema_history');assert history.returncode!=0 and '42501' in history.stderr
 immutable={'user':['loyalty_commands','points_transactions'],'hotel':['checkinn_feature_migrations'],'booking':['manual_refund_audit'],'revenue':['revenue_cash_transactions','revenue_event_receipts'],'regulations':['regulation_snapshots']}[owner]
 for table in immutable:
  result=run(own,f"SELECT has_table_privilege(current_user,'{table}','UPDATE') OR has_table_privilege(current_user,'{table}','DELETE')")
  checks.append({'case':'immutable table UPDATE/DELETE denied','table':table,'pass':result.stdout.strip()=='f'})
 results.append({'owner':owner,'database':own,'role':roles,'schema_create_denied':True,'history_access_denied':True,'checks':checks,'status':'PASS' if all(c['pass'] for c in checks) else 'FAIL'})
(out/'security-results.json').write_text(json.dumps({'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results},indent=2))
print(json.dumps([{'owner':x['owner'],'status':x['status'],'negative_checks':len(x['checks'])+2} for x in results],indent=2))
assert all(x['status']=='PASS' for x in results)
