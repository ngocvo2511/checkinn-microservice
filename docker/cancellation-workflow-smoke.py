"""Synthetic booking/payment/cancellation/refund-approval on isolated clones only.

VNPay return is simulated locally when sandbox signing config is present. No
request goes to VNPay and no real settlement is claimed. Email must use Mailpit.
All booking/room data is synthetic, and cleanup is scoped to generated UUIDs.
"""
import datetime
import hashlib
import hmac
import importlib.util
import json
from pathlib import Path
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def api(method, path, token=None, body=None):
    request = urllib.request.Request('http://localhost:8080' + path, method=method,
                                     data=None if body is None else json.dumps(body).encode(),
                                     headers={'Content-Type': 'application/json'})
    if token:
        request.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        error.close()
        return error.code, None


def check(condition, case):
    print(json.dumps({'case': case, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
    if not condition:
        raise AssertionError('Workflow regression failed')


def main():
    for service in ['booking-service', 'hotel-service', 'revenue-service']:
        h.verify_running_clone(service)
    notification = h.run(h.COMPOSE + ['ps', '-q', 'notification-service'])
    environment = json.loads(h.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', notification]))
    if 'SPRING_MAIL_HOST=mailpit' not in environment:
        raise RuntimeError('Refusing notification outside the local email sink')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    settings = config['services']['booking-service']['environment']
    customer_id = str(uuid.uuid4())
    customer = h.token(settings['SECRET_KEY'], customer_id, 'CUSTOMER')
    administrator = h.token(settings['SECRET_KEY'], str(uuid.uuid4()), 'ADMIN')
    room = str(uuid.uuid4())
    new_bookings = []
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

    def complete_payment(booking):
        if settings.get('VNPAY_HASH_SECRET') and settings.get('VNPAY_TMN_CODE'):
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
            params['vnp_Amount'] = '100000000'
            code, result = callback()
            check(code == 200 and result['status'] == 'COMPLETED', 'SIMULATED provider return records successful payment')
            code, replay = callback()
            check(code == 200 and replay['id'] == payment_id, 'SIMULATED provider callback replay is idempotent')
            return payment_id
        code, payment = api('POST', '/api/payments', customer, {'bookingId': booking, 'amount': 1000000, 'method': 'HOTEL'})
        check(code == 201, 'onsite payment creation uses canonical amount')
        payment_id = str(uuid.UUID(payment['id']))
        code, result = api('POST', '/api/payments/' + booking + '/confirm', administrator)
        check(code == 200 and result['status'] == 'COMPLETED', 'authorized onsite receipt confirms internal inventory')
        print(json.dumps({'case': 'VNPay provider settlement', 'result': 'NOT_RUN', 'reason': 'no signing config; onsite flow tested instead'}), flush=True)
        return payment_id

    try:
        tampered = json.loads(json.dumps(payload)); tampered['items'][0]['unitPrice'] = 1
        code, _ = api('POST', '/api/bookings', customer, tampered)
        check(code == 400 and h.sql('checkinn_hotel', "SELECT count(*) FROM room_holds WHERE room_type_id='" + room + "'") == '0', 'price tampering is rejected before holding inventory')
        code, booking = api('POST', '/api/bookings', customer, payload)
        check(code == 201, 'booking creates against trusted room catalog')
        booking_id = str(uuid.UUID(booking['id'])); new_bookings.append(booking_id)
        check(booking['totalAmount'] == 1000000 and booking['hotelName'] != payload['hotelName']
              and booking['cancellationPolicyVersion'] == 'STANDARD_24H_V1', 'price, names and accepted policy snapshot are server owned')
        payment_id = complete_payment(booking_id)
        code, booking = api('GET', '/api/bookings/' + booking_id, customer)
        check(code == 200 and booking['status'] == 'CONFIRMED', 'booking is confirmed only after internal hold confirmation')
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
        values = ','.join("'" + str(uuid.UUID(value)) + "'" for value in new_bookings)
        for _ in range(10):
            received = int(h.sql('checkinn_revenue', 'SELECT count(*) FROM payment_records WHERE booking_id IN (' + values + ')'))
            if received == len(new_bookings):
                break
            time.sleep(1)
        check(received == len(new_bookings), 'revenue consumer accepts payment event with additive bookingStatus field')
    finally:
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
        h.sql('checkinn_hotel', "BEGIN; DELETE FROM room_holds WHERE room_type_id='" + room + "'; DELETE FROM room_availability WHERE room_type_id='" + room + "'; DELETE FROM room_types WHERE id='" + room + "'; COMMIT;")
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
