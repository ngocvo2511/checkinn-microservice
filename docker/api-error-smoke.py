"""Stable hotel/booking HTTP errors + consistent availability on isolated clones only."""
import concurrent.futures,datetime,importlib.util,json,threading,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
    for service in ['hotel-service','booking-service']:h.verify_running_clone(service)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))['services']
    key=config['hotel-service']['environment']['SECRET_KEY']
    owner,admin,hotel,room=[str(uuid.uuid4()) for _ in range(4)]
    fault='apierr_'+uuid.uuid4().hex;code='TEST_'+uuid.uuid4().hex.upper()
    count=0
    def check(ok,name):
        nonlocal count
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
        count+=1
    def db(query):return h.sql('checkinn_hotel',query)
    def api(service,method,path,body=None,actor=None,role='OWNER',gateway=False,malformed=False):
        prefix='/api/hotel' if service=='hotel-service' else ''
        base='http://api-gateway:8080'+prefix if gateway else 'http://localhost:'+('8083' if service=='hotel-service' else '8084')
        token='synthetic-malformed' if malformed else h.token(key,actor,role) if actor else ''
        header='Authorization: Bearer '+token+'\n' if token else ''
        command=h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-D','-','-X',method,'-H','@-','-H','Content-Type: application/json','-w','\n%{http_code}']
        if body is not None:command+=['--data',json.dumps(body)]
        raw=h.run(command+[base+path],header);raw_headers,_,content=raw.partition('\n\n');text,_,status=content.rpartition('\n')
        headers={line.split(':',1)[0].lower():line.split(':',1)[1].strip() for line in raw_headers.splitlines() if ':' in line}
        try:result=json.loads(text)
        except ValueError:result={}
        return int(status),result,headers
    def error(result,status,code_,name):
        actual,body,headers=result
        check(actual==status and body.get('status')==status and body.get('code')==code_ and bool(body.get('requestId')) and bool(body.get('path')) and headers.get('x-request-id')==body.get('requestId') and 'type' not in body and 'exception' not in body and 'stackTrace' not in body,name)
    try:
        for gateway in [False,True]:
            deadline=time.monotonic()+120
            while gateway and api('hotel-service','GET','/amenity-catalog',gateway=True)[0]==503:
                if time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: gateway discovery')
                time.sleep(2)
            error(api('hotel-service','GET','/no-such-standardization-resource',gateway=gateway),404,'NOT_FOUND','missing resource is404 with structured error direct/gateway')
            error(api('hotel-service','POST','/amenity-catalog',gateway=gateway),405,'METHOD_NOT_ALLOWED','unsupported method is405 rather than500')
            error(api('hotel-service','GET','/admin/amenity-catalog',gateway=gateway),401,'UNAUTHORIZED','anonymous hotel private API has stable401')
            error(api('hotel-service','GET','/admin/amenity-catalog',actor=owner,gateway=gateway),403,'FORBIDDEN','authenticated wrong-role hotel access has stable403')
            error(api('hotel-service','GET','/amenity-catalog',malformed=True,gateway=gateway),401,'UNAUTHORIZED','malformed hotel bearer token is401 not500')
            error(api('booking-service','GET','/api/bookings/user/'+owner,gateway=gateway),401,'UNAUTHORIZED','anonymous booking API has stable401')
            error(api('booking-service','GET','/api/bookings/count/total',actor=owner,role='CUSTOMER',gateway=gateway),403,'FORBIDDEN','booking role denial has stable403')
            error(api('booking-service','GET','/api/bookings/user/'+owner,malformed=True,gateway=gateway),401,'UNAUTHORIZED','malformed booking token rejected with stable401')
            error(api('booking-service','GET','/api/payments/vnpay/return',gateway=gateway),400,'INVALID_REQUEST','unsigned callback keeps400 and adds JSON envelope')
            error(api('booking-service','GET','/api/payments/'+str(uuid.uuid4()),actor=admin,role='ADMIN',gateway=gateway),404,'NOT_FOUND','missing payment has stable404')
        check(db("WITH added AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','apierror-fixture-"+hotel+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM added")=='1','create scoped clone property')
        db("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic error room','is_active',true,'base_price',1000000,'total_rooms',3,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
        arrival=datetime.date.today()+datetime.timedelta(days=30);second=arrival+datetime.timedelta(days=1);departure=arrival+datetime.timedelta(days=2)
        path='/api/v1/availability/check?roomTypeId='+room+'&checkIn='+arrival.isoformat()+'&checkOut='+departure.isoformat()+'&quantity=1'
        error(api('hotel-service','GET',path.replace('quantity=1','quantity=0')),400,'INVALID_REQUEST','invalid quantity rejected before stock read')
        db("UPDATE room_types SET total_rooms=NULL WHERE id='"+room+"'")
        error(api('hotel-service','GET',path),409,'INVENTORY_NOT_CONFIGURED','unknown inventory is explicit conflict not guessed availability')
        db("UPDATE room_types SET total_rooms=3 WHERE id='"+room+"'; INSERT INTO room_availability(id,room_type_id,date,held,booked,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+room+"','"+arrival.isoformat()+"',0,3,now(),now()),('"+str(uuid.uuid4())+"','"+room+"','"+second.isoformat()+"',0,0,now(),now())")
        # Every committed state has a sold-out night. Read-committed reads across
        # different versions could otherwise falsely see both nights as available.
        stop=threading.Event()
        def writer():
            index=0
            while not stop.is_set():
                first,other=(3,0) if index%2==0 else (0,3)
                db("UPDATE room_availability SET booked=CASE WHEN date='"+arrival.isoformat()+"' THEN "+str(first)+" ELSE "+str(other)+" END WHERE room_type_id='"+room+"'")
                index+=1
            return index
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            work=pool.submit(writer)
            try:
                for _ in range(24):
                    status,body,_=api('hotel-service','GET',path)
                    check(status==200 and body.get('availableRooms')==0 and body.get('available') is False,'availability uses a coherent multi-night snapshot during concurrent stock changes')
            finally:stop.set()
            check(work.result()>0,'concurrent writer performed real commits')
        db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic internal detail'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON amenity_catalog DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.code='"+code+"') EXECUTE FUNCTION "+fault+"()")
        result=api('hotel-service','POST','/admin/amenity-catalog',{'code':code,'label':'Synthetic commit failure','active':True,'revision':0},admin,'ADMIN')
        error(result,500,'INTERNAL_ERROR','real deferred database commit failure is sanitized500')
        check('synthetic internal detail' not in json.dumps(result[1]) and db("SELECT count(*) FROM amenity_catalog WHERE code='"+code+"'")=='0','failed transaction leaves no catalog data and reveals no internal cause')
    finally:
        db('DROP TRIGGER IF EXISTS '+fault+' ON amenity_catalog; DROP FUNCTION IF EXISTS '+fault+'()')
        db("DELETE FROM amenity_catalog WHERE code='"+code+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"' AND name='apierror-fixture-"+hotel+"'")
        check(db("SELECT count(*) FROM hotels WHERE id='"+hotel+"'")=='0','scoped error fixtures removed')
    print(json.dumps({'verified_cases':count}),flush=True)

if __name__=='__main__':main()
