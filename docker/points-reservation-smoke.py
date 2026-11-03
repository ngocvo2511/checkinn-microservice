"""Reserved balance, races and conservative recovery on checkinn-test clones only."""
import concurrent.futures
import datetime
from decimal import Decimal
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec); spec.loader.exec_module(h)


def main():
    for service in ['user-service', 'booking-service', 'regulations-service']:
        h.verify_running_clone(service)
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['user-service']['environment']['SECRET_KEY']
    credential = h.token(key, 'booking-service', 'BOOKING_SERVICE')
    users = [str(uuid.uuid4()), str(uuid.uuid4())]
    user, other = users
    values = "','".join(users)
    bookings = []
    stopped = False
    failures = 0
    trigger = 'reserved_use_fault_' + uuid.uuid4().hex[:16]
    trigger_created = False

    def case(name, condition):
        nonlocal failures
        failures += not condition
        print(json.dumps({'case': name, 'result': 'PASS' if condition else 'FAIL'}), flush=True)

    def api(method, path, body=None, token=credential):
        args = h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-sS', '--max-time', '20', '-X', method, '-H', '@-', '-w', '\n%{http_code}']
        if body is not None: args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        raw = h.run(args + ['http://localhost:8082/api/loyalty-points' + path], 'Authorization: Bearer ' + token + '\n')
        data, status = raw.rsplit('\n', 1) if '\n' in raw else ('', raw)
        return int(status), json.loads(data) if data else None

    def balance(actor):
        status, data = api('GET', '/' + actor)
        if status != 200: raise RuntimeError('Balance unavailable')
        return data['usedPoints'], data['reservedPoints'], data['availablePoints']

    rates = json.loads(h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-fsS', '--max-time', '10',
                                         'http://regulations-service:8091/api/regulations/typed/points']))
    earn_rate = Decimal(str(rates['earnConversionRate'])); rate = Decimal(str(rates['redemptionConversionRate']))
    if earn_rate <= 0 or rate <= 0: raise RuntimeError('Valid Regulations required')

    def payload(actor, booking, points):
        deadline = (datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=5)).isoformat()
        return {'userId': actor, 'bookingId': booking, 'pointsToUse': points, 'totalAmount': str(rate * 2000), 'holdExpiresAt': deadline}

    def commit(actor, booking, points):
        return api('POST', '/use', {'userId': actor, 'bookingId': booking, 'pointsToUse': points, 'expectedDiscountAmount': str(rate * points)})

    def refund(actor, booking):
        return api('POST', '/refund?userId=' + actor + '&bookingId=' + booking)

    def parallel(actions):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            return list(pool.map(lambda action: action(), actions))

    def state(booking):
        return h.sql('checkinn_user', "SELECT state FROM points_reservations WHERE booking_id='" + booking + "'")

    def expire(booking):
        h.sql('checkinn_user', "UPDATE points_reservations SET hold_expires_at=now()-interval '1 second',next_check_at=now()-interval '1 second' WHERE booking_id='" + booking + "'")

    def wait_for(check, seconds=45):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if check(): return True
            time.sleep(0.5)
        return False

    def fixture_booking(actor, status='PENDING'):
        booking = str(uuid.uuid4()); bookings.append(booking)
        h.sql('checkinn_booking', "INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,created_at,updated_at) VALUES ('"
              + booking + "','" + actor + "','" + str(uuid.uuid4()) + "','Synthetic points state',current_date+10,current_date+11,1,0,'"
              + status + "',1000000,0,'Synthetic guest','reserve@example.invalid','0000000000',now(),now())")
        return booking

    def restore_booking():
        result = subprocess.run(h.COMPOSE + ['up', '-d', '--no-build', '--wait', '--wait-timeout', '150', 'booking-service'], capture_output=True, text=True, timeout=180)
        if result.returncode: raise RuntimeError('Test booking restart failed; inspect private diagnostics')

    if h.sql('checkinn_user', "SELECT (SELECT count(*) FROM loyalty_points WHERE user_id IN ('" + values
             + "'))+(SELECT count(*) FROM loyalty_commands WHERE user_id IN ('" + values + "'))") != '0':
        raise RuntimeError('Refusing existing users')
    try:
        for actor in users:
            status, _ = api('POST', '/earn?userId=' + actor + '&bookingId=' + str(uuid.uuid4()) + '&totalAmount=' + str(earn_rate * 1000))
            if status != 200: raise RuntimeError('Synthetic seed failed')
        first = fixture_booking(user)
        body = payload(user, first, 600)
        for actor, role in [(user, 'CUSTOMER'), (other, 'ADMIN'), (other, 'BOOKING_SERVICE')]:
            case('reserve denied for ' + role, api('POST', '/reserve', body, h.token(key, actor, role))[0] == 403)
        replies = parallel([lambda: api('POST', '/reserve', body)] * 8)
        case('same reservation replay returns one held quote', all(status == 200 and reply['pointsToUse'] == 600 for status, reply in replies))
        case('hold changes spendable balance without debit', balance(user) == (0, 600, 400))
        case('one reservation row', h.sql('checkinn_user', "SELECT count(*) FROM points_reservations WHERE user_id='" + user + "'") == '1')
        changed = {**body, 'pointsToUse': 601}
        case('changed reserve input rejected', api('POST', '/reserve', changed)[0] == 409)
        case('legacy debit cannot spend another hold', api('POST', '/use', {'userId': user, 'bookingId': str(uuid.uuid4()), 'pointsToUse': 500})[0] == 400)
        case('quote cannot fund another booking from held points', api('POST', '/quote', {'userId': user, 'pointsToUse': 500, 'totalAmount': str(rate * 2000)})[0] == 400)
        case('earning cannot precede reserved debit', api('POST', '/earn?userId=' + user + '&bookingId=' + first + '&totalAmount=1000')[0] == 409)

        # Recovery must retain an expired hold while its booking is ACTIVE.
        expire(first)
        observed = wait_for(lambda: h.sql('checkinn_user', "SELECT next_check_at>now() FROM points_reservations WHERE booking_id='" + first + "'") == 't')
        case('expired active reservation is checked and retained', observed and state(first) == 'HELD' and balance(user) == (0, 600, 400))
        replies = parallel([lambda: commit(user, first, 600)] * 8)
        case('commit after deadline is safe and replayable while funds remain held', all(status == 200 and Decimal(str(discount)) == rate * 600 for status, discount in replies))
        case('commit converts hold once without freeing spendable funds', state(first) == 'COMMITTED' and balance(user) == (600, 0, 400))
        case('one USE row', h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + first + "' AND transaction_type='USE'") == '1')
        status, earning = api('POST', '/earn?userId=' + user + '&bookingId=' + first + '&totalAmount=' + str(earn_rate * 3))
        case('valid committed reservation earns after USE without false legacy quarantine', status == 200
             and earning['awardedPoints'] == 3 and balance(user) == (600, 0, 403))
        case('cancel committed reservation once', all(status == 200 for status, _ in parallel([lambda: refund(user, first)] * 8))
             and balance(user) == (0, 0, 1000) and state(first) == 'CANCELLED')
        case('late commit is denied', commit(user, first, 600)[0] == 409)

        competitors = [payload(other, str(uuid.uuid4()), 600) for _ in range(2)]
        replies = parallel([lambda item=item: api('POST', '/reserve', item) for item in competitors])
        case('two pending reservations cannot use the same 1000 points', sorted(status for status, _ in replies) == [200, 400]
             and balance(other) == (0, 600, 400))
        winner = competitors[0]['bookingId'] if replies[0][0] == 200 else competitors[1]['bookingId']
        case('cancel unused hold returns funds without a debit', refund(other, winner)[0] == 200 and balance(other) == (0, 0, 1000))
        case('late reserve after cancellation is denied', api('POST', '/reserve', competitors[0] if winner == competitors[0]['bookingId'] else competitors[1])[0] == 409)

        rollback = str(uuid.uuid4())
        case('reserved commit rollback fixture', api('POST', '/reserve', payload(other, rollback, 200))[0] == 200)
        h.sql('checkinn_user', "BEGIN; CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.booking_id='"
              + rollback + "' AND NEW.command_type='USE' THEN RAISE EXCEPTION 'synthetic reserved use rollback'; END IF; RETURN NEW; END $$; CREATE TRIGGER "
              + trigger + " BEFORE INSERT ON loyalty_commands FOR EACH ROW EXECUTE FUNCTION " + trigger + "(); COMMIT;")
        trigger_created = True
        case('failed reserved receipt is unavailable', commit(other, rollback, 200)[0] == 503)
        case('failed reserved commit rolls back state account and journal', state(rollback) == 'HELD' and balance(other) == (0, 200, 800)
             and h.sql('checkinn_user', "SELECT count(*) FROM points_transactions WHERE booking_id='" + rollback + "'") == '0')
        h.sql('checkinn_user', 'BEGIN; DROP TRIGGER ' + trigger + ' ON loyalty_commands; DROP FUNCTION ' + trigger + '(); COMMIT;')
        trigger_created = False
        case('reserved commit retry succeeds once', commit(other, rollback, 200)[0] == 200 and state(rollback) == 'COMMITTED'
             and balance(other) == (200, 0, 800))
        case('reserved rollback fixture reversal', refund(other, rollback)[0] == 200 and balance(other) == (0, 0, 1000))

        in_flight = str(uuid.uuid4())
        # Simulate the booking transaction that owns the same creation lock but
        # has not committed a row. Recovery must distinguish this from MISSING.
        lock = subprocess.Popen(h.COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-qAt', '-U', 'checkinn_test', '-d', 'checkinn_booking'],
                                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            lock.stdin.write("BEGIN; SELECT pg_advisory_xact_lock(hashtextextended('points-booking-create:" + in_flight + "',0)); SELECT 'READY';\n")
            lock.stdin.flush()
            while lock.stdout.readline().strip() != 'READY':
                if lock.poll() is not None: raise RuntimeError('Synthetic creation lock unavailable')
            case('in-flight creation reservation fixture', api('POST', '/reserve', payload(other, in_flight, 200))[0] == 200)
            expire(in_flight)
            observed = wait_for(lambda: h.sql('checkinn_user', "SELECT next_check_at>now() FROM points_reservations WHERE booking_id='" + in_flight + "'") == 't')
            case('uncommitted booking is UNKNOWN and keeps its funds', observed and state(in_flight) == 'HELD' and balance(other) == (0, 200, 800))
        finally:
            lock.communicate('ROLLBACK;\n', timeout=15)
        case('rolled-back creation becomes a recoverable orphan', wait_for(lambda: state(in_flight) == 'CANCELLED') and balance(other) == (0, 0, 1000))

        orphan = str(uuid.uuid4()); orphan_body = payload(other, orphan, 200)
        case('orphan reserve creates held funds', api('POST', '/reserve', orphan_body)[0] == 200)
        expire(orphan)
        case('missing expired booking releases orphan via authenticated lookup', wait_for(lambda: state(orphan) == 'CANCELLED') and balance(other) == (0, 0, 1000))
        cancelled = fixture_booking(other, 'CANCELLED')
        case('cancelled-booking recovery fixture reserved', api('POST', '/reserve', payload(other, cancelled, 200))[0] == 200)
        expire(cancelled)
        case('cancelled booking releases expired hold', wait_for(lambda: state(cancelled) == 'CANCELLED') and balance(other) == (0, 0, 1000))

        # The lookup RPC exposes state only and cannot be used as general booking access.
        proto = Path(__file__).resolve().parent.parent / 'booking-service/src/main/proto'
        command = ['docker', 'run', '--rm', '--network', config['networks']['checkinn']['name'], '--mount',
                   'type=bind,source=' + str(proto) + ',target=/protos,readonly', 'fullstorydev/grpcurl:v1.9.3',
                   '-plaintext', '-max-time', '10', '-import-path', '/protos', '-proto', 'booking.proto']
        for subject, role, expected in [(None, None, 'Unauthenticated'), (other, 'ADMIN', 'PermissionDenied'),
                                        (other, 'USER_SERVICE', 'PermissionDenied'), ('user-service', 'USER_SERVICE', 'ACTIVE')]:
            headers = ['-H', 'authorization: Bearer ' + h.token(key, subject, role)] if subject else []
            result = subprocess.run(command + headers + ['-d', json.dumps({'bookingId': first, 'userId': user}),
                                    'booking-service:9093', 'booking.BookingGrpcService/GetLoyaltyBookingState'], capture_output=True, text=True, timeout=30)
            passed = result.returncode == 0 and json.loads(result.stdout) == {'state': 'ACTIVE'} if expected == 'ACTIVE' else result.returncode != 0 and expected in result.stderr
            case('recovery RPC scope ' + str(role), passed)
        for method, data in [('GetBooking', {'id': first}), ('CreateBooking', {'userId': 'user-service'})]:
            result = subprocess.run(command + ['-H', 'authorization: Bearer ' + h.token(key, 'user-service', 'USER_SERVICE'), '-d', json.dumps(data),
                                    'booking-service:9093', 'booking.BookingGrpcService/' + method], capture_output=True, text=True, timeout=30)
            case('recovery credential cannot ' + method, result.returncode != 0 and 'Unauthenticated' in result.stderr)

        stopped = True
        h.run(h.COMPOSE + ['stop', 'booking-service'])
        uncertain = str(uuid.uuid4())
        case('outage fixture reserves funds', api('POST', '/reserve', payload(other, uncertain, 200))[0] == 200)
        expire(uncertain)
        observed = wait_for(lambda: h.sql('checkinn_user', "SELECT next_check_at>now() FROM points_reservations WHERE booking_id='" + uncertain + "'") == 't')
        case('lookup outage never looks like a missing booking', observed and state(uncertain) == 'HELD' and balance(other) == (0, 200, 800))
        restore_booking(); stopped = False
        case('orphan recovery resumes when booking lookup returns', wait_for(lambda: state(uncertain) == 'CANCELLED') and balance(other) == (0, 0, 1000))
    finally:
        if trigger_created:
            h.sql('checkinn_user', 'BEGIN; DROP TRIGGER IF EXISTS ' + trigger + ' ON loyalty_commands; DROP FUNCTION IF EXISTS ' + trigger + '(); COMMIT;')
        if stopped: restore_booking()
        if bookings:
            quoted = "','".join(bookings)
            h.sql('checkinn_booking', "DELETE FROM bookings WHERE id IN ('" + quoted + "') AND user_id IN ('" + values + "')")
        h.sql('checkinn_user', "DELETE FROM points_reservations WHERE user_id IN ('" + values + "'); DELETE FROM loyalty_commands WHERE user_id IN ('"
              + values + "'); DELETE FROM points_transactions WHERE user_id IN ('" + values + "'); DELETE FROM loyalty_points WHERE user_id IN ('" + values + "')")
    case('synthetic reservation cleanup', h.sql('checkinn_user', "SELECT count(*) FROM points_reservations WHERE user_id IN ('" + values + "')") == '0')
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
