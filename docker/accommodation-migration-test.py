"""Apply the explicit feature migration ONLY to an already provisioned checkinn-test clone.
No cloning/reset, no source database connection; copied hotel values must remain identical.
"""
import importlib.util
import json
from pathlib import Path
import subprocess
s=importlib.util.spec_from_file_location('h',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def run(args,data=None):
    p=subprocess.run(args,input=data,capture_output=True,text=True,encoding='utf-8',timeout=45)
    if p.returncode: raise RuntimeError('Clone migration command failed; inspect locally')
    return p.stdout.strip()
h.run=run

def main():
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    assert config['services']['hotel-service']['environment']['SPRING_DATASOURCE_URL']=='jdbc:postgresql://postgres-test:5432/checkinn_hotel'
    # No need to launch an application to apply schema; the clone server/container itself must match.
    cid=h.run(h.COMPOSE+['ps','-q','postgres-test'])
    labels=json.loads(h.run(['docker','inspect','--format','{{json .Config.Labels}}',cid]))
    assert labels['com.docker.compose.project']=='checkinn-test'
    assert h.sql('checkinn_hotel','SELECT current_database(),current_user')=='checkinn_hotel|checkinn_test'
    fingerprint="SELECT count(*)||'|'||md5(coalesce(string_agg((to_jsonb(h)-'accommodation_type'-'revision')::text,'' ORDER BY id),'')) FROM hotels h"
    before=h.sql('checkinn_hotel',fingerprint)
    sql=Path(__file__).with_name('sql').joinpath('hotel-accommodation-type-20261104.sql').read_text(encoding='utf-8')
    for _ in range(2):
        h.run(h.COMPOSE+['exec','-T','postgres-test','psql','-X','-v','ON_ERROR_STOP=1','-U','checkinn_test','-d','checkinn_hotel'],sql)
        assert h.sql('checkinn_hotel',fingerprint)==before
    assert h.sql('checkinn_hotel',"SELECT is_nullable||'|'||coalesce(column_default,'NONE') FROM information_schema.columns WHERE table_name='hotels' AND column_name='accommodation_type'")=='YES|NONE'
    assert h.sql('checkinn_hotel',"SELECT convalidated FROM pg_constraint WHERE conname='hotels_accommodation_type_allowed'")=='t'
    assert h.sql('checkinn_hotel',"SELECT count(*) FROM hotels WHERE accommodation_type IS NOT NULL")=='0'
    assert h.sql('checkinn_hotel',"SELECT count(*) FROM checkinn_feature_migrations WHERE migration_id='hotel-accommodation-type-20261104'")=='1'
    print('PASS: migration twice; identical legacy values/count; nullable/no default; validated CHECK; no backfill; one receipt')
if __name__=='__main__': main()
