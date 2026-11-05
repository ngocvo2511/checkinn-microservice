"""Nullable description expansion and API round-trip on the verified hotel clone only."""
import argparse
import importlib.util
import json
from pathlib import Path
import time
import uuid

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('security', root / 'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--migrate-only', action='store_true')
    options = args.parse_args()
    h.verify_running_clone('hotel-service')
    if options.migrate_only:
        query = "SELECT count(*) || '|' || coalesce(md5(string_agg(md5((to_jsonb(r)-'description')::text),'' ORDER BY id)),'empty') FROM room_types r"
        before = h.sql('checkinn_hotel', query)
        for _ in range(2):
            h.run(h.COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', 'checkinn_test', '-d', 'checkinn_hotel'],
                  (root / 'sql' / 'room-description-20261105.sql').read_text())
        if before != h.sql('checkinn_hotel', query):
            raise RuntimeError('Legacy room fingerprint changed during expansion')
        shape = h.sql('checkinn_hotel', "SELECT data_type || '|' || is_nullable || '|' || coalesce(column_default,'NONE') FROM information_schema.columns WHERE table_name='room_types' AND column_name='description'")
        if shape != 'text|YES|NONE':
            raise RuntimeError('Description must be nullable text without a default')
        print('PASS: migration replay, legacy fingerprint, nullable TEXT/no default', flush=True)
        return
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel, owner, other = [str(uuid.uuid4()) for _ in range(3)]
    rooms = []
    def api(method, path, actor=owner, body=None, gateway=False):
        headers = '' if actor is None else 'Authorization: Bearer ' + h.token(key, actor, 'OWNER') + '\n'
        base = 'http://api-gateway:8080/api/hotel' if gateway else 'http://localhost:8083'
        command = h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '15', '-X', method, '-H', '@-',
                              '-H', 'Content-Type: application/json', '-w', '\n%{http_code}']
        if body is not None:
            command += ['--data', json.dumps(body, ensure_ascii=False)]
        raw = h.run(command + [base + path], headers)
        text, separator, code = raw.rpartition('\n')
        if not separator:
            text, code = '', raw
        try:
            value = json.loads(text) if text else None
        except ValueError:
            value = None
        return int(code), value
    def check(value, name):
        print(json.dumps({'case': name, 'result': 'PASS' if value else 'FAIL'}), flush=True)
        if not value:
            raise AssertionError(name)
    try:
        created = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','room-description-regression-"+hotel+"','is_active',true,'approved_status','APPROVED'))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM fixture")
        if created != '1':
            raise RuntimeError('NEEDS_ENVIRONMENT: no hotel template')
        body = {'hotelId': hotel, 'name': 'Room description fixture', 'basePrice': 1000000, 'roomAmount': 2,
                'capacity': {'adults': 2, 'children': 0}, 'amenities': [], 'description': 'Phòng hướng biển 🌊\nTầng 2'}
        code, result = api('POST', '/api/v1', body=body)
        check(code == 200 and bool(result), 'owner creates synthetic room')
        room = str(uuid.UUID(result['id']))
        rooms.append(room)
        check(result.get('description') == body['description'], 'create preserves Unicode description')
        for suffix, actor, gateway in [('', None, False), ('/preview', owner, False), ('', None, True)]:
            deadline = time.monotonic() + 90
            while True:
                code, result = api('GET', '/api/v1/' + room + suffix, actor, gateway=gateway)
                if code != 503 or not gateway or time.monotonic() >= deadline:
                    break
                time.sleep(1)
            check(code == 200 and result.get('description') == body['description'], 'public/private/gateway reload preserves description')
        long_description = 'Mô tả ' * 1000
        for patch, expected in [({'name': 'Renamed description fixture'}, body['description']),
                                ({'description': None}, body['description']), ({'description': ''}, ''),
                                ({'description': '"quoted"\n<script>plain text</script>'}, '"quoted"\n<script>plain text</script>'),
                                ({'description': long_description}, long_description)]:
            code, result = api('PUT', '/api/v1/' + room, body=patch)
            check(code == 200 and result.get('description') == expected, 'partial/null/clear/quoted/long update contract')
            code, result = api('GET', '/api/v1/' + room, None)
            check(code == 200 and result.get('description') == expected, 'persisted update reloads exactly')
        for actor, expected in [(None, 401), (other, 403)]:
            code, _ = api('PUT', '/api/v1/' + room, actor, {'description': 'unauthorized overwrite'})
            check(code == expected, 'anonymous/cross-owner description mutation denied')
        code, result = api('GET', '/api/v1/' + room, None)
        check(code == 200 and result.get('description') == long_description,
              'denied writes preserve stored description')
        body.pop('description')
        code, result = api('POST', '/api/v1', body=body)
        check(code == 200 and result.get('description') is None, 'legacy create has no fabricated description')
        rooms.append(str(uuid.UUID(result['id'])))
    finally:
        # Discover fresh rooms if a response was lost; restrict recovery to the generated hotel/owner.
        discovered = h.sql('checkinn_hotel', "SELECT r.id FROM room_types r JOIN hotels h ON h.id=r.hotel_id WHERE h.id='"+hotel+"' AND h.owner_id='"+owner+"'")
        rooms = list(set(rooms + [str(uuid.UUID(value)) for value in discovered.splitlines()]))
        for room in rooms:
            h.sql('checkinn_hotel', "DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'")
        h.sql('checkinn_hotel', "DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='room-description-regression-"+hotel+"'")
        check(h.sql('checkinn_hotel', "SELECT count(*) FROM hotels WHERE id='"+hotel+"'") == '0', 'synthetic fixture cleanup')


if __name__ == '__main__':
    main()
