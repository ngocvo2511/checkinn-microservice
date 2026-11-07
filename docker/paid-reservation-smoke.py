"""Verified-clone paid inventory recovery with SIMULATED signed provider callbacks only.
No provider API/payment/refund is contacted. Scoped faults and fixtures are removed.
"""
import argparse,datetime,hashlib,hmac,importlib.util,json,secrets,time,urllib.parse,uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--baseline',action='store_true');parser.add_argument('--migrate-only',action='store_true');args=parser.parse_args()
    h.verify_running_clone('booking-service')
    if args.migrate_only:
        fields="-'paid_reservation_status'-'paid_reservation_payment_id'-'paid_reservation_next_attempt_at'-'paid_reservation_attempts'-'paid_reservation_last_error'-'inventory_reviewed_by'-'inventory_review_reason'-'inventory_reviewed_at'"
        fingerprint="SELECT count(*)||'|'||coalesce(md5(string_agg(md5((to_jsonb(b)"+fields+")::text),'' ORDER BY id)),'empty') FROM bookings b"
        before=h.sql('checkinn_booking',fingerprint)
        for _ in range(2):
            for migration in ['paid-reservation-v1-20261105.sql','inventory-review-audit-20261106.sql']:
                h.run(h.COMPOSE+['exec','-T','postgres-test','psql','-X','-v','ON_ERROR_STOP=1','-U','checkinn_test','-d','checkinn_booking'],(root/'sql'/migration).read_text())
        assert before==h.sql('checkinn_booking',fingerprint),'Historical booking fingerprint changed'
        assert h.sql('checkinn_booking',"SELECT count(*) FROM information_schema.columns WHERE table_name='bookings' AND column_name IN ('paid_reservation_status','paid_reservation_payment_id','paid_reservation_next_attempt_at','paid_reservation_attempts','paid_reservation_last_error','inventory_reviewed_by','inventory_review_reason','inventory_reviewed_at') AND is_nullable='YES' AND column_default IS NULL")=='8'
        print('PASS additive paid queue migration/replay/fingerprint/nullable/no-default',flush=True);return
    for name in ['hotel-service','user-service','revenue-service']:h.verify_running_clone(name)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']));settings=config['services']['booking-service']['environment'];key=settings['SECRET_KEY']
    if not settings.get('VNPAY_HASH_SECRET') or not settings.get('VNPAY_TMN_CODE'):raise RuntimeError('NEEDS_ENVIRONMENT: local callback signing configuration missing')
    notification=h.run(h.COMPOSE+['ps','-q','notification-service']);assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',notification]))
    hotel,room,owner,guest=[str(uuid.uuid4()) for _ in range(4)];ids=[];faults=[];stopped=False
    arrival=datetime.date.today()+datetime.timedelta(days=20);departure=arrival+datetime.timedelta(days=1)
    def check(ok,name):
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
    def api(method,path,body=None,actor=guest,role='CUSTOMER'):
        headers='Authorization: Bearer '+h.token(key,actor,role)+'\n' if actor else ''
        command=h.COMPOSE+['exec','-T','booking-service','curl','-sS','--max-time','20','-H','@-','-H','Content-Type: application/json','-X',method,'-w','\n%{http_code}']
        if body is not None:command+=['--data',json.dumps(body)]
        raw=h.run(command+['http://localhost:8084'+path],headers);text,_,code=raw.rpartition('\n')
        try:value=json.loads(text)
        except ValueError:value=None
        return int(code),value
    def create():
        body={'idempotencyKey':str(uuid.uuid4()),'hotelId':hotel,'checkInDate':arrival.isoformat(),'checkOutDate':departure.isoformat(),'adults':2,'children':0,
            'contactName':'Synthetic paid recovery','contactEmail':'paid-recovery@example.invalid','contactPhone':'0000000000','acceptedCancellationPolicyVersion':'STANDARD_24H_V1',
            'items':[{'roomTypeId':room,'quantity':1,'unitPrice':1000000,'checkInDate':arrival.isoformat(),'checkOutDate':departure.isoformat()}]}
        code,b=api('POST','/api/bookings',body);check(code==201,'create isolated paid recovery booking');ids.append(str(uuid.UUID(b['id'])));return b
    def init(b):
        code,p=api('POST','/api/payments/vnpay/create?bookingId='+b['id']);check(code==200,'initiate local provider order without contacting provider');return str(uuid.UUID(p['orderId']))
    def callback(payment,txn,amount='100000000',valid=True):
        params={'vnp_TxnRef':payment,'vnp_TmnCode':settings['VNPAY_TMN_CODE'],'vnp_Amount':amount,'vnp_ResponseCode':'00','vnp_TransactionStatus':'00','vnp_TransactionNo':txn}
        query=urllib.parse.urlencode(sorted(params.items()));signature=hmac.new(settings['VNPAY_HASH_SECRET'].encode(),query.encode(),hashlib.sha512).hexdigest() if valid else 'invalid'
        return api('GET','/api/payments/vnpay/return?'+query+'&vnp_SecureHash='+signature,actor=None)
    def txn():return str(100000000000000+secrets.randbelow(899999999999999))
    def read(b):return api('GET','/api/bookings/'+b['id'])[1]
    def wait(b,state):
        deadline=time.monotonic()+100
        while time.monotonic()<deadline:
            value=read(b)
            if value and value.get('paidReservationStatus')==state:return value
            time.sleep(1)
        raise RuntimeError('Expected paid reservation recovery state not reached')
    def wait_sql(db,query,expected):
        deadline=time.monotonic()+45
        while time.monotonic()<deadline:
            value=h.sql(db,query)
            if value==expected:return value
            time.sleep(0.5)
        raise RuntimeError('Expected isolated fault evidence not observed')
    def drop_fault(item):
        db,table,name=item;h.sql(db,'DROP TRIGGER IF EXISTS '+name+' ON '+table+'; DROP FUNCTION IF EXISTS '+name+'()');faults.remove(item)
    def fault(b,condition,prefix='paid_fault_'):
        name=prefix+uuid.uuid4().hex[:16]
        h.sql('checkinn_booking',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+b['id']+"' AND "+condition+" THEN RAISE EXCEPTION 'Synthetic paid transaction fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+name+" AFTER UPDATE ON bookings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        item=('checkinn_booking','bookings',name);faults.append(item);return item
    try:
        assert h.sql('checkinn_hotel',"WITH added AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','paid-recovery-fixture-"+hotel+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM added")=='1'
        h.sql('checkinn_hotel',"INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','paid-recovery-room','is_active',true,'base_price',1000000,'total_rooms',20,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
        deadline=time.monotonic()+120
        while True:
            ready,_=api('GET','/api/bookings/hotel/'+hotel,actor=owner,role='OWNER')
            if ready!=503:break
            if time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: internal catalog discovery not ready')
            time.sleep(2)
        check(ready==200,'catalog discovery and ownership ready before payment mutations')
        b=create();payment=init(b);number=txn()
        check(callback(payment,number,valid=False)[0]==400,'invalid callback signature never mutates payment')
        check(callback(payment,number,amount='1')[0]==400,'valid signature with wrong amount never mutates payment')
        receipt_fault=fault(b,'NEW.paid_amount>0','paid_receipt_')
        check(callback(payment,number)[0]==500,'receipt commit fault is observable')
        check(read(b)['paidAmount']==0,'receipt rollback preserves zero received money')
        check(h.sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+b['holdId']+"'")=='HELD','receipt rollback never confirms remote inventory before source commit')
        if args.baseline:return
        check(h.sql('checkinn_revenue',"SELECT count(*) FROM payment_records WHERE booking_id='"+b['id']+"'")=='0','receipt rollback does not publish collected-money projection')
        drop_fault(receipt_fault)
        check(callback(payment,number)[0]==200,'SIMULATED signed callback records committed financial receipt')
        check(wait(b,'V1_CONFIRMED')['paidAmount']==1000000,'confirmed reservation retains exactly the verified received amount')
        with ThreadPoolExecutor(max_workers=4) as pool:replies=list(pool.map(lambda _:callback(payment,number),range(4)))
        check(all(code==200 and result['id']==payment for code,result in replies),'concurrent SIMULATED callback replays return one receipt')
        check(read(b)['paidAmount']==1000000,'callback replay never credits money twice')
        # Cancellation rollback cannot release a reservation whose source decision did not commit.
        cancel_fault=fault(b,"NEW.status='CANCELLED'",'paid_cancel_')
        check(api('DELETE','/api/bookings/'+b['id'])[0]==500,'cancellation commit fault is observable')
        check(read(b)['status']=='CONFIRMED' and h.sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+b['holdId']+"'")=='CONFIRMED','rolled-back paid cancellation retains reserved inventory')
        drop_fault(cancel_fault)
        check(api('DELETE','/api/bookings/'+b['id'])[0]==204,'accepted paid cancellation commits source before release')
        check(wait(b,'V1_RELEASED')['status']=='CANCELLED','paid cancellation releases original inventory asynchronously')
        check(api('DELETE','/api/bookings/'+b['id'])[0]==204,'paid cancellation replay is idempotent')
        check(h.sql('checkinn_booking',"SELECT status FROM payments WHERE id='"+payment+"'")=='COMPLETED','inventory release never claims a provider refund')
        check(h.sql('checkinn_booking',"SELECT count(*) FROM refund_requests WHERE booking_id='"+b['id']+"'")=='1','paid cancellation retains one auditable refund request')
        # Actual HTTP timeout after remote mutation; receipt is already durable, no callback replay required.
        c=create();cp=init(c);name='paid_slow_'+uuid.uuid4().hex[:16]
        h.sql('checkinn_hotel',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+c['holdId']+"' AND NEW.status='CONFIRMED' THEN PERFORM pg_sleep(8); END IF; RETURN NEW; END $$; CREATE TRIGGER "+name+" BEFORE UPDATE ON room_holds FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        slow=('checkinn_hotel','room_holds',name);faults.append(slow)
        check(callback(cp,txn())[0]==200,'SIMULATED receipt commits independently of inventory HTTP timeout')
        wait_sql('checkinn_booking',"SELECT paid_reservation_last_error FROM bookings WHERE id='"+c['id']+"'",'INVENTORY_UNAVAILABLE')
        check(read(c)['paidAmount']==1000000 and read(c)['status']=='PENDING_PAYMENT','unknown inventory outcome preserves committed money without false booking success')
        drop_fault(slow)
        check(wait(c,'V1_CONFIRMED')['paidAmount']==1000000,'HTTP-timeout outcome recovers automatically without provider callback replay')
        # Remote confirm success, local completion failure and a process restart.
        d=create();dp=init(d);completion=fault(d,"NEW.paid_reservation_status='V1_CONFIRMED'")
        check(callback(dp,txn())[0]==200,'SIMULATED receipt commits before local reservation completion fault')
        wait_sql('checkinn_booking',"SELECT paid_reservation_last_error FROM bookings WHERE id='"+d['id']+"'",'LOCAL_TRANSACTION_FAILED')
        check(read(d)['paidAmount']==1000000 and read(d)['status']=='PENDING_PAYMENT','failed completion cannot falsely acknowledge reservation or lose money')
        check(h.sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+d['holdId']+"'")=='CONFIRMED','remote inventory success is retained for idempotent completion replay')
        h.run(h.COMPOSE+['stop','booking-service']);stopped=True;drop_fault(completion);h.run(h.COMPOSE+['start','booking-service']);stopped=False
        deadline=time.monotonic()+150
        while True:
            container=h.run(h.COMPOSE+['ps','-q','booking-service']);state=json.loads(h.run(['docker','inspect','--format','{{json .State}}',container]))
            if state.get('Health',{}).get('Status')=='healthy':break
            if time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: restarted test booking service not healthy')
            time.sleep(2)
        h.verify_running_clone('booking-service')
        check(wait(d,'V1_CONFIRMED')['paidAmount']==1000000,'restart completes original paid reservation without callback replay or second charge')
        # Cancellation wins while an earlier remote confirmation has unknown local completion.
        e=create();ep=init(e);completion=fault(e,"NEW.paid_reservation_status='V1_CONFIRMED'")
        callback(ep,txn());wait_sql('checkinn_booking',"SELECT paid_reservation_last_error FROM bookings WHERE id='"+e['id']+"'",'LOCAL_TRANSACTION_FAILED')
        check(api('DELETE','/api/bookings/'+e['id'])[0]==204,'cancellation accepted during paid completion uncertainty')
        drop_fault(completion)
        check(wait(e,'V1_RELEASED')['status']=='CANCELLED','queued paid confirmation never reopens a cancelled source')
        # Expired inventory is a review outcome, never a new room allocation or a discarded receipt.
        f=create();fp=init(f)
        h.sql('checkinn_hotel',"UPDATE room_holds SET expires_at=now()-interval '1 day' WHERE id='"+f['holdId']+"'")
        check(callback(fp,txn())[0]==200,'SIMULATED late receipt is preserved even when inventory expired')
        check(wait(f,'V1_REVIEW_REQUIRED')['paidAmount']==1000000,'expired paid inventory needs review without erasing financial receipt')
        check(h.sql('checkinn_hotel',"SELECT count(*) FROM room_holds WHERE room_type_id='"+room+"'")=='5','reconciliation never guesses replacement room inventory')
        check(api('POST','/api/payments',{'bookingId':f['id'],'method':'HOTEL','amount':1000000})[0]==400,'paid reconciliation cannot start a second payment')
        check(h.sql('checkinn_hotel',"SELECT sum(booked) FROM room_availability WHERE room_type_id='"+room+"'")=='2','surviving booked inventory matches exactly the two committed reservations')
        check(h.sql('checkinn_hotel',"SELECT min(booked)>=0 AND min(held)>=0 FROM room_availability WHERE room_type_id='"+room+"'")=='t','paid recovery never makes inventory counters negative')
    finally:
        if stopped:h.run(h.COMPOSE+['start','booking-service'])
        for item in list(faults):drop_fault(item)
        discovered=h.sql('checkinn_booking',"SELECT id FROM bookings WHERE user_id='"+guest+"' AND hotel_id='"+hotel+"'")
        ids=list(set(ids+[str(uuid.UUID(x)) for x in discovered.splitlines()]))
        if ids:
            values="','".join(ids);time.sleep(2)
            h.sql('checkinn_booking',"DELETE FROM refund_requests WHERE booking_id IN ('"+values+"'); DELETE FROM payments WHERE booking_id IN ('"+values+"'); DELETE FROM booking_items WHERE booking_id IN ('"+values+"'); DELETE FROM bookings WHERE id IN ('"+values+"')")
            h.sql('checkinn_user',"DELETE FROM points_reservations WHERE user_id='"+guest+"'; DELETE FROM loyalty_commands WHERE user_id='"+guest+"'; DELETE FROM points_transactions WHERE user_id='"+guest+"'; DELETE FROM loyalty_points WHERE user_id='"+guest+"'")
            h.sql('checkinn_revenue',"DELETE FROM payment_records WHERE booking_id IN ('"+values+"'); DELETE FROM booking_status_records WHERE booking_id IN ('"+values+"')")
        h.sql('checkinn_hotel',"DELETE FROM room_holds WHERE room_type_id='"+room+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
        check(h.sql('checkinn_booking',"SELECT count(*) FROM bookings WHERE hotel_id='"+hotel+"'")=='0','synthetic paid recovery fixture cleanup')

if __name__=='__main__':main()
