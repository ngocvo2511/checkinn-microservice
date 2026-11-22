"""Disposable canonical Gateway smoke (Batch 9C follow-up). Test-only harness.

Actions (run from checkinn/):  python -X utf8 docker/canonical-smoke.py <action>
  prepare  generate secrets/init SQL in ignored .local/canonical-smoke (refuses existing state)
  package  mvn -o package current source for the 10 stack modules (tests skipped; suites run separately)
  build    docker images checkinn/<module>:canonical-smoke = checkinn/<module>:local + fresh jar
  infra    start postgres/rabbitmq/redis/mailpit of project checkinn-canonical-smoke (tmpfs only)
  migrate  host Flyway 10.22: five canonical V1 + no-op rerun + runtime grants (CanonicalSmokeMigrate.java)
  apps     start JVM services with profile canonical and wait for health
  smoke    synthetic fixtures through real Gateway APIs, READ/WRITE/correlation checks, SQL invariants
  down     stop and remove only this project's containers/network (no named volumes exist)
Safety: loopback ports 58080/58432/58025 only; never reads docker/.env or .env.test; never touches
Supabase, the legacy checkinn-test clone or other compose projects; tokens/passwords never leave .local.
"""
import json, os, re, secrets, socket, subprocess, sys, time, uuid, urllib.request, urllib.error, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / '.local' / 'canonical-smoke'
EVIDENCE = ROOT / 'Docs' / 'evidence' / 'batch-9c-canonical-smoke'
PROJECT = 'checkinn-canonical-smoke'
COMPOSE = ['docker', 'compose', '-p', PROJECT, '--project-directory', str(ROOT / 'docker'),
           '-f', str(ROOT / 'docker' / 'docker-compose.canonical-smoke.yml')]
OWNERS = ('user', 'hotel', 'booking', 'revenue', 'regulations')
MODULES = ['eureka-server', 'api-gateway', 'auth-service', 'user-service', 'hotel-service', 'booking-service',
           'revenue-service', 'regulations-service', 'notification-service']
GATEWAY, MAILPIT = 'http://127.0.0.1:58080', 'http://127.0.0.1:58025'
INFRA = ['postgres', 'rabbitmq', 'redis', 'mailpit', 'loki', 'promtail']
LOKI = 'http://127.0.0.1:58100'
# Replicas of hotel-service and booking-service for the multi-node run (CANONICAL_SMOKE_SCALE=2).
SCALE = int(os.environ.get('CANONICAL_SMOKE_SCALE', '1'))


def sh(args, **kw):
    p = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace', **kw)
    if p.returncode:
        raise RuntimeError(f'{args[:4]} failed ({p.returncode}): {p.stderr[-2000:]}')
    return p.stdout


def env_file():
    return json.loads((PRIVATE / 'state.private.json').read_text())['env']


def compose(*args, **kw):
    env = os.environ.copy()
    env.update(env_file())
    return sh(COMPOSE + list(args), env=env, **kw)


def require_owned():
    for line in compose('ps', '-a', '--format', '{{.Name}} {{.Label "checkinn.owner"}}').splitlines():
        name, _, owner = line.partition(' ')
        assert owner.strip() == 'canonical-smoke', f'Container {name} is not owned by this harness'


def prepare():
    assert not (PRIVATE / 'state.private.json').exists(), 'Refuse existing canonical-smoke state; run down and remove it explicitly'
    for port in (58080, 58432, 58025, 58100):
        with socket.socket() as s:
            s.bind(('127.0.0.1', port))
    existing = sh(['docker', 'ps', '-a', '--filter', 'label=com.docker.compose.project=' + PROJECT, '--format', '{{.Names}}']).split()
    assert not existing, 'Project already has containers: ' + ','.join(existing)
    PRIVATE.mkdir(parents=True, exist_ok=True)
    logs = PRIVATE / 'logs'
    if logs.exists():
        for old in logs.glob('*.log'): old.unlink()
    logs.mkdir(exist_ok=True)
    pw = lambda: secrets.token_urlsafe(32)
    env = {'SMOKE_PRIVATE_DIR': str(PRIVATE).replace('\\', '/'), 'SMOKE_SECRET_KEY': secrets.token_urlsafe(48),
           'SMOKE_POSTGRES_PASSWORD': pw(), 'SMOKE_RABBIT_PASSWORD': pw(), 'SMOKE_VNPAY_SECRET': pw()}
    sql = []
    for o in OWNERS:
        env[f'SMOKE_{o.upper()}_MIGRATOR_PASSWORD'] = pw()
        env[f'SMOKE_{o.upper()}_APP_PASSWORD'] = pw()
        sql += [f"CREATE ROLE smoke_{o}_migrator LOGIN PASSWORD '{env[f'SMOKE_{o.upper()}_MIGRATOR_PASSWORD']}';",
                f"CREATE ROLE smoke_{o}_app LOGIN PASSWORD '{env[f'SMOKE_{o.upper()}_APP_PASSWORD']}';",
                f"CREATE DATABASE checkinn_{o} OWNER smoke_{o}_migrator;",
                f"REVOKE ALL ON DATABASE checkinn_{o} FROM PUBLIC;",
                f"GRANT CONNECT ON DATABASE checkinn_{o} TO smoke_{o}_migrator, smoke_{o}_app;",
                f"\\connect checkinn_{o}",
                "REVOKE ALL ON SCHEMA public FROM PUBLIC;",
                f"GRANT USAGE ON SCHEMA public TO smoke_{o}_app;",
                "\\connect smoke_admin"]
    (PRIVATE / 'init.sql').write_text('\n'.join(sql) + '\n', encoding='utf-8', newline='\n')
    state = {'runId': uuid.uuid4().hex, 'createdAt': now(), 'env': env}
    (PRIVATE / 'state.private.json').write_text(json.dumps(state, indent=1))
    print('Prepared run', state['runId'], 'private state in .local/canonical-smoke (git-ignored)')


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def package():
    out = sh(['mvn.cmd' if os.name == 'nt' else 'mvn', '-o', '-B', '-ntp', '-pl', ','.join(MODULES), '-Dmaven.test.skip=true', 'package'])
    assert 'BUILD SUCCESS' in out
    print('Packaged', len(MODULES), 'modules from current source')


def build():
    for m in MODULES:
        jars = [p for p in (ROOT / m / 'target').glob('*.jar') if not p.name.endswith('.original')]
        assert len(jars) == 1, f'{m}: expected one packaged jar, found {jars}'
        sh(['docker', 'build', '-q', '-f', 'docker/Dockerfile.supabase-dev', '--build-arg', f'BASE_IMAGE=checkinn/{m}:local',
            '--build-arg', f'SERVICE={m}', '-t', f'checkinn/{m}:canonical-smoke', '--label', 'checkinn.owner=canonical-smoke', '.'])
        print('built', m)


def infra():
    compose('up', '-d', '--wait', '--wait-timeout', '180', *INFRA)
    require_owned()
    print('Infra healthy:', ', '.join(INFRA))


def migrate():
    require_owned()
    env = os.environ.copy()
    env.update(env_file())
    cp = str(ROOT / 'tools' / 'supabase-runner' / 'target' / 'dependency-10.22.0') + os.sep + '*'
    out = sh(['java', '-cp', cp, 'docker/CanonicalSmokeMigrate.java'], env=env)
    print(out.strip())
    (PRIVATE / 'migrate.log').write_text(out)


def apps():
    require_owned()
    scale = ['--scale', f'hotel-service={SCALE}', '--scale', f'booking-service={SCALE}'] if SCALE > 1 else []
    compose('up', '-d', '--wait', '--wait-timeout', '480', *scale)
    require_owned()
    print(compose('ps', '--format', '{{.Service}} {{.Health}}'))


def psql(db, query):
    return compose('exec', '-T', 'postgres', 'psql', '-X', '-q', '-At', '-v', 'ON_ERROR_STOP=1', '-U', 'smoke_admin', '-d', db, '-c', query).strip()


def down():
    if not (PRIVATE / 'state.private.json').exists():
        print('No state; nothing to stop'); return
    require_owned()
    compose('down', '--remove-orphans')
    leftovers = sh(['docker', 'ps', '-a', '--filter', 'label=com.docker.compose.project=' + PROJECT, '--format', '{{.Names}}']).split()
    volumes = sh(['docker', 'volume', 'ls', '--filter', 'label=com.docker.compose.project=' + PROJECT, '--format', '{{.Name}}']).split()
    print('down: containers left', leftovers, 'project volumes', volumes)


# ------------------------------------------------------------------ smoke
CHECKS = []


def check(name, ok, **detail):
    CHECKS.append(dict(name=name, passed=bool(ok), **detail))
    print(('PASS ' if ok else 'FAIL ') + name, '' if ok else json.dumps(detail, ensure_ascii=False)[:400], flush=True)


def api(path, method='GET', body=None, token=None, headers=None, timeout=30):
    h = {'Accept': 'application/json'}
    if body is not None: h['Content-Type'] = 'application/json'
    if token: h['Authorization'] = 'Bearer ' + token
    h.update(headers or {})
    req = urllib.request.Request(GATEWAY + path, data=None if body is None else json.dumps(body).encode(), headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read(); hdr = dict(r.headers); code = r.status
    except urllib.error.HTTPError as e:
        raw = e.read(); hdr = dict(e.headers); code = e.code
    try: data = json.loads(raw) if raw else None
    except ValueError: data = None
    return code, data, hdr


def envelope_ok(data, hdr, code=None):
    return isinstance(data, dict) and isinstance(data.get('requestId'), str) and data.get('status') is not None \
        and isinstance(data.get('code'), str) and (code is None or data['code'] == code) \
        and 'trace' not in data and 'exception' not in data


def otp_for(email):
    for _ in range(60):
        with urllib.request.urlopen(MAILPIT + '/api/v1/messages', timeout=5) as r:
            messages = json.load(r).get('messages', [])
        for m in messages:
            if any(x.get('Address') == email for x in m.get('To', [])):
                with urllib.request.urlopen(MAILPIT + '/api/v1/message/' + m['ID'], timeout=5) as r:
                    md = json.load(r)
                found = re.search(r'(?<!\d)\d{6}(?!\d)', md.get('Text', '') + ' ' + md.get('HTML', ''))
                if found: return found.group()
        time.sleep(1)
    return None


def account(role, tag):
    name = 'cs_' + tag + '_' + secrets.token_hex(4)
    email, password = name + '@example.invalid', secrets.token_urlsafe(18) + 'Aa1!'
    route = '/api/auth/register-owner' if role == 'OWNER' else '/api/auth/register'
    code, d, _ = api(route, 'POST', dict(username=name, password=password, email=email, fullName='Canonical smoke ' + tag))
    check(f'{tag}: register via Gateway', code == 200 and d and d.get('userId'), status=code)
    otp = otp_for(email)
    check(f'{tag}: OTP delivered to local Mailpit', bool(otp))
    code, _, _ = api('/api/auth/otp/verify', 'POST', dict(email=email, otpCode=otp))
    check(f'{tag}: verify OTP', code == 200, status=code)
    if role == 'ADMIN':
        # Disposable database only: promote a freshly registered synthetic account (no API exists for this).
        assert psql('checkinn_user', f"UPDATE users SET role='ADMIN' WHERE id='{uuid.UUID(d['userId'])}' AND role='CUSTOMER' RETURNING id") == d['userId']
    code, login, _ = api('/api/auth/login', 'POST', dict(usernameOrEmail=name, password=password))
    expected = 'CUSTOMER' if role == 'CUSTOMER' else role
    check(f'{tag}: real login returns {expected} JWT', code == 200 and login.get('role') == expected and login.get('token'), status=code,
          role=(login or {}).get('role'))
    return dict(id=login['userId'], token=login['token'], email=email)


def booking_body(customer, hotel, room, key, price, check_in='2030-03-10', check_out='2030-03-12', adults=2):
    _, policy, _ = api('/api/bookings/cancellation-policy')
    return {'idempotencyKey': key, 'hotelId': hotel, 'checkInDate': check_in, 'checkOutDate': check_out, 'adults': adults,
            'children': 0, 'contactName': 'Canonical smoke guest', 'contactEmail': customer['email'], 'contactPhone': '0900000000',
            'pointsToUse': 0, 'acceptedCancellationPolicyVersion': policy['version'],
            'items': [{'roomTypeId': room, 'quantity': 1, 'checkInDate': check_in, 'checkOutDate': check_out, 'unitPrice': price}]}


def counters(room, start=None, end=None):
    # room_availability has one row per stay night: report the per-night peak (held/booked) and live HELD hold rows.
    nights = f" AND date >= '{start}' AND date < '{end}'" if start else ''
    holds = f" AND check_in_date = '{start}'" if start else ''
    return psql('checkinn_hotel', f"SELECT coalesce(max(held),0)||'/'||coalesce(max(booked),0)||'/'||"
                f"(SELECT count(*) FROM room_holds WHERE room_type_id='{room}' AND status='HELD'{holds}) FROM room_availability WHERE room_type_id='{room}'{nights}")


def poll(fn, ok, seconds=60):
    deadline = time.monotonic() + seconds
    value = fn()
    while not ok(value) and time.monotonic() < deadline:
        time.sleep(2); value = fn()
    return value


def vnp_now():
    return datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).strftime('%Y%m%d%H%M%S')


def vnp_signed(params, secret):
    # Same canonical form as VnPayUtil.buildQuery (sorted keys, URL-encoded values) and HMAC-SHA512 over it.
    import hmac, hashlib
    query = '&'.join(f"{k}={urllib.parse.quote_plus(v)}" for k, v in sorted(params.items()) if v)
    return query + '&vnp_SecureHash=' + hmac.new(secret.encode(), query.encode(), hashlib.sha512).hexdigest()


def loki_lines(needle):
    q = urllib.parse.urlencode({'query': '{job="spring"} |= "' + needle + '"', 'limit': '200',
                                'start': str(int((time.time() - 3600) * 1e9)), 'end': str(int((time.time() + 60) * 1e9))})
    try:
        with urllib.request.urlopen(LOKI + '/loki/api/v1/query_range?' + q, timeout=20) as r:
            data = json.loads(r.read())
    except Exception:
        return {}
    files = {}
    for stream in data.get('data', {}).get('result', []):
        name = stream.get('stream', {}).get('filename', '?').rsplit('/', 1)[-1]
        files[name] = files.get(name, 0) + len(stream.get('values', []))
    return files


def lost_response_post(path, body, token):
    """Send a write and close the socket without reading the reply (client-side timeout / lost response)."""
    payload = json.dumps(body).encode()
    head = (f'POST {path} HTTP/1.1\r\nHost: 127.0.0.1:58080\r\nAuthorization: Bearer {token}\r\n'
            f'Content-Type: application/json\r\nContent-Length: {len(payload)}\r\nConnection: close\r\n\r\n').encode()
    with socket.create_connection(('127.0.0.1', 58080), timeout=10) as s:
        s.sendall(head + payload)
        s.shutdown(socket.SHUT_RDWR)


def smoke():
    require_owned()
    # The Gateway's Eureka cache refreshes every 30 s: wait until each routed service is resolvable (not a 503 from the LB).
    deadline = time.monotonic() + 120
    for probe in ('/api/hotel/cities', '/api/bookings/cancellation-policy', '/api/auth/actuator/health'):
        while api(probe)[0] == 503 and time.monotonic() < deadline: time.sleep(3)
    started = now()
    # Fresh suffix per invocation: city/hotel names are unique and the name search must match only this run.
    run = json.loads((PRIVATE / 'state.private.json').read_text())['runId'][-4:] + secrets.token_hex(3)
    admin, owner = account('ADMIN', 'admin'), account('OWNER', 'owner')
    c1, c2 = account('CUSTOMER', 'customer1'), account('CUSTOMER', 'customer2')

    # ---------------- fixtures through real APIs (publication is the one labelled SQL step)
    code, city, _ = api('/api/hotel/cities', 'POST', {'name': 'Canonical Smoke City ' + run}, admin['token'])
    check('ADMIN creates city via Gateway', code == 201 and city.get('id'), status=code)
    code, _, _ = api('/api/hotel/cities', 'POST', {'name': 'Forbidden City ' + run}, c1['token'])
    check('CUSTOMER cannot create city', code == 403, status=code)
    hotel_name = 'Canonical Smoke Hotel ' + run
    code, hotel, _ = api('/hotels', 'POST', {'name': hotel_name, 'description': 'Synthetic disposable hotel', 'starRating': 3,
                                             'cityId': city['id'], 'address': {'street': 'Synthetic street', 'city': 'Synthetic', 'country': 'VN'},
                                             'contactEmail': owner['email'], 'contactPhone': '0900000000', 'businessLicenseNumber': 'CS-' + run,
                                             'taxId': 'CS-TAX', 'ownerIdentityNumber': 'CS-ID', 'accommodationType': 'HOTEL',
                                             'policies': [], 'questions': [], 'amenityCategories': []}, owner['token'])
    check('OWNER creates hotel (DRAFT) via Gateway', code == 200 and hotel.get('id'), status=code)
    hid = hotel['id']
    code, _, _ = api(f'/api/hotel/hotels/{hid}')
    check('DRAFT hotel is not public', code in (403, 404), status=code)
    # Document/Storage submission is out of scope here (no Storage in this stack): synthetic publication in the disposable DB.
    assert psql('checkinn_hotel', f"UPDATE hotels SET approved_status='APPROVED',is_active=true WHERE id='{uuid.UUID(hid)}' AND approved_status='DRAFT' RETURNING id") == hid
    price = 1200000
    rooms = []
    for label in ('approved', 'pending'):
        code, room, _ = api('/api/v1', 'POST', {'hotelId': hid, 'name': 'Smoke room ' + label, 'basePrice': price, 'capacity': {'adults': 2, 'children': 1},
                                                'roomAmount': 2, 'amenities': [], 'description': 'Synthetic ' + label}, owner['token'])
        check(f'OWNER creates {label} room via Gateway', code == 200 and room.get('id'), status=code)
        code, p, _ = api(f"/api/v1/{room['id']}/inventory-review", 'POST', {'quantity': 2, 'revision': 0, 'source': 'Synthetic canonical smoke declaration'}, owner['token'])
        check(f'OWNER proposes inventory for {label} room', code == 201 and p.get('status') == 'PENDING', status=code)
        rooms.append((room['id'], p))
    (ra, pa), (rb, pb) = rooms
    code, _, _ = api(f"/api/hotel/admin/inventory-reviews/{pa['id']}/review", 'POST', {'approve': True, 'revision': pa['revision'], 'reason': 'x'}, owner['token'])
    check('OWNER cannot approve own inventory', code == 403, status=code)
    code, d, _ = api(f"/api/hotel/admin/inventory-reviews/{pa['id']}/review", 'POST', {'approve': True, 'revision': pa['revision'], 'reason': 'Synthetic smoke approval'}, admin['token'])
    check('ADMIN approves inventory via Gateway', code == 200 and d.get('status') == 'APPROVED', status=code)

    # ---------------- READ (anonymous, Gateway)
    stay = 'checkIn=2030-03-10&checkOut=2030-03-12'
    code, page, h = api('/api/hotel/hotels/offers/page?hotelName=' + urllib.parse.quote(hotel_name) + '&' + stay)
    offer = (page or {}).get('content', [{}])[0].get('offer', {}) if (page or {}).get('content') else {}
    check('READ search page returns only the approved room offer', code == 200 and page['totalElements'] == 1 and offer.get('roomTypeId') == ra
          and offer.get('availableRooms') == 2 and offer.get('checkedAt'), status=code, total=(page or {}).get('totalElements'))
    code, detail, _ = api(f'/api/hotel/hotels/{hid}')
    ids = [r.get('id') for r in (detail or {}).get('roomTypes', [])]
    check('READ hotel detail lists approved room only', code == 200 and ids == [ra], status=code, rooms=ids)
    check('READ public hotel detail and search page carry no ownerId (F-9B-2)', (detail or {}).get('ownerId') is None
          and all((c.get('hotel') or {}).get('ownerId') is None for c in (page or {}).get('content', [])),
          detailOwnerIdPresent=(detail or {}).get('ownerId') is not None)
    code, listing, _ = api(f'/api/hotel/api/v1/hotel/{hid}')
    check('READ rooms by hotel lists approved room only', code == 200 and [r['id'] for r in listing] == [ra], status=code)
    code, _, _ = api(f'/api/hotel/api/v1/{ra}')
    check('READ approved room', code == 200, status=code)
    code, d, h = api(f'/api/hotel/api/v1/{rb}')
    check('READ unapproved room is 409 INVENTORY_APPROVAL_REQUIRED envelope (F-9B-4 kept; Booking admission depends on it)',
          code == 409 and envelope_ok(d, h, 'INVENTORY_APPROVAL_REQUIRED'), status=code)
    today = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).date()
    for label, query in [('past check-in', f'checkIn={today - datetime.timedelta(days=1)}&checkOut={today + datetime.timedelta(days=1)}'),
                         ('31 nights', 'checkIn=2030-03-01&checkOut=2030-04-01'), ('21 rooms', stay + '&rooms=21'),
                         ('41 adults', stay + '&adults=41'), ('41 children', stay + '&children=41')]:
        code, d, h = api('/api/hotel/hotels/offers/page?hotelName=' + urllib.parse.quote(hotel_name) + '&' + query)
        check(f'READ search {label} is 400 (F-9B-3)', code == 400 and envelope_ok(d, h), status=code, message=(d or {}).get('message'))
    code, d, _ = api('/api/hotel/hotels/offers/page?hotelName=' + urllib.parse.quote(hotel_name) + '&checkIn=2030-03-01&checkOut=2030-03-31&rooms=1&adults=2')
    check('READ search 30 nights is accepted (F-9B-3 boundary)', code == 200, status=code)
    code, d, h = api(f'/api/hotel/api/v1/availability/check?roomTypeId={ra}&{stay}&quantity=21')
    check('READ availability quantity 21 is 400 (F-9B-3)', code == 400 and envelope_ok(d, h), status=code)
    code, d, _ = api(f'/api/hotel/api/v1/availability/check?roomTypeId={ra}&{stay}&quantity=1')
    check('READ availability approved room', code == 200 and {k: d.get(k) for k in ('available', 'availableRooms')} == {'available': True, 'availableRooms': 2}, status=code, body=d)
    code, d, h = api(f'/api/hotel/api/v1/availability/check?roomTypeId={rb}&{stay}&quantity=1')
    check('READ availability unapproved room is business conflict, not sold out', code == 409 and envelope_ok(d, h, 'INVENTORY_APPROVAL_REQUIRED'), status=code)
    for path in ['/api/hotel/hotels/accommodation-types', '/api/hotel/amenity-catalog', '/api/hotel/hotels/amenities/categories',
                 '/api/hotel/hotels/policies/categories', '/api/hotel/cities', '/api/hotel/cities/search/locations?query=Canonical',
                 f'/api/hotel/api/v1/reviews/summaries?hotelIds={hid}', f'/api/hotel/api/v1/reviews/stats/hotel/{hid}', '/api/bookings/cancellation-policy']:
        code, _, _ = api(path)
        check('READ public catalog ' + path.split('?')[0], code == 200, status=code)
    for query, label in [(stay, 'no destination'), ('hotelName=x&size=51', 'size 51'), ('hotelName=x&checkIn=2030-03-12&checkOut=2030-03-10', 'reversed stay')]:
        code, d, h = api('/api/hotel/hotels/offers/page?' + query)
        check('READ invalid search is 400 envelope: ' + label, code == 400 and envelope_ok(d, h) and h.get('X-Request-ID') == d.get('requestId'), status=code)
    code, d, h = api(f'/api/hotel/hotels/{uuid.uuid4()}')
    check('READ unknown hotel is 404 NOT_FOUND envelope', code == 404 and envelope_ok(d, h, 'NOT_FOUND'), status=code)
    for path in [f'/api/hotel/hotels/{hid}/preview', '/api/hotel/admin/inventory-reviews', f'/api/hotel/internal/hotels/{hid}/owner',
                 f'/api/hotel/api/v1/availability/hold/{uuid.uuid4()}', '/api/hotel/hotels/pending', f'/api/hotel/api/v1/{ra}/inventory-review']:
        code, d, h = api(path, headers={'X-Is-Admin': 'true', 'X-User-Id': owner['id']})
        check('READ private route anonymous 401 ' + path.split('/api/hotel')[-1].split(hid)[0], code == 401 and envelope_ok(d, h, 'UNAUTHORIZED'), status=code)
    for path in ['/api/hotel/admin/inventory-reviews', f'/api/hotel/internal/hotels/{hid}/owner', '/api/hotel/hotels/pending']:
        code, _, _ = api(path, token=c1['token'], headers={'X-Is-Admin': 'true'})
        check('READ admin/internal route CUSTOMER 403 ' + path.split('/api/hotel')[-1].split(hid)[0], code == 403, status=code)

    # ---------------- WRITE (CUSTOMER, Gateway)
    trace = secrets.token_hex(16)
    k1 = str(uuid.uuid4())
    b1_body = booking_body(c1, hid, ra, k1, price)
    code, _, _ = api('/api/bookings', 'POST', b1_body)
    check('WRITE anonymous create is 401', code == 401, status=code)
    # Booking's own discovery cache must list hotel-service before the first write (startup race seen in 9G run A):
    # a stale-price request reaches Hotel and is rejected with 400 (no hold); 503 means "not yet resolvable".
    poll(lambda: api('/api/bookings', 'POST', booking_body(c1, hid, ra, str(uuid.uuid4()), price - 1), c1['token'])[0], lambda c: c != 503, 120)
    code, b1, _ = api('/api/bookings', 'POST', b1_body, c1['token'], headers={'traceparent': f'00-{trace}-{secrets.token_hex(8)}-01'})
    check('WRITE create booking 201 with server total', code == 201 and b1.get('id') and float(b1.get('totalAmount', 0)) == price * 2
          and b1.get('userId') == c1['id'], status=code, total=(b1 or {}).get('totalAmount'))
    code, again, _ = api('/api/bookings', 'POST', b1_body, c1['token'])
    check('WRITE retry with same key returns same booking', code == 201 and again.get('id') == b1['id'], status=code)
    changed = dict(b1_body, adults=1)
    code, d, h = api('/api/bookings', 'POST', changed, c1['token'])
    check('WRITE same key with changed input is 409 envelope', code == 409 and envelope_ok(d, h, 'CONFLICT'), status=code)
    check('WRITE one hold counted after create+retry', counters(ra) == '1/0/1', counters=counters(ra))
    k2 = str(uuid.uuid4())
    b2_body = booking_body(c1, hid, ra, k2, price)
    lost_response_post('/api/bookings', b2_body, c1['token'])
    time.sleep(3)
    code, b2, _ = api('/api/bookings', 'POST', b2_body, c1['token'])
    rows = psql('checkinn_booking', f"SELECT count(*) FROM bookings WHERE user_id='{uuid.UUID(c1['id'])}'")
    check('WRITE lost response then retry with same key: one booking, no extra hold', code == 201 and rows == '2' and counters(ra) == '2/0/2',
          status=code, bookings=rows, counters=counters(ra))
    code, d, _ = api(f'/api/hotel/api/v1/availability/check?roomTypeId={ra}&{stay}&quantity=1')
    check('READ availability reflects holds (0 left)', code == 200 and {k: d.get(k) for k in ('available', 'availableRooms')} == {'available': False, 'availableRooms': 0}, body=d)
    stale_trace = secrets.token_hex(16)
    code, d, h = api('/api/bookings', 'POST', booking_body(c1, hid, ra, str(uuid.uuid4()), price - 1), c1['token'],
                     headers={'traceparent': f'00-{stale_trace}-{secrets.token_hex(8)}-01'})
    check('WRITE stale price is 400 envelope, no hold', code == 400 and envelope_ok(d, h) and 'price' in (d.get('message') or '').lower()
          and counters(ra) == '2/0/2', status=code, message=(d or {}).get('message'))
    check('C5 Gateway→Booking: error requestId equals client trace id', (d or {}).get('requestId') == stale_trace, requestId=(d or {}).get('requestId'))
    code, d, h = api('/api/bookings', 'POST', booking_body(c1, hid, rb, str(uuid.uuid4()), price), c1['token'])
    check('WRITE unapproved room booking is 409, no hold', code == 409 and envelope_ok(d, h) and counters(rb) == '0/0/0', status=code, code=(d or {}).get('code'))
    before = counters(ra)
    for label, body, field in [('31 nights', booking_body(c1, hid, ra, str(uuid.uuid4()), price, '2030-03-01', '2030-04-01'), 'checkOutDate'),
                               ('41 adults', booking_body(c1, hid, ra, str(uuid.uuid4()), price, adults=41), 'adults'),
                               ('past arrival', booking_body(c1, hid, ra, str(uuid.uuid4()), price, '2020-01-10', '2020-01-12'), 'checkOutDate')]:
        code, d, h = api('/api/bookings', 'POST', body, c1['token'])
        check(f'WRITE booking {label} is 400 field error, no hold (F-9B-3)', code == 400 and field in ((d or {}).get('fieldErrors') or {})
              and counters(ra) == before, status=code, fields=sorted(((d or {}).get('fieldErrors') or {}).keys()))
    for method, path in [('GET', f"/api/bookings/{b1['id']}"), ('DELETE', f"/api/bookings/{b1['id']}"), ('GET', f"/api/payments/booking/{b1['id']}"),
                         ('POST', f"/api/payments/vnpay/create?bookingId={b1['id']}")]:
        code, _, _ = api(path, method, token=c2['token'], headers={'X-User-Id': c1['id']})
        check(f'WRITE other customer {method} {path.split("?")[0].rsplit("/", 1)[0]} is 403', code == 403, status=code)
    for method, path in [('PUT', f"/api/bookings/{b1['id']}/status?status=CONFIRMED"), ('GET', '/api/bookings/count/total'),
                         ('GET', f'/api/bookings/hotel/{hid}'), ('GET', '/api/payments/manual-refunds')]:
        code, _, _ = api(path, method, token=c1['token'], headers={'X-Is-Admin': 'true'})
        check(f'WRITE out-of-scope {method} {path.split("?")[0]} is 403 for CUSTOMER', code == 403, status=code)
    code, d, _ = api(f"/api/bookings/{b1['id']}", token=c1['token'])
    check('WRITE owner reads booking status', code == 200 and d.get('id') == b1['id'], status=code, bookingStatus=(d or {}).get('status'))
    code, d, _ = api(f"/api/bookings/user/{c1['id']}", token=c1['token'])
    check('WRITE owner lists own bookings', code == 200 and {x['id'] for x in d} == {b1['id'], b2['id']}, status=code)
    code, d, _ = api(f"/api/payments/vnpay/create?bookingId={b1['id']}", 'POST', token=c1['token'])
    url = (d or {}).get('redirectUrl') or ''
    check('WRITE VNPay URL handoff (signed locally, provider not called)', code == 200 and url.startswith('https://sandbox.vnpayment.vn/'),
          status=code, keys=sorted((d or {}).keys()))
    for b in (b2, b1):
        code, _, _ = api(f"/api/bookings/{b['id']}", 'DELETE', token=c1['token'])
        check('WRITE owner cancels booking 204', code == 204, status=code)
    time.sleep(2)
    check('WRITE cancellation releases holds (counters 0/0/0)', counters(ra) == '0/0/0', counters=counters(ra))
    code, d, _ = api(f'/api/hotel/api/v1/availability/check?roomTypeId={ra}&{stay}&quantity=1')
    check('READ availability restored after cancel, with server checkedAt (F-9B-5)', code == 200 and d.get('availableRooms') == 2
          and isinstance(d.get('checkedAt'), str) and d['checkedAt'].endswith('Z'), body=d)
    code, d, _ = api(f"/api/bookings/{b1['id']}", 'DELETE', token=c1['token'])
    check('WRITE repeated cancel is idempotent (204) with no further inventory effect', code == 204 and counters(ra) == '0/0/0', status=code, counters=counters(ra))
    code, d, h = api(f"/api/payments/vnpay/create?bookingId={b1['id']}", 'POST', token=c1['token'])
    check('WRITE VNPay create for a cancelled booking is a 400 envelope, no exception text (F-9C-4)',
          code == 400 and envelope_ok(d, h, 'INVALID_REQUEST') and d.get('message') == 'Payment cannot be created for this booking'
          and h.get('X-Request-ID') == d.get('requestId') and 'CANCELLED' not in json.dumps(d), status=code, body=d)

    # ---------------- Batch 9G: full paid lifecycle (STD-028). VNPay return is signed locally with this stack's
    # synthetic secret (provider never called); the refund is Sandbox Internal accounting, not a provider refund.
    secret = env_file()['SMOKE_VNPAY_SECRET']
    p_in, p_out = '2030-04-10', '2030-04-12'
    code, b3, _ = api('/api/bookings', 'POST', booking_body(c1, hid, ra, str(uuid.uuid4()), price, p_in, p_out), c1['token'])
    check('E2E paid: create booking 201', code == 201 and (b3 or {}).get('id'), status=code)
    code, init, _ = api(f"/api/payments/vnpay/create?bookingId={b3['id']}", 'POST', token=c1['token'])
    query = dict(urllib.parse.parse_qsl(urllib.parse.urlsplit((init or {}).get('redirectUrl', '')).query))
    ret = {'vnp_TmnCode': 'SMOKETEST', 'vnp_TxnRef': query.get('vnp_TxnRef', ''), 'vnp_Amount': query.get('vnp_Amount', ''),
           'vnp_ResponseCode': '00', 'vnp_TransactionStatus': '00', 'vnp_TransactionNo': str(10**8 + secrets.randbelow(10**8)), 'vnp_PayDate': vnp_now()}
    check('E2E paid: VNPay URL carries order and amount (2 nights x price x 100)', code == 200 and ret['vnp_TxnRef']
          and ret['vnp_Amount'] == str(int(price * 2 * 100)), status=code, amount=ret['vnp_Amount'])
    tampered = vnp_signed(ret, secret).replace('vnp_Amount=' + ret['vnp_Amount'], 'vnp_Amount=' + str(int(ret['vnp_Amount']) + 100))
    code, _, _ = api('/api/payments/vnpay/return?' + tampered)
    check('E2E paid: tampered amount (signature no longer matches) is 400, nothing paid', code == 400, status=code)
    code, d, _ = api('/api/payments/vnpay/return?' + vnp_signed(dict(ret, vnp_Amount=str(int(ret['vnp_Amount']) + 100)), secret))
    check('E2E paid: validly signed but wrong amount is 400 (persisted amount check)', code == 400, status=code)
    code, paid, _ = api('/api/payments/vnpay/return?' + vnp_signed(ret, secret))
    check('E2E paid: signed VNPay return 00 completes the payment', code == 200 and (paid or {}).get('status') == 'COMPLETED', status=code, payment=(paid or {}).get('status'))
    code, again, _ = api('/api/payments/vnpay/return?' + vnp_signed(ret, secret))
    check('E2E paid: replayed return is idempotent', code == 200 and (again or {}).get('status') == 'COMPLETED', status=code)
    pid = (paid or {}).get('id')
    status_of = lambda: (api(f"/api/bookings/{b3['id']}", token=c1['token'])[1] or {}).get('status')
    check('E2E paid: booking becomes CONFIRMED', poll(status_of, lambda s: s == 'CONFIRMED') == 'CONFIRMED', status=status_of())
    check('E2E paid: inventory booked (per-night booked 1, no live hold)', poll(lambda: counters(ra, p_in, p_out), lambda c: c == '0/1/0') == '0/1/0',
          counters=counters(ra, p_in, p_out))
    cash = lambda: psql('checkinn_revenue', f"SELECT count(*) FILTER (WHERE kind='CREDIT')||'/'||count(*) FILTER (WHERE kind='DEBIT')||'/'||"
                                            f"coalesce(sum(CASE WHEN kind='CREDIT' THEN amount ELSE -amount END),0)::bigint FROM revenue_cash_transactions WHERE payment_id='{pid}'")
    check('E2E paid: Revenue projects exactly one CREDIT', poll(cash, lambda c: c.startswith('1/0/')) == f"1/0/{int(price * 2)}", revenue=cash())
    code, _, _ = api(f"/api/bookings/{b3['id']}", 'DELETE', token=c1['token'])
    check('E2E paid: customer cancels within the free window (204) -> CANCELLED', code == 204 and status_of() == 'CANCELLED', status=code)
    code, rr, _ = api(f"/api/payments/{pid}/refund-request", token=c1['token'])
    check('E2E paid: cancellation opened a refund request', code == 200 and (rr or {}).get('status'), status=code, refund=(rr or {}).get('status'))
    code, _, _ = api(f"/api/payments/{pid}/refund-request/approve", 'POST', token=c1['token'])
    check('E2E paid: CUSTOMER cannot approve a refund (403)', code == 403, status=code)
    code, rr, _ = api(f"/api/payments/{pid}/refund-request/approve", 'POST', token=admin['token'])
    check('E2E paid: ADMIN approves the refund request', code == 200 and (rr or {}).get('status') == 'APPROVED_PENDING_EXECUTION', status=code, refund=(rr or {}).get('status'))
    code, _, _ = api(f"/api/payments/{pid}/refund", 'POST', token=c1['token'])
    check('E2E paid: CUSTOMER cannot execute a refund (403)', code == 403, status=code)
    code, rf, _ = api(f"/api/payments/{pid}/refund", 'POST', token=admin['token'])
    check('E2E paid: ADMIN executes the Sandbox Internal refund -> REFUNDED', code == 200 and (rf or {}).get('status') == 'REFUNDED', status=code, payment=(rf or {}).get('status'))
    code, rf, _ = api(f"/api/payments/{pid}/refund", 'POST', token=admin['token'])
    check('E2E paid: repeated refund execution is idempotent', code == 200 and (rf or {}).get('status') == 'REFUNDED', status=code)
    check('E2E paid: Revenue has one CREDIT and one DEBIT, net 0', poll(cash, lambda c: c == '1/1/0') == '1/1/0', revenue=cash())
    check('E2E paid: inventory released after cancellation', poll(lambda: counters(ra, p_in, p_out), lambda c: c == '0/0/0') == '0/0/0',
          counters=counters(ra, p_in, p_out))
    refund_row = psql('checkinn_booking', f"SELECT r.status||'/'||r.execution_mode||'/'||b.paid_amount::bigint FROM refund_requests r JOIN bookings b ON b.id=r.booking_id WHERE r.payment_id='{pid}'")
    check('E2E paid: refund recorded as SANDBOX_INTERNAL, booking paid_amount back to 0', refund_row == 'REFUNDED/SANDBOX_INTERNAL/0', refund=refund_row)

    # ---------------- Batch 9G: multi-node (STD-028), only when replicas were started (CANONICAL_SMOKE_SCALE>1)
    if SCALE > 1:
        import concurrent.futures
        for app in ('HOTEL-SERVICE', 'BOOKING-SERVICE'):
            raw = compose('exec', '-T', 'eureka-server', 'curl', '-fsS', '-H', 'Accept: application/json', f'http://localhost:8761/eureka/apps/{app}')
            inst = json.loads(raw)['application']['instance']
            inst = inst if isinstance(inst, list) else [inst]
            check(f'MULTINODE Eureka lists {SCALE} UP {app} instances', sum(1 for i in inst if i.get('status') == 'UP') == SCALE, instances=len(inst))
        m_in, m_out = '2030-05-10', '2030-05-12'
        bodies = [(booking_body(c, hid, ra, str(uuid.uuid4()), price, m_in, m_out), c['token']) for c in (c1, c2) * 3]
        with concurrent.futures.ThreadPoolExecutor(len(bodies)) as pool:
            results = list(pool.map(lambda b: api('/api/bookings', 'POST', b[0], b[1]), bodies))
        codes = sorted(r[0] for r in results)
        check('MULTINODE 6 concurrent creates (distinct keys) on a stock-2 room: exactly two 201, the rest 409',
              codes.count(201) == 2 and codes.count(409) == 4, codes=codes)
        check('MULTINODE inventory never oversold (per-night held <= 2, 2 live holds)', counters(ra, m_in, m_out) == '2/0/2', counters=counters(ra, m_in, m_out))
        s_in, s_out = '2030-06-10', '2030-06-12'
        same = booking_body(c2, hid, ra, str(uuid.uuid4()), price, s_in, s_out)
        with concurrent.futures.ThreadPoolExecutor(4) as pool:
            results = list(pool.map(lambda _: api('/api/bookings', 'POST', same, c2['token']), range(4)))
        ids = {(r[1] or {}).get('id') for r in results if r[0] == 201}
        rows = psql('checkinn_booking', f"SELECT count(*) FROM bookings WHERE user_id='{uuid.UUID(c2['id'])}' AND check_in_date='{s_in}'")
        check('MULTINODE 4 concurrent creates with one idempotency key: one booking, one hold',
              rows == '1' and len(ids) <= 1 and counters(ra, s_in, s_out) == '1/0/1', codes=sorted(r[0] for r in results), bookings=rows, counters=counters(ra, s_in, s_out))
        per_replica = {}
        for cid in compose('ps', '-q', 'booking-service').split():
            per_replica[cid[:12]] = sh(['docker', 'logs', cid]).count('[CREATE_BOOKING] Request received')
        check('MULTINODE booking creates were served by every booking-service replica', len(per_replica) == SCALE and all(per_replica.values()),
              perReplica=per_replica)
        for r in results[:1]:
            if r[0] == 201: api(f"/api/bookings/{r[1]['id']}", 'DELETE', token=c2['token'])

    # ---------------- Batch 9H: one error per service through the Gateway uses the standard envelope (STD-026)
    hv = lambda h, k: next((v for kk, v in (h or {}).items() if kk.lower() == k.lower()), None)
    for label, path, method, body, token, statuses in [
            ('auth wrong password', '/api/auth/login', 'POST', {'usernameOrEmail': 'cs_nobody_' + run, 'password': 'synthetic-wrong-Aa1!'}, None, (400, 401)),
            ('user admin route as CUSTOMER', f'/api/user/admin/users/{uuid.uuid4()}', 'GET', None, c1['token'], (403,)),
            ('revenue reversed dates', f"/api/reports/owner/summary?ownerId={owner['id']}&from=2030-02-01&to=2030-01-01", 'GET', None, owner['token'], (400,)),
            ('regulations unknown key', '/api/regulations/NO_SUCH_KEY_' + run.upper(), 'GET', None, None, (401, 403, 404)),
            ('booking unknown payment', f'/api/payments/{uuid.uuid4()}', 'GET', None, c1['token'], (403, 404)),
            ('hotel unknown hotel', f'/api/hotel/hotels/{uuid.uuid4()}', 'GET', None, None, (404,))]:
        code, d, h = api(path, method, body, token)
        keys = {'timestamp', 'status', 'error', 'code', 'message', 'path', 'requestId'}
        check(f'ENVELOPE {label}: standard fields and X-Request-ID = requestId (STD-026)',
              code in statuses and isinstance(d, dict) and keys <= set(d) and hv(h, 'X-Request-ID') == d.get('requestId')
              and 'Exception' not in json.dumps(d), status=code, missing=sorted(keys - set(d or {})), code_=(d or {}).get('code'))

    # ---------------- Batch 9H: F-9G-1 — both Gateway-allowed browser origins reach Booking
    for origin in ('http://localhost:3000', 'http://127.0.0.1:3000'):
        code, d, h = api('/api/bookings/cancellation-policy', headers={'Origin': origin})
        check(f'CORS Booking accepts browser origin {origin} through the Gateway (F-9G-1)', code == 200 and hv(h, 'Access-Control-Allow-Origin') == origin,
              status=code, allowOrigin=hv(h, 'Access-Control-Allow-Origin'))
    code, _, _ = api('/api/bookings/cancellation-policy', headers={'Origin': 'https://unknown.example.invalid'})
    check('CORS unknown origin is still rejected', code == 403, status=code)

    # ---------------- Batch 9G: Gateway trace bridge + access log (STD-030)
    gw = compose('logs', '--no-color', 'api-gateway')
    check('C5 Gateway access log carries the client trace id', f'traceId={trace}' in gw, occurrences=gw.count(trace))
    code, d, h = api(f'/api/bookings/{uuid.uuid4()}', token=c1['token'])
    rid = (d or {}).get('requestId') or ''
    gw = poll(lambda: compose('logs', '--no-color', 'api-gateway'), lambda g: f'traceId={rid}' in g, 10)
    check('C5 Gateway starts a trace when the client sends none (Booking error requestId = Gateway trace id)',
          code in (403, 404) and len(rid) == 32 and f'traceId={rid}' in gw, status=code, requestId=rid)
    check('C5 Gateway access log has no query strings or tokens', 'Bearer ' not in gw and '?' not in ''.join(l for l in gw.splitlines() if '[GATEWAY_ACCESS]' in l))

    # ---------------- Batch 9G: log ingestion via the project's Promtail/Loki configs (STD-030)
    files = poll(lambda: loki_lines(trace), lambda f: any('api-gateway' in k for k in f) and any('booking-service' in k for k in f)
                 and any('hotel-service' in k for k in f), 90)
    check('LOGS Loki has the client trace from Gateway, Booking and Hotel log files', any('api-gateway' in k for k in files)
          and any('booking-service' in k for k in files) and any('hotel-service' in k for k in files), files=files)
    leaked = loki_lines('eyJ')
    check('LOGS no JWT-looking values ingested', not leaked, files=leaked)

    # ---------------- Batch 9G: NON-intercepted browser smoke (STD-028), opt-in with CANONICAL_SMOKE_LIVE=1.
    # Real Next dev server (port 3000) -> real Gateway -> services; fixture and a real CUSTOMER JWT in an ignored file.
    if os.environ.get('CANONICAL_SMOKE_LIVE') == '1':
        l_in, l_out = '2030-07-10', '2030-07-12'
        code, live, _ = api('/api/bookings', 'POST', booking_body(c1, hid, ra, str(uuid.uuid4()), price, l_in, l_out), c1['token'])
        check('BROWSER live fixture booking created', code == 201 and (live or {}).get('id'), status=code)
        fixture = PRIVATE / 'live-fixture.private.json'
        fixture.write_text(json.dumps({'hotelName': hotel_name, 'hotelId': hid, 'approvedRoom': 'Smoke room approved', 'pendingRoom': 'Smoke room pending',
                                       'bookingId': live['id'], 'checkIn': l_in, 'checkOut': l_out, 'token': c1['token'],
                                       'user': {'userId': c1['id'], 'fullName': 'Canonical smoke customer1', 'role': 'CUSTOMER'}}), encoding='utf-8')
        before = compose('logs', '--no-color', 'api-gateway').count('[GATEWAY_ACCESS]')
        frontend = ROOT.parent / 'checkinn-frontend'
        npx = 'npx.cmd' if os.name == 'nt' else 'npx'
        browser = subprocess.run([npx, 'playwright', 'test', '--config=playwright.live.config.ts', '--reporter=line'], cwd=frontend,
                             capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=900,
                             env=dict(os.environ, CANONICAL_LIVE_FIXTURE=str(fixture)))
        fixture.unlink()
        (PRIVATE / 'live-browser.log').write_text(browser.stdout[-20000:] + browser.stderr[-5000:], encoding='utf-8')
        import re as _re
        passed = sum(int(n) for n in _re.findall(r'(\d+) passed', browser.stdout)); failed = sum(int(n) for n in _re.findall(r'(\d+) failed', browser.stdout))
        check('BROWSER non-intercepted Playwright: search, detail (approved only), payment page with API policy',
              browser.returncode == 0 and passed == 3 and failed == 0, returncode=browser.returncode, passedCount=passed, failedCount=failed)
        after = compose('logs', '--no-color', 'api-gateway').count('[GATEWAY_ACCESS]')
        check('BROWSER requests reached the real Gateway (access-log lines added)', after - before >= 5, added=after - before)
        api(f"/api/bookings/{live['id']}", 'DELETE', token=c1['token'])

    # ---------------- Batch 9G: Gateway-generated failures use the envelope (STD-026)
    compose('pause', 'hotel-service')
    try:
        t0 = time.monotonic(); code, d, h = api(f'/api/hotel/hotels/{hid}', timeout=60); elapsed = round(time.monotonic() - t0, 1)
    finally:
        compose('unpause', 'hotel-service')
    check('GATEWAY response timeout is a 504 DEPENDENCY_TIMEOUT envelope after ~20 s, requestId = X-Request-ID',
          code == 504 and envelope_ok(d, h, 'DEPENDENCY_TIMEOUT') and h.get('X-Request-ID') == (d or {}).get('requestId') and 18 <= elapsed <= 26,
          status=code, seconds=elapsed, code_=(d or {}).get('code'))
    compose('stop', 'regulations-service')
    code, d, h = api('/api/regulations/typed/points', timeout=60)
    check('GATEWAY stopped service is a 503 DEPENDENCY_UNAVAILABLE envelope without internal address',
          code == 503 and envelope_ok(d, h, 'DEPENDENCY_UNAVAILABLE') and ':8091' not in json.dumps(d or {}) and 'refused' not in json.dumps(d or {}).lower(),
          status=code, code_=(d or {}).get('code'))

    # ---------------- C5 runtime correlation from container logs (trace id only, no payloads)
    seen = {}
    for svc in ('api-gateway', 'booking-service', 'hotel-service'):
        logs = compose('logs', '--no-color', svc)
        seen[svc] = logs.count(trace)
    check('C5 trace id present in booking-service logs', seen['booking-service'] > 0, seen=seen)
    check('C5 trace id present in hotel-service logs (Booking→Hotel REST propagation, F-9C-2)', seen['hotel-service'] > 0, seen=seen)

    # ---------------- schema/invariants
    for o in OWNERS:
        hist = psql('checkinn_' + o, "SELECT count(*)||'/'||sum(CASE WHEN success THEN 1 ELSE 0 END) FROM flyway_schema_history")
        check(f'Schema {o}: exactly one successful V1 history row', hist == '1/1', history=hist)

    # ---------------- F-9B-1 last: stop the disposable PostgreSQL; reads must be a bounded 503, never 500/empty data
    compose('stop', 'postgres')
    # Pool wait 10 s (canonical connection-timeout, F-9D-1) < Gateway 20 s: the client gets the service's 503 envelope.
    outage = []
    for name, svc, path, token in [('Hotel search', 'hotel-service', '/api/hotel/hotels/offers/page?hotelName=' + urllib.parse.quote(hotel_name) + '&' + stay, None),
                                   ('Booking read', 'booking-service', f"/api/bookings/{b1['id']}", c1['token'])]:
        t0 = time.monotonic(); code, d, h = api(path, token=token, timeout=60); elapsed = round(time.monotonic() - t0, 1)
        check(f'OUTAGE {name} with database stopped is 503 DEPENDENCY_UNAVAILABLE envelope within 15 s (F-9B-1, F-9D-1)',
              code == 503 and envelope_ok(d, h, 'DEPENDENCY_UNAVAILABLE') and 'content' not in (d or {}) and elapsed <= 15,
              status=code, code_=(d or {}).get('code'), seconds=elapsed)
        outage.append((name, svc))
    # Batch 9F (STD-026): services that used to answer a dead pool with 401 (user) or 500 (revenue).
    for name, path, token, code_expected in [('User profile', '/api/user/profile', c1['token'], None),
                                             ('Owner revenue summary', f"/api/reports/owner/summary?ownerId={owner['id']}&from=2030-01-01&to=2030-01-31", owner['token'], 'DEPENDENCY_UNAVAILABLE')]:
        t0 = time.monotonic(); code, d, h = api(path, token=token, timeout=60); elapsed = round(time.monotonic() - t0, 1)
        check(f'OUTAGE {name} with database stopped is 503 within 15 s, no exception text (STD-026, Batch 9F)',
              code == 503 and (code_expected is None or (d or {}).get('code') == code_expected) and elapsed <= 15
              and 'jdbc' not in json.dumps(d or {}).lower() and 'EntityManager' not in json.dumps(d or {}),
              status=code, code_=(d or {}).get('code'), seconds=elapsed)
    for name, svc in outage:
        logs = compose('logs', '--no-color', '--since', '2m', svc)
        check(f'OUTAGE {name}: service maps the dead pool to the 503 dependency handler (F-9B-1), not 500',
              'Database unavailable - type: CannotCreateTransactionException' in logs and 'Unhandled API exception' not in logs,
              mapped=logs.count('Database unavailable'), unhandled=logs.count('Unhandled API exception'))
    passed = sum(1 for c in CHECKS if c['passed'] is True); failed = sum(1 for c in CHECKS if c['passed'] is False)
    result = dict(batch='9C canonical smoke', runId=run, startedAt=started, finishedAt=now(), gateway='127.0.0.1:58080 (disposable)',
                  hosted=False, supabase=False, legacyTestClone=False, schema='canonical V1 via Flyway 10.22, profile canonical (validate)',
                  syntheticSql=['users.role ADMIN promotion of a freshly registered synthetic account', 'hotels publication DRAFT→APPROVED (no Storage/document workflow)'],
                  checks=CHECKS, summary=dict(passed=passed, failed=failed, observations=sum(1 for c in CHECKS if c['passed'] is None)))
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    # Optional output name keeps earlier evidence files unchanged (e.g. `smoke results-fixes.json`).
    out = sys.argv[2] if len(sys.argv) > 2 and re.fullmatch(r'results[-a-z0-9]*\.json', sys.argv[2]) else 'results.json'
    (EVIDENCE / out).write_text(json.dumps(result, indent=1, ensure_ascii=False), encoding='utf-8', newline='\n')
    print('SUMMARY', json.dumps(result['summary']))
    if failed: sys.exit(1)


if __name__ == '__main__':
    import urllib.parse
    {'prepare': prepare, 'package': package, 'build': build, 'infra': infra, 'migrate': migrate, 'apps': apps,
     'smoke': smoke, 'down': down, 'status': lambda: print(compose('ps', '-a'))}[sys.argv[1]]()
