"""Idempotency, PostgreSQL concurrency and rollback on checkinn-test only."""
import concurrent.futures
from decimal import Decimal
import importlib.util
import json
from pathlib import Path
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('user-service')
    h.verify_running_clone('regulations-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['user-service']['environment']['SECRET_KEY']
    credential = h.token(key, 'booking-service', 'BOOKING_SERVICE')
    users = [str(uuid.uuid4()) for _ in range(4)]
    user, race_user, legacy_user, rollback_user = users
    all_users = "','".join(users)
    if h.sql('checkinn_user', "SELECT (SELECT count(*) FROM loyalty_points WHERE user_id IN ('" + all_users
             + "')) + (SELECT count(*) FROM points_transactions WHERE user_id IN ('" + all_users
             + "')) + (SELECT count(*) FROM loyalty_commands WHERE user_id IN ('" + all_users + "'))") != '0':
        raise RuntimeError('Refusing existing fixture')
    failures = 0

    def case(name, actual, expected=True):
        nonlocal failures
        passed = actual == expected
        failures += not passed
        print(json.dumps({'case': name, 'actual': actual, 'expected': expected,
                          'result': 'PASS' if passed else 'FAIL'}), flush=True)

    def api(method, path, body=None):
        args = h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-sS', '--max-time', '20', '-X', method,
                            '-H', '@-', '-w', '\n%{http_code}']
        if body is not None:
            args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        raw = h.run(args + ['http://localhost:8082/api/loyalty-points' + path], 'Authorization: Bearer ' + credential + '\n')
        text, status = raw.rsplit('\n', 1) if '\n' in raw else ('', raw)
        return int(status), json.loads(text) if text else None

    def earn(actor, booking, amount):
        return api('POST', '/earn?userId=' + actor + '&bookingId=' + booking + '&totalAmount=' + str(amount))

    def use(actor, booking, points, discount=None):
        body = {'userId': actor, 'bookingId': booking, 'pointsToUse': points}
        if discount is not None:
            body['expectedDiscountAmount'] = str(discount)
        return api('POST', '/use', body)

    def refund(actor, booking):
        return api('POST', '/refund?userId=' + actor + '&bookingId=' + booking)

    def parallel(actions):
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            return list(pool.map(lambda action: action(), actions))

    def balance(actor):
        status, data = api('GET', '/' + actor)
        if status != 200:
            raise RuntimeError('Balance read failed')
        return data['totalPoints'], data['usedPoints'], data['availablePoints']

    def count(table, actor, extra=''):
        return int(h.sql('checkinn_user', "SELECT count(*) FROM " + table + " WHERE user_id='" + actor + "'" + extra))

    rates = json.loads(h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-fsS', '--max-time', '10',
                                         'http://regulations-service:8091/api/regulations/typed/points']))
    earn_rate, redemption_rate = Decimal(str(rates['earnConversionRate'])), Decimal(str(rates['redemptionConversionRate']))
    if earn_rate <= 0 or redemption_rate <= 0:
        raise RuntimeError('Valid regulations required')
    booking, other_booking = str(uuid.uuid4()), str(uuid.uuid4())
    trigger = 'loyalty_rollback_' + uuid.uuid4().hex
    trigger_created = False
    try:
        case('missing account reads zero', balance(user), (0, 0, 0))
        case('read does not insert account', count('loyalty_points', user), 0)
        results = parallel([lambda: earn(user, booking, earn_rate * 10)] * 8)
        case('concurrent identical earn requests succeed', all(status == 200 for status, _ in results))
        case('first account created once', count('loyalty_points', user), 1)
        case('earn changes balance once', balance(user), (10, 0, 10))
        case('earn ledger row exactly once', count('points_transactions', user, " AND transaction_type='EARN'"), 1)
        case('changed earn input conflicts', earn(user, booking, earn_rate * 11)[0], 409)
        case('cross user command conflicts', earn(race_user, booking, earn_rate * 10)[0], 409)
        results = parallel([lambda: use(user, booking, 4, redemption_rate * 4)] * 8)
        case('concurrent redemption replays same discount', all(status == 200 and Decimal(str(value)) == redemption_rate * 4 for status, value in results))
        case('redemption balance exactly once', balance(user), (10, 4, 6))
        case('redemption ledger exactly once', count('points_transactions', user, " AND transaction_type='USE'"), 1)
        case('changed redemption count conflicts', use(user, booking, 5, redemption_rate * 5)[0], 409)
        case('changed discount snapshot conflicts', use(user, booking, 4, redemption_rate * 5)[0], 409)
        status, quote = api('POST', '/quote', {'userId': user, 'pointsToUse': 9, 'totalAmount': str(redemption_rate * 10)})
        case('quote uses actual points after cap', status == 200 and quote['pointsToUse'] == 5
             and Decimal(str(quote['discountAmount'])) == redemption_rate * 5)
        case('quote does not debit', balance(user), (10, 4, 6))
        third_booking = str(uuid.uuid4())
        results = parallel([lambda: use(user, other_booking, 4), lambda: use(user, third_booking, 4)])
        case('concurrent bookings cannot overspend', sorted(status for status, _ in results), [200, 400])
        case('overspend loser has no debit', balance(user), (10, 8, 2))
        spent_booking = other_booking if results[0][0] == 200 else third_booking
        results = parallel([lambda: refund(user, booking)] * 8)
        case('concurrent reversal replay succeeds', all(status == 200 for status, _ in results))
        case('reversal preserves debt spent elsewhere', balance(user), (0, 4, -4))
        case('earning and redemption reversed exactly once', count('points_transactions', user,
             " AND transaction_type IN ('REVERSE_EARN','REVERSE_USE')"), 2)
        case('late earn is blocked', earn(user, booking, earn_rate * 10)[0], 409)
        case('late redemption is blocked', use(user, booking, 4, redemption_rate * 4)[0], 409)
        case('another user cannot replay reversal', refund(race_user, booking)[0], 409)
        case('redemption-only reversal succeeds', refund(user, spent_booking)[0], 200)
        case('redemption-only reversal restores remaining balance', balance(user), (0, 0, 0))
        empty = str(uuid.uuid4())
        case('empty cancellation persists tombstone', refund(race_user, empty)[0], 200)
        case('tombstone blocks delayed earn', earn(race_user, empty, earn_rate)[0], 409)
        case('tombstone blocks delayed debit', use(race_user, empty, 1)[0], 409)
        for index in range(4):
            race_booking = str(uuid.uuid4())
            results = parallel([lambda: earn(race_user, race_booking, earn_rate * 3), lambda: refund(race_user, race_booking)])
            case('earn versus cancellation race ' + str(index), results[0][0] in (200, 409) and results[1][0] == 200
                 and balance(race_user) == (0, 0, 0))
        batch_bookings = [str(uuid.uuid4()) for _ in range(8)]
        results = parallel([lambda current=current: earn(race_user, current, earn_rate) for current in batch_bookings])
        case('different booking earns do not lose updates', all(status == 200 for status, _ in results)
             and balance(race_user) == (8, 0, 8))

        # Ambiguous old rows are never normalized or replayed automatically.
        legacy_booking = str(uuid.uuid4())
        h.sql('checkinn_user', "INSERT INTO loyalty_points(id,user_id,total_points,used_points,available_points,created_at,updated_at) VALUES ('"
              + str(uuid.uuid4()) + "','" + legacy_user + "',100,0,100,now(),now())")
        for _ in range(2):
            h.sql('checkinn_user', "INSERT INTO points_transactions(id,user_id,booking_id,transaction_type,points,description,created_at) VALUES ('"
                  + str(uuid.uuid4()) + "','" + legacy_user + "','" + legacy_booking + "','EARN',10,'Synthetic legacy duplicate',now())")
        case('ambiguous legacy reversal requires reconciliation', refund(legacy_user, legacy_booking)[0], 409)
        case('ambiguous legacy account cannot quote a new discount', api('POST', '/quote', {
            'userId': legacy_user, 'pointsToUse': 1, 'totalAmount': str(redemption_rate * 10)})[0], 409)
        case('ambiguous legacy account cannot spend on another booking', use(legacy_user, str(uuid.uuid4()), 1)[0], 409)
        case('ambiguous legacy account cannot accumulate a new earning', earn(legacy_user, str(uuid.uuid4()), earn_rate)[0], 409)
        case('legacy balance and rows untouched', balance(legacy_user) == (100, 0, 100) and count('points_transactions', legacy_user) == 2)

        # Fail the receipt INSERT after account and ledger writes to prove real rollback.
        rollback_booking = str(uuid.uuid4())
        case('rollback account seeded by actual earn', earn(rollback_user, rollback_booking, earn_rate * 10)[0], 200)
        h.sql('checkinn_user', "CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.user_id='"
              + rollback_user + "'::uuid AND NEW.command_type='USE' THEN RAISE EXCEPTION 'synthetic rollback'; END IF; RETURN NEW; END $$; CREATE TRIGGER "
              + trigger + " BEFORE INSERT ON loyalty_commands FOR EACH ROW EXECUTE FUNCTION " + trigger + "()")
        trigger_created = True
        case('receipt storage failure returns unavailable', use(rollback_user, rollback_booking, 2)[0], 503)
        case('account rolls back with failed receipt', balance(rollback_user), (10, 0, 10))
        case('ledger rolls back with failed receipt', count('points_transactions', rollback_user, " AND transaction_type='USE'"), 0)
        case('failed receipt is absent', count('loyalty_commands', rollback_user, " AND command_type='USE'"), 0)
        h.sql('checkinn_user', 'DROP TRIGGER ' + trigger + ' ON loyalty_commands; DROP FUNCTION ' + trigger + '()')
        trigger_created = False
        case('same command retries after rollback', use(rollback_user, rollback_booking, 2)[0], 200)
        case('retry debits once', balance(rollback_user), (10, 2, 8))
    finally:
        if trigger_created:
            h.sql('checkinn_user', 'DROP TRIGGER IF EXISTS ' + trigger + ' ON loyalty_commands; DROP FUNCTION IF EXISTS ' + trigger + '()')
        h.sql('checkinn_user', "DELETE FROM points_reservations WHERE user_id IN ('" + all_users + "'); DELETE FROM loyalty_commands WHERE user_id IN ('" + all_users
              + "'); DELETE FROM points_transactions WHERE user_id IN ('" + all_users
              + "'); DELETE FROM loyalty_points WHERE user_id IN ('" + all_users + "')")
    case('synthetic fixture cleanup', sum(count(table, actor) for table in
         ['loyalty_commands', 'points_transactions', 'loyalty_points'] for actor in users), 0)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
