"""Read-only authorization checks against the isolated checkinn-test stack only.

Uses existing cloned booking fixtures; never prints IDs, tokens or response bodies.
JWTs are short-lived test credentials signed in memory with the test stack key.
Run after rebuilding booking-service. --baseline records pre-fix status codes.
"""
import argparse
import base64
import hashlib
import hmac
import json
import re
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
COMPOSE = ['docker', 'compose', '-p', 'checkinn-test', '--project-directory', str(ROOT),
           '--env-file', str(ROOT / '.env'), '--env-file', str(ROOT / '.env.test'),
           '-f', str(ROOT / 'docker-compose.yml'), '-f', str(ROOT / 'docker-compose.test.yml'),
           '--profile', 'app']


def run(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True, text=True, timeout=45)
    if result.returncode:
        raise RuntimeError('Test stack command failed; inspect local diagnostics privately')
    return result.stdout.strip()


def sql(database, query):
    return run(COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-U', 'checkinn_test',
                         '-d', database, '-Atc', query])


def verify_running_clone(service):
    container = run(COMPOSE + ['ps', '-q', service]).strip()
    environment = json.loads(run(['docker', 'inspect', '--format', '{{json .Config.Env}}', container]))
    database = service.removesuffix('-service')
    if 'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-test:5432/checkinn_' + database not in environment:
        raise RuntimeError('Running service is not using the isolated clone')


def encode(value):
    return base64.urlsafe_b64encode(value).rstrip(b'=')


def token(key, user, role):
    payload = {'sub': user, 'role': role, 'iat': int(time.time()), 'exp': int(time.time()) + 300}
    data = encode(b'{"alg":"HS256","typ":"JWT"}') + b'.' + encode(json.dumps(payload).encode())
    return (data + b'.' + encode(hmac.new(key.encode(), data, hashlib.sha256).digest())).decode()


def status(path, credential, direct):
    if direct:
        headers = 'Authorization: Bearer ' + credential + '\n' if credential else ''
        return int(run(COMPOSE + ['exec', '-T', 'booking-service', 'curl', '-sS', '--max-time', '20',
                                 '-o', '/dev/null', '-w', '%{http_code}', '-H', '@-',
                                 'http://localhost:8084' + path], headers))
    request = urllib.request.Request('http://localhost:8080' + path)
    if credential:
        request.add_header('Authorization', 'Bearer ' + credential)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            return response.status
    except urllib.error.HTTPError as error:
        error.close()
        return error.code


def main():
    baseline = argparse.ArgumentParser()
    baseline.add_argument('--baseline', action='store_true')
    args = baseline.parse_args()
    config = json.loads(run(COMPOSE + ['config', '--format', 'json']))
    services = config['services']
    for service in ['booking-service', 'hotel-service']:
        if services[service]['environment']['SPRING_DATASOURCE_URL'].split('/')[2] != 'postgres-test:5432':
            raise RuntimeError('Refusing to run outside isolated cloned databases')
        verify_running_clone(service)
    key = services['booking-service']['environment']['SECRET_KEY']
    rows = sql('checkinn_booking', 'SELECT id, user_id, hotel_id FROM bookings ORDER BY created_at LIMIT 1')
    if not rows:
        raise RuntimeError('NEEDS_ENVIRONMENT: no cloned booking fixture')
    booking, customer, hotel = rows.split('|')
    # Validate before interpolating UUID fixtures into a read-only query.
    for value in [booking, customer, hotel]:
        uuid.UUID(value)
    owner = sql('checkinn_hotel', "SELECT owner_id FROM hotels WHERE id = '" + hotel + "'")
    other = str(uuid.uuid4())
    cases = [
        ('anonymous booking', '/api/bookings/' + booking, None, 401),
        ('customer own booking', '/api/bookings/' + booking, token(key, customer, 'CUSTOMER'), 200),
        ('customer cross booking', '/api/bookings/' + booking, token(key, other, 'CUSTOMER'), 403),
        ('customer cross user list', '/api/bookings/user/' + customer, token(key, other, 'CUSTOMER'), 403),
        ('customer hotel list', '/api/bookings/hotel/' + hotel, token(key, other, 'CUSTOMER'), 403),
        ('unrelated owner hotel list', '/api/bookings/hotel/' + hotel, token(key, other, 'OWNER'), 403),
        ('administrator booking', '/api/bookings/' + booking, token(key, other, 'ADMIN'), 200),
        ('anonymous counts', '/api/bookings/count/total', None, 401),
    ]
    if owner:
        uuid.UUID(owner)
        cases.append(('hotel owner booking', '/api/bookings/' + booking, token(key, owner, 'OWNER'), 200))
    failures = 0
    for direct in [True, False]:
        for name, path, credential, expected in cases:
            actual = status(path, credential, direct)
            passed = actual == expected
            failures += not passed
            print(json.dumps({'surface': 'service' if direct else 'gateway', 'case': name,
                              'expected': expected, 'actual': actual,
                              'result': 'PASS' if passed else 'FAIL'}), flush=True)
    proto = ROOT.parent / 'booking-service/src/main/proto'
    network = config['networks']['checkinn']['name']
    for forged in [False, True]:
        command = ['docker', 'run', '--rm', '--network', network, '--mount',
                   'type=bind,source=' + str(proto) + ',target=/protos,readonly',
                   'fullstorydev/grpcurl:v1.9.3', '-plaintext', '-max-time', '20',
                   '-import-path', '/protos', '-proto', 'booking.proto']
        if forged:
            command += ['-H', 'x-user-id: ' + customer]
        result = subprocess.run(command + ['-d', json.dumps({'id': booking}),
                                'booking-service:9093', 'booking.BookingGrpcService/GetBooking'],
                                capture_output=True, text=True, timeout=45)
        match = re.search(r'Code:\s*(\w+)', result.stderr)
        code = match.group(1) if match else 'OK' if result.returncode == 0 else 'TRANSPORT_ERROR'
        passed = code == 'Unauthenticated'
        failures += not passed
        print(json.dumps({'surface': 'grpc', 'case': 'forged user header' if forged else 'anonymous booking',
                          'expected': 'Unauthenticated', 'actual': code,
                          'result': 'PASS' if passed else 'FAIL'}), flush=True)
    return 0 if args.baseline or not failures else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        # Never print subprocess output, configuration or HTTP response bodies.
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
