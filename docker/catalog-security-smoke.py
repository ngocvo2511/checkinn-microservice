"""Catalog authorization on verified nonexistent UUIDs, never existing records.

Uses the isolated checkinn-test project; no create endpoint or data fixture writes.
Successful admin authorization should reach a 404, not mutate any catalog record.
"""
import importlib.util
import json
from pathlib import Path
import uuid

spec = importlib.util.spec_from_file_location('booking_security', Path(__file__).with_name('booking-security-smoke.py'))
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)


def main():
    helpers.verify_running_clone('hotel-service')
    config = json.loads(helpers.run(helpers.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    target = str(uuid.uuid4())
    actor = str(uuid.uuid4())
    count = helpers.sql('checkinn_hotel', "SELECT (SELECT count(*) FROM cities WHERE id='" + target
                        + "') + (SELECT count(*) FROM hotels WHERE id='" + target
                        + "') + (SELECT count(*) FROM room_types WHERE id='" + target + "')")
    if count != '0':
        raise RuntimeError('Refusing to use an existing catalog record')
    owner = helpers.token(key, actor, 'OWNER')
    admin = helpers.token(key, actor, 'ADMIN')
    cases = [
        ('anonymous city delete', 'DELETE', '/cities/' + target, None, 401),
        ('owner city delete', 'DELETE', '/cities/' + target, owner, 403),
        ('admin city authorization', 'DELETE', '/cities/' + target, admin, 404),
        ('anonymous hotel activate', 'PATCH', '/hotels/' + target + '/activate', None, 401),
        ('owner hotel deactivate', 'PUT', '/hotels/' + target + '/deactivate', owner, 403),
        ('admin hotel authorization', 'PATCH', '/hotels/' + target + '/activate', admin, 404),
        ('forged room owner without token', 'PUT', '/api/v1/' + target + '/activate', None, 401),
        ('room principal reaches resource lookup', 'PUT', '/api/v1/' + target + '/activate', owner, 404),
    ]
    failures = 0
    for direct in [True, False]:
        for name, method, path, credential, expected in cases:
            # Gateway uses the existing hotel prefix/rewrite for all these routes.
            url = 'http://localhost:8083' + path if direct else 'http://api-gateway:8080/api/hotel' + path
            header = 'X-OWNER-ID: ' + target + '\nX-Is-Admin: true\n'
            if credential:
                header += 'Authorization: Bearer ' + credential + '\n'
            code = int(helpers.run(helpers.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS',
                                   '--max-time', '20', '-X', method, '-H', '@-', '-o', '/dev/null',
                                   '-w', '%{http_code}', url], header))
            passed = code == expected
            failures += not passed
            print(json.dumps({'surface': 'service' if direct else 'gateway', 'case': name,
                              'expected': expected, 'actual': code, 'result': 'PASS' if passed else 'FAIL'}), flush=True)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
