"""Payment hold gate on checkinn-test only; fresh fixtures, no provider calls.

Temporarily stops/restarts only the clone hotel-service to verify unknown outcomes.
Run separately from other runtime suites. Never prints credentials or copied data.
"""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    for service in ['booking-service', 'hotel-service', 'revenue-service']:
        h.verify_running_clone(service)
    notification = h.run(h.COMPOSE + ['ps', '-q', 'notification-service'])
    environment = json.loads(h.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', notification]))
    if 'SPRING_MAIL_HOST=mailpit' not in environment:
        raise RuntimeError('Refusing notification outside the local email sink')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['booking-service']['environment']['SECRET_KEY']
    guest, room = str(uuid.uuid4()), str(uuid.uuid4())
    bookings, holds = [], []
    stopped = False
    failures = 0
    fault = 'hold_gate_fault_' + uuid.uuid4().hex[:16]
    fault_installed = False

    def check(ok, case):
        nonlocal failures
        failures += not ok
        print(json.dumps({'case': case, 'result': 'PASS' if ok else 'FAIL'}), flush=True)

    def request(service, port, method, path, role, subject, body=None):
        headers = 'Authorization: Bearer ' + h.token(key, subject, role) + '\n'
        args = h.COMPOSE + ['exec', '-T', service, 'curl', '-sS', '--max-time', '15',
                           '-X', method, '-H', '@-', '-H', 'Content-Type: application/json', '-w', '\n%{http_code}']
        if body is not None:
            args += ['--data', json.dumps(body)]
        raw = h.run(args + ['http://localhost:' + str(port) + path], headers)
        text, separator, code = raw.rpartition('\n')
        if not separator:
            text, code = '', raw
        try:
            result = json.loads(text) if text else None
        except ValueError:
            result = None
        return int(code), result

    def payment(booking):
        result = request('booking-service', 8084, 'POST', '/api/payments', 'CUSTOMER', guest,
                         {'bookingId': booking, 'method': 'HOTEL', 'amount': 1000000})
        print(json.dumps({'diagnostic': 'payment HTTP status', 'status': result[0]}), flush=True)
        return result

    def restore():
        nonlocal stopped
        h.run(h.COMPOSE + ['start', 'hotel-service'])
        stopped = False
        for _ in range(60):
            container = h.run(h.COMPOSE + ['ps', '-q', 'hotel-service'])
            if h.run(['docker', 'inspect', '--format', '{{.State.Health.Status}}', container]) == 'healthy':
                return
            time.sleep(1)
        raise RuntimeError('NEEDS_ENVIRONMENT: clone hotel did not recover')

    def wait_for_catalog(hotel):
        # Eureka fetch (30s) and Spring LoadBalancer cache (35s) converge separately.
        # A successful read-only ownership lookup proves the booking JVM can resolve hotel.
        deadline = time.monotonic() + 120
        while True:
            code, _ = request('booking-service', 8084, 'GET', '/api/bookings/hotel/' + hotel, 'OWNER', guest)
            if code == 403:
                return
            if time.monotonic() >= deadline:
                raise RuntimeError('NEEDS_ENVIRONMENT: booking catalog discovery not ready')
            time.sleep(1)

    try:
        hotel = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types, to_jsonb(r) || jsonb_build_object('id','" + room + "','name','hold-gate-regression-" + room + "','total_rooms',10,'base_price',1000000,'is_active',true,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r JOIN hotels hotel ON hotel.id=r.hotel_id WHERE hotel.is_active=true AND hotel.approved_status='APPROVED' LIMIT 1 RETURNING hotel_id) SELECT hotel_id FROM fixture")
        hotel = str(uuid.UUID(hotel))
        # Service health precedes convergence of the booking JVM's Eureka cache.
        # Probe a read-only owner check; do not retry booking POST to establish readiness.
        wait_for_catalog(hotel)
        check_in = (datetime.date.today() + datetime.timedelta(days=30)).isoformat()
        check_out = (datetime.date.today() + datetime.timedelta(days=31)).isoformat()
        for _ in range(4):
            code, booking = request('booking-service', 8084, 'POST', '/api/bookings', 'CUSTOMER', guest,
                {'hotelId': hotel, 'checkInDate': check_in, 'checkOutDate': check_out, 'adults': 2, 'children': 0,
                 'contactName': 'Synthetic Guest', 'contactEmail': 'hold-gate@example.invalid', 'contactPhone': '0000000000',
                 'acceptedCancellationPolicyVersion': 'STANDARD_24H_V1',
                 'items': [{'roomTypeId': room, 'checkInDate': check_in, 'checkOutDate': check_out,
                            'quantity': 1, 'unitPrice': 1000000}]})
            if code != 201 or not booking:
                raise RuntimeError('NEEDS_ENVIRONMENT: synthetic booking creation failed with status ' + str(code))
            bookings.append(str(uuid.UUID(booking['id'])))
            holds.append(str(uuid.UUID(booking['holdId'])))

        # Transport failure is UNKNOWN, not an expired/cancelled booking.
        stopped = True
        h.run(h.COMPOSE + ['stop', 'hotel-service'])
        code, _ = payment(bookings[0])
        check(code == 503, 'inventory outage returns503')
        state = h.sql('checkinn_booking', "SELECT status || '|' || (SELECT count(*) FROM payments WHERE booking_id=b.id) FROM bookings b WHERE id='" + bookings[0] + "'")
        check(state == 'PENDING|0', 'outage preserves committed booking and writes no payment')
        restore()
        # Eureka fetch/registration is asynchronous even after health is UP. Let one
        # full default cache interval elapse before the next mutating scenario.
        # This is test setup, never a retry of an ambiguous booking/payment command.
        time.sleep(35)
        wait_for_catalog(hotel)

        code, _ = request('hotel-service', 8083, 'POST', '/api/v1/availability/hold/' + holds[1] + '/release', 'BOOKING_SERVICE', 'booking-service')
        if code != 200:
            raise RuntimeError('Fixture release failed')
        code, _ = payment(bookings[1])
        check(code == 409, 'released hold rejects new payment with409')

        h.sql('checkinn_booking', "UPDATE bookings SET hold_expires_at=localtimestamp - interval '1 minute' WHERE id='" + bookings[2] + "'")
        code, _ = payment(bookings[2])
        check(code == 409, 'expired local deadline rejects new payment with409')
        state = h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE id IN ('" + "','".join(bookings[:3]) + "') AND status='PENDING'")
        check(state == '3', 'failed payment checks do not partially cancel bookings')

        code, response = request('booking-service', 8084, 'GET', '/api/bookings/' + bookings[3], 'CUSTOMER', guest)
        deadline = (response or {}).get('holdExpiresAt', '')
        check(code == 200 and deadline.endswith('Z'), 'REST hold deadline is an absolute UTC instant')
        code, accepted_payment = payment(bookings[3])
        check(code == 201, 'active hold allows onsite payment creation without provider contact')
        code, replay = payment(bookings[3])
        check(code == 201 and replay['id'] == accepted_payment['id'], 'repeated onsite choice replays the same payment')
        count = h.sql('checkinn_booking', "SELECT count(*) FROM payments WHERE booking_id='" + bookings[3] + "'")
        check(count == '1', 'successful payment has exactly one persisted attempt')

        payload = {'idempotencyKey': str(uuid.uuid4()), 'hotelId': hotel,
                   'checkInDate': check_in, 'checkOutDate': check_out, 'adults': 2, 'children': 0,
                   'contactName': 'Synthetic Guest', 'contactEmail': 'hold-gate@example.invalid', 'contactPhone': '0000000000',
                   'acceptedCancellationPolicyVersion': 'STANDARD_24H_V1',
                   'items': [{'roomTypeId': room, 'checkInDate': check_in, 'checkOutDate': check_out,
                              'quantity': 1, 'unitPrice': 1000000}]}
        def create():
            result = request('booking-service', 8084, 'POST', '/api/bookings', 'CUSTOMER', guest, payload)
            print(json.dumps({'diagnostic': 'keyed booking HTTP status', 'status': result[0]}), flush=True)
            return result
        with ThreadPoolExecutor(max_workers=4) as workers:
            results = list(workers.map(lambda _: create(), range(4)))
        successful = [value for code, value in results if code == 201 and value]
        check(len(successful) == 4 and len({value['id'] for value in successful}) == 1,
              'four concurrent keyed creates return one booking')
        for value in successful:
            value = str(uuid.UUID(value['id']))
            if value not in bookings:
                bookings.append(value)
        payload['contactPhone'] = '0000000001'
        code, _ = create()
        check(code == 409, 'same key with changed input rejects409')

        # A real deferred PostgreSQL commit failure happens after remote inventory succeeds.
        # Retrying the command must reuse that hold and stable booking correlation.
        payload['idempotencyKey'] = str(uuid.uuid4())
        payload['contactPhone'] = '0000000002'
        key_hash = hashlib.sha256(('booking-create-v1:' + guest + ':' + payload['idempotencyKey']).encode()).hexdigest()
        command = str(uuid.UUID(bytes=hashlib.md5(('booking-hold-v1:' + key_hash).encode()).digest(), version=3))
        h.sql('checkinn_booking', "CREATE FUNCTION " + fault + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.user_id='" + guest + "' AND NEW.contact_phone='0000000002' THEN RAISE EXCEPTION 'Synthetic deferred booking commit fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER " + fault + " AFTER INSERT ON bookings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + fault + "()")
        fault_installed = True
        code, _ = create()
        check(code == 500, 'deferred local commit failure is observable')
        original = h.sql('checkinn_hotel', "SELECT id FROM room_holds WHERE command_id='" + command + "'")
        original = str(uuid.UUID(original))
        check(h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE idempotency_key_hash='" + key_hash + "'") == '0',
              'failed commit leaves no falsely accepted booking')
        h.sql('checkinn_booking', 'DROP TRIGGER ' + fault + ' ON bookings; DROP FUNCTION ' + fault + '()')
        fault_installed = False
        code, recovered = create()
        check(code == 201 and recovered['holdId'] == original, 'retry after commit failure recovers original hold')
        if code == 201:
            bookings.append(str(uuid.UUID(recovered['id'])))
        check(h.sql('checkinn_hotel', "SELECT count(*) FROM room_holds WHERE command_id='" + command + "'") == '1',
              'recovery does not create a second inventory hold')

        command = str(uuid.uuid4())
        hold_body = {'roomTypeId': room, 'checkInDate': check_in, 'checkOutDate': check_out, 'quantity': 1, 'commandId': command}
        def hold_request():
            return request('hotel-service', 8083, 'POST', '/api/v1/availability/hold', 'BOOKING_SERVICE', 'booking-service', hold_body)
        with ThreadPoolExecutor(max_workers=4) as workers:
            replies = list(workers.map(lambda _: hold_request(), range(4)))
        values = [body for code, body in replies if code == 200 and body]
        check(len(values) == 4 and len({(body['holdId'], body['expiresAt']) for body in values}) == 1,
              'concurrent inventory command preserves one hold and deadline')
        hold_body['quantity'] = 2
        code, _ = hold_request()
        check(code == 409, 'inventory command input mismatch rejects409')
        hold_body['quantity'] = 1
        code, _ = request('hotel-service', 8083, 'POST', '/api/v1/availability/hold/' + values[0]['holdId'] + '/release', 'BOOKING_SERVICE', 'booking-service')
        code, terminal = hold_request()
        check(code == 200 and terminal['status'] == 'RELEASED' and terminal['holdId'] == values[0]['holdId'],
              'terminal inventory command replay never creates or renews supply')
    finally:
        if stopped:
            restore()
        if fault_installed:
            h.sql('checkinn_booking', 'DROP TRIGGER IF EXISTS ' + fault + ' ON bookings; DROP FUNCTION IF EXISTS ' + fault + '()')
        # Also discover a committed booking if its HTTP response was lost/undecodable.
        discovered = h.sql('checkinn_booking', "SELECT DISTINCT b.id FROM bookings b JOIN booking_items i ON i.booking_id=b.id WHERE i.room_type_id='" + room + "' AND b.user_id='" + guest + "' AND b.contact_email='hold-gate@example.invalid'")
        for value in discovered.splitlines():
            value = str(uuid.UUID(value))
            if value not in bookings:
                bookings.append(value)
        if bookings:
            ids = "','".join(bookings)
            h.sql('checkinn_revenue', "DELETE FROM payment_records WHERE booking_id IN ('" + ids + "'); DELETE FROM booking_status_records WHERE booking_id IN ('" + ids + "')")
            h.sql('checkinn_booking', "DELETE FROM payments WHERE booking_id IN ('" + ids + "'); DELETE FROM booking_items WHERE booking_id IN ('" + ids + "'); DELETE FROM bookings WHERE id IN ('" + ids + "') AND user_id='" + guest + "'")
        h.sql('checkinn_hotel', "DELETE FROM room_holds WHERE room_type_id='" + room + "'; DELETE FROM room_availability WHERE room_type_id='" + room + "'; DELETE FROM room_types WHERE id='" + room + "' AND name='hold-gate-regression-" + room + "'")
        check(h.sql('checkinn_hotel', "SELECT count(*) FROM room_types WHERE id='" + room + "'") == '0', 'synthetic fixture cleanup')
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
