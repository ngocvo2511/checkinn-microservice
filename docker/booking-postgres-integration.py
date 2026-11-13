"""Opt-in existing PostgreSQL suites in a NEW UUID-owned container.

Copies schema only from the verified disposable Batch 2 project, never data.
Seeds synthetic minimal records; no application or scheduler runs on these DBs.
"""
import importlib.util
import json
import os
import re
import subprocess
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('smoke', ROOT / 'booking-gateway-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
name = 'checkinn-booking-it-' + uuid.uuid4().hex
label = 'checkinn.batch2.integration'
local = ROOT.parent / '.local/evidence/batch-2-booking'
results = []
local.mkdir(parents=True, exist_ok=True)
created = False


def run(args, data=None):
    p = subprocess.run(args, input=data, text=True, encoding='utf-8', capture_output=True, timeout=90)
    if p.returncode:
        (local / 'integration-command-error.log').write_text(p.stdout+p.stderr, encoding='utf-8')
        raise RuntimeError('Private integration command failed')
    return p.stdout.strip()


def sql(db, query, user='checkinn_test'):
    return run(['docker','exec','-i',name,'psql','-X','-v','ON_ERROR_STOP=1','-U',user,'-d',db],query)


try:
    h.verify()
    run(['docker','run','-d','--name',name,'--label',label+'=true','-p','127.0.0.1::5432',
         '-e','POSTGRES_HOST_AUTH_METHOD=trust','postgres:18-alpine'])
    created = True
    deadline=time.monotonic()+60
    while subprocess.run(['docker','exec',name,'pg_isready','-U','postgres'],capture_output=True).returncode:
        if time.monotonic()>deadline:raise RuntimeError('New PostgreSQL not ready')
        time.sleep(.5)
    sql('postgres','CREATE ROLE checkinn_test LOGIN SUPERUSER;','postgres')
    port=run(['docker','port',name,'5432/tcp']).rsplit(':',1)[1]
    for db in ('hotel','booking'):
        run(['docker','exec',name,'createdb','-U','postgres','-O','checkinn_test','checkinn_'+db])
        schema=run(h.COMPOSE+['exec','-T','postgres-smoke','pg_dump','-U','smoke_runner','--schema-only','--no-owner','--no-privileges','checkinn_smoke_'+db])
        sql('checkinn_'+db,schema)
    city,hotel,room,booking,owner=(str(uuid.uuid4()) for _ in range(5))
    sql('checkinn_hotel',f"""INSERT INTO cities(id,name,hotel_count,created_at) VALUES ('{city}','Synthetic integration city',0,now());
INSERT INTO hotels(id,owner_id,city_id,name,address,business_license_number,approved_status,is_active,revision,created_at,updated_at)
VALUES ('{hotel}','{owner}','{city}','Synthetic integration hotel','{{}}'::jsonb,'SYNTHETIC','APPROVED',true,0,now(),now());
INSERT INTO room_types(id,hotel_id,name,base_price,capacity,is_active,total_rooms,created_at,updated_at)
VALUES ('{room}','{hotel}','Synthetic integration room',125000.50,'{{"adults":2,"children":1}}'::jsonb,true,2,now(),now());""")
    sql('checkinn_booking',f"""INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,created_at,updated_at)
VALUES ('{booking}','{owner}','{hotel}','Synthetic integration hotel','2027-01-10','2027-01-11',1,0,'PENDING',125000.50,0,'Synthetic','synthetic@example.invalid','0900000000',now(),now());""")
    for db,suite in [('hotel','AvailabilityRangeIntegrationTest'),('booking','BookingEventOutboxIntegrationTest')]:
        env=dict(os.environ,CHECKINN_RANGE_TEST='true',CHECKINN_RANGE_DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/checkinn_{db}',CHECKINN_RANGE_DB_PASSWORD='')
        log=local/(suite+'.log')
        with log.open('w',encoding='utf-8') as out:
            p=subprocess.run(['mvn.cmd','-o','-B','-ntp','-pl',db+'-service','-Dtest='+suite,'test'],cwd=ROOT.parent,env=env,stdout=out,stderr=subprocess.STDOUT,timeout=300)
        text=log.read_text(encoding='utf-8');counts=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*\n',text)
        row=dict(suite=suite,exit=p.returncode,build='SUCCESS' if 'BUILD SUCCESS' in text else 'FAILURE',totals=counts[-1] if counts else None,broker='mock RabbitTemplate' if db=='booking' else 'not used')
        results.append(row);print(json.dumps(row),flush=True)
        if p.returncode or row['build'] != 'SUCCESS' or not row['totals'] or tuple(map(int, row['totals'])) != (1, 0, 0, 0):
            raise RuntimeError('Expected exactly one executed integration test with no failures, errors or skips')
finally:
    (ROOT.parent/'Docs/evidence/batch-2-booking/postgres-integration-results.json').write_text(json.dumps(results,indent=2))
    if created and run(['docker','inspect','--format','{{index .Config.Labels "'+label+'"}}',name])=='true':
        run(['docker','rm','-f','--volumes',name])
if not results or any(x['exit'] for x in results):raise SystemExit(1)
