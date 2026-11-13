"""Batch 2: synthetic Gateway lifecycle, only in checkinn-booking-smoke.

Run --bootstrap once after compose startup, then run without arguments. No JWTs,
passwords, customer bodies, or raw HTTP dumps are written to reviewed evidence.
SQL triggers inject failures only into this explicitly disposable project.
"""
from concurrent.futures import ThreadPoolExecutor
import copy
import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import secrets
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PROJECT = 'checkinn-booking-smoke'
COMPOSE = ['docker', 'compose', '-p', PROJECT, '--project-directory', str(ROOT),
           '--env-file', str(ROOT / '.env.booking-smoke'), '-f', str(ROOT / 'docker-compose.yml'),
           '-f', str(ROOT / 'docker-compose.booking-smoke.yml'), '--profile', 'app']
EVIDENCE = ROOT.parent / 'Docs/evidence/batch-2-booking/gateway-results.json'
RESULTS = []
LOCAL = ROOT.parent / '.local/evidence/batch-2-booking'
LOCAL.mkdir(parents=True, exist_ok=True)


def run(args, data=None, timeout=90):
    p = subprocess.run(args, input=data, capture_output=True, text=True, encoding='utf-8', timeout=timeout)
    if p.returncode:
        (ROOT.parent / '.local/evidence/batch-2-booking/last-command-error.log').write_text(p.stdout + p.stderr, encoding='utf-8')
        raise RuntimeError('Isolated command failed; inspect private diagnostics')
    return p.stdout.strip()


def verify():
    for service in ('postgres-smoke', 'booking-service', 'hotel-service', 'user-service'):
        cid = run(COMPOSE + ['ps', '-q', service])
        if not cid:
            raise RuntimeError('Missing isolated service: ' + service)
        info = json.loads(run(['docker', 'inspect', cid]))[0]
        assert info['Config']['Labels']['com.docker.compose.project'] == PROJECT
        if service != 'postgres-smoke':
            db = service.removesuffix('-service')
            assert f'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-smoke:5432/checkinn_smoke_{db}' in info['Config']['Env']
        else:
            assert any(m.get('Name') == PROJECT + '_booking-smoke-postgres-data' for m in info['Mounts'])


def init_env():
    path = ROOT / '.env.booking-smoke'
    if path.exists():
        raise RuntimeError('Refusing to overwrite existing private environment')
    if run(['docker','ps','-aq','--filter','label=com.docker.compose.project='+PROJECT]):
        raise RuntimeError('Refusing new credentials for an existing project')
    path.write_text('\n'.join(['REFUND_SMOKE_POSTGRES_PASSWORD='+secrets.token_urlsafe(24),
        'SECRET_KEY='+secrets.token_urlsafe(40),'RABBIT_USER=smoke_runner',
        'RABBIT_PASS='+secrets.token_urlsafe(24),'GATEWAY_PORT=28080','EUREKA_PORT=28761',
        'RABBIT_MANAGEMENT_PORT=35672','REFUND_SMOKE_INTERNAL_ENABLED=false',
        'SPRING_CLOUD_CONFIG_ENABLED=false','SPRING_CONFIG_IMPORT=','']),encoding='utf-8')
    print('Private isolated environment created; no credentials printed')


def readiness():
    verify()
    ids=run(COMPOSE+['ps','-q']).splitlines()
    info=json.loads(run(['docker','inspect',*ids]))
    services=[]
    for x in info:
        assert x['Config']['Labels']['com.docker.compose.project']==PROJECT
        services.append(dict(service=x['Config']['Labels']['com.docker.compose.service'],
            image_id=x['Image'],healthy=x['State'].get('Health',{}).get('Status')=='healthy',
            mounts=[dict(type=m['Type'],name=m.get('Name'),destination=m['Destination']) for m in x['Mounts']]))
    registry=json.loads(run(COMPOSE+['exec','-T','eureka-server','curl','-fsS','-H','Accept: application/json','http://localhost:8761/eureka/apps']))
    apps={x['name']:[y['status'] for y in x['instance']] for x in registry['applications']['application']}
    required = {'postgres-smoke', 'redis', 'rabbitmq', 'eureka-server', 'user-service',
                'auth-service', 'hotel-service', 'booking-service', 'api-gateway'}
    assert {s['service'] for s in services} == required
    assert all(s['healthy'] for s in services)
    assert all(apps.get(s)==['UP'] for s in ('AUTH-SERVICE','USER-SERVICE','HOTEL-SERVICE','BOOKING-SERVICE','API-GATEWAY'))
    assert http('GET','/api/bookings/cancellation-policy')[0]==200
    (EVIDENCE.parent/'environment-results.json').write_text(json.dumps(dict(project=PROJECT,services=services,eureka=apps,refund_enabled=False),indent=2))
    print('All nine containers healthy, required Eureka registrations UP, Gateway policy route ready')


def sql(db, query):
    assert db in ('hotel', 'booking', 'user')
    return run(COMPOSE + ['exec', '-T', 'postgres-smoke', 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
                         '-U', 'smoke_runner', '-d', 'checkinn_smoke_' + db, '-At'], query)


def http(method, path, body=None, token=None, base='http://127.0.0.1:28080'):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=35) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            value = json.loads(raw) if raw else None
        except ValueError:
            value = None
        return error.code, value


def check(ok, case, **facts):
    row = dict(case=case, result='PASS' if ok else 'FAIL', **facts)
    RESULTS.append(row)
    EVIDENCE.write_text(json.dumps(RESULTS, indent=2), encoding='utf-8')
    print(json.dumps(row), flush=True)
    if not ok:
        raise AssertionError(case)


def bootstrap():
    verify()
    files = ['booking-idempotency-20261105.sql', 'onsite-reservation-v1-20261105.sql',
             'paid-reservation-v1-20261105.sql', 'loyalty-settlement-upgrade.sql',
             'booking-transaction-time-v1-20261107.sql', 'booking-event-outbox-v1-20261107.sql',
             'manual-refund-v1-20261112.sql', 'sandbox-internal-refund-v1-20261112.sql']
    sql('booking', (ROOT.parent / 'booking-service/src/main/resources/db/migration/V3__Cancellation_Policy_And_Refund_Requests.sql').read_text())
    for name in files:
        sql('booking', (ROOT / 'sql' / name).read_text())
    for name in ['V4__Room_Availability.sql']:
        sql('hotel', (ROOT.parent / 'hotel-service/src/main/resources/db/migration' / name).read_text())
    sql('hotel', (ROOT / 'sql/hold-idempotency-20261105.sql').read_text())
    sql('hotel', (ROOT / 'sql/amenity-catalog-v1-20261106.sql').read_text())
    sql('hotel', (ROOT / 'booking-smoke-accommodation-bootstrap.sql').read_text())
    sql('hotel', (ROOT / 'sql/hotel-draft-approval-upgrade.sql').read_text())
    summary = {'bootstrap': 'disposable JPA update followed by additive SQL; not production/legacy certification',
               'booking_sql': ['V3__Cancellation_Policy_And_Refund_Requests.sql'] + files,
               'hotel_sql': ['V4__Room_Availability.sql', 'hold-idempotency-20261105.sql',
                             'amenity-catalog-v1-20261106.sql', 'booking-smoke-accommodation-bootstrap.sql',
                             'hotel-draft-approval-upgrade.sql'],
               'hotel_v5': 'Not replayed: JPA already creates expires_at; historical V5 is not idempotent',
               'indexes': {db: sql(db, "SELECT indexname FROM pg_indexes WHERE schemaname='public' ORDER BY indexname").splitlines() for db in ('hotel','booking')}}
    (EVIDENCE.parent / 'schema-bootstrap.json').write_text(json.dumps(summary, indent=2))
    print('Disposable bootstrap completed', flush=True)


def actor(role='CUSTOMER'):
    tag = uuid.uuid4().hex[:12]
    password = secrets.token_urlsafe(18) + 'Aa1!'
    email = role.lower() + tag + '@example.invalid'
    if role == 'CUSTOMER':
        status, result = http('POST', '/api/auth/register', dict(username='u'+tag, email=email, password=password, fullName='Synthetic Booking Actor'))
        check(status in (200, 201), 'Gateway customer registration', http=status)
        user = result['userId']
        sql('user', f"UPDATE users SET is_email_verified=true WHERE id='{user}';")
    else:
        user = str(uuid.uuid4())
        sql('user', f"CREATE EXTENSION IF NOT EXISTS pgcrypto; INSERT INTO users(id,username,email,password,role,is_email_verified,is_active,created_at) VALUES ('{user}','u{tag}','{email}',crypt('{password}',gen_salt('bf')),'{role}',true,true,now());")
    status, result = http('POST', '/api/auth/login', dict(usernameOrEmail=email, password=password))
    check(status == 200 and result.get('role') == role, 'Gateway real JWT login ' + role, http=status)
    return user, result['token']


def fixture(owner, total=2):
    hotel, city, room = (str(uuid.uuid4()) for _ in range(3))
    sql('hotel', f"""INSERT INTO cities(id,name,hotel_count,created_at) VALUES ('{city}','Synthetic-{city}',0,now());
INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,accommodation_type,revision,created_at,updated_at)
VALUES ('{hotel}','{owner}','{city}','Synthetic Booking Hotel','{{"street":"Synthetic","city":"Synthetic","country":"VN"}}'::jsonb,'APPROVED',true,'SYNTHETIC','HOTEL',0,now(),now());
INSERT INTO room_types(id,hotel_id,name,base_price,capacity,amenities,is_active,total_rooms,created_at,updated_at)
VALUES ('{room}','{hotel}','Synthetic Room',125000.50,'{{"adults":2,"children":1}}'::jsonb,'[]'::jsonb,true,{total},now(),now());""")
    return hotel, room


def body(hotel, room):
    start = datetime.date.today() + datetime.timedelta(days=45)
    end = start + datetime.timedelta(days=2)
    return dict(idempotencyKey=str(uuid.uuid4()), hotelId=hotel, hotelName='Untrusted client name',
                checkInDate=str(start), checkOutDate=str(end), adults=2, children=0,
                contactName='Synthetic Booking Actor', contactEmail='synthetic@example.invalid', contactPhone='0900000000',
                acceptedCancellationPolicyVersion='STANDARD_24H_V1', totalAmount=1,
                items=[dict(roomTypeId=room, roomTypeName='Untrusted client room', ratePlanId=room,
                            checkInDate=str(start), checkOutDate=str(end), quantity=1, unitPrice=125000.50, nights=999)])


def concurrent(fn, count=2):
    barrier = threading.Barrier(count)
    def call(i):
        barrier.wait()
        start = time.monotonic_ns()
        status, result = fn(i)
        return dict(http=status, start_ns=start, end_ns=time.monotonic_ns(), body=result)
    with ThreadPoolExecutor(max_workers=count) as pool:
        return list(pool.map(call, range(count)))


def counters(room):
    raw = sql('hotel', f"SELECT coalesce(json_agg(x ORDER BY date),'[]') FROM (SELECT date,held,booked FROM room_availability WHERE room_type_id='{room}') x;")
    return json.loads(raw)


def holds(room):
    return json.loads(sql('hotel', f"SELECT coalesce(json_agg(x ORDER BY id),'[]') FROM (SELECT id,status,quantity,command_id FROM room_holds WHERE room_type_id='{room}') x;"))


def trigger(db, table, event):
    sql(db, f"CREATE FUNCTION batch2_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'BATCH2_TEST_ONLY_FAILURE'; END $$; CREATE TRIGGER batch2_failure BEFORE {event} ON {table} FOR EACH ROW EXECUTE FUNCTION batch2_fail();")


def untrigger(db, table):
    sql(db, f'DROP TRIGGER IF EXISTS batch2_failure ON {table}; DROP FUNCTION IF EXISTS batch2_fail();')


def main():
    verify()
    customer, token = actor()
    other, other_token = actor()
    owner, _ = actor('OWNER')
    _, admin_token = actor('ADMIN')
    hotel, room = fixture(owner)
    request = body(hotel, room)
    check(not counters(room) and not holds(room), 'Fresh SQL stock: total=2, no counters or holds')
    status, booking = http('POST', '/api/bookings', request, token)
    check(status == 201, 'Create through Gateway with CUSTOMER JWT', http=status)
    bid = booking['id']
    snapshot = sql('booking', f"SELECT user_id,status,total_amount,cancellation_policy_version,hold_id,hold_expires_at IS NOT NULL,hotel_name FROM bookings WHERE id='{bid}';")
    check(snapshot.startswith(f'{customer}|PENDING|250001.00|STANDARD_24H_V1|') and snapshot.endswith('|t|Synthetic Booking Hotel'),
          'SQL canonical owner price policy expiry and catalog name', total='250001.00', status='PENDING')
    check(sql('booking', f"SELECT quantity,unit_price,nights,subtotal,room_type_name FROM booking_items WHERE booking_id='{bid}';") == '1|125000.50|2|250001.00|Synthetic Room', 'SQL canonical booking item ignores client nights and names')
    check(len(holds(room)) == 1 and holds(room)[0]['status'] == 'HELD' and all(x['held']==1 and x['booked']==0 for x in counters(room)) and len(counters(room))==2, 'SQL one hold covers two nights; held=1 booked=0', counters=counters(room))
    original = counters(room)
    status, replay = http('POST', '/api/bookings', request, token)
    check(status==201 and replay['id']==bid and original==counters(room) and len(holds(room))==1, 'Same key retry returns original booking; singleton hold', http=status)
    conflict = copy.deepcopy(request); conflict['adults']=1
    check(http('POST','/api/bookings',conflict,token)[0]==409 and original==counters(room), 'Same key different payload rejected without inventory change')
    check(http('GET','/api/bookings/'+bid,None,other_token)[0]==403 and http('DELETE','/api/bookings/'+bid,None,other_token)[0]==403, 'Non-owner read and cancel denied')
    check(http('POST','/api/bookings',body(hotel,room))[0]==401, 'Anonymous create denied')
    forged=body(hotel,room);forged['userId']=other
    check(http('POST','/api/bookings',forged,token)[0]==403, 'Forged customer ID denied')
    policy=sql('booking',f"SELECT cancellation_policy_version,check_in_at,free_cancellation_until FROM bookings WHERE id='{bid}';")
    cancelled=concurrent(lambda _:http('DELETE','/api/bookings/'+bid,None,token))
    check(all(x['http']==204 for x in cancelled), 'Concurrent owner cancellation', clients=[{k:v for k,v in x.items() if k!='body'} for x in cancelled])
    check(http('DELETE','/api/bookings/'+bid,None,token)[0]==204 and sql('booking',f"SELECT status FROM bookings WHERE id='{bid}';")=='CANCELLED' and holds(room)[0]['status']=='RELEASED' and all(x['held']==x['booked']==0 for x in counters(room)), 'Cancel retry releases exactly once; counters restored', counters=counters(room))
    check(policy==sql('booking',f"SELECT cancellation_policy_version,check_in_at,free_cancellation_until FROM bookings WHERE id='{bid}';") and sql('booking',f"SELECT count(*) FROM refund_requests WHERE booking_id='{bid}';")=='0', 'Cancellation policy immutable; unpaid booking creates no refund')
    # True concurrent first creation, not replay of an already accepted command.
    dup=body(hotel,room); rows=concurrent(lambda _:http('POST','/api/bookings',dup,token))
    ids=[x['body'].get('id') if isinstance(x['body'],dict) else None for x in rows]
    check(all(x['http']==201 for x in rows) and ids[0]==ids[1] and len(holds(room))==2 and all(x['held']==1 for x in counters(room)), 'Concurrent duplicate first create is singleton', clients=[{k:v for k,v in x.items() if k!='body'} for x in rows])
    check(http('POST','/api/bookings/'+ids[0]+'/cancel-reviewed',{'reason':'Synthetic test cancellation'},admin_token)[0]==204, 'ADMIN reviewed cancellation under existing contract')
    # Compete for one actual configured room using distinct commands.
    h2,r2=fixture(owner,1); competition=[body(h2,r2),body(h2,r2)]
    rows=concurrent(lambda i:http('POST','/api/bookings',competition[i],token))
    check(sorted(x['http'] for x in rows)==[201,409] and len(holds(r2))==1 and all(x['held']==1 and x['booked']==0 for x in counters(r2)), 'Distinct concurrent commands compete for stock=1 without oversell', http=[x['http'] for x in rows], counters=counters(r2))
    for x in rows:
        if x['http']==201:http('DELETE','/api/bookings/'+x['body']['id'],None,token)
    # Invalid catalog and input matrix: compare both booking count and whole room hold/counter state.
    def negative(label, change, expected, setup=None, restore=None):
        b=body(hotel,room);change(b)
        before=(sql('booking','SELECT count(*) FROM bookings;'),holds(room),counters(room))
        if setup:sql('hotel',setup)
        try:status,result=http('POST','/api/bookings',b,token)
        finally:
            if restore:sql('hotel',restore)
        after=(sql('booking','SELECT count(*) FROM bookings;'),holds(room),counters(room))
        check(status in expected and before==after and not any(k in (result or {}) for k in ('trace','exception','stackTrace')),label,http=status,expected=expected)
    negative('Invalid hotel ID',lambda b:b.update(hotelId=str(uuid.uuid4())),[400])
    def missing_room(b):
        b['items'][0].update(roomTypeId=str(uuid.uuid4()),ratePlanId=None)
    negative('Invalid room ID',missing_room,[400])
    negative('Room does not belong to hotel',lambda b:b.update(hotelId=h2),[400])
    negative('Inactive hotel',lambda b:None,[400],f"UPDATE hotels SET is_active=false WHERE id='{hotel}';",f"UPDATE hotels SET is_active=true WHERE id='{hotel}';")
    negative('Unapproved hotel',lambda b:None,[400],f"UPDATE hotels SET approved_status='DRAFT' WHERE id='{hotel}';",f"UPDATE hotels SET approved_status='APPROVED' WHERE id='{hotel}';")
    negative('Inactive room',lambda b:None,[400],f"UPDATE room_types SET is_active=false WHERE id='{room}';",f"UPDATE room_types SET is_active=true WHERE id='{room}';")
    negative('Capacity exceeded',lambda b:b.update(adults=3),[400])
    negative('Invalid dates',lambda b:b.update(checkOutDate=b['checkInDate']),[400])
    negative('Tampered positive unit price',lambda b:b['items'][0].update(unitPrice=1),[400])
    negative('Negative unit price',lambda b:b['items'][0].update(unitPrice=-1),[400])
    negative('Missing inventory configuration',lambda b:None,[400,409],f"UPDATE room_types SET total_rooms=NULL WHERE id='{room}';",f"UPDATE room_types SET total_rooms=2 WHERE id='{room}';")
    # Failure after remote hold: booking INSERT aborts; compensation must release known hold.
    before=len(holds(room)); trigger('booking','bookings','INSERT')
    failed=body(hotel,room)
    try:status,_=http('POST','/api/bookings',failed,token)
    finally:untrigger('booking','bookings')
    check(status==500 and len(holds(room))==before+1 and holds(room)[-1:] is not None and all(x['status']=='RELEASED' for x in holds(room)) and all(x['held']==x['booked']==0 for x in counters(room)), 'Booking persistence failure after hold compensates known inventory',http=status,counters=counters(room))
    # Inventory transaction fails before hold insert: no leak, retry can succeed.
    before=holds(room);trigger('hotel','room_holds','INSERT'); req=body(hotel,room)
    try:status,_=http('POST','/api/bookings',req,token)
    finally:untrigger('hotel','room_holds')
    check(status==503 and before==holds(room) and all(x['held']==0 for x in counters(room)), 'Availability failure rolls back inventory and returns unavailable',http=status)
    status,b=http('POST','/api/bookings',req,token)
    check(status==201,'Retry after availability failure succeeds',http=status)
    # Cancel release fails: booking status must remain PENDING until retry succeeds.
    trigger('hotel','room_holds','UPDATE')
    try:status,_=http('DELETE','/api/bookings/'+b['id'],None,token)
    finally:untrigger('hotel','room_holds')
    check(status==503 and sql('booking',f"SELECT status FROM bookings WHERE id='{b['id']}';")=='PENDING' and all(x['held']==1 for x in counters(room)), 'Cancellation release failure preserves retryable booking and stock',http=status)
    check(http('DELETE','/api/bookings/'+b['id'],None,token)[0]==204 and all(x['held']==0 for x in counters(room)), 'Cancellation retry after partial failure restores inventory')
    # Drop downstream response only after upstream commits and independent SQL sees it.
    lost=body(hotel,room); committed={}
    class LostResponse(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def do_POST(self):
            payload=json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            s,b=http('POST','/api/bookings',payload,token)
            committed.update(http=s,id=b.get('id'))
            committed['visible']=s==201 and sql('booking',f"SELECT count(*) FROM bookings WHERE id='{b['id']}';")=='1'
            self.close_connection=True
    server=HTTPServer(('127.0.0.1',0),LostResponse)
    thread=threading.Thread(target=server.handle_request);thread.start(); disconnected=False
    try:http('POST','/api/bookings',lost,base='http://127.0.0.1:'+str(server.server_port))
    except (ConnectionError,urllib.error.URLError):disconnected=True
    finally:thread.join(45);server.server_close()
    status,b=http('POST','/api/bookings',lost,token)
    check(disconnected and committed.get('visible') and status==201 and b['id']==committed['id'], 'Lost HTTP response after commit retries original command',http=status)
    http('DELETE','/api/bookings/'+b['id'],None,token)
    # Catalog actual process outage in this isolated project only.
    run(COMPOSE+['stop','hotel-service'])
    try:
        before=sql('booking','SELECT count(*) FROM bookings;')
        status,_=http('POST','/api/bookings',body(hotel,room),token)
        check(status==503 and before==sql('booking','SELECT count(*) FROM bookings;'),'Hotel catalog unavailable fails closed before booking mutation',http=status)
    finally:run(COMPOSE+['start','hotel-service'])
    invariant=sql('hotel',"SELECT count(*) FROM room_availability a JOIN room_types r ON a.room_type_id=r.id WHERE held<0 OR booked<0 OR held+booked>total_rooms;")
    check(invariant=='0','SQL global inventory invariant: no negative counters or oversell')
    check(sql('hotel','SELECT count(*) FROM (SELECT command_id FROM room_holds WHERE command_id IS NOT NULL GROUP BY command_id HAVING count(*)>1) x;')=='0' and sql('booking','SELECT count(*) FROM (SELECT idempotency_key_hash FROM bookings WHERE idempotency_key_hash IS NOT NULL GROUP BY idempotency_key_hash HAVING count(*)>1) x;')=='0','SQL unique booking and hold commands')
    print(json.dumps({'passed':len(RESULTS),'failed':0}),flush=True)


if __name__=='__main__':
    try:
        if '--init-env' in sys.argv: init_env()
        elif '--bootstrap' in sys.argv: bootstrap()
        elif '--ready' in sys.argv: readiness()
        else: main()
    except Exception as exc:
        print(json.dumps({'result':'ERROR','type':type(exc).__name__}),flush=True)
        raise SystemExit(1)
