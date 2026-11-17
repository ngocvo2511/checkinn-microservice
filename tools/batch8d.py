"""Batch8D read-optimization harness (opt-in, local Windows operator tool).

Reuses the reviewed Batch8B/8C contracts (guarded lifecycle, exact-ID fixture manifest,
read-only diagnostics, exact-ID cleanup) but writes only to the Batch8D evidence and
private directories. Never runs migrations/DDL, never force-kills, never touches ADMIN.

SQL attribution: SQL/JdbcTemplate/transaction DEBUG loggers are enabled on the owned
Hotel JVM only inside bounded trace windows (loopback actuator, owned process), and
statement text is recorded without bind values (binder logging stays OFF).
"""
import concurrent.futures, datetime, hashlib, importlib.util, json, re, secrets, statistics, sys, time, urllib.request, urllib.error, uuid
from pathlib import Path

R = Path(__file__).resolve().parents[1]
OUT = R / 'Docs/evidence/batch-8d-read-optimization'
PRIVATE = R / '.local/batch8d'
OUT.mkdir(parents=True, exist_ok=True); PRIVATE.mkdir(parents=True, exist_ok=True)
ARTIFACTS = {'before': PRIVATE / 'artifacts/hotel-service-before.jar', 'after': PRIVATE / 'artifacts/hotel-service-after.jar'}


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, R / path); mod = importlib.util.module_from_spec(spec); spec.loader.exec_module(mod); return mod


d = load('b8d_diag', 'tools/batch8c-diagnostics.py'); d.OUT = OUT
rt = load('b8d_runtime', 'tools/batch8c-runtime.py'); rt.d.OUT = OUT
smoke = load('b8d_smoke', 'tools/batch8c-smoke.py'); smoke.d.OUT = OUT
bench_mod = load('b8d_bench', 'tools/batch8c-benchmark.py')
a = bench_mod.c.a  # shared batch8b-acceptance instance used by the benchmark module
a.OUT = OUT; a.PRIVATE = PRIVATE; bench_mod.b.a = a; bench_mod.b.O = OUT
life = load('b8d_life', 'tools/batch8c-lifecycle.py'); life.a = a; life.c.a = a
hr = load('b8d_hosted_readonly', 'tools/batch8-hosted-readonly.py'); hr.OUT = OUT
h = d.h; stack = h.stack


def write(name, value): d.write(name, value)
def now(): return datetime.datetime.now(datetime.timezone.utc).isoformat()


# ---------------------------------------------------------------- preflight / preservation
def preflight(phase):
    import subprocess
    rows = []
    for owner in h.GROUPS:
        if phase == 'before':
            r = subprocess.run([sys.executable, str(R / 'tools/supabase-runner/run-preflight.py'), owner, '--runtime-check'], capture_output=True, text=True, timeout=120)
            src = h.PRIVATE / (owner + '.hosted-runtime-readonly-2026-11-17.json')
            if src.exists(): write(owner + '-runtime-preflight.json', json.loads(src.read_text()))
            print(owner, 'runtime preflight exit', r.returncode, flush=True)
        try:
            snap = hr.snapshot(owner); write(owner + '-' + phase + '.json', snap)
            rows.append(dict(owner=owner, baselineDifferences=snap['baselineDifferences'], schemaFingerprint=snap['schemaFingerprint'], flywayRows=len(snap['history']), counts=snap['counts'], triggers=snap['triggers']))
        except Exception as e:
            rows.append(dict(owner=owner, status='BLOCKED', error=type(e).__name__))
        diag = d.diagnostic(owner); write(owner + '-sessions-' + phase + '.json', diag)
        rows[-1].update(connection=dict(status=diag['status'], maxConnections=diag.get('maxConnections'), reserved=diag.get('reserved'), runtimeRoleCap=diag.get('runtimeRoleCap'), tls=diag.get('clientTls'), version=diag.get('version'), database=diag.get('database')))
        print(owner, rows[-1].get('baselineDifferences'), rows[-1]['connection'], flush=True)
    write('preflight-summary-' + phase + '.json', rows)


def preservation(phase):
    import hashlib as hl, hmac
    keypath = PRIVATE / 'digest-key.private'
    if not keypath.exists(): keypath.write_bytes(secrets.token_bytes(32))
    key = keypath.read_bytes()
    for owner in h.GROUPS:
        tables = sorted(json.loads((OUT / (owner + '-before.json')).read_text())['counts'])
        digests = {}
        with d.connect(owner) as c:
            with c.cursor() as cur:
                cur.execute('SET TRANSACTION READ ONLY')
                for table in tables:
                    cur.execute('SELECT row_to_json(t)::text FROM public.' + table + ' t')
                    vals = sorted(hmac.new(key, r[0].encode(), hl.sha256).hexdigest() for r in cur.fetchall())
                    digests[table] = dict(count=len(vals), digest=hl.sha256(json.dumps(vals).encode()).hexdigest())
                if owner == 'user':
                    cur.execute("SELECT id::text,role,is_active,username FROM users WHERE username='admin'")
                    admin = cur.fetchall(); assert len(admin) == 1 and admin[0][1:] == ('ADMIN', True, 'admin')
                    write('admin-preservation-' + phase + '.json', dict(identityHmac=hmac.new(key, admin[0][0].encode(), hl.sha256).hexdigest(), role='ADMIN', active=True, exists=True, completeUsersTableDigest=digests['users'], note='HMAC over complete rows (includes password column) without exporting it'))
        write(owner + '-digests-' + phase + '.json', digests); print(owner, 'digests', phase, flush=True)


def manifest_init():
    p = OUT / 'fixture-manifest.json'
    assert not p.exists(), 'Batch8D manifest already exists; never reuse/overwrite a run manifest'
    run = 'batch8d-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(4)
    write(p.name, dict(testRunId=run, createdAtUtc=now(), writesAllowed=False, records=[], protectedAdmin='admin', reason='Writes gated by preflight + runtime readiness'))
    print(run)


# ---------------------------------------------------------------- lifecycle
def _jar_popen(original, jar):
    def popen(command, **kw):
        if command and command[0] == 'java' and '-jar' in command:
            i = command.index('-jar') + 1
            if Path(command[i]).parent == R / 'hotel-service/target':
                command = command[:i] + [str(jar)] + command[i + 1:] + ['--management.endpoints.web.exposure.include=health,info,metrics,loggers']
        return original(command, **kw)
    return popen


def start(name, jar_label=None):
    import subprocess
    port = stack.state()['ports'][name]
    res = subprocess.run(['powershell', '-NoProfile', '-Command', "Get-CimInstance Win32_Process -Filter \"name='java.exe'\" | Select-Object -ExpandProperty CommandLine | ConvertTo-Json -Compress"], capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=15)
    cmds = json.loads(res.stdout) if res.stdout.strip() else []
    cmds = [cmds] if isinstance(cmds, str) else cmds
    assert not any(c and ('--server.port=' + str(port) in c or (name == 'hotel' and ('hotel-service-before.jar' in c or 'hotel-service-after.jar' in c))) for c in cmds), 'Existing JVM for port/artifact requires review'
    original = stack.subprocess.Popen
    if name == 'hotel':
        jar = ARTIFACTS[jar_label]; assert jar.exists(), 'Missing hotel artifact ' + jar_label
        stack.subprocess.Popen = _jar_popen(original, jar)
    try: rt.start(name)
    finally: stack.subprocess.Popen = original
    if name == 'hotel':
        st = stack.state(); st['processes']['hotel']['batch8dArtifact'] = jar_label
        st['processes']['hotel']['batch8dArtifactSha256'] = hashlib.sha256(ARTIFACTS[jar_label].read_bytes()).hexdigest(); stack.save(st)
        events = json.loads((OUT / 'hotel-artifact-events.json').read_text()) if (OUT / 'hotel-artifact-events.json').exists() else []
        events.append(dict(atUtc=now(), artifact=jar_label, sha256=st['processes']['hotel']['batch8dArtifactSha256'], pidCreation=st['processes']['hotel'].get('creationDate')))
        write('hotel-artifact-events.json', events)


def chain_ready(label):
    for attempt in range(6):
        if smoke.smoke(label + '-attempt-' + str(attempt)):
            metrics(label)
            budget = []
            for owner in ('user', 'hotel', 'booking'):
                diag = d.diagnostic(owner)
                if diag['status'] != 'PASS': raise RuntimeError('DIAGNOSTIC_BLOCKED')
                observed = sum(r['backendRows'] for r in diag['backendActivity'] if r['role'] == 'checkinn_' + owner + '_app')
                budget.append(dict(owner=owner, observedRuntimeBackends=observed, roleCap=diag['runtimeRoleCap'], poolMax=2, headroom=diag['runtimeRoleCap'] - observed))
            ok = all(b['headroom'] >= 1 for b in budget)
            write('benchmark-readiness-gate.json', dict(passed=ok, atUtc=now(), label=label, smoke='smoke-' + label + '-attempt-' + str(attempt) + '.json', budget=budget,
                  hotelArtifact=stack.state()['processes']['hotel'].get('batch8dArtifact')))
            print('FULL_CHAIN_READY' if ok else 'HEADROOM_BLOCKED', budget, flush=True)
            if not ok: raise RuntimeError('INSUFFICIENT_HEADROOM_NO_BENCHMARK')
            return
        if attempt < 5: time.sleep(10)
    raise RuntimeError('CHAIN_READINESS_FAILED_NO_BENCHMARK')


def start_core(label, jar_label):
    assert not stack.state()['processes'], 'All prior owned JVMs must have exited'
    rt.invalidate_readiness('Restart invalidates prior chain readiness')
    for name in rt.SERVICES: start(name, jar_label)
    chain_ready(label)


def restart_hotel(label, jar_label):
    rt.invalidate_readiness('Hotel artifact switch invalidates readiness')
    rt.stop('hotel'); start('hotel', jar_label); chain_ready(label)


def metrics(label):
    rows = {}
    for name in ('user', 'hotel', 'booking'):
        port = stack.state()['ports'][name]; m = {}
        for key in ('hikaricp.connections.active', 'hikaricp.connections.idle', 'hikaricp.connections.pending', 'hikaricp.connections.max', 'hikaricp.connections.timeout', 'hikaricp.connections.acquire', 'hikaricp.connections.usage', 'hikaricp.connections.creation', 'jvm.memory.used?tag=area:heap', 'jvm.memory.max?tag=area:heap', 'jvm.gc.pause', 'process.cpu.usage', 'process.cpu.time'):
            code, result = smoke.api(port, '/actuator/metrics/' + key)
            m[key] = result if code == 200 else {'httpStatus': code}
        rows[name] = m
    write('runtime-metrics-' + label + '.json', rows)


# ---------------------------------------------------------------- fixtures
def account(role, index):
    name = 'b8d_' + role.lower() + secrets.token_hex(5); password = secrets.token_urlsafe(20) + 'Aa1!'; email = name + '@example.invalid'
    secret = dict(username=name, password=password, email=email, role=role); p = PRIVATE / (name + '.json'); p.write_text(json.dumps(secret))
    route = '/api/auth/register-owner' if role == 'OWNER' else '/api/auth/register'
    code, result = a.api(route, 'POST', dict(username=name, password=password, email=email, fullName='Synthetic Batch8D ' + role))
    a.check(code == 200 and bool(result.get('userId')), role + ' register Gateway', code)
    uid = result['userId']; a.record('user', 'users', 'id', uid, async_effects=['user_profiles', 'otps', 'Rabbit OTP message; no SMTP consumer started'])
    a.discover()
    # Exact synthetic identity only; OTP never exported. Not SMTP delivery acceptance.
    otp = a.sql('user', f"SELECT json_build_object('otp',otp_code)::text FROM otps WHERE email='{email}'")[0]['otp']
    code, _ = a.api('/api/auth/otp/verify', 'POST', dict(email=email, otpCode=otp)); a.check(code == 200, role + ' API verify exact synthetic OTP (DB-assisted setup)', code)
    code, result = a.api('/api/auth/login', 'POST', dict(usernameOrEmail=name, password=password)); a.check(code == 200 and result.get('role') == role, role + ' real Gateway login', code)
    secret.update(id=uid, token=result['token']); p.write_text(json.dumps(secret)); return secret


a.account = account


def setup():
    assert json.loads((OUT / 'benchmark-readiness-gate.json').read_text())['passed']
    a.setup()


def dataset(hotel_count, rooms_per_hotel):
    gate = json.loads((OUT / 'benchmark-readiness-gate.json').read_text()); assert gate['passed']
    assert smoke.smoke('dataset-gate-' + str(hotel_count) + 'x' + str(rooms_per_hotel)), 'Live full-chain readiness must pass before dataset writes'
    accounts = json.loads((PRIVATE / 'accounts.json').read_text()); owners = [x for x in accounts if x['role'] == 'OWNER']; at = a.admin()
    dp = PRIVATE / 'dataset.json'; ds = json.loads(dp.read_text()) if dp.exists() else {'hotels': [], 'rooms': []}
    run = a.manifest()['testRunId']
    city = a.sql('hotel', "SELECT json_build_object('id',id)::text FROM cities ORDER BY id LIMIT 1")[0]['id']; ds['city'] = city
    for i in range(hotel_count):
        owner = owners[i % len(owners)]
        if i >= len(ds['hotels']):
            code, body = a.api('/hotels', 'POST', {'name': 'Synthetic Batch8D ' + run[-8:] + ' ' + str(i), 'description': 'Synthetic temporary read-optimization benchmark', 'starRating': 3, 'cityId': city, 'address': {'street': 'Synthetic street', 'city': 'Synthetic city', 'country': 'VN'}, 'contactEmail': owner['email'], 'contactPhone': '0900000000', 'businessLicenseNumber': 'SYN-B8D-' + str(i), 'taxId': 'SYN-B8D', 'ownerIdentityNumber': 'SYN-B8D', 'accommodationType': 'HOTEL', 'policies': [], 'questions': [], 'amenityCategories': []}, owner['token'])
            a.check(code == 200 and 'id' in body, 'OWNER create hotel Gateway', code); hid = body['id']; a.record('hotel', 'hotels', 'id', hid, [owner['id']])
            ds['hotels'].append({'id': hid, 'ownerId': owner['id']}); dp.write_text(json.dumps(ds))
            # Exact owned DRAFT synthetic setup; not publication API acceptance (Storage forbidden).
            a.sql('hotel', f"UPDATE hotels SET approved_status='APPROVED',is_active=true WHERE id='{uuid.UUID(hid)}' AND owner_id='{uuid.UUID(owner['id'])}' AND approved_status='DRAFT'", True)
        hid = ds['hotels'][i]['id']
        while len([x for x in ds['rooms'] if x['hotelId'] == hid]) < rooms_per_hotel:
            j = len(ds['rooms'])
            code, body = a.api('/api/v1', 'POST', {'hotelId': hid, 'name': 'Synthetic B8D room ' + str(j), 'basePrice': 1000000 + j * 1000, 'capacity': {'adults': 2, 'children': 1}, 'roomAmount': 2, 'amenities': [], 'description': 'Temporary synthetic benchmark'}, owner['token'])
            a.check(code == 200 and 'id' in body, 'OWNER create room Gateway', code); rid = body['id']; a.record('hotel', 'room_types', 'id', rid, [hid])
            ds['rooms'].append({'id': rid, 'hotelId': hid, 'ownerId': owner['id']}); dp.write_text(json.dumps(ds))
            code, p = a.api('/api/v1/' + rid + '/inventory-review', 'POST', {'quantity': 2, 'revision': 0, 'source': 'Synthetic Batch8D declaration; no physical stock assertion'}, owner['token'])
            a.check(code == 201 and p.get('status') == 'PENDING', 'OWNER submit pending inventory', code)
            a.record('hotel', 'room_inventory_review_requests', 'id', p['id'], [rid]); a.record('hotel', 'room_inventory_review_states', 'room_id', rid, [rid, p['id']])
            if j != 0:  # room 0 stays PENDING: exclusion invariant + later negative E2E
                code, body = a.api('/api/hotel/admin/inventory-reviews/' + p['id'] + '/review', 'POST', {'approve': True, 'revision': p['revision'], 'reason': 'Synthetic Batch8D controlled benchmark setup'}, at)
                a.check(code == 200 and body.get('status') == 'APPROVED', 'ADMIN approve inventory Gateway', code)
    a.discover()
    shape = dict(hotels=len(ds['hotels']), rooms=len(ds['rooms']), perHotel=[len([x for x in ds['rooms'] if x['hotelId'] == hh['id']]) for hh in ds['hotels']], pendingRoomIndex=0, publication='SQL synthetic exact-ID setup; not hotel-publication API acceptance')
    write('dataset-' + str(hotel_count) + 'x' + str(rooms_per_hotel) + '.json', dict(createdAtUtc=now(), dataset=ds, shape=shape)); print(shape, flush=True)


def shrink(hotel_count, rooms_per_hotel):
    """Exact-ID removal of the measured-stage delta so AFTER uses the identical BEFORE stage IDs."""
    import psycopg
    m = a.manifest(); assert m['writesAllowed'] and not any(r['table'] == 'bookings' for r in m['records']), 'No booking fixtures may exist before shrink'
    dp = PRIVATE / 'dataset.json'; ds = json.loads(dp.read_text())
    keep_h = [x['id'] for x in ds['hotels'][:hotel_count]]
    keep_r = [r['id'] for hid in keep_h for r in [x for x in ds['rooms'] if x['hotelId'] == hid][:rooms_per_hotel]]
    drop_h = [x['id'] for x in ds['hotels'][hotel_count:]]; drop_r = [x['id'] for x in ds['rooms'] if x['id'] not in keep_r]
    manifested = {(r['table'], r['id']) for r in m['records']}
    assert all(('hotels', x) in manifested for x in drop_h) and all(('room_types', x) in manifested for x in drop_r)
    a.discover(); m = a.manifest(); manifested = {(r['table'], r['id']) for r in m['records']}
    rooms_sql = ','.join("'" + str(uuid.UUID(x)) + "'" for x in drop_r)
    plan = []
    for table, key, pred in [('room_holds', 'id', 'room_type_id'), ('room_availability', 'id', 'room_type_id'), ('room_inventory_review_states', 'room_id', 'room_id'), ('room_inventory_review_requests', 'id', 'room_id')]:
        ids = [r['id'] for r in a.sql('hotel', f"SELECT json_build_object('id',{key})::text FROM {table} WHERE {pred} IN ({rooms_sql})")] if drop_r else []
        assert all((table, str(x)) in manifested for x in ids), 'Unmanifested child: stop, do not expand scope'
        plan.append((table, key, [str(x) for x in ids]))
    plan += [('room_types', 'id', drop_r), ('hotels', 'id', drop_h)]
    # Rooms of dropped hotels must all be in drop_r (no unmanifested room under a dropped hotel).
    if drop_h:
        live = [r['id'] for r in a.sql('hotel', "SELECT json_build_object('id',id)::text FROM room_types WHERE hotel_id IN (" + ','.join("'" + str(uuid.UUID(x)) + "'" for x in drop_h) + ")")]
        assert set(live) <= set(drop_r)
    a.sql('hotel', "SELECT json_build_object('ready',true)::text"); conn = a.READ_CONNECTIONS['hotel']; counts = []
    try:
        with conn.cursor() as cur:
            cur.execute('SET LOCAL statement_timeout=30000')
            for table, key, ids in plan:
                if not ids: counts.append((table, 0)); continue
                q = f"DELETE FROM public.{table} WHERE {key} IN (" + ','.join("'" + str(uuid.UUID(x)) + "'" for x in ids) + ")"
                cur.execute(q); assert cur.rowcount == len(ids), 'Exact count mismatch; rollback'; counts.append((table, cur.rowcount))
        conn.commit()
    except Exception:
        conn.rollback(); raise
    removed = {(t, x) for t, k, ids in plan for x in ids}
    m = a.manifest()
    for r in m['records']:
        if (r['table'], r['id']) in removed: r['cleanupVerification'] = 'ABSENT_STAGE_SHRINK_' + str(hotel_count) + 'x' + str(rooms_per_hotel)
    a.save(m)
    remaining = sum(a.sql('hotel', f"SELECT json_build_object('n',count(*))::text FROM {t} WHERE {k} IN (" + ','.join("'" + str(uuid.UUID(x)) + "'" for x in ids) + ")")[0]['n'] for t, k, ids in plan if ids)
    ds['hotels'] = ds['hotels'][:hotel_count]; ds['rooms'] = [x for x in ds['rooms'] if x['id'] in keep_r]; dp.write_text(json.dumps(ds))
    write('shrink-' + str(hotel_count) + 'x' + str(rooms_per_hotel) + '.json', dict(atUtc=now(), deleted=counts, remainingOfDeleted=remaining, keptHotels=keep_h, keptRooms=keep_r,
          method='Exact manifested IDs, one hotel-DB transaction, expected rowcounts; no wildcard; no booking/financial rows involved'))
    assert remaining == 0; print('SHRINK_DONE', counts, flush=True)


# ---------------------------------------------------------------- measurement
def paths():
    ds = json.loads((PRIVATE / 'dataset.json').read_text()); approved_room = ds['rooms'][1]
    return {'search_undated': '/hotels/offers?cityId=' + ds['city'],
            'search_dated': '/hotels/offers?cityId=' + ds['city'] + '&checkIn=2027-02-10&checkOut=2027-02-11',
            'public_catalog': '/hotels', 'hotel_detail': '/hotels/' + ds['rooms'][0]['hotelId'],
            'room_availability': '/api/v1/availability/check?roomTypeId=' + approved_room['id'] + '&checkIn=2027-02-10&checkOut=2027-02-11&quantity=1'}


bench_mod.b.paths = paths
LOGGERS = {'org.hibernate.SQL': ('DEBUG', 'OFF'), 'org.springframework.jdbc.core.JdbcTemplate': ('DEBUG', None), 'org.springframework.orm.jpa.JpaTransactionManager': ('DEBUG', None)}


def set_loggers(enabled):
    port = stack.state()['ports']['hotel']
    for name, (on, off) in LOGGERS.items():
        req = urllib.request.Request('http://127.0.0.1:' + str(port) + '/actuator/loggers/' + name, method='POST', headers={'Content-Type': 'application/json'}, data=json.dumps({'configuredLevel': on if enabled else off}).encode())
        with urllib.request.urlopen(req, timeout=10) as r: assert r.status in (200, 204)


def fingerprint(sql):
    s = re.sub(r'\s+', ' ', sql.strip())
    s = re.sub(r'\((\?\s*,\s*)+\?\)', '(?...)', s)
    s = re.sub(r"'[^']*'", "'?'", s)  # defensive: no literal values in evidence
    return hashlib.sha256(s.encode()).hexdigest()[:12], s[:400] if s.startswith('select h1_0.') else s


def normalize_body(body):
    """Volatile clock fields removed; lists sorted by id for set-equivalence."""
    def walk(x):
        if isinstance(x, dict): return {k: walk(v) for k, v in x.items() if k != 'checkedAt'}
        if isinstance(x, list):
            items = [walk(v) for v in x]
            return sorted(items, key=lambda v: json.dumps(v, sort_keys=True))
        return x
    return walk(body)


def summarize_body(name, body):
    if name == 'room_availability': return dict(availableRooms=body.get('availableRooms') if isinstance(body, dict) else None)
    hotels = body if isinstance(body, list) else [body]
    out = []
    for item in hotels:
        hotel = item.get('hotel', item) if isinstance(item, dict) else {}
        offer = item.get('offer') if isinstance(item, dict) else None
        out.append(dict(hotelId=hotel.get('id'), roomTypeIds=sorted(r.get('id') for r in hotel.get('roomTypes') or []), lowestPrice=hotel.get('lowestPrice'),
                        mediaAssets=len(hotel.get('mediaAssets') or []), policies=len(hotel.get('policies') or []), faqs=len(hotel.get('faqs') or []), amenityCategories=len(hotel.get('amenityCategories') or []),
                        offerRoomTypeId=offer.get('roomTypeId') if offer else None, offerAvailable=offer.get('availableRooms') if offer else None))
    return sorted(out, key=lambda v: v['hotelId'] or '')


def trace(label):
    log = h.PRIVATE / 'hotel.console.private.log'; rows = []; bodies = {}
    ds = json.loads((PRIVATE / 'dataset.json').read_text()); pending = ds['rooms'][0]['id']
    for name, path in paths().items():
        warm = bench_mod.b.request(path); write('trace-warmup-' + label + '-' + name + '.json', warm)
        if warm['status'] != 200 or warm['ms'] > 30000: raise RuntimeError('STOP_TRACE_WARMUP_FAILURE')
        set_loggers(True)
        try:
            for sample in range(3):
                trace_id = secrets.token_hex(16); offset = log.stat().st_size; started = time.perf_counter()
                code, body = a.api(path, headers={'traceparent': '00-' + trace_id + '-' + secrets.token_hex(8) + '-01'})
                elapsed = (time.perf_counter() - started) * 1000; time.sleep(0.4)
                with log.open('rb') as f: f.seek(offset); chunk = f.read().decode(errors='replace')
                records = []
                for ln in chunk.splitlines():  # multi-line log events: SQL text follows the header line
                    if re.match(r'^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d', ln): records.append(ln)
                    elif records: records[-1] += '\n' + ln
                mine = [rec for rec in records if '[traceId=' + trace_id + ']' in rec.split('\n', 1)[0]]
                sessions = re.findall(r'\[traceId=' + trace_id + r'\][^\r\n]*Session Metrics \{(.*?)\}', chunk, re.S)
                execute = [(int(ns), int(n)) for se in sessions for ns, n in re.findall(r'(\d+) nanoseconds spent executing (\d+) JDBC statements', se)]
                acquisition = [int(ns) for se in sessions for ns, _ in re.findall(r'(\d+) nanoseconds spent acquiring (\d+) JDBC connections', se)]
                acq_count = [int(n) for se in sessions for n in re.findall(r'\d+ nanoseconds spent acquiring (\d+) JDBC connections', se)]
                hib = [ln.split('org.hibernate.SQL - ', 1)[1] for ln in mine if 'org.hibernate.SQL - ' in ln]
                jdbc = [m.group(1) for ln in mine for m in [re.search(r'Executing prepared SQL statement \[(.*)\]', ln, re.S)] if m]
                tx_new = sum('Creating new transaction' in ln for ln in mine); tx_join = sum('Participating in existing transaction' in ln for ln in mine)
                groups = {}
                for kind, stmts in (('hibernate', hib), ('jdbcTemplate', jdbc)):
                    for s in stmts:
                        fp, text = fingerprint(s); g = groups.setdefault(fp, dict(kind=kind, sql=text, count=0)); g['count'] += 1
                rows.append(dict(endpoint=name, sample=sample, status=code, ms=elapsed, traceId=trace_id, matchedSessions=len(sessions),
                                 hibernateStatisticsStatements=sum(n for _, n in execute) if sessions else None,
                                 hibernateJdbcMsIncludingNetwork=sum(ns for ns, _ in execute) / 1e6 if sessions else None,
                                 connectionAcquisitions=sum(acq_count) if sessions else None, acquisitionMs=sum(acquisition) / 1e6 if sessions else None,
                                 loggedHibernateSql=len(hib), loggedJdbcTemplateSql=len(jdbc), physicalTransactions=tx_new, participatingTransactions=tx_join,
                                 fingerprints=sorted(groups.items(), key=lambda kv: -kv[1]['count'])))
                if code == 200:
                    canon = json.dumps(normalize_body(body), sort_keys=True); raw = json.dumps(body, sort_keys=True)
                    bodies.setdefault(name, []).append(dict(normalizedSha256=hashlib.sha256(canon.encode()).hexdigest(), summary=summarize_body(name, body),
                                                            pendingRoomExposed=pending in raw, bytes=len(raw)))
                    (PRIVATE / ('response-' + label + '-' + name + '.json')).write_text(json.dumps(body, sort_keys=True, indent=1))
                write('sql-trace-' + label + '.json', rows); write('responses-' + label + '.json', bodies)
                print(label, name, code, rows[-1]['hibernateStatisticsStatements'], len(hib), len(jdbc), tx_new, round(elapsed), flush=True)
                if code != 200 or elapsed > 30000: raise RuntimeError('STOP_SLOW_OR_FAILED_ENDPOINT')
                if not sessions: raise RuntimeError('REQUEST_TRACE_INSTRUMENTATION_NOT_MATCHED')
        finally:
            set_loggers(False)
    return rows


def bench(label):
    bench_mod.bench_per_cell(label); metrics('after-bench-' + label)


def direct(label):
    rows = []
    for name, path in paths().items():
        for sample in range(30):
            r = bench_mod.b.request(path, 8083); rows.append(dict(endpoint=name, sample=sample, **r))
            if r['status'] != 200: write('direct-' + label + '.json', rows); raise RuntimeError('DIRECT_FAILURE_STOP')
        write('direct-' + label + '.json', rows); print(label, 'direct', name, flush=True)


CALL_SITES = [  # (predicate on normalized SQL, category, source call site)
    (lambda s: 'from public.media_assets' in s and 'target_id=?' in s, 'media-per-collection', 'Lazy Hotel.mediaAssets / RoomType.mediaAssets init (HotelMapper.toMediaAssetResponses, RoomTypeMapper.toMediaAssetResponses), one SELECT per collection'),
    (lambda s: 'from public.media_assets' in s, 'media-batched', 'Batched media collection init (@BatchSize)'),
    (lambda s: 'JOIN room_inventory_review_states' in s and 'r.id IN' in s, 'approval-set', 'RoomTypeRepository.findEffectivelyApprovedRoomIds (HotelMapper public facts; AvailabilityService.approvedRoomIds via HotelOfferSearchService; getAvailableRoomCounts)'),
    (lambda s: 'SELECT EXISTS' in s and 'room_inventory_review_states' in s, 'approval-single', 'RoomTypeRepository.hasEffectiveInventoryApproval (single-room availability)'),
    (lambda s: 'from public.room_types' in s and 'hotel_id' in s.split(' where ', 1)[-1], 'rooms-by-hotel', 'Hotel.roomTypes lazy init / findByHotelIdIn'),
    (lambda s: 'from public.room_types' in s, 'rooms-by-id', 'RoomTypeRepository.findAllById/findById (availability)'),
    (lambda s: 'from public.hotel_amenity_categories' in s and 'hotel_amenities' not in s, 'amenity-categories', 'HotelAmenityCategoryRepository.findAllByHotelId[In] (HotelMapper public amenities)'),
    (lambda s: 'from public.hotel_amenities' in s, 'amenity-items', 'HotelAmenityRepository.findAllByCategoryHotelId[In]'),
    (lambda s: 'from public.hotel_policies' in s, 'policies', 'HotelPolicyRepository.findAllByHotelId[In] (JOIN FETCH category)'),
    (lambda s: 'from public.hotel_questions' in s, 'faqs', 'QuestionRepository.findAllByHotelId[In]'),
    (lambda s: 'amenity_mapping_states' in s, 'amenity-effective-codes', 'AmenityCatalogService.effectiveCodes (JdbcTemplate)'),
    (lambda s: 'from public.cities' in s, 'city', 'Hotel.city lazy (first-level cache: once per city)'),
    (lambda s: 'from public.room_availability' in s, 'availability-peaks', 'RoomAvailabilityRepository peak occupancy'),
    (lambda s: 'from public.hotels' in s or s.startswith('select h1_0.'), 'hotels', 'Hotel list / findById / findAllById (statement text truncated at 400 chars in early traces)'),
]


def attribution(labels):
    out = {}
    for label in labels:
        rows = json.loads((OUT / ('sql-trace-' + label + '.json')).read_text()); per = {}
        for r in rows:
            if r['sample'] != 0: continue
            cats = {}
            for fp, g in r['fingerprints']:
                cat = next((c for pred, c, _ in CALL_SITES if pred(g['sql'])), 'other')
                cats[cat] = cats.get(cat, 0) + g['count']
            stable = len({(x['hibernateStatisticsStatements'], x['loggedJdbcTemplateSql']) for x in rows if x['endpoint'] == r['endpoint']}) == 1
            per[r['endpoint']] = dict(hibernateStatements=r['hibernateStatisticsStatements'], jdbcTemplateStatements=r['loggedJdbcTemplateSql'], totalSqlStatements=r['hibernateStatisticsStatements'] + r['loggedJdbcTemplateSql'],
                                      physicalSpringTransactions=r['physicalTransactions'], connectionAcquisitions=r['connectionAcquisitions'], byCategory=dict(sorted(cats.items(), key=lambda kv: -kv[1])), stableAcross3Samples=stable,
                                      meanHttpMsTraced=statistics.mean(x['ms'] for x in rows if x['endpoint'] == r['endpoint']), meanHibernateJdbcMs=statistics.mean(x['hibernateJdbcMsIncludingNetwork'] for x in rows if x['endpoint'] == r['endpoint']))
        out[label] = per
    write('root-cause-attribution-' + '-'.join(labels[0].split('-')[:1]) + '.json', dict(callSites={c: site for _, c, site in CALL_SITES}, stages=out))
    for label, per in out.items():
        for ep, v in per.items(): print(label, ep, v['totalSqlStatements'], v['byCategory'], flush=True)


def compare():
    """Controlled comparison: same hosted dataset IDs per stage, same paths/warmup/concurrency/pool."""
    def summ(label):
        p = OUT / ('benchmark-' + label + '-summary.json')
        return {(c['endpoint'], c['concurrency']): c for c in json.loads(p.read_text())} if p.exists() else {}
    def sql(label):
        rows = json.loads((OUT / ('sql-trace-' + label + '.json')).read_text()); res = {}
        for r in rows:
            res.setdefault(r['endpoint'], set()).add(r['hibernateStatisticsStatements'] + r['loggedJdbcTemplateSql'])
        return {k: sorted(v) for k, v in res.items()}
    def direct(label):
        p = OUT / ('direct-' + label + '.json')
        if not p.exists(): return {}
        rows = json.loads(p.read_text()); out = {}
        for ep in {r['endpoint'] for r in rows}:
            v = [r['ms'] for r in rows if r['endpoint'] == ep and r['status'] == 200]
            out[ep] = dict(n=len(v), p50=bench_mod.b.pct(v, .5), p95=bench_mod.b.pct(v, .95))
        return out
    pct = lambda before, after: None if not before else round((before - after) / before * 100, 1)
    table = []; rooms = {'small': 4, 'medium': 15, 'larger': 50}
    for stage in ('small', 'medium', 'larger'):
        sb, sa = summ('before-' + stage), summ('after-' + stage); qb, qa = sql('before-' + stage), sql('after-' + stage)
        db, da = direct('before-' + stage), direct('after-' + stage)
        for ep in ('search_undated', 'search_dated', 'public_catalog', 'hotel_detail', 'room_availability'):
            row = dict(dataset=stage, roomTypes=rooms[stage], endpoint=ep, sqlBefore=qb.get(ep), sqlAfter=qa.get(ep))
            if row['sqlBefore'] and row['sqlAfter'] and len(row['sqlBefore']) == len(row['sqlAfter']) == 1:
                row['sqlReductionPct'] = pct(row['sqlBefore'][0], row['sqlAfter'][0])
            cells = []
            for c in (1, 3, 5, 10):
                b, a2 = sb.get((ep, c)), sa.get((ep, c))
                cell = dict(concurrency=c, before=('SKIPPED_SAFETY' if b and 'p50' not in b else None), after=('SKIPPED_SAFETY' if a2 and 'p50' not in a2 else None))
                if b and 'p50' in b: cell.update(p50Before=b['p50'], p95Before=b['p95'], rpsBefore=b['rps'], errorsBefore=b['errors'], timeoutsBefore=b['timeouts'])
                if a2 and 'p50' in a2: cell.update(p50After=a2['p50'], p95After=a2['p95'], rpsAfter=a2['rps'], errorsAfter=a2['errors'], timeoutsAfter=a2['timeouts'])
                if 'p50Before' in cell and 'p50After' in cell:
                    cell.update(comparable=True, p50ImprovementPct=pct(cell['p50Before'], cell['p50After']), p95ImprovementPct=pct(cell['p95Before'], cell['p95After']), throughputRatio=round(cell['rpsAfter'] / cell['rpsBefore'], 2))
                else: cell['comparable'] = False
                cells.append(cell)
            row['gateway'] = cells
            if ep in db and ep in da: row['direct'] = dict(before=db[ep], after=da[ep], p50ImprovementPct=pct(db[ep]['p50'], da[ep]['p50']))
            table.append(row)
    slope = {}
    for ep in ('search_undated', 'search_dated', 'public_catalog', 'hotel_detail', 'room_availability'):
        for phase in ('before', 'after'):
            pts = [(rooms[s], sql(phase + '-' + s)[ep][0]) for s in ('small', 'medium', 'larger')]
            slope.setdefault(ep, {})[phase] = dict(points=pts, statementsPerAddedRoomType=round((pts[-1][1] - pts[0][1]) / (pts[-1][0] - pts[0][0]), 3))
    write('before-after.json', dict(method='Same hosted fixture IDs per stage (BEFORE grown 2x2->3x5->5x10; AFTER measured 5x10 then exact-ID shrink to the identical 3x5 and 2x2 ID sets). Same endpoints/query params/3 warmups per cell/30 samples/C1-3-5-10 safety gate/pool max2.',
                                    rows=table, queryCountSlope=slope))
    for r in table:
        c1 = r['gateway'][0]
        print(r['dataset'], r['endpoint'], r['sqlBefore'], r['sqlAfter'], r.get('sqlReductionPct'), round(c1.get('p50Before', 0)), round(c1.get('p50After', 0)), c1.get('p50ImprovementPct'), c1.get('p95ImprovementPct'), flush=True)
    for ep, v in slope.items(): print('slope', ep, v['before']['statementsPerAddedRoomType'], '->', v['after']['statementsPerAddedRoomType'])


if __name__ == '__main__':
    act = sys.argv[1]
    if act == 'attribution': attribution(sys.argv[2:]); sys.exit(0)
    if act == 'compare': compare(); sys.exit(0)
    try:
        if act == 'preflight': preflight(sys.argv[2])
        elif act == 'preservation': preservation(sys.argv[2])
        elif act == 'manifest-init': manifest_init()
        elif act == 'start-deps': stack.dependencies(True)
        elif act == 'start-core': start_core(sys.argv[2], sys.argv[3])
        elif act == 'restart-hotel': restart_hotel(sys.argv[2], sys.argv[3])
        elif act == 'stop':
            for n in sys.argv[2:] or [n for n in reversed(rt.SERVICES) if n in stack.state()['processes']]: rt.stop(n)
        elif act == 'ready': chain_ready(sys.argv[2])
        elif act == 'metrics': metrics(sys.argv[2])
        elif act == 'setup': setup()
        elif act == 'dataset': dataset(int(sys.argv[2]), int(sys.argv[3]))
        elif act == 'shrink': shrink(int(sys.argv[2]), int(sys.argv[3]))
        elif act == 'trace': trace(sys.argv[2])
        elif act == 'bench': bench(sys.argv[2])
        elif act == 'bench-endpoint':  # repeat one endpoint's cells under a separate label (original cells kept)
            only = sys.argv[3]; full = paths
            bench_mod.b.paths = lambda: {k: v for k, v in full().items() if k == only}
            bench(sys.argv[2])
        elif act == 'direct': direct(sys.argv[2])
        elif act == 'discover': a.discover()
        elif act == 'e2e': a.e2e()
        elif act == 'concurrency': sys.exit(0 if life.concurrency() else 1)
        elif act == 'cleanup': a.cleanup()
        elif act == 'cleanup-broker':
            original = a.PRIVATE
            class Names:
                def glob(self, pattern): return original.glob('b8d_*.json' if pattern == 'b8b_*.json' else pattern)
            a.PRIVATE = Names()
            try: a.cleanup_broker()
            finally: a.PRIVATE = original
        else: raise SystemExit('unknown action')
    finally:
        for c in a.READ_CONNECTIONS.values(): c.close()
