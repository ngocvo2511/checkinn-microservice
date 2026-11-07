"""Literal Unicode location lookup and legacy text-search compatibility on test clones."""
import importlib.util
import json
import secrets
import time
import uuid
from pathlib import Path
from urllib.parse import urlencode

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('h', root / 'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('hotel-service')
    provinces = [str(uuid.uuid4()) for _ in range(3)]
    cities = [str(uuid.uuid4()) for _ in range(3)]
    hotel, room, owner = [str(uuid.uuid4()) for _ in range(3)]
    marker = 'loccheck_' + secrets.token_hex(12)
    literal = marker + ' %_\\ Đà Nẵng'
    count = 0

    def db(query):
        return h.sql('checkinn_hotel', query)

    def check(ok, name):
        nonlocal count
        print(json.dumps({'case': name, 'result': 'PASS' if ok else 'FAIL'}), flush=True)
        if not ok:
            raise AssertionError(name)
        count += 1

    def fingerprint(table):
        return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM " + table + ' r')

    def text(value):
        # ASCII SQL arguments, JSON decoding only for this generated fixture value.
        return "('" + json.dumps(value).replace("'", "''") + "'::jsonb #>> '{}')"

    def api(surface, path, params):
        service, base = ('hotel-service', 'http://localhost:8083') if surface == 'direct' else ('api-gateway', 'http://localhost:8080/api/hotel')
        raw = h.run(h.COMPOSE + ['exec', '-T', service, 'curl', '-sS', '--max-time', '20', '-w', '\n%{http_code}', base + path + '?' + urlencode(params)])
        body, _, status = raw.rpartition('\n')
        try:
            value = json.loads(body)
        except ValueError:
            value = None
        return int(status), value

    tables = ['provinces', 'cities', 'hotels', 'room_types']
    before = {table: fingerprint(table) for table in tables}
    try:
        # Retry only discovery warm-up reads, never fixture writes or mutations.
        deadline = time.monotonic() + 60
        while True:
            status, value = api('gateway', '/cities/search/locations', {'query': marker})
            if status == 200:
                break
            if status not in (502, 503, 504) or time.monotonic() > deadline:
                raise RuntimeError('Gateway location read readiness failed')
            time.sleep(1)
        for i in range(3):
            prefix = literal if i < 2 else marker + ' XX\\ Đà Nẵng noise'
            db("INSERT INTO provinces(id,name,hotel_count,created_at) VALUES ('" + provinces[i] + "'," + text(prefix + ' province' + str(i)) + ",0,now())")
            db("INSERT INTO cities(id,name,parent_id,hotel_count,created_at) VALUES ('" + cities[i] + "'," + text(prefix + ' cityonly' + str(i)) + ",'" + provinces[i] + "',0,now())")
        check(db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','" + hotel + "','owner_id','" + owner + "','city_id','" + cities[0] + "','name','" + marker + "','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a") == '1', 'scoped published hotel in matching city created')
        check(db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','" + room + "','hotel_id','" + hotel + "','base_price',200000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a") == '1', 'scoped room retains authoritative price and inventory')
        for surface in ['direct', 'gateway']:
            status, matches = api(surface, '/cities/search/locations', {'query': literal.upper()})
            check(status == 200 and isinstance(matches, list) and {v['id'] for v in matches} == set(provinces[:2] + cities[:2]), surface + ' literal metacharacters and Vietnamese case-insensitive lookup')
            check([v['type'] for v in matches] == ['PROVINCE', 'PROVINCE', 'CITY', 'CITY'], surface + ' existing province-before-city grouping retained')
            status, matches = api(surface, '/cities/search/locations', {'query': literal + ' cityonly'})
            by_id = {v['id']: v for v in matches} if isinstance(matches, list) else {}
            check(status == 200 and set(by_id) == set(cities[:2]) and all(by_id[cities[i]]['parentId'] == provinces[i] and by_id[cities[i]]['parentName'] == literal + ' province' + str(i) for i in range(2)), surface + ' city-only search preserves stable parent ID and literal label')
            status, matches = api(surface, '/cities/search/locations', {'query': marker + ' absent'})
            check(status == 200 and matches == [], surface + ' true no-match is empty200')
            status, matches = api(surface, '/cities/all-locations', {})
            check(status == 200 and set(provinces + cities).issubset({v['id'] for v in matches}), surface + ' explicit all-locations catalog contract retained')
            for endpoint in ['/hotels/offers', '/hotels/offers/page']:
                for name, params in [('name-and-text', {'hotelName': marker, 'cityName': literal.upper()}),
                                     ('text-only', {'cityName': literal.upper()}),
                                     ('province-id', {'provinceId': provinces[0]}), ('city-id', {'cityId': cities[0]})]:
                    status, offers = api(surface, endpoint, params)
                    rows = offers.get('content') if isinstance(offers, dict) else offers
                    check(status == 200 and isinstance(rows, list) and len(rows) == 1 and rows[0]['hotel']['id'] == hotel and rows[0]['offer']['roomTypeId'] == room, surface + ' stable scoped location hotel candidates ' + name + ' ' + endpoint)
    finally:
        db("DELETE FROM room_types WHERE id='" + room + "' AND hotel_id='" + hotel + "'; DELETE FROM hotels WHERE id='" + hotel + "' AND owner_id='" + owner + "' AND name='" + marker + "'")
        for i in range(3):
            db("DELETE FROM cities WHERE id='" + cities[i] + "' AND parent_id='" + provinces[i] + "'; DELETE FROM provinces WHERE id='" + provinces[i] + "'")
        check(all(fingerprint(table) == before[table] for table in tables), 'exact location fixture cleanup preserves all pre-existing records')
    print(json.dumps({'verified_cases': count}), flush=True)


if __name__ == '__main__':
    main()
