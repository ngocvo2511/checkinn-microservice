"""Concurrent HTTP transactions against one fresh room in the PostgreSQL clone.

No existing inventory is changed. Only rows with the generated fixture UUID are
removed. Internal inventory credentials are minted in memory and never printed.
"""
import concurrent.futures
import importlib.util
import json
import time
from pathlib import Path
import uuid

spec = importlib.util.spec_from_file_location('inventory_smoke', Path(__file__).with_name('inventory-smoke.py'))
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)
h = inventory.helpers


def check(condition, name):
    print(json.dumps({'case': name, 'result': 'PASS' if condition else 'FAIL'}), flush=True)
    if not condition:
        raise AssertionError('Inventory invariant violated')


def main():
    h.verify_running_clone('hotel-service')
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    inventory.SERVICE_CREDENTIAL = h.token(config['services']['hotel-service']['environment']['SECRET_KEY'], 'booking-service', 'BOOKING_SERVICE')
    room = str(uuid.uuid4())
    created = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types, to_jsonb(r) || jsonb_build_object('id','" + room + "','total_rooms',1,'name','concurrency-regression-" + room + "'))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM fixture")
    if created != '1':
        raise RuntimeError('No cloned room fixture')
    def counts():
        return h.sql('checkinn_hotel', "SELECT held,booked FROM room_availability WHERE room_type_id='" + room + "' AND date='2027-01-10'")
    try:
        payload = {'roomTypeId': room, 'checkInDate': '2027-01-10', 'checkOutDate': '2027-01-11', 'quantity': 1}
        service_credential = inventory.SERVICE_CREDENTIAL
        inventory.SERVICE_CREDENTIAL = None
        code, _ = inventory.request('hold', payload)
        check(code == 401, 'anonymous clients cannot create inventory holds directly')
        inventory.SERVICE_CREDENTIAL = h.token(config['services']['hotel-service']['environment']['SECRET_KEY'], str(uuid.uuid4()), 'ADMIN')
        code, _ = inventory.request('hold', payload)
        check(code == 403, 'user ADMIN token cannot impersonate booking-service inventory identity')
        inventory.SERVICE_CREDENTIAL = service_credential
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: inventory.request('hold', payload), range(4)))
        check(sum(code == 200 for code, _ in results) == 1 and all(code in [200, 400] for code, _ in results),
              'four competing requests for the last room have exactly one winner')
        check(counts() == '1|0', 'held and booked counters after first-row concurrency')
        hold = next(body['holdId'] for code, body in results if code == 200)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            confirmed = list(pool.map(lambda _: inventory.request('hold/' + hold + '/confirm', {}), range(2)))
        check(all(code == 200 for code, _ in confirmed) and counts() == '0|1', 'concurrent confirm replay books exactly once')
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            cancelled = list(pool.map(lambda _: inventory.request('hold/' + hold + '/cancel', {}), range(2)))
        check(all(code == 200 for code, _ in cancelled) and counts() == '0|0', 'concurrent cancellation restores booked inventory exactly once')
        code, held = inventory.request('hold', payload)
        check(code == 200, 'room can be held again after cancellation')
        hold = held['holdId']
        h.sql('checkinn_hotel', "UPDATE room_holds SET expires_at = now() - interval '1 minute' WHERE id='" + str(uuid.UUID(hold)) + "' AND room_type_id='" + room + "'")
        code, _ = inventory.request('hold/' + hold + '/confirm', {})
        check(code == 409 and counts().split('|')[1] == '0', 'expired hold cannot confirm before or during cleanup')
        for _ in range(70):
            if counts() == '0|0':
                break
            time.sleep(1)
        check(counts() == '0|0', 'scheduled cleanup releases expired inventory in a separate transaction')
        code, held = inventory.request('hold', payload)
        check(code == 200, 'inventory remains reusable after scheduled expiration')
        hold = held['holdId']
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            confirming = pool.submit(inventory.request, 'hold/' + hold + '/confirm', {})
            releasing = pool.submit(inventory.request, 'hold/' + hold + '/release', {})
            codes = [confirming.result()[0], releasing.result()[0]]
        race_counts = counts()
        if sorted(codes) != [200, 409] or race_counts not in ['0|0', '0|1']:
            print(json.dumps({'diagnostic': 'confirm_release', 'http_statuses': codes, 'held_booked': race_counts}), flush=True)
        check(sorted(codes) == [200, 409] and race_counts in ['0|0', '0|1'], 'confirm-versus-release race has one legal transition without counter corruption')
    finally:
        h.sql('checkinn_hotel', "BEGIN; DELETE FROM room_holds WHERE room_type_id='" + room + "'; DELETE FROM room_availability WHERE room_type_id='" + room + "'; DELETE FROM room_types WHERE id='" + room + "'; COMMIT;")
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result': 'ERROR', 'type': type(error).__name__}))
        raise SystemExit(1)
