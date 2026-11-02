"""Review booking eligibility against checkinn-test clones only; synthetic UUID cleanup."""
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    for service in ['hotel-service', 'booking-service']:
        h.verify_running_clone(service)
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel = h.sql('checkinn_hotel', 'SELECT id FROM hotels LIMIT 1')
    hotel = str(uuid.UUID(hotel))
    guest, other, wrong_hotel = [str(uuid.uuid4()) for _ in range(3)]
    bookings = []
    failures = 0

    def check(name, actual, expected):
        nonlocal failures
        failures += actual != expected
        print(json.dumps({'case': name, 'expected': expected, 'actual': actual,
                          'result': 'PASS' if actual == expected else 'FAIL'}), flush=True)

    def create(url, booking, actor=guest, target=hotel):
        body = {'hotelId': target, 'rating': 8, 'title': 'Synthetic review',
                'content': 'Synthetic eligibility regression', 'bookingId': booking}
        headers = 'Authorization: Bearer ' + h.token(key, actor, 'CUSTOMER') + '\n'
        return int(h.run(h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20',
                                     '-X', 'POST', '-H', '@-', '-H', 'Content-Type: application/json',
                                     '--data', json.dumps(body), '-o', '/dev/null', '-w', '%{http_code}', url], headers))

    # Wait for the new booking RPC to appear in hotel discovery before fixtures.
    unknown = str(uuid.uuid4())
    if h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE id='" + unknown + "'") != '0':
        raise RuntimeError('Readiness probe must use a nonexistent booking')
    for attempt in range(30):
        if all(create(base + '/api/v1/reviews', unknown) == 403 for base in
               ['http://localhost:8083', 'http://api-gateway:8080/api/hotel']):
            break
        time.sleep(2)
    else:
        raise RuntimeError('NEEDS_ENVIRONMENT: booking eligibility discovery unavailable')

    try:
        for direct in [True, False]:
            surface = 'direct' if direct else 'gateway'
            url = ('http://localhost:8083' if direct else 'http://api-gateway:8080/api/hotel') + '/api/v1/reviews'
            booking = str(uuid.uuid4())
            if h.sql('checkinn_booking', "SELECT count(*) FROM bookings WHERE id='" + booking + "'") != '0':
                raise RuntimeError('Refusing existing fixture')
            bookings.append(booking)
            h.sql('checkinn_booking', "INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,created_at,updated_at) VALUES ('"
                  + booking + "','" + guest + "','" + hotel + "','Synthetic review hotel',current_date-2,current_date-1,1,0,'CHECKED_OUT',1000000,1000000,'Synthetic guest','review@example.invalid','0000000000',now(),now())")
            if direct:
                proto = Path(__file__).resolve().parent.parent / 'booking-service/src/main/proto'
                command = ['docker', 'run', '--rm', '--network', config['networks']['checkinn']['name'],
                           '--mount', 'type=bind,source=' + str(proto) + ',target=/protos,readonly',
                           'fullstorydev/grpcurl:v1.9.3', '-plaintext', '-max-time', '10',
                           '-import-path', '/protos', '-proto', 'booking.proto']
                for subject, role, expected in [(None, None, 'Unauthenticated'), (other, 'ADMIN', 'PermissionDenied'),
                                                ('hotel-service', 'ADMIN', 'PermissionDenied'),
                                                ('other-service', 'HOTEL_SERVICE', 'PermissionDenied'),
                                                ('hotel-service', 'HOTEL_SERVICE', 'eligible')]:
                    headers = ['-H', 'Authorization: Bearer ' + h.token(key, subject, role)] if subject else []
                    body = {'bookingId': booking, 'guestId': guest, 'hotelId': hotel}
                    result = subprocess.run(command + headers + ['-d', json.dumps(body), 'booking-service:9093',
                                            'booking.BookingGrpcService/CheckReviewEligibility'],
                                            capture_output=True, text=True, timeout=30)
                    passed = (result.returncode == 0 and json.loads(result.stdout) == {'eligible': True}) if expected == 'eligible' else (
                        result.returncode != 0 and expected in result.stderr)
                    check('grpc eligibility ' + str(role) + ' ' + ('service' if subject == 'hotel-service' else 'other'), passed, True)
                for method, body in [('GetBooking', {'id': booking}), ('CreateBooking', {'userId': 'hotel-service'})]:
                    result = subprocess.run(command + ['-H', 'Authorization: Bearer ' + h.token(key, 'hotel-service', 'HOTEL_SERVICE'),
                                            '-d', json.dumps(body), 'booking-service:9093', 'booking.BookingGrpcService/' + method],
                                            capture_output=True, text=True, timeout=30)
                    check('grpc hotel service cannot ' + method, result.returncode != 0 and 'Unauthenticated' in result.stderr, True)
            check(surface + ' missing booking', create(url, None), 400)
            check(surface + ' unknown booking', create(url, str(uuid.uuid4())), 403)
            check(surface + ' other guest', create(url, booking, actor=other), 403)
            check(surface + ' other hotel', create(url, booking, target=wrong_hotel), 403)
            for status in ['PENDING', 'PENDING_PAYMENT', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW']:
                h.sql('checkinn_booking', "UPDATE bookings SET status='" + status + "' WHERE id='" + booking + "'")
                check(surface + ' reject ' + status, create(url, booking), 403)
            check(surface + ' no rejected review persisted', int(h.sql('checkinn_hotel', "SELECT count(*) FROM hotel_reviews WHERE guest_id='" + guest + "'")), 0 if direct else 1)
            h.sql('checkinn_booking', "UPDATE bookings SET status='CHECKED_OUT' WHERE id='" + booking + "'")
            check(surface + ' checked out guest', create(url, booking), 201)
            check(surface + ' duplicate', create(url, booking), 400)
        check('one review per eligible booking', int(h.sql('checkinn_hotel', "SELECT count(*) FROM hotel_reviews WHERE guest_id='" + guest + "'")), 2)
    finally:
        h.sql('checkinn_hotel', "DELETE FROM hotel_reviews WHERE guest_id='" + guest + "'")
        for booking in bookings:
            h.sql('checkinn_booking', "DELETE FROM bookings WHERE id='" + booking + "' AND user_id='" + guest + "'")
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
