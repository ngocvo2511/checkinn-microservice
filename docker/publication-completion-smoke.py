"""Complete STD-023 matrix on fresh UUID fixtures in checkinn-test; no external writes."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
import uuid

spec = importlib.util.spec_from_file_location('h', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec); spec.loader.exec_module(h)

# Windows locale decoding must not corrupt Vietnamese city names or JSON.
def utf8_run(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True, text=True, encoding='utf-8', timeout=45)
    if result.returncode: raise RuntimeError('Test stack command failed; inspect private diagnostics')
    return result.stdout.strip()
h.run = utf8_run


def main():
    for service in ['hotel-service', 'booking-service', 'revenue-service']: h.verify_running_clone(service)
    config = json.loads(h.run(h.COMPOSE + ['config', '--format', 'json']))
    if config['services']['notification-service']['environment']['SPRING_MAIL_HOST'] != 'mailpit': raise RuntimeError('Mailpit required')
    key = config['services']['hotel-service']['environment']['SECRET_KEY']
    hotel_container = h.run(h.COMPOSE + ['ps','-q','hotel-service'])
    owner, other, guest, admin = [str(uuid.uuid4()) for _ in range(4)]
    fixtures = [dict(hotel=str(uuid.uuid4()), room=str(uuid.uuid4()), inactive=str(uuid.uuid4()), booking=str(uuid.uuid4()), status=state, active=active)
                for state, active in [('APPROVED', True), ('APPROVED', False), ('DRAFT', True), ('PENDING', True), ('REJECTED', True)]]
    marker = 'Synthetic-complete-publication-' + uuid.uuid4().hex
    Path('docker/logs/publication-completion-last-fixture.json').write_text(json.dumps({'fixtures': fixtures, 'owner': owner, 'guest': guest}), encoding='utf-8')

    def check(ok, name):
        print(json.dumps({'case': name, 'result': 'PASS' if ok else 'FAIL'}), flush=True)
        if not ok: raise AssertionError('Publication completion regression')

    def api(path, actor=None, role='OWNER', direct=True, method='GET', body=None, domain='hotel', extra=''):
        base = {'hotel':'http://localhost:8083','booking':'http://booking-service:8084','revenue':'http://revenue-service:8088'}[domain] if direct else 'http://api-gateway:8080'
        prefix = '' if direct or domain != 'hotel' else '/api/hotel'
        auth = 'Authorization: Bearer ' + h.token(key, actor, role) + '\n' if actor else ''
        args = ['docker', 'exec', '-i', hotel_container, 'curl', '-sS', '--max-time', '20', '-H', '@-', '-w', '\n%{http_code}', '-X', method]
        if body is not None: args += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        r = subprocess.run(args + [base + prefix + path], input=auth + extra, capture_output=True, text=True, encoding='utf-8', timeout=30)
        if r.returncode: raise RuntimeError('HTTP transport failed')
        data, code = r.stdout.rsplit('\n', 1)
        try: data = json.loads(data) if data else None
        except ValueError: pass
        return int(code), data

    proto = Path(__file__).resolve().parent.parent / 'hotel-service/src/main/proto'
    rpc_base = ['docker', 'run', '--rm', '--network', config['networks']['checkinn']['name'], '--mount',
                'type=bind,source=' + str(proto) + ',target=/protos,readonly', 'fullstorydev/grpcurl:v1.9.3', '-plaintext',
                '-max-time', '10', '-import-path', '/protos', '-proto', 'hotel.proto']
    def rpc(method, body, review=False):
        command = rpc_base[:-1] + ['review.proto'] if review else rpc_base
        r = subprocess.run(command + ['-H', 'x-user-id: ' + owner, '-d', json.dumps(body), 'hotel-service:9092', ('review.ReviewGrpcService/' if review else 'hotel.HotelGrpcService/') + method],
                           capture_output=True, text=True, encoding='utf-8', timeout=30)
        if r.returncode == 0: return 'OK', json.loads(r.stdout) if r.stdout.strip() else {}
        code = re.search(r'Code: ([A-Za-z]+)', r.stderr)
        return code.group(1) if code else 'TRANSPORT_ERROR', None

    for attempt in range(60):
        if api('/cities', direct=False)[0] == 200 and api('/api/bookings/hotel/' + fixtures[0]['hotel'], owner, domain='booking', direct=False)[0] in (403, 503): break
        time.sleep(1)
    else: raise RuntimeError('Discovery unavailable')
    cities = [str(uuid.UUID(x)) for x in h.sql('checkinn_hotel', 'SELECT id FROM cities ORDER BY id LIMIT 2').splitlines()]
    if len(cities) != 2: raise RuntimeError('Two cloned cities required')
    city, new_city = cities
    old_fingerprint = h.sql('checkinn_hotel', "SELECT md5(string_agg(to_jsonb(h)::text,'' ORDER BY id)) FROM hotels h")
    try:
        for f in fixtures:
            address = json.dumps({'street': 'Synthetic street', 'city': 'Synthetic city', 'country': 'VN', 'latitude': 12, 'longitude': 108, 'coordinateSource': 'OWNER_PROVIDED'})
            h.sql('checkinn_hotel', "INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','" + f['hotel']
                  + "','owner_id','" + owner + "','city_id','" + city + "','name','" + marker + "','address','" + address + "'::jsonb,'is_active',"
                  + str(f['active']).lower() + ",'approved_status','" + f['status'] + "','created_at',now(),'updated_at',now()))).* FROM hotels h LIMIT 1")
            for room, active in [(f['room'], True), (f['inactive'], False)]:
                h.sql('checkinn_hotel', "INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','" + room
                      + "','hotel_id','" + f['hotel'] + "','name','Synthetic publication room','base_price',1000000,'total_rooms',3,'capacity',jsonb_build_object('adults',2,'children',0),'amenities','[]'::jsonb,'is_active',"
                      + str(active).lower() + ",'created_at',now(),'updated_at',now()))).* FROM room_types r LIMIT 1")
            h.sql('checkinn_booking', "INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,created_at,updated_at) VALUES ('"
                  + f['booking'] + "','" + guest + "','" + f['hotel'] + "','Synthetic publication',current_date-2,current_date-1,1,0,'CHECKED_OUT',1000000,1000000,'Synthetic guest','publication@example.invalid','0000000000',now(),now())")
        for f in fixtures:
            code, data = api('/api/v1/reviews', guest, 'CUSTOMER', method='POST', body={'hotelId':f['hotel'],'bookingId':f['booking'],'rating':8,'title':'Synthetic review','content':'Synthetic publication review fixture'})
            check(code == 201, 'eligible historical guest can create review independently of hotel publication')
            f['review'] = str(uuid.UUID(data['id']))
        for f in fixtures:
            published = f['active'] and f['status'] == 'APPROVED'
            label = f['status'] + (' active' if f['active'] else ' inactive')
            for direct in [True, False]:
                surface = ('direct ' if direct else 'gateway ') + label
                check(api('/api/v1/' + f['room'], direct=direct)[0] == (200 if published else 404), surface + ' room detail')
                for target, kind, visible in [(f['hotel'],'HOTEL',published),(f['room'],'ROOM_TYPE',published),(f['inactive'],'ROOM_TYPE',False)]:
                    query = '?targetId=' + target + '&targetType=' + kind
                    check(api('/medias' + query, direct=direct)[0] == (200 if visible else 404), surface + ' public media target ' + kind + (' active' if target != f['inactive'] else ' inactive'))
                    check(api('/medias/preview' + query, owner, direct=direct)[0] == 200, surface + ' own private media target ' + kind)
                    check(api('/medias/preview' + query, other, direct=direct)[0] == 403, surface + ' other owner private media denied ' + kind)
                for path in ['/api/v1/reviews/' + f['review'], '/api/v1/reviews/' + f['review'] + '/response', '/api/v1/reviews/hotel/' + f['hotel'], '/api/v1/reviews/stats/hotel/' + f['hotel']]:
                    check(api(path, direct=direct)[0] == (200 if published else 404), surface + ' public review parent visibility ' + ('list' if '/hotel/' in path else 'detail'))
                check(api('/api/v1/reviews/check-booking/' + f['booking'], guest, 'CUSTOMER', direct=direct)[0] == 200, surface + ' own review remains private-accessible')

                check(api('/api/v1/' + f['inactive'], direct=direct)[0] == 404, surface + ' inactive room hidden')
                code, rows = api('/api/v1/hotel/' + f['hotel'], direct=direct)
                check((code == 200 and len(rows) == 1 and rows[0]['id'] == f['room']) if published else code == 404, surface + ' room list')
                for room in [f['room'], f['inactive']]:
                    check(api('/api/v1/' + room + '/preview', direct=direct)[0] == 401, surface + ' anonymous room preview denied')
                    check(api('/api/v1/' + room + '/preview', owner, direct=direct)[0] == 200, surface + ' own room preview')
                    check(api('/api/v1/' + room + '/preview', admin, 'ADMIN', direct=direct)[0] == 200, surface + ' admin room preview')
                    check(api('/api/v1/' + room + '/preview', other, direct=direct, extra='X-User-Id: ' + owner + '\nX-Is-Admin: true\n')[0] == 403, surface + ' cross-owner room preview denied')
                code, rows = api('/api/v1/hotel/' + f['hotel'] + '/preview', owner, direct=direct)
                check(code == 200 and {r['id'] for r in rows} == {f['room'],f['inactive']}, surface + ' own room list keeps inactive')
                path = '/api/v1/availability/check?roomTypeId=' + f['room'] + '&checkIn=2027-05-01&checkOut=2027-05-02&quantity=1'
                code, availability = api(path, direct=direct)
                check((code == 200 and availability['availableRooms'] == 3) if published else code == 404, surface + ' availability publication')
                check(api(path.replace(f['room'], f['inactive']), direct=direct)[0] == 404, surface + ' inactive room no availability')
                if not published:
                    check(api('/api/v1/availability/hold', 'booking-service', 'BOOKING_SERVICE', direct=direct, method='POST', body={'roomTypeId':f['room'],'checkInDate':'2027-05-01','checkOutDate':'2027-05-02','quantity':1})[0] == 409, surface + ' hidden parent rejects new hold')
                check(api('/api/bookings/hotel/' + f['hotel'], owner, domain='booking', direct=direct)[0] == 200, surface + ' owner retains historical bookings')
                check(api('/api/bookings/hotel/' + f['hotel'], other, domain='booking', direct=direct)[0] == 403, surface + ' other owner cannot access bookings')
                check(api('/api/bookings/' + f['booking'], guest, 'CUSTOMER', domain='booking', direct=direct)[0] == 200, surface + ' guest retains receipt')
                report = '/api/reports/occupancy?hotelId=' + f['hotel'] + '&from=2026-10-01&to=2026-10-31'
                code, data = api(report, owner, domain='revenue', direct=direct)
                check(code == 200 and data['capacityNights'] == 186, surface + ' historical owner report retains capacity of inactive rooms')
                check(api(report, other, domain='revenue', direct=direct)[0] == 403, surface + ' unrelated owner report denied')
                for actor, role, expected in [(None,'OWNER',401),(owner,'OWNER',403),(admin,'ADMIN',403),('booking-service','BOOKING_SERVICE',403),('forged','REVENUE_SERVICE',403),('revenue-service','REVENUE_SERVICE',200)]:
                    code, data = api('/internal/reports/hotels/' + f['hotel'], actor, role, direct=direct)
                    check(code == expected and (code != 200 or (set(data) == {'id','ownerId','name','city','roomTypes'} and len(data['roomTypes']) == 2)), surface + ' report context narrow caller ' + role + (' machine' if actor == 'revenue-service' else ' other'))
                for actor, role, expected in [(owner,'OWNER',403),('booking-service','BOOKING_SERVICE',403),('revenue-service','REVENUE_SERVICE',200)]:
                    code, data = api('/internal/reports/rooms/' + f['inactive'], actor, role, direct=direct)
                    check(code == expected and (code != 200 or data == {'id':f['inactive'],'totalRooms':3}), surface + ' private historical capacity caller ' + role)
                for actor, role, expected in [(None,'OWNER',401),(owner,'OWNER',403),(admin,'ADMIN',403),('forged','BOOKING_SERVICE',403),('booking-service','BOOKING_SERVICE',200)]:
                    code, data = api('/internal/hotels/' + f['hotel'] + '/owner', actor, role, direct=direct)
                    check(code == expected and (code != 200 or data == {'ownerId':owner}), surface + ' minimal internal identity ' + role + (' machine' if actor == 'booking-service' else ' other'))
            for method, body in [('GetHotelById',{'hotelId':f['hotel']}),('GetRoomTypesByHotel',{'hotelId':f['hotel']}),('GetRoomTypeById',{'roomTypeId':f['room']})]:
                code, data = rpc(method, body)
                check(code == ('OK' if published else 'NotFound'), label + ' anonymous grpc ' + method)
                if published and method in ['GetHotelById','GetRoomTypesByHotel']:
                    check(len(data.get('roomTypes',[])) == 1 and data['roomTypes'][0]['id'] == f['room'], label + ' grpc nested rooms filter')
            check(rpc('GetRoomTypeById',{'roomTypeId':f['inactive']})[0] == 'NotFound', label + ' grpc inactive room hidden')
            check(rpc('GetReviewById',{'reviewId':f['review']},review=True)[0] == ('OK' if published else 'NotFound'), label + ' anonymous review grpc parent visibility')
        f = fixtures[0]
        code, data = api('/hotels/' + f['hotel'])
        check(code == 200 and len(data['roomTypes']) == 1, 'public hotel DTO excludes inactive rooms')
        code, data = api('/hotels/' + f['hotel'] + '/preview', owner)
        check(code == 200 and len(data['roomTypes']) == 2, 'private hotel DTO retains inactive rooms')
        for source in [None,'CITY_CENTROID']:
            address={'street':'Synthetic street','city':'Synthetic city','country':'VN','latitude':12,'longitude':108}
            if source: address['coordinateSource']=source
            h.sql('checkinn_hotel', "UPDATE hotels SET address='" + json.dumps(address) + "'::jsonb WHERE id='" + f['hotel'] + "'")
            code, data = api('/hotels/' + f['hotel'])
            check(code == 200 and data['address']['latitude'] is None and data['address']['coordinateSource'] == (source or 'LEGACY_UNVERIFIED'), 'legacy or city origin is not a hotel pin')
            code, data = rpc('GetHotelById',{'hotelId':f['hotel']})
            check(code == 'OK' and data['address']['coordinateSource'] == (source or 'LEGACY_UNVERIFIED') and not data['address'].get('latitude'), 'grpc coordinate provenance parity')
        before = h.sql('checkinn_hotel', "SELECT city_id,address FROM hotels WHERE id='"+f['hotel']+"'")
        check(api('/hotels/' + f['hotel'], owner, method='PUT', body={'cityId':new_city})[0] == 400 and before == h.sql('checkinn_hotel', "SELECT city_id,address FROM hotels WHERE id='"+f['hotel']+"'"), 'city-only update rejected without partial mutation')
        code, data = api('/hotels/' + f['hotel'], owner, method='PUT', body={'cityId':new_city,'address':{'street':'New synthetic street','city':'stale text','country':'VN'}})
        city_name = h.sql('checkinn_hotel', "SELECT name FROM cities WHERE id='"+new_city+"'")
        check(code == 200 and data['cityId'] == new_city and data['address']['city'] == city_name and data['address']['latitude'] is None and data['address']['coordinateSource'] == 'UNKNOWN', 'city and address resolve together without centroid fallback')
        code, data = api('/hotels/' + f['hotel'], owner, method='PUT', body={'address':{'street':'New synthetic street','city':city_name,'country':'VN','latitude':12.3,'longitude':108.3,'coordinateSource':'OWNER_PROVIDED'}})
        check(code == 200 and data['address']['latitude'] == 12.3 and data['address']['coordinateSource'] == 'OWNER_PROVIDED', 'owner confirms actual hotel coordinates')
        code, held = api('/api/v1/availability/hold', 'booking-service', 'BOOKING_SERVICE', method='POST', body={'roomTypeId':f['room'],'checkInDate':'2027-05-01','checkOutDate':'2027-05-02','quantity':1})
        check(code == 200, 'published room can create hold')
        hold = str(uuid.UUID(held['holdId']))
        h.sql('checkinn_hotel', "UPDATE hotels SET is_active=false WHERE id='"+f['hotel']+"'")
        check(api('/api/v1/availability/hold/' + hold + '/confirm', 'booking-service','BOOKING_SERVICE',method='POST')[0] == 200, 'existing accepted hold can finalize after hotel unpublication')
        check(api('/api/v1/availability/hold/' + hold + '/cancel', 'booking-service','BOOKING_SERVICE',method='POST')[0] == 200, 'existing reservation can cancel after hotel unpublication')
        check(api('/api/v1/availability/hold/' + hold + '/cancel', 'booking-service','BOOKING_SERVICE',method='POST')[0] == 200, 'unpublished cancellation replay remains idempotent')
        check(h.sql('checkinn_hotel',"SELECT held,booked FROM room_availability WHERE room_type_id='"+f['room']+"'") == '0|0', 'reservation counters return to zero')
    finally:
        rooms = "','".join([f[k] for f in fixtures for k in ['room','inactive']])
        hotels = "','".join(f['hotel'] for f in fixtures)
        h.sql('checkinn_hotel', "BEGIN; DELETE FROM review_responses WHERE review_id IN (SELECT id FROM hotel_reviews WHERE hotel_id IN ('"+hotels+"')); DELETE FROM review_feedbacks WHERE review_id IN (SELECT id FROM hotel_reviews WHERE hotel_id IN ('"+hotels+"')); DELETE FROM hotel_reviews WHERE hotel_id IN ('"+hotels+"') AND guest_id='"+guest+"'; DELETE FROM room_holds WHERE room_type_id IN ('"+rooms+"'); DELETE FROM room_availability WHERE room_type_id IN ('"+rooms+"'); DELETE FROM room_types WHERE id IN ('"+rooms+"'); DELETE FROM hotels WHERE id IN ('"+hotels+"') AND owner_id='"+owner+"'; COMMIT;")
        h.sql('checkinn_booking', "DELETE FROM bookings WHERE id IN ('"+"','".join(f['booking'] for f in fixtures)+"') AND user_id='"+guest+"'")
    check(h.sql('checkinn_hotel', "SELECT count(*) FROM hotels WHERE name='"+marker+"'") == '0', 'synthetic fixture cleanup')
    check(old_fingerprint == h.sql('checkinn_hotel', "SELECT md5(string_agg(to_jsonb(h)::text,'' ORDER BY id)) FROM hotels h"), 'existing hotel data fingerprint unchanged')


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print(json.dumps({'result':'ERROR','type':type(error).__name__}),flush=True)
        raise SystemExit(1)
