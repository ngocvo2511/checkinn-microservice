"""Corrupt persisted-counter/quantity safety on scoped test-clone fixtures only."""
import importlib.util
import json
import secrets
import uuid
from pathlib import Path
from urllib.parse import urlencode

root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('h', root / 'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('hotel-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    token = h.token(config['services']['hotel-service']['environment']['SECRET_KEY'], 'booking-service', 'BOOKING_SERVICE')
    hotel, room, owner = [str(uuid.uuid4()) for _ in range(3)]
    marker = 'countercheck_' + secrets.token_hex(12)
    count = failures = 0
    last_error_code = None

    def db(query):
        return h.sql('checkinn_hotel', query)

    def check(ok, name):
        nonlocal count, failures
        count += 1
        failures += not ok
        print(json.dumps({'case': name, 'result': 'PASS' if ok else 'FAIL'}), flush=True)

    def fingerprint(table, where=''):
        return db("SELECT md5(COALESCE(string_agg(to_jsonb(r)::text,'|' ORDER BY id),'')) FROM " + table + ' r ' + where)

    def api(path, body=None, method='POST'):
        nonlocal last_error_code
        settings = 'request = ' + json.dumps(method) + '\nheader = "Content-Type: application/json"\nheader = ' + json.dumps('Authorization: Bearer ' + token) + '\n'
        if body is not None:
            settings += 'data-binary = ' + json.dumps(json.dumps(body)) + '\n'
        raw = h.run(h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20', '--config', '-', '-w', '\n%{http_code}', 'http://localhost:8083' + path], settings)
        try:
            value = json.loads(raw.rpartition('\n')[0])
            last_error_code = value.get('code') if isinstance(value, dict) else None
        except ValueError:
            last_error_code = None
        return int(raw.rpartition('\n')[2])

    tables = ['hotels', 'room_types', 'room_availability', 'room_holds']
    before = {table: fingerprint(table) for table in tables}
    scoped = "WHERE room_type_id='" + room + "'"
    try:
        if db("WITH a AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','" + hotel + "','owner_id','" + owner + "','name','" + marker + "','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM a") != '1':
            raise RuntimeError('Synthetic hotel fixture unavailable')
        if db("WITH a AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','" + room + "','hotel_id','" + hotel + "','total_rooms',10,'base_price',200000,'capacity',jsonb_build_object('adults',2,'children',0),'is_active',true))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM a") != '1':
            raise RuntimeError('Synthetic room fixture unavailable')
        scenarios = [('read-negative-held', -1, 5, 1), ('read-negative-booked', 5, -1, 1), ('search-negative-held', -1, 5, 1),
                     ('create-overflow', 2147483647, 2147483647, 1), ('confirm-overcommitted', 11, 0, 1), ('release-underflow', -2147483648, 0, 1)]
        scenarios += [(operation + '-quantity-' + str(quantity), 1, 1, quantity) for operation in ['confirm', 'release', 'cancel'] for quantity in [-1, 0]]
        for name, held, booked, quantity in scenarios:
            db('DELETE FROM room_holds ' + scoped + '; DELETE FROM room_availability ' + scoped)
            db("INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('" + str(uuid.uuid4()) + "','" + room + "','2028-02-01'," + str(held) + ',' + str(booked) + ',now(),now())')
            hold_id = str(uuid.uuid4())
            if name != 'create-overflow' and not name.startswith(('read-', 'search-')):
                status = 'CONFIRMED' if name.startswith('cancel') else 'HELD'
                db("INSERT INTO room_holds(id,room_type_id,hotel_id,check_in_date,check_out_date,quantity,status,expires_at,created_at,updated_at) VALUES ('" + hold_id + "','" + room + "','" + hotel + "','2028-02-01','2028-02-02'," + str(quantity) + ",'" + status + "',now()+interval '2 days',now(),now())")
            snapshot = [fingerprint(table, scoped) for table in ['room_availability', 'room_holds']]
            if name.startswith('read-'):
                status = api('/api/v1/availability/check?' + urlencode({'roomTypeId': room, 'checkIn': '2028-02-01', 'checkOut': '2028-02-02', 'quantity': 1}), method='GET')
            elif name.startswith('search-'):
                status = api('/hotels/offers/page?' + urlencode({'hotelName': marker, 'checkIn': '2028-02-01', 'checkOut': '2028-02-02'}), method='GET')
            elif name == 'create-overflow':
                status = api('/api/v1/availability/hold', {'roomTypeId': room, 'checkInDate': '2028-02-01', 'checkOutDate': '2028-02-02', 'quantity': 1})
            else:
                status = api('/api/v1/availability/hold/' + hold_id + '/' + name.split('-')[0])
            domain_code_valid = not name.startswith(('read-', 'search-')) or last_error_code == 'INVENTORY_INCONSISTENT'
            check(status == 409 and domain_code_valid, name + ' conflicts without inventing capacity or finalizing bad data')
            check(snapshot == [fingerprint(table, scoped) for table in ['room_availability', 'room_holds']], name + ' leaves exact counters and hold state unchanged')
    finally:
        db('DELETE FROM room_holds ' + scoped + '; DELETE FROM room_availability ' + scoped)
        db("DELETE FROM room_types WHERE id='" + room + "' AND hotel_id='" + hotel + "'; DELETE FROM hotels WHERE id='" + hotel + "' AND owner_id='" + owner + "' AND name='" + marker + "'")
        check(all(fingerprint(table) == before[table] for table in tables), 'exact corrupt-fixture cleanup preserves pre-existing records')
    print(json.dumps({'cases': count, 'failures': failures}), flush=True)
    return int(bool(failures))


if __name__ == '__main__':
    raise SystemExit(main())
