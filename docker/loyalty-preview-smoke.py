"""Read-only scoped quote preview on checkinn-test, with a synthetic account only."""
from decimal import Decimal, ROUND_DOWN
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
    user, other = str(uuid.uuid4()), str(uuid.uuid4())
    failures = 0

    def case(name, condition):
        nonlocal failures
        failures += not condition
        print(json.dumps({'case': name, 'result': 'PASS' if condition else 'FAIL'}), flush=True)

    def request(path, actor=None, role='CUSTOMER', body=None, gateway=False):
        base = 'http://api-gateway:8080' if gateway else 'http://localhost:8082'
        headers = 'Authorization: Bearer ' + h.token(key, actor, role) + '\n' if actor else ''
        raw = h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-sS', '--max-time', '20',
                                '-X', 'POST', '-H', '@-', '-H', 'Content-Type: application/json',
                                '--data', json.dumps(body or {}), '-w', '\n%{http_code}', base + '/api/loyalty-points' + path], headers)
        data, status = raw.rsplit('\n', 1) if '\n' in raw else ('', raw)
        return int(status), json.loads(data) if data else None

    if h.sql('checkinn_user', "SELECT count(*) FROM loyalty_points WHERE user_id='" + user + "'") != '0':
        raise RuntimeError('Refusing existing fixture')
    rates = json.loads(h.run(h.COMPOSE + ['exec', '-T', 'user-service', 'curl', '-fsS', '--max-time', '10',
                                         'http://regulations-service:8091/api/regulations/typed/points']))
    rate = Decimal(str(rates['redemptionConversionRate']))
    if rate <= 0:
        raise RuntimeError('Valid Regulations fixture required')
    actual = min(900, int((Decimal('500000') / rate).to_integral_value(rounding=ROUND_DOWN)))
    body = {'pointsToUse': 900, 'totalAmount': 1000000, 'userId': other}
    try:
        h.sql('checkinn_user', "INSERT INTO loyalty_points(id,user_id,total_points,used_points,available_points,created_at,updated_at) VALUES ('"
              + str(uuid.uuid4()) + "','" + user + "',1000000,0,1000000,now(),now())")
        for gateway in [False, True]:
            label = 'gateway ' if gateway else 'direct '
            path = '/' + user + '/quote'
            case(label + 'anonymous preview denied', request(path, body=body, gateway=gateway)[0] == 401)
            case(label + 'cross-user preview denied', request(path, other, body=body, gateway=gateway)[0] == 403)
            status, preview = request(path, user, body=body, gateway=gateway)
            case(label + 'self preview uses path identity and Regulations cap', status == 200
                 and preview['pointsToUse'] == actual and Decimal(str(preview['discountAmount'])) == actual * rate
                 and Decimal(str(preview['redemptionRate'])) == rate)
            status, internal = request('/quote', 'booking-service', 'BOOKING_SERVICE', {**body, 'userId': user}, gateway)
            case(label + 'preview matches internal booking quote', status == 200 and internal == preview)
            case(label + 'customer still cannot call internal quote', request('/quote', user, body={**body, 'userId': user}, gateway=gateway)[0] == 403)
            case(label + 'preview cannot grant mutation permission', request('/use', user,
                 body={'userId': user, 'pointsToUse': 1, 'bookingId': str(uuid.uuid4())}, gateway=gateway)[0] == 403)
        case('quote does not change account or ledger', h.sql('checkinn_user', "SELECT available_points FROM loyalty_points WHERE user_id='"
             + user + "'") == '1000000' and h.sql('checkinn_user', "SELECT (SELECT count(*) FROM points_transactions WHERE user_id='"
             + user + "') + (SELECT count(*) FROM loyalty_commands WHERE user_id='" + user + "')") == '0')
    finally:
        h.sql('checkinn_user', "DELETE FROM loyalty_commands WHERE user_id='" + user + "'; DELETE FROM points_transactions WHERE user_id='"
              + user + "'; DELETE FROM loyalty_points WHERE user_id='" + user + "'")
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
