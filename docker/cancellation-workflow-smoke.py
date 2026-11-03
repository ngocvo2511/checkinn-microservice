"""Synthetic booking/payment/cancellation/refund-approval on isolated clones only.

VNPay return is simulated locally when sandbox signing config is present. No
request goes to VNPay and no real settlement is claimed. Email must use Mailpit.
All booking/room data is synthetic, and cleanup is scoped to generated UUIDs.
"""
import datetime
from decimal import Decimal, ROUND_DOWN
import hashlib
import hmac
import importlib.util
import json
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def api(method, path, token=None, body=None, _recovery_deadline=None):
    if _recovery_deadline is None: _recovery_deadline = time.monotonic() + 45
    request = urllib.request.Request('http://localhost:8080' + path, method=method,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
    if callable(token): token = token()
    if token:
        request.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        error.close()
        print(json.dumps({'case': 'workflow HTTP diagnostic', 'method': method, 'status': error.code}), flush=True)
        # A deliberate service restart temporarily removes its discovery route.
        # Never blindly retry booking/payment creation or provider callback POST/GET.
        safe = (method == 'GET' and path.startswith('/api/bookings/')) or method == 'DELETE'
        if error.code == 503 and safe and time.monotonic() < _recovery_deadline:
            time.sleep(1)
            return api(method, path, token, body, _recovery_deadline)
        return error.code, None


def check(condition, case):
    print(json.dumps({'case': case, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
    if not condition:
        raise AssertionError('Workflow regression failed')


def main():
    for service in ['booking-service', 'hotel-service', 'revenue-service', 'user-service']:
        h.verify_running_clone(service)
    notification = h.run(h.COMPOSE + ['ps', '-q', 'notification-service'])
    environment = json.loads(h.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', notification]))
    if 'SPRING_MAIL_HOST=mailpit' not in environment:
        raise RuntimeError('Refusing notification outside the local email sink')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    settings = config['services']['booking-service']['environment']
    customer_id = str(uuid.uuid4())
    customer = lambda: h.token(settings['SECRET_KEY'], customer_id, 'CUSTOMER')
    administrator_id = str(uuid.uuid4())
    administrator = lambda: h.token(settings['SECRET_KEY'], administrator_id, 'ADMIN')
    room = str(uuid.uuid4())
    new_bookings = []
    fault_name = 'points_commit_fault_' + uuid.uuid4().hex[:16]
    fault_installed = False
    active_faults = []
    created = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types, to_jsonb(r) || jsonb_build_object('id','" + room + "','name','workflow-regression-" + room + "','base_price',1000000,'total_rooms',1,'is_active',true,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r JOIN hotels hotel ON hotel.id=r.hotel_id WHERE hotel.is_active=true AND hotel.approved_status='APPROVED' LIMIT 1 RETURNING hotel_id) SELECT hotel_id FROM fixture")
    if not created:
        raise RuntimeError('NEEDS_ENVIRONMENT: no active approved hotel fixture')
    hotel = str(uuid.UUID(created))
    check_in = (datetime.date.today() + datetime.timedelta(days=10)).isoformat()
    check_out = (datetime.date.today() + datetime.timedelta(days=11)).isoformat()
    payload = {'hotelId': hotel, 'hotelName': 'client-name-must-not-be-trusted',
               'checkInDate': check_in, 'checkOutDate': check_out, 'adults': 2, 'children': 0,
               'contactName': 'Regression customer', 'contactEmail': 'workflow@example.invalid', 'contactPhone': '0900000000',
               'acceptedCancellationPolicyVersion': 'STANDARD_24H_V1',
               'items': [{'roomTypeId': room, 'roomTypeName': 'client-room-must-not-be-trusted', 'ratePlanId': room,
                          'checkInDate': check_in, 'checkOutDate': check_out, 'quantity': 1, 'unitPrice': 1000000}]}

    def complete_payment(booking, amount=1000000, onsite=False, commit_fault=None):
        if not onsite and settings.get('VNPAY_HASH_SECRET') and settings.get('VNPAY_TMN_CODE'):
            code, payment = api('POST', '/api/payments/vnpay/create?bookingId=' + booking, customer)
            check(code == 200, 'VNPay initiation uses canonical server total without contacting provider')
            payment_id = str(uuid.UUID(payment['orderId']))
            params = {'vnp_TxnRef': payment_id, 'vnp_TmnCode': settings['VNPAY_TMN_CODE'], 'vnp_Amount': '1',
                      'vnp_ResponseCode': '00', 'vnp_TransactionStatus': '00',
                      'vnp_TransactionNo': str(100000000000000 + secrets.randbelow(899999999999999))}
            def callback():
                query = urllib.parse.urlencode(sorted(params.items()))
                signature = hmac.new(settings['VNPAY_HASH_SECRET'].encode(), query.encode(), hashlib.sha512).hexdigest()
                return api('GET', '/api/payments/vnpay/return?' + query + '&vnp_SecureHash=' + signature)
            code, _ = callback()
            check(code == 400, 'correctly signed but wrong amount is rejected before payment mutation')
            params['vnp_Amount'] = str(amount * 100)
            code, result = callback()
            check(code == 200 and result['status'] == 'COMPLETED', 'SIMULATED provider return records successful payment')
            code, replay = callback()
            check(code == 200 and replay['id'] == payment_id, 'SIMULATED provider callback replay is idempotent')
            return payment_id
        code, payment = api('POST', '/api/payments', customer, {'bookingId': booking, 'amount': amount, 'method': 'HOTEL'})
        check(code == 201, 'onsite payment creation uses canonical amount')
        payment_id = str(uuid.UUID(payment['id']))
        if commit_fault:
            source_query = "SELECT status,paid_amount,coalesce(loyalty_status,'NULL') FROM bookings WHERE id='" + booking + "'"
            before = h.sql('checkinn_booking', source_query)
            code, _ = api('POST', '/api/payments/' + booking + '/confirm', administrator)
            unchanged = h.sql('checkinn_booking', source_query) == before
            payment_state = h.sql('checkinn_booking', "SELECT status FROM payments WHERE id='" + payment_id + "'")
            ledger_rows = h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + booking + "'")
            print(json.dumps({'case': 'payment rollback diagnostics', 'status': code, 'sourceUnchanged': unchanged, 'paymentState': payment_state, 'ledgerRows': ledger_rows}), flush=True)
            check(code in (500, 503) and unchanged and payment_state == 'ONSITE_PENDING' and ledger_rows == '0',
                  'local payment commit failure leaves no premature USE or EARN')
            drop_fault(commit_fault)
        code, result = api('POST', '/api/payments/' + booking + '/confirm', administrator)
        check(code == 200 and result['status'] == 'COMPLETED', 'authorized onsite receipt confirms internal inventory')
        if not onsite:
            print(json.dumps({'case': 'VNPay provider settlement', 'result': 'NOT_RUN', 'reason': 'no signing config; onsite flow tested instead'}), flush=True)
        return payment_id

    def wait_for(predicate, seconds=60):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate(): return True
            time.sleep(0.5)
        return False

    def loyalty_state(booking, expected):
        return wait_for(lambda: h.sql('checkinn_booking', "SELECT loyalty_status FROM bookings WHERE id='" + booking + "'") == expected)

    def install_fault(database, table, event, condition, deferred=False):
        name = 'loyalty_fault_' + uuid.uuid4().hex[:16]
        timing = 'AFTER' if deferred else 'BEFORE'
        kind = 'CONSTRAINT ' if deferred else ''
        defer = ' DEFERRABLE INITIALLY DEFERRED' if deferred else ''
        h.sql(database, 'BEGIN; CREATE FUNCTION ' + name + '() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF ' + condition
              + " THEN RAISE EXCEPTION 'synthetic loyalty boundary fault'; END IF; RETURN NEW; END $$; CREATE " + kind + 'TRIGGER '
              + name + ' ' + timing + ' ' + event + ' ON ' + table + defer + ' FOR EACH ROW EXECUTE FUNCTION ' + name + '(); COMMIT;')
        item = (database, table, name); active_faults.append(item)
        return item

    def drop_fault(item):
        database, table, name = item
        h.sql(database, 'BEGIN; DROP TRIGGER IF EXISTS ' + name + ' ON ' + table + '; DROP FUNCTION IF EXISTS ' + name + '(); COMMIT;')
        active_faults.remove(item)

    try:
        check(wait_for(lambda: api('GET', '/api/bookings/count/total', administrator)[0] == 200, 90), 'gateway discovery ready before workflow assertions')
        tampered = json.loads(json.dumps(payload)); tampered['items'][0]['unitPrice'] = 1
        code, _ = api('POST', '/api/bookings', customer, tampered)
        print(json.dumps({'case': 'price validation readiness status', 'status': code}), flush=True)
        check(code == 400 and h.sql('checkinn_hotel', "SELECT count(*) FROM room_holds WHERE room_type_id='" + room + "'") == '0', 'price tampering is rejected before holding inventory')
        code, booking = api('POST', '/api/bookings', customer, payload)
        check(code == 201, 'booking creates against trusted room catalog')
        booking_id = str(uuid.UUID(booking['id'])); new_bookings.append(booking_id)
        check(booking['totalAmount'] == 1000000 and booking['hotelName'] != payload['hotelName']
              and booking['cancellationPolicyVersion'] == 'STANDARD_24H_V1', 'price, names and accepted policy snapshot are server owned')
        payment_id = complete_payment(booking_id)
        check(loyalty_state(booking_id, 'SETTLED'), 'committed paid booking automatically settles points')
        code, booking = api('GET', '/api/bookings/' + booking_id, customer)
        check(code == 200 and booking['status'] == 'CONFIRMED', 'booking is confirmed only after internal hold confirmation')
        earned = h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE user_id='" + customer_id + "' AND booking_id='" + booking_id + "' AND transaction_type='EARN'")
        check(earned == '1', 'booking Feign service credential awards points once despite callback replay')
        original_hold = str(uuid.UUID(booking['holdId']))
        missing_hold = str(uuid.uuid4())
        h.sql('checkinn_booking', "UPDATE bookings SET hold_id='" + missing_hold + "' WHERE id='" + booking_id + "'")
        code, _ = api('DELETE', '/api/bookings/' + booking_id, customer)
        unchanged = h.sql('checkinn_booking', "SELECT status FROM bookings WHERE id='" + booking_id + "'")
        requests = h.sql('checkinn_booking', "SELECT count(*) FROM refund_requests WHERE booking_id='" + booking_id + "'")
        check(code == 503 and unchanged == 'CONFIRMED' and requests == '0',
              'inventory acknowledgement failure rolls back local cancellation and refund request')
        h.sql('checkinn_booking', "UPDATE bookings SET hold_id='" + original_hold + "' WHERE id='" + booking_id + "'")
        code, _ = api('DELETE', '/api/bookings/' + booking_id, customer)
        check(code == 204, 'eligible paid cancellation succeeds')
        code, receipt = api('GET', '/api/payments/' + payment_id + '/refund-request', customer)
        check(code == 200 and receipt['status'] == 'REQUESTED' and receipt['amount'] == 1000000
              and receipt['requestedBy'] == customer_id, 'refund request preserves server amount and authenticated requester')
        code, _ = api('POST', '/api/payments/' + payment_id + '/refund-request/approve', customer)
        check(code == 403, 'customer cannot approve own refund')
        code, receipt = api('POST', '/api/payments/' + payment_id + '/refund-request/approve', administrator)
        check(code == 200 and receipt['status'] == 'APPROVED_PENDING_EXECUTION', 'ADMIN approval does not claim completed refund')
        code, _ = api('POST', '/api/payments/' + payment_id + '/refund', administrator)
        check(code == 503, 'unverified provider execution remains unavailable instead of faking refund')
        code, payment = api('GET', '/api/payments/' + payment_id, customer)
        code2, booking = api('GET', '/api/bookings/' + booking_id, customer)
        check(code == 200 and code2 == 200 and payment['status'] == 'COMPLETED'
              and booking['status'] == 'CANCELLED' and booking['paidAmount'] == 1000000, 'money receipt remains recorded while refund awaits provider execution')
        code, _ = api('DELETE', '/api/bookings/' + booking_id, customer)
        check(code == 204 and h.sql('checkinn_booking', "SELECT count(*) FROM refund_requests WHERE booking_id='" + booking_id + "'") == '1', 'cancellation retry does not duplicate refund request')
        check(h.sql('checkinn_hotel', "SELECT held,booked FROM room_availability WHERE room_type_id='" + room + "'") == '0|0', 'cancellation restores confirmed inventory')
        legacy = json.loads(json.dumps(payload)); legacy.pop('acceptedCancellationPolicyVersion')
        code, booking = api('POST', '/api/bookings', customer, legacy)
        check(code == 201 and booking.get('cancellationPolicyVersion') is None, 'legacy client is not assigned a policy retroactively')
        legacy_id = str(uuid.UUID(booking['id'])); new_bookings.append(legacy_id)
        complete_payment(legacy_id)
        code, _ = api('DELETE', '/api/bookings/' + legacy_id, customer)
        check(code == 409, 'paid legacy booking requires explicit review')
        code, _ = api('POST', '/api/bookings/' + legacy_id + '/cancel-reviewed', administrator, {'reason': 'HOTEL_UNAVAILABLE'})
        check(code == 204, 'ADMIN reviewed hotel-unavailable cancellation can handle legacy booking')
        check(loyalty_state(booking_id, 'REVERSED') and loyalty_state(legacy_id, 'REVERSED'), 'cancellations automatically complete durable point reversal')
        # Real service caller, authoritative rate/cap, both receipt paths and reversal.
        rates = json.loads(h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-fsS', '--max-time', '10',
                                             'http://regulations-service:8091/api/regulations/typed/points']))
        earn_rate, redemption_rate = Decimal(str(rates['earnConversionRate'])), Decimal(str(rates['redemptionConversionRate']))
        if earn_rate <= 0 or redemption_rate < 1 or redemption_rate != redemption_rate.to_integral_value():
            raise RuntimeError('NEEDS_ENVIRONMENT: positive whole-VND redemption rate required for this payment fixture')
        cap_points = int(Decimal('500000') / redemption_rate)
        if cap_points <= 0:
            raise RuntimeError('NEEDS_ENVIRONMENT: rate exceeds fixture discount cap')
        seed_points = cap_points + 10
        service = h.token(settings['SECRET_KEY'], 'booking-service', 'BOOKING_SERVICE')
        seed_status = int(h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-sS', '--max-time', '20', '-X', 'POST',
                                            '-H', '@-', '-o', '/dev/null', '-w', '%{http_code}',
                                            'http://localhost:8082/api/loyalty-points/earn?userId=' + customer_id + '&bookingId='
                                            + str(uuid.uuid4()) + '&totalAmount=' + str(earn_rate * seed_points)],
                                            'Authorization: Bearer ' + service + '\n'))
        check(seed_status == 200, 'seed synthetic points via verified service command')
        print(json.dumps({'case': 'seed diagnostics', 'balance': h.sql('checkinn_user', "SELECT available_points FROM loyalty_points WHERE user_id='" + customer_id + "'"), 'expected': seed_points}), flush=True)
        for onsite in [False, True]:
            label = 'onsite' if onsite else 'VNPay or configured fallback'
            points_payload = json.loads(json.dumps(payload)); points_payload['pointsToUse'] = cap_points + 500
            code, points_booking = api('POST', '/api/bookings', customer, points_payload)
            check(code == 201, label + ' points booking creates from service quote')
            points_id = str(uuid.UUID(points_booking['id'])); new_bookings.append(points_id)
            discount = int(redemption_rate * cap_points)
            net = 1000000 - discount
            check(points_booking['usedPoints'] == cap_points and points_booking['pointsDiscountAmount'] == discount
                  and points_booking['totalAmount'] == net, label + ' capped discount debits only actual whole points')
            check(h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + points_id + "'") == 'HELD'
                  and h.sql('checkinn_user', "SELECT used_points FROM loyalty_points WHERE user_id='" + customer_id + "'") == '0',
                  label + ' pending booking reserves points without spending them')
            competing = json.loads(json.dumps(points_payload))
            for item in [competing, competing['items'][0]]:
                item['checkInDate'] = (datetime.date.today() + datetime.timedelta(days=12)).isoformat()
                item['checkOutDate'] = (datetime.date.today() + datetime.timedelta(days=13)).isoformat()
            before_count = h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE user_id='" + customer_id + "'")
            code, _ = api('POST', '/api/bookings', customer, competing)
            check(code == 400 and h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE user_id='" + customer_id + "'") == before_count
                  and h.sql('checkinn_hotel', "SELECT sum(held) FROM room_availability WHERE room_type_id='" + room + "'") == '1',
                  label + ' competing pending discount fails and rolls back booking and inventory')
            if not onsite:
                fault = install_fault('checkinn_user', 'loyalty_commands', 'INSERT', "NEW.booking_id='" + points_id + "' AND NEW.command_type='EARN'")
            else:
                fault = install_fault('checkinn_booking', 'bookings', 'UPDATE', "NEW.id='" + points_id + "' AND NEW.loyalty_status='SETTLED'")
            payment_fault = install_fault('checkinn_booking', 'bookings', 'UPDATE', "NEW.id='" + points_id + "' AND NEW.status='CONFIRMED'", deferred=True) if onsite else None
            points_payment = complete_payment(points_id, net, onsite, payment_fault)
            if not onsite:
                check(wait_for(lambda: h.sql('checkinn_booking', "SELECT loyalty_attempts>0 AND loyalty_status='PENDING' FROM bookings WHERE id='" + points_id + "'") == 't')
                      and h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + points_id + "' AND transaction_type='USE'") == '1'
                      and h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + points_id + "' AND transaction_type='EARN'") == '0',
                      'EARN outage after USE retains durable intent without fake earning')
                result = subprocess.run(h.COMPOSE + ['restart', 'booking-service'], capture_output=True, text=True, timeout=90)
                if result.returncode: raise RuntimeError('Test booking restart failed')
                result = subprocess.run(h.COMPOSE + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '150', 'booking-service'], capture_output=True, text=True, timeout=180)
                if result.returncode: raise RuntimeError('Test booking readiness failed')
                h.verify_running_clone('booking-service')
                check(wait_for(lambda: api('GET', '/api/bookings/' + points_id, customer)[0] == 200, 90), 'gateway re-registers restarted booking before workflow resumes')
                check(h.sql('checkinn_booking', "SELECT loyalty_status FROM bookings WHERE id='" + points_id + "'") == 'PENDING',
                      'durable settlement intent survives booking process restart')
            else:
                check(wait_for(lambda: h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + points_id + "' AND transaction_type IN ('USE','EARN')") == '2')
                      and h.sql('checkinn_booking', "SELECT loyalty_status='PENDING' AND earned_points IS NULL FROM bookings WHERE id='" + points_id + "'") == 't',
                      'remote ledger success with local marker commit failure preserves pending intent')
            drop_fault(fault)
            check(loyalty_state(points_id, 'SETTLED'), label + ' automatic retry finishes without payment callback replay')
            check(h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + points_id + "'") == 'COMMITTED',
                  label + ' paid receipt commits its reservation')
            code, settled = api('GET', '/api/bookings/' + points_id, customer)
            awarded = int((Decimal(net) / earn_rate).to_integral_value(rounding=ROUND_DOWN))
            print(json.dumps({'case': label + ' earning diagnostics', 'status': code, 'bookingStatus': settled.get('status') if settled else None,
                              'loyaltyStatus': settled.get('loyaltyStatus') if settled else None, 'earned': settled.get('earnedPoints') if settled else None, 'expected': awarded}), flush=True)
            check(code == 200 and settled['status'] == 'CONFIRMED' and settled['earnedPoints'] == awarded,
                  label + ' booking uses actual net earning receipt from Regulations')
            if onsite:
                code, replay = api('POST', '/api/payments/' + points_id + '/confirm', administrator)
                check(code == 200 and replay['id'] == points_payment, 'onsite receipt replay can retry loyalty safely')
            check(h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE user_id='" + customer_id
                        + "' AND booking_id='" + points_id + "' AND transaction_type IN ('EARN','USE')") == '2',
                  label + ' payment replay has exactly one EARN and one USE')
            if onsite:
                fault = install_fault('checkinn_booking', 'bookings', 'UPDATE', "NEW.id='" + points_id + "' AND NEW.status='CANCELLED'", deferred=True)
                code, _ = api('DELETE', '/api/bookings/' + points_id, customer)
                check(code in (500, 503) and h.sql('checkinn_booking', "SELECT status FROM bookings WHERE id='" + points_id + "'") == 'CONFIRMED'
                      and h.sql('checkinn_user', "SELECT count(*) FROM loyalty_commands WHERE booking_id='" + points_id + "' AND command_type='CANCEL'") == '0',
                      'local cancellation commit failure has no premature remote point reversal')
                drop_fault(fault)
                fault = install_fault('checkinn_booking', 'bookings', 'UPDATE', "NEW.id='" + points_id + "' AND NEW.loyalty_status='REVERSED'")
            code, _ = api('DELETE', '/api/bookings/' + points_id, customer)
            check(code == 204, label + ' paid cancellation durably requests point reversal')
            if onsite:
                check(wait_for(lambda: h.sql('checkinn_user', "SELECT count(*) FROM loyalty_commands WHERE booking_id='" + points_id + "' AND command_type='CANCEL'") == '1')
                      and h.sql('checkinn_booking', "SELECT status='CANCELLED' AND loyalty_status='PENDING' FROM bookings WHERE id='" + points_id + "'") == 't',
                      'remote reversal success survives lost local completion marker')
                drop_fault(fault)
            check(loyalty_state(points_id, 'REVERSED'), label + ' reversal is retried automatically to completion')
            code, _ = api('DELETE', '/api/bookings/' + points_id, customer)
            restored_balance = h.sql('checkinn_user', "SELECT available_points FROM loyalty_points WHERE user_id='" + customer_id + "'")
            print(json.dumps({'case': label + ' reversal replay diagnostics', 'status': code, 'balance': restored_balance, 'expected': seed_points}), flush=True)
            check(code == 204 and restored_balance == str(seed_points), label + ' reversal replay restores seed balance once')
            check(h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + points_id + "'") == 'CANCELLED',
                  label + ' cancellation closes the reservation')
        values = ','.join("'" + str(uuid.UUID(value)) + "'" for value in new_bookings)
        for _ in range(10):
            received = int(h.sql('checkinn_revenue', 'SELECT count(*) FROM payment_records WHERE booking_id IN (' + values + ')'))
            if received == len(new_bookings):
                break
            time.sleep(1)
        check(received == len(new_bookings), 'revenue consumer accepts payment event with additive bookingStatus field')
        # A deferred, fixture-scoped failure occurs at local commit, after the
        # remote reservation succeeds. Only the new HELD-orphan recovery is asserted.
        h.sql('checkinn_booking', "BEGIN; CREATE FUNCTION " + fault_name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.room_type_id::text='"
              + room + "' THEN RAISE EXCEPTION 'synthetic deferred booking commit failure'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "
              + fault_name + " AFTER INSERT ON booking_items DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION " + fault_name + "(); COMMIT;")
        fault_installed = True
        before_count = h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE user_id='" + customer_id + "'")
        code, _ = api('POST', '/api/bookings', customer, points_payload)
        h.sql('checkinn_booking', 'BEGIN; DROP TRIGGER ' + fault_name + ' ON booking_items; DROP FUNCTION ' + fault_name + '(); COMMIT;')
        fault_installed = False
        check(code in (500, 503) and h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE user_id='" + customer_id + "'") == before_count,
              'deferred booking commit fault rolls back the local booking')
        orphan = str(uuid.UUID(h.sql('checkinn_user', "SELECT booking_id FROM points_reservations WHERE user_id='" + customer_id + "' AND state='HELD'")))
        new_bookings.append(orphan)
        check(h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE id='" + orphan + "'") == '0',
              'remote reservation survives local commit failure as an identifiable orphan')
        h.sql('checkinn_user', "UPDATE points_reservations SET hold_expires_at=now()-interval '1 second',next_check_at=now()-interval '1 second' WHERE booking_id='" + orphan + "'")
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            if h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + orphan + "'") == 'CANCELLED':
                break
            time.sleep(0.5)
        check(h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + orphan + "'") == 'CANCELLED'
              and h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + orphan + "'") == '0'
              and h.sql('checkinn_user', "SELECT available_points FROM loyalty_points WHERE user_id='" + customer_id + "'") == str(seed_points),
              'authenticated missing-booking recovery releases orphan without a spend or earning')
    finally:
        for item in list(active_faults):
            drop_fault(item)
        if fault_installed:
            h.sql('checkinn_booking', 'BEGIN; DROP TRIGGER IF EXISTS ' + fault_name + ' ON booking_items; DROP FUNCTION IF EXISTS ' + fault_name + '(); COMMIT;')
        # Read back IDs belonging to this generated room in case a response was lost after commit.
        persisted = h.sql('checkinn_booking', "SELECT DISTINCT booking_id FROM booking_items WHERE room_type_id='" + room + "'")
        new_bookings = list(set(new_bookings + [str(uuid.UUID(value)) for value in persisted.splitlines() if value]))
        if new_bookings:
            values = ','.join("'" + str(uuid.UUID(value)) + "'" for value in new_bookings)
            # Give asynchronous consumers a bounded opportunity to process synthetic events, never purge queues.
            for _ in range(6):
                received = int(h.sql('checkinn_revenue', 'SELECT count(*) FROM payment_records WHERE booking_id IN (' + values + ')'))
                if received >= len(new_bookings):
                    break
                time.sleep(1)
            h.sql('checkinn_revenue', 'BEGIN; DELETE FROM payment_records WHERE booking_id IN (' + values + '); DELETE FROM booking_status_records WHERE booking_id IN (' + values + '); COMMIT;')
            h.sql('checkinn_booking', 'BEGIN; DELETE FROM refund_requests WHERE booking_id IN (' + values + '); DELETE FROM payments WHERE booking_id IN (' + values + '); DELETE FROM booking_items WHERE booking_id IN (' + values + '); DELETE FROM bookings WHERE id IN (' + values + '); COMMIT;')
        h.sql('checkinn_user', "DELETE FROM points_reservations WHERE user_id='" + customer_id + "'; DELETE FROM loyalty_commands WHERE user_id='" + customer_id + "'; DELETE FROM points_transactions WHERE user_id='" + customer_id + "'; DELETE FROM loyalty_points WHERE user_id='" + customer_id + "'")
        h.sql('checkinn_hotel', "BEGIN; DELETE FROM room_holds WHERE room_type_id='" + room + "'; DELETE FROM room_availability WHERE room_type_id='" + room + "'; DELETE FROM room_types WHERE id='" + room + "'; COMMIT;")
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
