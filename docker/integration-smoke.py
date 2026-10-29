"""Read-only application checks plus one synthetic email captured by test Mailpit.

Run only against the checkinn-test Compose project, after all app services are ready.
No customer data, credentials, tokens or response bodies are printed or saved.
"""
import base64
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request
import urllib.parse

ROOT = Path(__file__).resolve().parent
COMPOSE = ['docker', 'compose', '-p', 'checkinn-test', '--project-directory', str(ROOT),
           '--env-file', str(ROOT / '.env'), '--env-file', str(ROOT / '.env.test'),
           '-f', str(ROOT / 'docker-compose.yml'), '-f', str(ROOT / 'docker-compose.test.yml'), '--profile', 'app']
RESULTS = []


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
    if result.returncode:
        status = re.search(r'Code:\s*(\w+)', result.stderr)
        raise RuntimeError(f'Command exit {result.returncode}' + (f'; gRPC {status[1]}' if status else ''))
    return result.stdout


def check(name, action):
    try:
        action()
        RESULTS.append({'check': name, 'status': 'PASS'})
    except urllib.error.HTTPError as error:
        RESULTS.append({'check': name, 'status': 'FAIL', 'reason': f'HTTP {error.code}'})
    except Exception as error:
        # Only fixed diagnostic strings from these helpers; no response bodies.
        message = str(error) if isinstance(error, (AssertionError, RuntimeError)) else type(error).__name__
        RESULTS.append({'check': name, 'status': 'FAIL', 'reason': message})
    print(json.dumps(RESULTS[-1]), flush=True)


def sql(database, query):
    return run(COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-U', 'checkinn_test', '-d', database, '-Atc', query]).strip()


def grpc(address, method, payload, proto=None):
    command = ['docker', 'run', '--rm', '--network', 'checkinn-test_checkinn']
    if proto:
        command += ['--mount', f'type=bind,source={proto.parent},target=/protos,readonly']
    command += ['fullstorydev/grpcurl:v1.9.3', '-max-time', '20', '-plaintext']
    if proto:
        command += ['-import-path', '/protos', '-proto', proto.name]
    return json.loads(run(command + ['-d', json.dumps(payload), address, method]))


def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def main():
    config = json.loads(run(COMPOSE + ['config', '--format', 'json']))
    notification_id = run(COMPOSE + ['ps', '-q', 'notification-service']).strip()
    actual_env = json.loads(run(['docker', 'inspect', '--format', '{{json .Config.Env}}', notification_id]))
    expect('SPRING_MAIL_HOST=mailpit' in actual_env, 'Refusing email check: notification is not using the local sink')
    for name in ('user', 'hotel', 'booking', 'revenue', 'regulations'):
        container = run(COMPOSE + ['ps', '-q', name + '-service']).strip()
        env = json.loads(run(['docker', 'inspect', '--format', '{{json .Config.Env}}', container]))
        expect(f'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-test:5432/checkinn_{name}' in env,
               'Refusing tests: an app datasource is not the isolated clone')
    port = config['services']['api-gateway']['ports'][0]['published']
    gateway = f'http://127.0.0.1:{port}'

    def http_json(url, payload=None, headers=None):
        data = None if payload is None else json.dumps(payload).encode()
        request = urllib.request.Request(url, data=data, headers=headers or {})
        with urllib.request.urlopen(request, timeout=25) as response:
            return json.load(response)

    for endpoint in ('/cities', '/hotels'):
        check('gateway GET ' + endpoint, lambda path=endpoint: expect(isinstance(http_json(gateway + path), list), 'Expected JSON array'))
    for endpoint in ('/api/regulations/typed/points', '/api/regulations/typed/commission',
                     '/api/reports/summary?from=2026-01-01&to=2026-10-28'):
        check('gateway GET ' + endpoint, lambda path=endpoint: expect(isinstance(http_json(gateway + path), dict), 'Expected JSON object'))

    def scoped_revenue():
        fixture = sql('checkinn_hotel', 'SELECT id FROM hotels ORDER BY id LIMIT 1')
        expect(bool(fixture), 'No hotel fixture in cloned database')
        query = urllib.parse.urlencode({'hotelId': fixture, 'from': '2026-01-01', 'to': '2026-10-28'})
        expect(isinstance(http_json(gateway + '/api/reports/summary?' + query), dict), 'Expected JSON summary')
    check('gateway revenue summary with cloned hotel fixture', scoped_revenue)

    def database_connections():
        active = sql('postgres', "SELECT count(DISTINCT datname) FROM pg_stat_activity WHERE usename='checkinn_test' AND backend_type='client backend' AND datname IN ('checkinn_user','checkinn_hotel','checkinn_booking','checkinn_revenue','checkinn_regulations')")
        expect(active == '5', 'Not all five cloned databases have application connections')
    check('application connections to all five cloned databases', database_connections)

    def user_rpc():
        fixture = sql('checkinn_user', 'SELECT id FROM users ORDER BY id LIMIT 1')
        expect(bool(fixture), 'No user fixture in cloned database')
        proto = next(p for p in (ROOT.parent / 'user-service/src/main/proto').glob('*.proto') if 'service UserService' in p.read_text())
        response = grpc('user-service:9097', 'UserService/GetUserById', {'id': fixture}, proto)
        expect(response.get('id') == fixture, 'Unexpected user gRPC response')
    check('user gRPC GetUserById on cloned fixture', user_rpc)

    def hotel_rpc():
        fixture = sql('checkinn_hotel', 'SELECT id FROM hotels ORDER BY id LIMIT 1')
        expect(bool(fixture), 'No hotel fixture in cloned database')
        response = grpc('hotel-service:9092', 'hotel.HotelGrpcService/GetHotelById', {'hotel_id': fixture})
        expect(response.get('id') == fixture, 'Unexpected hotel gRPC response')
    check('hotel gRPC GetHotelById on cloned fixture', hotel_rpc)
    check('media gRPC empty batch (no Supabase request)',
          lambda: expect(isinstance(grpc('media-service:9096', 'media.MediaGrpcService/GetSignedUrls', {'file_paths': []}), dict), 'Unexpected media RPC response'))

    rabbit = config['services']['rabbitmq']['environment']
    rabbit_port = config['services']['rabbitmq']['ports'][0]['published']
    encoded = base64.b64encode((rabbit['RABBITMQ_DEFAULT_USER'] + ':' + rabbit['RABBITMQ_DEFAULT_PASS']).encode()).decode()
    headers = {'Authorization': 'Basic ' + encoded, 'Content-Type': 'application/json'}

    def consumer_check():
        queues = http_json(f'http://127.0.0.1:{rabbit_port}/api/queues/checkinn', headers=headers)
        consumers = {queue['name']: queue['consumers'] for queue in queues}
        expect(all(consumers.get(name, 0) > 0 for name in ('notification.otp.queue', 'notification.payment.queue', 'revenue.payment.queue')),
               'An expected notification/revenue queue has no consumer')
    check('RabbitMQ notification/revenue consumers', consumer_check)

    def email_check():
        recipient = 'docker-smoke@example.invalid'
        # Synthetic recipient and local Mailpit only; no email is relayed externally.
        event = {'email': recipient, 'otp_code': '875421', 'event_type': 'otp_verification'}
        payload = {'properties': {'content_type': 'application/json', 'delivery_mode': 2},
                   'routing_key': 'otp.verification', 'payload': json.dumps(event), 'payload_encoding': 'string'}
        response = http_json(f'http://127.0.0.1:{rabbit_port}/api/exchanges/checkinn/user.events/publish', payload, headers)
        expect(response.get('routed') is True, 'Synthetic event was not routed')
        for _ in range(12):
            messages = http_json('http://127.0.0.1:8025/api/v1/messages')
            if any(address.get('Address') == recipient for message in messages.get('messages', []) for address in message.get('To', [])):
                return
            time.sleep(2)
        raise AssertionError('Synthetic notification was not captured in Mailpit within 24 seconds')
    check('RabbitMQ -> notification -> SMTP -> local Mailpit', email_check)

    folder = ROOT / 'logs'
    folder.mkdir(exist_ok=True)
    (folder / 'integration-results.json').write_text(json.dumps(RESULTS, indent=2) + '\n')
    raise SystemExit(1 if any(item['status'] != 'PASS' for item in RESULTS) else 0)


if __name__ == '__main__':
    main()
