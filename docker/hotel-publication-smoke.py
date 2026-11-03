"""Public hotel REST visibility and authorized preview on generated clone fixtures only."""
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('hotel-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    if config['services']['notification-service']['environment']['SPRING_MAIL_HOST'] != 'mailpit':
        raise RuntimeError('Mailpit required')
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    owner, other, admin = [str(uuid.uuid4()) for _ in range(3)]
    marker = 'Synthetic-publication-' + uuid.uuid4().hex
    states = [('APPROVED', True), ('APPROVED', False), ('DRAFT', True), ('PENDING', True), ('REJECTED', True)]
    fixtures = [(str(uuid.uuid4()), status, active) for status, active in states]
    ids = {row[0] for row in fixtures}
    manifest = Path(__file__).parent / 'logs/hotel-publication-last-fixture.json'
    manifest.write_text(json.dumps({'hotels': sorted(ids), 'owner': owner, 'marker': marker}), encoding='utf-8')

    def check(ok, case):
        print(json.dumps({'case': case, 'result': 'PASS' if ok else 'FAIL'}), flush=True)
        if not ok: raise AssertionError('Hotel publication regression')

    def api(path, actor=None, role='OWNER', direct=True, extra=''):
        base = 'http://localhost:8083' if direct else 'http://api-gateway:8080/api/hotel'
        auth = 'Authorization: Bearer ' + h.token(key, actor, role) + '\n' if actor else ''
        result = subprocess.run(h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20', '-H', '@-',
                                '-w', '\n%{http_code}', base + path], input=auth + extra, capture_output=True, text=True,
                                encoding='utf-8', timeout=30)
        if result.returncode: raise RuntimeError('Hotel test HTTP transport failed')
        body, status = result.stdout.rsplit('\n', 1)
        return int(status), json.loads(body) if body else None

    for attempt in range(40):
        if api('/cities', direct=False)[0] == 200: break
        time.sleep(1)
    else: raise RuntimeError('Gateway discovery unavailable')
    city = str(uuid.UUID(h.sql('checkinn_hotel', 'SELECT city_id FROM hotels LIMIT 1')))
    try:
        for hotel, status, active in fixtures:
            created = h.sql('checkinn_hotel', "INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"
                            + hotel + "','owner_id','" + owner + "','name','" + marker + "','is_active'," + str(active).lower()
                            + ",'approved_status','" + status + "','created_at',now(),'updated_at',now()))).* FROM hotels h LIMIT 1 RETURNING id")
            if created.splitlines()[0] != hotel: raise RuntimeError('Synthetic hotel creation failed')
        for direct in [True, False]:
            surface = 'direct' if direct else 'gateway'
            for hotel, status, active in fixtures:
                label = surface + ' ' + status + (' active' if active else ' inactive')
                expected = 200 if active and status == 'APPROVED' else 404
                check(api('/hotels/' + hotel, direct=direct)[0] == expected, label + ' public detail')
                check(api('/hotels/' + hotel, owner, direct=direct)[0] == expected, label + ' public detail stays public for owner')
                check(api('/hotels/' + hotel + '/preview', direct=direct)[0] == 401, label + ' anonymous preview denied')
                code, body = api('/hotels/' + hotel + '/preview', owner, direct=direct)
                check(code == 200 and body['id'] == hotel and body['approvedStatus'] == status and body['isActive'] == active,
                      label + ' owner preview preserves state')
                check(api('/hotels/' + hotel + '/preview', admin, 'ADMIN', direct=direct)[0] == 200, label + ' admin preview')
                check(api('/hotels/' + hotel + '/preview', other, direct=direct, extra='X-User-Id: ' + owner + '\nX-Is-Admin: true\n')[0] == 403,
                      label + ' forged identity cannot preview')
                check(api('/hotels/' + hotel + '/preview', other, 'CUSTOMER', direct=direct)[0] == 403, label + ' unrelated customer denied')
            for path in ['/hotels', '/hotels/search?name=' + marker, '/hotels/city/' + city]:
                code, rows = api(path, direct=direct)
                visible = {row['id'] for row in rows} & ids if isinstance(rows, list) else set()
                check(code == 200 and visible == {fixtures[0][0]}, surface + ' public list applies active and approval: ' + path.split('?')[0].split('/')[2 if path.startswith('/hotels/city') else 1])
            for path in ['/hotels/owner', '/hotels/owner/' + city]:
                code, rows = api(path, owner, direct=direct)
                check(code == 200 and {row['id'] for row in rows} == ids, surface + ' own list retains unpublished hotels')
            check(api('/hotels/' + str(uuid.uuid4()) + '/preview', owner, direct=direct)[0] == 404, surface + ' missing preview returns 404')
    finally:
        h.sql('checkinn_hotel', "DELETE FROM hotels WHERE id IN ('" + "','".join(sorted(ids)) + "') AND owner_id='" + owner + "' AND name='" + marker + "'")
    check(h.sql('checkinn_hotel', "SELECT count(*) FROM hotels WHERE id IN ('" + "','".join(sorted(ids)) + "')") == '0', 'synthetic hotel cleanup')


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}), flush=True)
        raise SystemExit(1)
