"""Review authorization on fresh fixtures in the isolated clone only.

Never prints tokens, identifiers, response bodies or copied hotel data.
Cleanup is restricted to generated review UUIDs; existing reviews are untouched.
"""
import importlib.util
import json
from pathlib import Path
import uuid
import time
import subprocess

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)


def main():
    helpers.verify_running_clone('hotel-service')
    config = json.loads(helpers.run(helpers.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    # Discovery may lag a healthy service after starting the stack. Use a public
    # read as readiness probe before running any fixture mutation assertions.
    for attempt in range(30):
        ready = helpers.run(helpers.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '5',
                         '-o', '/dev/null', '-w', '%{http_code}', 'http://api-gateway:8080/api/hotel/cities'])
        if ready == '200':
            break
        time.sleep(2)
    else:
        raise RuntimeError('NEEDS_ENVIRONMENT: gateway discovery not ready')

    row = helpers.sql('checkinn_hotel', 'SELECT id, owner_id FROM hotels WHERE owner_id IS NOT NULL LIMIT 1')
    if not row:
        raise RuntimeError('NEEDS_ENVIRONMENT: cloned hotel owner fixture required')
    hotel, owner = row.split('|')
    uuid.UUID(hotel); uuid.UUID(owner)
    guest, intruder = str(uuid.uuid4()), str(uuid.uuid4())
    other_token = helpers.token(key, intruder, 'OWNER')
    owner_token = helpers.token(key, owner, 'OWNER')
    failures = 0
    for direct in [True, False]:
        review = str(uuid.uuid4())
        if helpers.sql('checkinn_hotel', "SELECT count(*) FROM hotel_reviews WHERE id='" + review + "'") != '0':
            raise RuntimeError('Refusing to use existing review')
        helpers.sql('checkinn_hotel', "INSERT INTO hotel_reviews(id,hotel_id,guest_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES ('"
                    + review + "','" + hotel + "','" + guest + "',8,'Synthetic security review','Synthetic local regression fixture',0,0,'PUBLISHED',now(),now())")
        try:
            path = '/api/v1/reviews/' + review
            cases = [
                ('anonymous forged admin delete', 'DELETE', path, None, None, 401),
                ('other user forged author/admin delete', 'DELETE', path, other_token, None, 403),
                ('anonymous forged vote', 'POST', path + '/helpful', None, None, 401),
                ('authenticated vote', 'POST', path + '/helpful', other_token, None, 200),
                ('unrelated owner response', 'POST', path + '/response', other_token, {'content': 'Synthetic owner response'}, 403),
                ('actual hotel owner response', 'POST', path + '/response', owner_token, {'content': 'Synthetic owner response'}, 201),
            ]
            for name, method, endpoint, credential, body, expected in cases:
                url = ('http://localhost:8083' if direct else 'http://api-gateway:8080/api/hotel') + endpoint
                headers = 'X-User-Id: ' + guest + '\nX-Is-Admin: true\n'
                if credential:
                    headers += 'Authorization: Bearer ' + credential + '\n'
                command = helpers.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20',
                                             '-X', method, '-H', '@-', '-o', '/dev/null', '-w', '%{http_code}']
                if body:
                    command += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
                actual = int(helpers.run(command + [url], headers))
                passed = actual == expected
                failures += not passed
                print(json.dumps({'surface': 'service' if direct else 'gateway', 'case': name,
                                  'expected': expected, 'actual': actual, 'result': 'PASS' if passed else 'FAIL'}), flush=True)
            # Verify forged identities did not create feedback for the header's user.
            count = helpers.sql('checkinn_hotel', "SELECT count(*) FROM review_feedbacks WHERE review_id='" + review + "' AND user_id='" + intruder + "'")
            forged_count = helpers.sql('checkinn_hotel', "SELECT count(*) FROM review_feedbacks WHERE review_id='" + review + "' AND user_id='" + guest + "'")
            passed = count == '1' and forged_count == '0'
            failures += not passed
            print(json.dumps({'surface': 'service' if direct else 'gateway', 'case': 'single authenticated feedback row',
                              'result': 'PASS' if passed else 'FAIL'}), flush=True)
        finally:
            helpers.sql('checkinn_hotel', "DELETE FROM review_feedbacks WHERE review_id='" + review + "'; DELETE FROM review_responses WHERE review_id='" + review + "'; DELETE FROM hotel_reviews WHERE id='" + review + "'")
    proto = Path(__file__).resolve().parent.parent / 'hotel-service/src/main/proto'
    network = config['networks']['checkinn']['name']
    for forged in [False, True]:
        command = ['docker', 'run', '--rm', '--network', network, '--mount',
                   'type=bind,source=' + str(proto) + ',target=/protos,readonly',
                   'fullstorydev/grpcurl:v1.9.3', '-plaintext', '-max-time', '20',
                   '-import-path', '/protos', '-proto', 'review.proto']
        if forged:
            command += ['-H', 'x-user-id: ' + guest]
        result = subprocess.run(command + ['-d', json.dumps({'reviewId': str(uuid.uuid4()), 'userId': guest, 'isAdmin': True}),
                                           'hotel-service:9092', 'review.ReviewGrpcService/DeleteReview'],
                                capture_output=True, text=True, timeout=45)
        passed = result.returncode != 0 and 'Unauthenticated' in result.stderr
        failures += not passed
        print(json.dumps({'surface': 'grpc', 'case': 'forged metadata/body' if forged else 'anonymous forged admin body',
                          'result': 'PASS' if passed else 'FAIL'}), flush=True)
    return 1 if failures else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
