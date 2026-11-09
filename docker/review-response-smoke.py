"""Private review history and owner reply races; generated fixtures in checkinn-test only."""
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import uuid

spec = importlib.util.spec_from_file_location('security', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    h.verify_running_clone('hotel-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel, owner, guest, other = [str(uuid.uuid4()) for _ in range(4)]
    reviews, bookings = [[str(uuid.uuid4()) for _ in range(4)] for _ in range(2)]
    count = 0

    def check(condition, label):
        nonlocal count
        print(json.dumps({'case': label, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
        if not condition:
            raise AssertionError('Review response regression')
        count += 1

    def api(method, path, actor=None, role='CUSTOMER', body=None, headers=''):
        args = h.COMPOSE + ['exec', '-T', 'hotel-service', 'curl', '-sS', '--max-time', '20',
                            '-X', method, '-H', '@-', '-w', '\n%{http_code}']
        if body is not None:
            args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        auth = 'Authorization: Bearer ' + h.token(key, actor, role) + '\n' if actor else ''
        raw = subprocess.run(args + ['http://localhost:8083/api/v1/reviews' + path], input=auth + headers,
                             capture_output=True, text=True, encoding='utf-8', timeout=30)
        if raw.returncode:
            raise RuntimeError('Test HTTP transport failed')
        data, code = raw.stdout.rsplit('\n', 1)
        return int(code), json.loads(data) if data else None

    proto = Path(__file__).resolve().parent.parent / 'hotel-service/src/main/proto'
    rpc_base = ['docker', 'run', '--rm', '--network', config['networks']['checkinn']['name'], '--mount',
                'type=bind,source=' + str(proto) + ',target=/protos,readonly', 'fullstorydev/grpcurl:v1.9.3',
                '-plaintext', '-max-time', '10', '-import-path', '/protos', '-proto', 'review.proto']

    def rpc(method, body, actor=None, role='CUSTOMER'):
        auth = ['-H', 'authorization: Bearer ' + h.token(key, actor, role)] if actor else []
        result = subprocess.run(rpc_base + auth + ['-d', json.dumps(body), 'hotel-service:9092',
                                'review.ReviewGrpcService/' + method], capture_output=True,
                                text=True, encoding='utf-8', timeout=30)
        if result.returncode == 0:
            return 'OK'
        code = re.search(r'Code: ([A-Za-z]+)', result.stderr)
        return code.group(1) if code else 'TRANSPORT_ERROR'

    orphan_sql = ("SELECT (SELECT count(*) FROM review_responses r LEFT JOIN hotel_reviews h ON h.id=r.review_id WHERE h.id IS NULL)"
                  "||':'||(SELECT count(*) FROM review_feedbacks r LEFT JOIN hotel_reviews h ON h.id=r.review_id WHERE h.id IS NULL)")
    before = h.sql('checkinn_hotel', orphan_sql)
    try:
        created = h.sql('checkinn_hotel', "INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"
                        + hotel + "','owner_id','" + owner + "','name','Synthetic private review hotel','is_active',true,"
                        "'approved_status','APPROVED','created_at',now(),'updated_at',now()))).* FROM hotels h LIMIT 1 RETURNING id")
        check(created.splitlines()[0] == hotel, 'isolated synthetic hotel created')
        for i, status in enumerate(['PUBLISHED', 'PENDING', 'REJECTED', 'PUBLISHED']):
            author = other if i == 3 else guest
            h.sql('checkinn_hotel', "INSERT INTO hotel_reviews(id,hotel_id,guest_id,booking_id,rating,title,content,helpful_count,unhelpful_count,status,created_at,updated_at) VALUES ('"
                  + reviews[i] + "','" + hotel + "','" + author + "','" + bookings[i]
                  + "',8,'Synthetic private review','Synthetic review content',0,0,'" + status + "',now(),now())")
        check(api('GET', '/me', headers='X-User-Id: ' + guest + '\n')[0] == 401, 'anonymous private history denied despite forged header')
        code, first = api('GET', '/me?page=0&size=2&userId=' + other, guest, headers='X-User-Id: ' + other + '\n')
        check(code == 200 and first['totalElements'] == 3 and first['totalPages'] == 2, 'JWT scoped history counts and pagination')
        code, last = api('GET', '/me?page=1&size=2', guest)
        all_rows = first['content'] + last['content']
        check(code == 200 and len({r['id'] for r in all_rows}) == 3 and all(r['guestId'] == guest for r in all_rows), 'no foreign guest rows or duplicate pages')
        check({r['status'] for r in all_rows} == {'PUBLISHED', 'PENDING', 'REJECTED'}, 'own moderated history remains available')
        check(api('GET', '/me', owner, 'OWNER')[1]['totalElements'] == 0, 'empty private history is a genuine successful page')
        check(api('GET', '/' + reviews[1])[0] == 404, 'pending review remains hidden publicly')
        check(api('GET', '/check-booking/' + bookings[0] + '?userId=' + guest, other)[0] == 404, 'booking lookup cannot impersonate another guest')
        check(api('POST', '/' + reviews[0] + '/response', other, 'OWNER', {'content': 'Synthetic response'})[0] == 403, 'unrelated hotel owner denied')
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            replies = list(pool.map(lambda _: api('POST', '/' + reviews[0] + '/response', owner, 'OWNER',
                                                  {'content': 'Concurrent synthetic response'}), range(8)))
        check(sorted(code for code, _ in replies) == [201] + [409] * 7, 'eight real HTTP requests produce one reply and seven conflicts')
        reply = next(data for code, data in replies if code == 201)
        reply_path = '/response/' + reply['id']
        check(api('PUT', reply_path, other, 'OWNER', {'content': 'Synthetic update'})[0] == 403, 'foreign reply update denied')
        check(api('PUT', reply_path, owner, 'OWNER', {'content': 'Synthetic updated response'})[0] == 200, 'current hotel owner can update own reply')
        rows = api('GET', '/me?size=10', guest)[1]['content']
        check(next(r for r in rows if r['id'] == reviews[0])['ownerResponse']['content'] == 'Synthetic updated response', 'private history includes persisted owner response')
        check(rpc('GetReviewById', {'reviewId': 'invalid-synthetic-id'}) == 'InvalidArgument', 'native RPC malformed identifier is invalid argument')
        check(rpc('CreateReview', {'hotelId': hotel, 'bookingId': bookings[0], 'rating': 12, 'title': 'Synthetic review', 'content': 'Synthetic review content'}, guest) == 'InvalidArgument', 'native RPC validates rating before booking dependency')
        check(rpc('AddReviewResponse', {'reviewId': reviews[0], 'content': ' '}, owner, 'OWNER') == 'InvalidArgument', 'native RPC rejects blank reply')
        check(rpc('DeleteReview', {'reviewId': reviews[0]}, other) == 'PermissionDenied', 'native RPC preserves review ownership')
        check(rpc('GetReviewById', {'reviewId': reviews[0]}) == 'OK', 'native anonymous published read still works')
        check(api('POST', '/' + reviews[0] + '/helpful', other)[0] == 200, 'authenticated vote fixture accepted')
        check(api('DELETE', '/' + reviews[0], guest)[0] == 204, 'review author can delete own review')
        counts = h.sql('checkinn_hotel', "SELECT (SELECT count(*) FROM review_responses WHERE review_id='" + reviews[0]
                       + "')||':'||(SELECT count(*) FROM review_feedbacks WHERE review_id='" + reviews[0] + "')")
        check(counts.splitlines()[0] == '0:0', 'authorized parent deletion cascades only its replies and votes')
        check(api('GET', '/me', guest)[1]['totalElements'] == 2, 'history count reflects authorized deletion')
        check(h.sql('checkinn_hotel', orphan_sql) == before, 'historical orphan counts unchanged')
        print(json.dumps({'passed': count}), flush=True)
    finally:
        ids = ','.join("'" + value + "'" for value in reviews)
        h.sql('checkinn_hotel', 'BEGIN; DELETE FROM review_responses WHERE review_id IN (' + ids
              + '); DELETE FROM review_feedbacks WHERE review_id IN (' + ids + '); DELETE FROM hotel_reviews WHERE id IN ('
              + ids + "); DELETE FROM hotels WHERE id='" + hotel + "' AND owner_id='" + owner + "'; COMMIT;")


if __name__ == '__main__':
    main()
