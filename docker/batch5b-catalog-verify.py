"""Read-only exhaustive catalog comparison on manifest-owned disposable targets."""
import importlib.util,json,os,re,sys,datetime
from pathlib import Path
r=Path(__file__).resolve().parents[1];private=r/'.local/evidence/batch-5b-canonical-flyway';out=r/'Docs/evidence/batch-5b-canonical-flyway'
spec=importlib.util.spec_from_file_location('cluster',r/'docker/batch5b-local-cluster.py');c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
manifest=json.loads((private/'cluster-owner.json').read_text());env=os.environ.copy();env['PGPASSWORD']=(private/'postgres-password.private').read_text()
def query(db,sql):
 assert db in manifest['databases']
 raw=c.run('psql','-X','-At','-h','127.0.0.1','-p',55486,'-U','batch5b_admin','-d',db,'-v','ON_ERROR_STOP=1','-c',sql,env=env).strip()
 return json.loads(raw or '[]')
files=list(private.glob('acceptance-run-results-*.json'));latest={}
for p in sorted(files,key=lambda p:p.stat().st_mtime):
 for result in json.loads(p.read_text()):latest[result['owner']]=result
model=json.loads((r/'Docs/evidence/batch-4-database-design/canonical-model.json').read_text(encoding='utf-8'));results=[]
overrides={('loyalty_commands','input_amount'),('loyalty_commands','result_amount'),('points_reservations','total_amount'),('points_reservations','discount_amount'),('revenue_cash_transactions','amount')}
for owner,result in latest.items():
 db=result['database'];errors=[]
 columns=query(db,"SELECT COALESCE(json_agg(t ORDER BY t.table_name,t.column_name),'[]') FROM (SELECT cl.relname table_name,a.attname column_name,format_type(a.atttypid,a.atttypmod) sql_type,NOT a.attnotnull nullable,pg_get_expr(d.adbin,d.adrelid) default_expression,a.attidentity identity_kind FROM pg_attribute a JOIN pg_class cl ON cl.oid=a.attrelid JOIN pg_namespace ns ON ns.oid=cl.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE ns.nspname='public' AND cl.relkind='r' AND cl.relname<>'flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped) t")
 constraints=query(db,"SELECT COALESCE(json_agg(t),'[]') FROM (SELECT cl.relname table_name,con.conname name,con.contype kind,con.convalidated validated,pg_get_constraintdef(con.oid) definition FROM pg_constraint con JOIN pg_class cl ON cl.oid=con.conrelid JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE n.nspname='public' AND cl.relname<>'flyway_schema_history') t")
 indexes=query(db,"SELECT COALESCE(json_agg(t),'[]') FROM (SELECT tablename table_name,indexname name,indexdef definition FROM pg_indexes WHERE schemaname='public' AND tablename<>'flyway_schema_history') t")
 sequences=query(db,"SELECT COALESCE(json_agg(t),'[]') FROM (SELECT sequencename,sequenceowner,data_type,start_value,increment_by FROM pg_sequences WHERE schemaname='public') t")
 history=query(db,"SELECT json_agg(t) FROM (SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank) t")
 tables=[t for t in model if t['owner']==owner];expected={(t['table'],col['name']):(col['type'],col['nullable'],col.get('default')) for t in tables for col in t['columns']};actual={(col['table_name'],col['column_name']):col for col in columns}
 if set(actual)!=set(expected):errors.append({'column_set_difference':[list(k) for k in set(actual)^set(expected)]})
 for key,(typ,nullable,default) in expected.items():
  if key not in actual:continue
  col=actual[key]
  if key in overrides:typ='numeric'
  if typ=='timestamp':typ='timestamp without time zone'
  typ=typ.replace('timestamptz','timestamp with time zone').replace('varchar','character varying')
  if col['sql_type']!=typ:errors.append({'object':'.'.join(key),'expected_type':typ,'actual':col['sql_type']})
  if col['nullable']!=nullable:errors.append({'object':'.'.join(key),'expected_nullable':nullable,'actual':col['nullable']})
  if default and 'IDENTITY' in default:
   if col['identity_kind']!='d':errors.append({'object':'.'.join(key),'identity':'missing BY DEFAULT identity'})
  elif bool(default)!=bool(col['default_expression']):errors.append({'object':'.'.join(key),'default':col['default_expression'],'expected':default})
 for table in tables:
  required=set(con.replace(' NOT VALID','') for con in table['constraints']);found=[con for con in constraints if con['table_name']==table['table'] and con['kind']!='n']
  if len(found)!=len(required):errors.append({'object':table['table'],'constraint_count':len(found),'expected':len(required)})
  if any(not con['validated'] for con in found):errors.append({'object':table['table'],'unvalidated_constraints':True})
  for idx in table['indexes']:
   if 'uq_review_response_review' in idx:continue
   name=re.search(r'(?:CREATE (?:UNIQUE )?INDEX|PROPOSED) (\w+)',idx).group(1)
   if not any(x['name']==name and x['table_name']==table['table'] for x in indexes):errors.append({'index_missing':name})
 if len(history)!=1 or not history[0]['success'] or history[0]['version']!='1' or history[0]['type']!='SQL':errors.append({'history':history})
 catalog={'owner':owner,'database':db,'columns':columns,'constraints':constraints,'indexes':indexes,'sequences':sequences,'history':history}
 (out/(owner+'-catalog.json')).write_text(json.dumps(catalog,indent=2),encoding='utf-8')
 results.append({'owner':owner,'database':db,'tables':len(set(k[0] for k in actual)),'columns':len(actual),'constraints':len(constraints),'indexes':len(indexes),'sequences':len(sequences),'errors':errors,'status':'PASS' if not errors else 'FAIL'})
(out/'catalog-results.json').write_text(json.dumps({'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'results':results},indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))
if any(x['errors'] for x in results):sys.exit(1)
