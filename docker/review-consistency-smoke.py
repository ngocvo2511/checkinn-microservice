"""Review races and moderation visibility on generated fixtures in checkinn-test only."""
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    for service in ['hotel-service', 'booking-service']:
        h.verify_running_clone(service)
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel, owner, guest, other = [str(uuid.uuid4()) for _ in range(4)]
    bookings = [str(uuid.uuid4()), str(uuid.uuid4())]
    trigger = 'review_race_' + uuid.uuid4().hex[:16]
    trigger_created = False
    manifest = Path(__file__).parent / 'logs' / 'review-consistency-last-fixture.json'
    manifest.parent.mkdir(exist_ok=True)
    manifest.write_text(json.dumps({'hotel': hotel, 'owner': owner, 'guest': guest, 'bookings': bookings}), encoding='utf-8')

    def check(condition, name):
        print(json.dumps({'case': name, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
        if not condition: raise AssertionError('Review consistency regression')

    def api(method, path, actor=None, role='CUSTOMER', body=None, direct=True, headers=None):
        base = 'http://localhost:8083' if direct else 'http://api-gateway:8080/api/hotel'
        args = h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20', '-X', method, '-H', '@-', '-w', '\n%{http_code}']
        if body is not None: args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        auth = 'Authorization: Bearer ' + h.token(key, actor, role) + '\n' if actor else ''
        raw = subprocess.run(args + [base + '/api/v1/reviews' + path], input=auth + (headers or ''), capture_output=True,
                             text=True, encoding='utf-8', timeout=30)
        if raw.returncode: raise RuntimeError('Test HTTP transport failed')
        data, code = raw.stdout.rsplit('\n', 1)
        return int(code), json.loads(data) if data else None

    proto = Path(__file__).resolve().parent.parent / 'hotel-service/src/main/proto'
    rpc_base = ['docker', 'run', '--rm', '--network', config['networks']['checkinn']['name'], '--mount',
                'type=bind,source=' + str(proto) + ',target=/protos,readonly', 'fullstorydev/grpcurl:v1.9.3', '-plaintext',
                '-max-time', '10', '-import-path', '/protos', '-proto', 'review.proto']

    def rpc(method, body, actor=None):
        auth = ['-H', 'authorization: Bearer ' + h.token(key, actor, 'CUSTOMER')] if actor else []
        result = subprocess.run(rpc_base + auth + ['-d', json.dumps(body), 'hotel-service:9092', 'review.ReviewGrpcService/' + method],
                                capture_output=True, text=True, encoding='utf-8', timeout=30)
        if result.returncode == 0: return 'OK', json.loads(result.stdout) if result.stdout.strip() else {}
        code = re.search(r'Code: ([A-Za-z]+)', result.stderr)
        return code.group(1) if code else 'TRANSPORT_ERROR', None

    def payload(booking):
        return {'hotelId': hotel, 'bookingId': booking, 'rating': 8, 'title': 'Synthetic review', 'content': 'Synthetic review consistency fixture'}

    def wait_for(predicate, seconds=60):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate(): return True
            time.sleep(0.5)
        return False

    # Readiness is a non-mutating booking-eligibility rejection on a missing UUID.
    check(wait_for(lambda: all(api('POST', '', guest, body=payload(str(uuid.uuid4())), direct=direct)[0] == 403
                               for direct in [True, False])), 'review eligibility and gateway discovery ready')
    try:
        created = h.sql('checkinn_hotel', "INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"
                        + hotel + "','owner_id','" + owner + "','name','Synthetic review consistency " + hotel
                        + "','is_active',true,'approved_status','APPROVED','created_at',now(),'updated_at',now()))).* FROM hotels h LIMIT 1 RETURNING id")
        if not created or created.splitlines()[0] != hotel: raise RuntimeError('Synthetic hotel fixture unavailable')
        for booking in bookings:
            h.sql('checkinn_booking', "INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,created_at,updated_at) VALUES ('"
                  + booking + "','" + guest + "','" + hotel + "','Synthetic review consistency',current_date-2,current_date-1,1,0,'CHECKED_OUT',1000000,1000000,'Synthetic guest','review@example.invalid','0000000000',now(),now())")
        h.sql('checkinn_hotel', "BEGIN; CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.guest_id='" + guest
              + "'::uuid THEN PERFORM pg_sleep(0.35); END IF; RETURN NEW; END $$; CREATE TRIGGER " + trigger
              + " BEFORE INSERT ON hotel_reviews FOR EACH ROW EXECUTE FUNCTION " + trigger + "(); COMMIT;")
        trigger_created = True
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda n: api('POST', '', guest, body=payload(bookings[0]), direct=n % 2 == 0), range(8)))
        check(sorted(code for code, _ in results) == [201] + [400] * 7, 'eight concurrent REST creates return one success and controlled duplicates')
        check(all('insert into' not in str(body).lower() and 'hotel_reviews' not in str(body).lower() for code, body in results if code != 201),
              'duplicate errors expose no SQL or constraint details')
        review = str(uuid.UUID(next(body['id'] for code, body in results if code == 201)))
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            calls = [lambda n=n: api('POST', '', guest, body=payload(bookings[1]), direct=n % 2 == 0) for n in range(4)]
            calls += [lambda: rpc('CreateReview', {**payload(bookings[1]), 'guestId': other}, guest) for _ in range(4)]
            mixed = list(pool.map(lambda call: call(), calls))
        statuses = [code for code, _ in mixed]
        check(sum(code in (201, 'OK') for code in statuses) == 1 and all(code in (201, 400, 'OK', 'AlreadyExists') for code in statuses),
              'mixed REST and gRPC creation shares the database uniqueness boundary')
        check(h.sql('checkinn_hotel', "SELECT count(*) FROM hotel_reviews WHERE hotel_id='" + hotel + "' AND guest_id='" + guest + "'") == '2',
              'exactly one review per eligible booking persists')
        code, response = api('POST', '/' + review + '/response', owner, 'OWNER', {'content': 'Synthetic owner response for private parent visibility'})
        check(code == 201 and response is not None, 'legitimate owner response creates')
        for status in ['PUBLISHED', 'PENDING', 'REJECTED']:
            h.sql('checkinn_hotel', "UPDATE hotel_reviews SET status='" + status + "' WHERE hotel_id='" + hotel + "'")
            for direct in [True, False]:
                label = ('direct' if direct else 'gateway') + ' ' + status
                expected = 200 if status == 'PUBLISHED' else 404
                check(api('GET', '/' + review, direct=direct)[0] == expected, label + ' anonymous detail visibility')
                check(api('GET', '/' + review + '/response', direct=direct)[0] == expected, label + ' parent response visibility')
                code, page = api('GET', '/hotel/' + hotel, direct=direct)
                check(code == 200 and int(page['totalElements']) == (2 if status == 'PUBLISHED' else 0), label + ' public list filters moderation')
                code, stats = api('GET', '/stats/hotel/' + hotel, direct=direct)
                check(code == 200 and int(stats['totalReviews']) == (2 if status == 'PUBLISHED' else 0), label + ' public statistics filter moderation')
                code, own = api('GET', '/check-booking/' + bookings[0], guest, direct=direct)
                check(code == 200 and own['status'] == status, label + ' author private lookup retains own review')
                check(api('GET', '/check-booking/' + bookings[0], other, direct=direct, headers='X-User-Id: ' + guest + '\n')[0] == 404,
                      label + ' private lookup ignores forged guest header')
                if status != 'PUBLISHED':
                    check(api('POST', '/' + review + '/helpful', other, direct=direct)[0] == 404, label + ' hidden review rejects public votes')
            expected_rpc = 'OK' if status == 'PUBLISHED' else 'NotFound'
            check(rpc('GetReviewById', {'reviewId': review})[0] == expected_rpc, status + ' anonymous RPC detail parity')
            check(rpc('GetReviewResponse', {'reviewId': review})[0] == expected_rpc, status + ' anonymous RPC response parity')
        check(api('GET', '/check-booking/' + bookings[0])[0] == 401, 'private lookup requires authentication')
        code, private = api('PUT', '/' + review, guest, body={'title': 'Edited private review title'})
        check(code == 200 and private['status'] == 'REJECTED', 'author content edit does not publish rejected review')
        check(api('PUT', '/' + review, other, body={'title': 'Forged edit attempt'})[0] == 403, 'other guest cannot edit private review')
        check(rpc('CheckUserReviewExists', {'hotelId': hotel, 'bookingId': str(uuid.uuid4()), 'guestId': guest}, guest)[1].get('exists', False) is False,
              'RPC supplied booking does not match another stay')
        check(rpc('CheckUserReviewExists', {'hotelId': hotel, 'bookingId': bookings[0], 'guestId': other}, guest)[1].get('exists', False) is True,
              'RPC private lookup uses signed guest and includes rejected review')
        check(rpc('CheckUserReviewExists', {'hotelId': hotel, 'bookingId': bookings[0], 'guestId': guest}, other)[1].get('exists', False) is False,
              'RPC private lookup ignores forged body guest')
        # Hold the row, start a vote, moderate, then release. The waiter must read the new status.
        h.sql('checkinn_hotel', "UPDATE hotel_reviews SET status='PUBLISHED' WHERE id='" + review + "'")
        before = h.sql('checkinn_hotel', "SELECT helpful_count,unhelpful_count FROM hotel_reviews WHERE id='" + review + "'")
        lock = subprocess.Popen(h.COMPOSE + ['exec', '-T', 'postgres-test', 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                                             '-U', 'checkinn_test', '-d', 'checkinn_hotel'], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, text=True, encoding='utf-8')
        try:
            lock.stdin.write("BEGIN; SELECT id FROM hotel_reviews WHERE id='" + review + "' FOR UPDATE; SELECT 'READY';\n")
            lock.stdin.flush()
            while lock.stdout.readline().strip() != 'READY':
                if lock.poll() is not None: raise RuntimeError('Synthetic row lock unavailable')
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                vote = pool.submit(lambda: api('POST', '/' + review + '/helpful', other))
                waiting = wait_for(lambda: int(h.sql('checkinn_hotel', "SELECT count(*) FROM pg_stat_activity WHERE datname='checkinn_hotel' AND wait_event_type='Lock' AND query ILIKE '%hotel_reviews%'")) > 0, 10)
                lock.communicate("UPDATE hotel_reviews SET status='REJECTED' WHERE id='" + review + "'; COMMIT;\n", timeout=15)
                check(waiting and vote.result()[0] == 404 and h.sql('checkinn_hotel', "SELECT status FROM hotel_reviews WHERE id='" + review + "'") == 'REJECTED'
                      and h.sql('checkinn_hotel', "SELECT helpful_count,unhelpful_count FROM hotel_reviews WHERE id='" + review + "'") == before,
                      'waiting vote observes moderation commit and cannot republish or change counters')
        finally:
            if lock.poll() is None: lock.communicate('ROLLBACK;\n', timeout=15)
    finally:
        if trigger_created:
            h.sql('checkinn_hotel', 'BEGIN; DROP TRIGGER IF EXISTS ' + trigger + ' ON hotel_reviews; DROP FUNCTION IF EXISTS ' + trigger + '(); COMMIT;')
        review_ids = "SELECT id FROM hotel_reviews WHERE hotel_id='" + hotel + "' AND guest_id='" + guest + "'"
        h.sql('checkinn_hotel', 'BEGIN; DELETE FROM review_responses WHERE review_id IN (' + review_ids + '); DELETE FROM review_feedbacks WHERE review_id IN ('
              + review_ids + '); DELETE FROM hotel_reviews WHERE hotel_id=\'' + hotel + "' AND guest_id='" + guest + "'; DELETE FROM hotels WHERE id='" + hotel + "'; COMMIT;")
        for booking in bookings:
            h.sql('checkinn_booking', "DELETE FROM bookings WHERE id='" + booking + "' AND user_id='" + guest + "'")
    check(h.sql('checkinn_hotel', "SELECT count(*) FROM hotel_reviews WHERE hotel_id='" + hotel + "'") == '0', 'synthetic review cleanup')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
