"""Check/apply the reviewed Vietnam destination catalog to the configured Hotel DB.

Explicit insert-only reference data; never a startup migration or test fixture replay.
Compile HostedReferenceSeed.java into the private approved-classes directory first.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import unicodedata
import uuid
import acceptance_runtime as h

CATALOG=h.ROOT/'config/reference-data/vietnam-destinations-2026.json'

def current():
    query="SELECT json_build_object('provinces',COALESCE((SELECT json_agg(row_to_json(p)) FROM public.provinces p),'[]'::json),'cities',COALESCE((SELECT json_agg(row_to_json(c)) FROM public.cities c),'[]'::json))::text"
    return json.loads(h.sql('hotel',query))

def validate(catalog,before):
    provinces=catalog['provinces'];cities=catalog['cities']
    assert len(provinces)==34 and len(set(provinces))==34
    assert len({name for name,parent in cities})==len(cities)
    for name in provinces+[name for name,parent in cities]:
        assert name==unicodedata.normalize('NFC',name) and ';' not in name
    assert all(parent in provinces for name,parent in cities)
    existingParents={r['id']:r['name'] for r in before['provinces']}
    intended=dict(cities)
    for row in before['cities']:
        if row['name'] in intended:
            assert existingParents.get(row['parent_id'])==intended[row['name']], 'Existing destination parent differs; review rather than overwrite'

def statements(catalog):
    q=lambda value:"'"+value.replace("'","''")+"'"
    result=[]
    for name in catalog['provinces']:
        ident=str(uuid.uuid5(uuid.NAMESPACE_URL,'checkinn/reference/vn34/province/'+name))
        result.append("INSERT INTO public.provinces (id,name,latitude,longitude,hotel_count,created_at) VALUES ("+q(ident)+","+q(name)+",NULL,NULL,0,now()) ON CONFLICT (name) DO NOTHING")
    for name,parent in catalog['cities']:
        ident=str(uuid.uuid5(uuid.NAMESPACE_URL,'checkinn/reference/vn34/destination/'+name))
        result.append("INSERT INTO public.cities (id,name,latitude,longitude,parent_id,hotel_count,created_at) VALUES ("+q(ident)+","+q(name)+",NULL,NULL,(SELECT id FROM public.provinces WHERE name="+q(parent)+"),0,now()) ON CONFLICT (name) DO NOTHING")
    return ';\n'.join(result)

def insert(catalog):
    values=h.stack.values();env=h.stack.clean_parent_env()
    for suffix in ('_MIGRATION_DB_URL','_MIGRATION_DB_USERNAME','_MIGRATION_DB_PASSWORD','_DB_URL','_DB_USERNAME','_DB_PASSWORD'):
        env['HOTEL'+suffix]=values['HOTEL'+suffix]
    env.update(BATCH6B_MANAGEMENT_TOKEN=values[h.GROUPS['hotel']],BATCH6B_TARGET_MANIFEST=str(h.PRIVATE/'targets.properties'),BATCH6B_BACKEND_ROOT=str(h.ROOT))
    cp=os.pathsep.join([str(h.PRIVATE/'approved-classes'),str(h.ROOT/'tools/supabase-runner/target/supabase-runner-1.3.0.jar'),str(h.ROOT/'tools/supabase-runner/target/dependency-10.22.0/*')])
    r=subprocess.run(['java','-cp',cp,'com.checkinn.db.HostedReferenceSeed'],input=statements(catalog),env=env,capture_output=True,text=True,encoding='utf-8',timeout=90)
    if r.returncode:raise RuntimeError('REFERENCE_SEED_FAILED '+','.join(line for line in r.stderr.splitlines() if line.startswith('BLOCKED ')))
    line=next(line for line in r.stdout.splitlines() if line.startswith('REFERENCE_ROWS_INSERTED '))
    return int(line.split()[1])

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['check','apply']);args=parser.parse_args()
    catalog=json.loads(CATALOG.read_text(encoding='utf-8'));before=current();validate(catalog,before)
    print('Reviewed catalog: 34 provinces / '+str(len(catalog['cities']))+' travel destinations; current rows: '+str(len(before['provinces']))+'/'+str(len(before['cities'])),flush=True)
    if args.action=='check':return
    backup=h.PRIVATE/'backups/vietnam-reference-20261116';backup.mkdir(exist_ok=True)
    # Preserve the first preimage when reapplying the idempotent catalog.
    if not (backup/'before.private.json').exists():(backup/'before.private.json').write_text(json.dumps(before,indent=2)+'\n',encoding='utf-8')
    added=insert(catalog);after=current();validate(catalog,after)
    assert set(catalog['provinces'])<={r['name'] for r in after['provinces']}
    assert {name for name,parent in catalog['cities']}<={r['name'] for r in after['cities']}
    for table in ('provinces','cities'):
        existing={r['id']:r for r in after[table]}
        assert all(existing[r['id']]==r for r in before[table]), 'Existing rows must remain unchanged'
    assert insert(catalog)==0,'Repeated seed must insert zero rows'
    result=dict(status='PASS',catalogVersion=catalog['version'],catalogSha256=hashlib.sha256(CATALOG.read_bytes()).hexdigest(),provinces=len(catalog['provinces']),destinations=len(catalog['cities']),rowsInserted=added,immediateReapplyRowsInserted=0,existingRowsUnchanged=True,hotelCountsInitializedToZero=True,coordinates='NULL pending verified geospatial data',schemaChanged=False)
    (backup/'after.private.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf-8')
    out=h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/vietnam-reference-2026-11-16.json';out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result),flush=True)

if __name__=='__main__':main()
