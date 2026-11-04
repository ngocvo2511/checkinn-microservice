"""Same-room search regressions on fresh fixtures in the existing isolated clone."""
import importlib.util
import json
from pathlib import Path
import subprocess
import uuid
from urllib.parse import urlencode

spec = importlib.util.spec_from_file_location('h', Path(__file__).with_name('booking-security-smoke.py'))
h = importlib.util.module_from_spec(spec); spec.loader.exec_module(h)
def utf8_run(args, data=None):
    result = subprocess.run(args, input=data, capture_output=True, text=True, encoding='utf-8', timeout=45)
    if result.returncode: raise RuntimeError('Isolated search test command failed')
    return result.stdout.strip()
h.run = utf8_run

def main():
    h.verify_running_clone('hotel-service')
    hotel, owner = str(uuid.uuid4()), str(uuid.uuid4())
    rooms = [str(uuid.uuid4()) for _ in range(3)]
    marker = 'search-regression-' + hotel
    container = h.run(h.COMPOSE + ['ps','-q','hotel-service'])
    city = str(uuid.UUID(h.sql('checkinn_hotel','SELECT id FROM cities ORDER BY id LIMIT 1')))
    def check(value, name):
        print(json.dumps({'case':name,'result':'PASS' if value else 'FAIL'}),flush=True)
        if not value: raise AssertionError(name)
    def request(params, direct=True):
        base = 'http://localhost:8083/hotels/offers' if direct else 'http://api-gateway:8080/api/hotel/hotels/offers'
        result = h.run(['docker','exec',container,'curl','-sS','--max-time','20','-w','\n%{http_code}',base+'?'+urlencode(params)])
        body, code = result.rsplit('\n',1)
        return int(code), json.loads(body) if body else None
    def search(**extra):
        return request(dict(hotelName=marker,checkIn='2027-01-10',checkOut='2027-01-12',**extra))
    try:
        created = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','city_id','"+city+"','name','"+marker+"','is_active',true,'approved_status','APPROVED','created_at',now(),'updated_at',now()))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM fixture")
        check(created == '1','fresh hotel fixture created in verified clone')
        for room, price, adults, children, total in [(rooms[0],200000,2,0,10),(rooms[1],100000,2,0,2),(rooms[2],300000,4,2,2)]:
            created = h.sql('checkinn_hotel', "WITH fixture AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic offer room','base_price',"+str(price)+",'total_rooms',"+str(total)+",'capacity',jsonb_build_object('adults',"+str(adults)+",'children',"+str(children)+"),'amenities','[]'::jsonb,'is_active',true,'created_at',now(),'updated_at',now()))).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM fixture")
            check(created == '1','fresh room fixture created')
        h.sql('checkinn_hotel',"INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+rooms[1]+"','2027-01-11',0,2,now(),now())")
        code, result = search(rooms=1,adults=4,children=0,maxPrice=200000)
        check(code == 200 and result == [],'one requested room cannot borrow capacity from ten physical rooms')
        code, result = search(rooms=2,adults=4,children=0,minPrice=200000,maxPrice=200000)
        offer = result[0]['offer'] if code == 200 and len(result) == 1 else {}
        check(offer.get('roomTypeId') == rooms[0] and offer.get('quantity') == 2 and offer.get('totalPerNight') == 400000,'two rooms of same type satisfy capacity and bind price')
        code, result = search(rooms=1,adults=2,children=0)
        check(code == 200 and result[0]['offer']['unitPrice'] == 200000,'cheapest room sold out on one stay night cannot set offer price')
        code, result = search(rooms=1,adults=4,children=2,minPrice=300000,maxPrice=300000)
        check(code == 200 and result[0]['offer']['roomTypeId'] == rooms[2],'inclusive price and adult/child constraints match same room')
        code, result = request(dict(hotelName=marker,rooms=1,adults=2))
        check(code == 200 and result[0]['offer']['availableRooms'] is None and result[0]['offer']['checkedAt'] is None,'undated search never presents configured inventory as dated availability')
        code, result = search(rooms=0)
        check(code == 400,'invalid room count is a validation error')
        code, result = request(dict(hotelName=marker,checkIn='2027-01-10'))
        check(code == 400,'half a date range is rejected')
        h.sql('checkinn_hotel',"UPDATE hotels SET is_active=false WHERE id='"+hotel+"'")
        code, result = search(rooms=1)
        check(code == 200 and result == [],'unpublished parent cannot appear in offers')
        h.sql('checkinn_hotel',"UPDATE hotels SET is_active=true WHERE id='"+hotel+"'")
        code, result = request(dict(hotelName=marker,cityId=city,rooms=1),False)
        check(code == 200 and len(result) == 1,'gateway offer route and stable city ID work without legacy fanout')
        code, result = request(dict(cityId=str(uuid.uuid4()),cityName='ignored legacy label',rooms=1))
        check(code == 200 and result == [],'unknown stable city ID is never reinterpreted through its display label')
        h.sql('checkinn_hotel',"UPDATE room_types SET total_rooms=NULL WHERE id='"+rooms[0]+"'")
        code, result = search(rooms=1,adults=2,minPrice=200000,maxPrice=200000)
        check(code == 409,'unknown inventory is an error rather than sold out')
        check(h.sql('checkinn_hotel',"SELECT held,booked FROM room_availability WHERE room_type_id='"+rooms[1]+"'") == '0|2','search never mutates availability counters')
    finally:
        for room in rooms:
            h.sql('checkinn_hotel',"DELETE FROM room_availability WHERE room_type_id='"+room+"'")
            h.sql('checkinn_hotel',"DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'")
        h.sql('checkinn_hotel',"DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")

if __name__ == '__main__': main()
