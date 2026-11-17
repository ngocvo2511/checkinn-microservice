"""Batch8 read-only preflight. Never creates fixtures or invokes migrations."""
import datetime, hashlib, importlib.util, json, re, subprocess, sys, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'Docs/evidence/batch-8-hosted-e2e-performance'
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
spec=importlib.util.spec_from_file_location('cleanup_transport',ROOT/'.local/batch6b/cleanup-fixtures.py')
transport=importlib.util.module_from_spec(spec);spec.loader.exec_module(transport)
def write(name,obj):
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/name).write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def snapshot(owner):
    baseline=json.loads((ROOT/f'Docs/evidence/batch-7-g2-provenance/{owner}-catalog.json').read_text())
    tables=sorted({c['table_name'] for c in baseline['columns']})
    # Only aggregate business data; never export account/customer rows.
    queries=["SELECT json_build_object('table','"+t+"','count',(SELECT count(*) FROM public."+t+"))::text" for t in tables]
    queries += ["SELECT json_build_object('history',json_agg(t))::text FROM (SELECT installed_rank,version,description,type,script,checksum,success FROM public.flyway_schema_history ORDER BY installed_rank) t",
      "SELECT json_build_object('columns',json_agg(t ORDER BY t.table_name,t.column_name))::text FROM (SELECT cl.relname table_name,a.attname column_name,format_type(a.atttypid,a.atttypmod) sql_type,NOT a.attnotnull nullable,pg_get_expr(d.adbin,d.adrelid) default_expression,a.attidentity identity_kind FROM pg_attribute a JOIN pg_class cl ON cl.oid=a.attrelid JOIN pg_namespace ns ON ns.oid=cl.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE ns.nspname='public' AND cl.relkind='r' AND cl.relname<>'flyway_schema_history' AND a.attnum>0 AND NOT a.attisdropped) t",
      "SELECT json_build_object('constraints',json_agg(t))::text FROM (SELECT cl.relname table_name,con.conname name,con.contype kind,con.convalidated validated,pg_get_constraintdef(con.oid) definition FROM pg_constraint con JOIN pg_class cl ON cl.oid=con.conrelid JOIN pg_namespace n ON n.oid=cl.relnamespace WHERE n.nspname='public' AND cl.relname<>'flyway_schema_history' AND con.contype<>'n') t",
      "SELECT json_build_object('indexes',json_agg(t))::text FROM (SELECT tablename table_name,indexname name,indexdef definition FROM pg_indexes WHERE schemaname='public' AND tablename<>'flyway_schema_history') t",
      "SELECT json_build_object('triggers',coalesce(json_agg(t),'[]'))::text FROM (SELECT event_object_table,trigger_name,event_manipulation,action_timing FROM information_schema.triggers WHERE trigger_schema='public') t",
      "SELECT json_build_object('database',current_database(),'schema',current_schema(),'version',current_setting('server_version'),'eventTriggers',(SELECT count(*) FROM pg_event_trigger))::text"]
    raw=transport.sql(owner,';'.join(queries))
    record={'owner':owner,'counts':{r['table']:r['count'] for r in raw if 'table' in r}}
    for r in raw:
        if 'table' not in r:record.update(r)
    diffs=[]
    norm=lambda x:sorted(json.dumps(i,sort_keys=True) for i in x)
    for field in ('columns','constraints','indexes','history'):
        expected=baseline[field]
        if field=='constraints':expected=[v for v in expected if v['kind']!='n']
        if norm(record[field])!=norm(expected):diffs.append(field)
    record['baselineDifferences']=diffs
    record['schemaFingerprint']=hashlib.sha256(json.dumps({f:norm(record[f]) for f in ('columns','constraints','indexes','history')},sort_keys=True).encode()).hexdigest()
    return record
def main():
    phase=sys.argv[1] if len(sys.argv)>1 else 'before'
    if phase=='before':
        write('fixture-manifest.json',{'testRunId':'batch8-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8],'records':[],'writesAllowed':False,'cleanupStatus':'No fixture created; safety gate pending'})
        for owner in h.GROUPS:
            source=h.PRIVATE/f'{owner}.hosted-runtime-readonly-2026-11-17.json'
            if source.exists():write(owner+'-runtime-preflight.json',json.loads(source.read_text()))
    records=[]
    for owner in h.GROUPS:
        try:
            record=snapshot(owner);write(f'{owner}-{phase}.json',record);records.append({'owner':owner,'differences':record['baselineDifferences'],'counts':record['counts']})
            print(owner+' metadata '+('MATCH' if not record['baselineDifferences'] else 'DRIFT '+str(record['baselineDifferences'])),flush=True)
        except Exception as e:
            records.append({'owner':owner,'blocked':str(e)});print(owner+' BLOCKED',flush=True)
    write(phase+'-summary.json',records)
    return int(any(r.get('blocked') or r.get('differences') for r in records))
if __name__=='__main__':sys.exit(main())
