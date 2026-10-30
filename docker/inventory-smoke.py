"""Inventory regression on newly created fixtures in the isolated PostgreSQL clone.

Copies one cloned room's required fields, creates fresh UUIDs, and removes only
these fixtures in finally. Never updates existing rooms or prints their data.
"""
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)
SERVICE_CREDENTIAL = None


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request('http://localhost:8080/api/v1/availability/' + path,
                                 data=data, headers={'Content-Type': 'application/json'})
    if SERVICE_CREDENTIAL:
        req.add_header('Authorization', 'Bearer ' + SERVICE_CREDENTIAL)
    try:
        with urllib.request.urlopen(req, timeout=25) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        error.close()
        return error.code, None


def main():
    global SERVICE_CREDENTIAL
    config = json.loads(helpers.run(helpers.COMPOSE + ['config', '--format', 'json']))
    if config['services']['hotel-service']['environment']['SPRING_DATASOURCE_URL'] != 'jdbc:postgresql://postgres-test:5432/checkinn_hotel':
        raise RuntimeError('Refusing fixtures outside the isolated clone')
    helpers.verify_running_clone('hotel-service')
    SERVICE_CREDENTIAL = helpers.token(config['services']['hotel-service']['environment']['SECRET_KEY'], 'booking-service', 'BOOKING_SERVICE')
    failures = 0
    for total in [None, 0, -1, 10]:
        room = str(uuid.uuid4())
        sql_total = 'NULL' if total is None else str(total)
        # The unique fixture ID is generated here, never supplied by the caller.
        created = helpers.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types, to_jsonb(r) || jsonb_build_object('id', '" + room + "', 'total_rooms', " + sql_total + ", 'name', 'inventory-regression-" + room + "'))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM fixture")
        if created != '1':
            raise RuntimeError('No cloned room fixture available')
        try:
            params = 'roomTypeId=' + room + '&checkIn=2027-01-10&checkOut=2027-01-11&quantity=1'
            code, body = request('check?' + params)
            expected = 200 if total == 10 else 409
            passed = code == expected and (total != 10 or body.get('availableRooms') == 10)
            failures += not passed
            print(json.dumps({'case': 'inventory read', 'totalRooms': total, 'expected': expected,
                              'actual': code, 'result': 'PASS' if passed else 'FAIL'}), flush=True)
            if total != 10:
                code, _ = request('hold', {'roomTypeId': room, 'checkInDate': '2027-01-10',
                                           'checkOutDate': '2027-01-11', 'quantity': 1})
                counters = helpers.sql('checkinn_hotel', "SELECT (SELECT count(*) FROM room_holds WHERE room_type_id='" + room + "') + (SELECT count(*) FROM room_availability WHERE room_type_id='" + room + "')")
                passed = code == 409 and counters == '0'
                failures += not passed
                print(json.dumps({'case': 'invalid inventory hold has no writes', 'totalRooms': total,
                                  'actual': code, 'result': 'PASS' if passed else 'FAIL'}), flush=True)
        finally:
            # Only synthetic rows bearing the UUID created above, in the verified clone.
            helpers.sql('checkinn_hotel', "BEGIN; DELETE FROM room_holds WHERE room_type_id='" + room + "'; DELETE FROM room_availability WHERE room_type_id='" + room + "'; DELETE FROM room_types WHERE id='" + room + "'; COMMIT;")
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
