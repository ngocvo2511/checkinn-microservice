"""HTTP validation regression on isolated test clones, with UUID-scoped fixtures only."""
import argparse, datetime, importlib.util, json, time, uuid
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py')
h=importlib.util.module_from_spec(spec); spec.loader.exec_module(h)

def main():
    args=argparse.ArgumentParser(); args.add_argument('--baseline',action='store_true'); options=args.parse_args()
    for service in ['hotel-service','booking-service']: h.verify_running_clone(service)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    key=config['services']['hotel-service']['environment']['SECRET_KEY']
    assert key==config['services']['booking-service']['environment']['SECRET_KEY']
    hotel,room,owner,guest=[str(uuid.uuid4()) for _ in range(4)]
    def api(service,method,path,body=None,actor=owner,role='OWNER',gateway=False):
        port='8083' if service=='hotel-service' else '8084'
        base=('http://api-gateway:8080/api/hotel' if service=='hotel-service' else 'http://api-gateway:8080') if gateway else 'http://localhost:'+port
        headers='Authorization: Bearer '+h.token(key,actor,role)+'\n' if actor else ''
        command=h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-X',method,'-H','@-','-H','Content-Type: application/json','-w','\n%{http_code}']
        if body is not None: command+=['--data',json.dumps(body)]
        raw=h.run(command+[base+path],headers); body,_,status=raw.rpartition('\n')
        try: value=json.loads(body)
        except ValueError: value=None
        return int(status),value
    def check(ok,name):
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok: raise AssertionError(name)
    try:
        created=h.sql('checkinn_hotel',"WITH added AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','validation-fixture-"+hotel+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM added")
        if created!='1': raise RuntimeError('NEEDS_ENVIRONMENT: no cloned hotel template')
        h.sql('checkinn_hotel',"INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','validation-fixture-room','is_active',true,'base_price',1000000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
        before=h.sql('checkinn_hotel',"SELECT md5(to_jsonb(r)::text) FROM room_types r WHERE id='"+room+"'")
        for gateway in [False,True]:
            route='gateway' if gateway else 'direct'
            if gateway:
                deadline=time.monotonic()+120
                while api('hotel-service','GET','/hotels/'+hotel,actor=None,gateway=True)[0]!=200:
                    if time.monotonic()>deadline: raise RuntimeError('NEEDS_ENVIRONMENT: gateway discovery unavailable')
                    time.sleep(2)
            for body in [{'basePrice':-1},{'roomAmount':0},{'capacity':{'adults':-1,'children':0}},{'capacity':{'adults':1}},{'roomAmount':1.5},{'name':' '},{'name':'x'*101}]:
                code,value=api('hotel-service','PUT','/api/v1/'+room,body,gateway=gateway)
                check(code==400 and isinstance(value.get('fieldErrors') if isinstance(value,dict) else None,dict),route+' invalid room input rejected with fieldErrors')
            check(before==h.sql('checkinn_hotel',"SELECT md5(to_jsonb(r)::text) FROM room_types r WHERE id='"+room+"'"),route+' invalid room inputs preserve source fingerprint')
            for body in [{'starRating':6},{'contactEmail':'bad-email'},{'name':'x'*256},{'businessLicenseNumber':'x'*51},{'address':{'street':'','city':'Synthetic','country':'VN'}}]:
                code,value=api('hotel-service','PUT','/hotels/'+hotel,body,gateway=gateway)
                check(code==400 and bool(value.get('fieldErrors')),route+' invalid hotel input rejected with fieldErrors')
            code,value=api('hotel-service','POST','/hotels',{},gateway=gateway)
            check(code==400 and 'name' in value.get('fieldErrors',{}),route+' hotel create activates required validation')
            arrival=datetime.date.today()+datetime.timedelta(days=20)
            booking={'idempotencyKey':str(uuid.uuid4()),'hotelId':hotel,'checkInDate':arrival.isoformat(),'checkOutDate':(arrival+datetime.timedelta(days=1)).isoformat(),
                     'adults':2,'children':0,'contactName':'Validation fixture','contactEmail':'validation-fixture@example.invalid','contactPhone':'0000000000',
                     'items':[{'roomTypeId':room,'quantity':1,'unitPrice':1000000,'checkInDate':arrival.isoformat(),'checkOutDate':(arrival+datetime.timedelta(days=1)).isoformat()}]}
            cases=[{**booking,'contactName':'x'*256},{**booking,'specialRequests':'x'*256},{**booking,'contactEmail':'bad-email'},{**booking,'contactName':None},{**booking,'contactPhone':'abc'},{**booking,'adults':1.5},{**booking,'hotelId':'invalid-uuid'},{**booking,'items':[None]},{**booking,'checkOutDate':arrival.isoformat()}]
            for body in cases:
                code,value=api('booking-service','POST','/api/bookings',body,guest,'CUSTOMER',gateway)
                check(code==400 and bool(value.get('fieldErrors')),route+' invalid booking input rejected with fieldErrors')
            check(h.sql('checkinn_booking',"SELECT count(*) FROM bookings WHERE hotel_id='"+hotel+"'")=='0',route+' invalid booking inputs never persist a booking')
            check(h.sql('checkinn_hotel',"SELECT count(*) FROM room_holds WHERE room_type_id='"+room+"'")=='0',route+' invalid booking inputs never create inventory holds')
        code,_=api('hotel-service','PUT','/api/v1/'+room,{'description':'Validated partial update'})
        check(code==200,'valid partial room update preserves omitted values')
        check(h.sql('checkinn_hotel',"SELECT base_price::int||'|'||total_rooms FROM room_types WHERE id='"+room+"'")=='1000000|2','partial update keeps price and inventory')
        h.sql('checkinn_hotel',"UPDATE room_types SET total_rooms=NULL WHERE id='"+room+"'")
        check(api('hotel-service','PUT','/api/v1/'+room,{'roomAmount':3})[0]==409,'unknown legacy inventory needs review instead of null unboxing or guessed assignment')
        check(h.sql('checkinn_hotel',"SELECT total_rooms IS NULL FROM room_types WHERE id='"+room+"'")=='t','unknown inventory remains unchanged')
        check(api('hotel-service','PUT','/api/v1/'+room,{'description':'Legacy description edit'})[0]==200,'legacy unknown inventory still permits unrelated partial edits')
    finally:
        # Delete only fixture records bound to both new room and hotel UUIDs; no copied source rows.
        # In baseline mode the first invalid update may succeed; cleanup still removes only this synthetic room.
        # Remove any unexpected booking/hold made by a regression, bound to this fresh fixture only.
        h.sql('checkinn_booking',"DELETE FROM booking_items WHERE booking_id IN (SELECT id FROM bookings WHERE hotel_id='"+hotel+"'); DELETE FROM bookings WHERE hotel_id='"+hotel+"'")
        h.sql('checkinn_hotel',"DELETE FROM room_holds WHERE room_type_id='"+room+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'")
        h.sql('checkinn_hotel',"DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='validation-fixture-"+hotel+"'")
        check(h.sql('checkinn_hotel',"SELECT count(*) FROM hotels WHERE id='"+hotel+"'")=='0','synthetic validation fixture cleanup')

if __name__=='__main__': main()
