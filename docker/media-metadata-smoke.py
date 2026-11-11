"""Real HTTP metadata checks; generated fixture rows in the verified test clone only.

Never uploads/deletes cloud objects. Provider behavior is tested with a mock in JPA tests.
"""
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import subprocess
import uuid

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('hotel-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel, owner, other, room, first, second = [str(uuid.uuid4()) for _ in range(6)]
    passed = 0

    def check(condition, label):
        nonlocal passed
        print(json.dumps({'case': label, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
        if not condition:
            raise AssertionError('Metadata regression')
        passed += 1

    def api(method, path, actor=None, body=None):
        args = h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20', '-X', method, '-H', '@-', '-w', '\n%{http_code}']
        if body is not None:
            args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        auth = 'Authorization: Bearer ' + h.token(key, actor, 'OWNER') + '\n' if actor else ''
        result = subprocess.run(args + ['http://localhost:8083' + path], input=auth, capture_output=True, text=True, encoding='utf-8', timeout=30)
        if result.returncode:
            raise RuntimeError('Test HTTP transport failed')
        raw, status = result.stdout.rsplit('\n', 1)
        return int(status), json.loads(raw) if raw else None

    try:
        made = h.sql('checkinn_hotel', "INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT '" + hotel + "','" + owner + "',h.city_id,'Synthetic metadata HTTP hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1 RETURNING id")
        check(made.splitlines()[0] == hotel, 'isolated metadata fixture created')
        room_body = {'hotelId': hotel, 'name': 'Synthetic room', 'basePrice': 125000, 'capacity': {'adults': 2, 'children': 0}, 'roomAmount': 1, 'amenities': []}
        check(api('POST', '/api/v1', other, room_body)[0] == 403, 'unrelated owner cannot create room')
        status, created = api('POST', '/api/v1', owner, room_body)
        check(status == 200 and bool(created.get('id')), 'legitimate room creation still succeeds')
        room = str(uuid.UUID(created['id']))
        check(api('PUT', '/api/v1/' + room, owner, {'isActive': False})[0] == 200, 'room fixture deactivated through authorized API')

        for target, kind in [(hotel, 'HOTEL'), (room, 'ROOM_TYPE')]:
            for index, media in enumerate([first, second]):
                h.sql('checkinn_hotel', f"INSERT INTO media_assets(id,target_id,target_type,url,is_thumbnail,sort_order,created_at,updated_at) VALUES ('{media}','{target}','{kind}','https://example.invalid/synthetic.png',{str(index == 0).lower()},{index},now(),now())")
            path = '/medias/' + second + '/thumbnail'
            check(api('PATCH', path)[0] == 401, kind + ' anonymous thumbnail denied')
            check(api('PATCH', path, other)[0] == 403, kind + ' cross-owner thumbnail denied')
            status, ack = api('PATCH', path, owner)
            check(status == 200 and ack['isThumbnail'] is True, kind + ' owner receives true thumbnail acknowledgement')
            with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
                results = list(pool.map(lambda media: api('PATCH', '/medias/' + media + '/thumbnail', owner), [first, second] * 3))
            count = h.sql('checkinn_hotel', f"SELECT count(*) FROM media_assets WHERE target_id='{target}' AND target_type='{kind}' AND is_thumbnail")
            check(all(status == 200 and ack['isThumbnail'] is True for status, ack in results) and count == '1', kind + ' concurrent thumbnail writes leave one flag')
            h.sql('checkinn_hotel', f"DELETE FROM media_assets WHERE target_id='{target}' AND target_type='{kind}'")
        check(api('DELETE', '/hotels/' + hotel + '/draft', owner)[0] == 409, 'inactive room still blocks draft deletion')
        check(h.sql('checkinn_hotel', f"SELECT count(*) FROM room_types WHERE id='{room}'") == '1', 'blocked draft deletion preserves room')
        body = {'amenityCategories': [{'title': 'Synthetic', 'items': [{'title': 'Do not discard'}]}]}
        check(api('PUT', '/hotels/' + hotel + '/amenities', owner, body)[0] == 400, 'response-only items shape rejected')
        h.sql('checkinn_hotel', f"DELETE FROM room_types WHERE id='{room}' AND hotel_id='{hotel}'")
        check(api('DELETE', '/hotels/' + hotel + '/draft', owner)[0] == 200, 'empty draft remains deletable')
        print(json.dumps({'passed': passed}), flush=True)
    finally:
        h.sql('checkinn_hotel', f"DELETE FROM media_assets WHERE (target_id='{hotel}' AND target_type='HOTEL') OR (target_id='{room}' AND target_type='ROOM_TYPE'); DELETE FROM room_types WHERE hotel_id='{hotel}'; DELETE FROM hotels WHERE id='{hotel}' AND owner_id='{owner}'")


if __name__ == '__main__':
    main()
