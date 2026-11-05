"""Onsite V1 on verified clones/Mailpit only. No provider/storage calls.
Faults target fresh UUIDs; restart only test booking-service. Run alone.
"""
import argparse, datetime, importlib.util, json, time, uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
root = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('h', root/'booking-security-smoke.py')
h = importlib.util.module_from_spec(spec); spec.loader.exec_module(h)

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--migrate-only',action='store_true'); parser.add_argument('--baseline',action='store_true'); args=parser.parse_args()
    h.verify_running_clone('booking-service')
    if args.migrate_only:
        fingerprint="SELECT count(*)||'|'||coalesce(md5(string_agg(md5((to_jsonb(b)-'onsite_reservation_status'-'onsite_next_attempt_at'-'onsite_attempts'-'onsite_last_error')::text),'' ORDER BY id)),'empty') FROM bookings b"
        before=h.sql('checkinn_booking',fingerprint)
        for _ in range(2): h.run(h.COMPOSE+['exec','-T','postgres-test','psql','-X','-v','ON_ERROR_STOP=1','-U','checkinn_test','-d','checkinn_booking'],(root/'sql/onsite-reservation-v1-20261105.sql').read_text())
        assert before==h.sql('checkinn_booking',fingerprint), 'Historical row fingerprint changed'
        assert h.sql('checkinn_booking',"SELECT count(*) FROM information_schema.columns WHERE table_name='bookings' AND column_name IN ('onsite_reservation_status','onsite_next_attempt_at','onsite_attempts','onsite_last_error') AND is_nullable='YES' AND column_default IS NULL")=='4'
        print('PASS additive migration/replay/fingerprint/nullable/no-default',flush=True); return
    for name in ['hotel-service','user-service','revenue-service']: h.verify_running_clone(name)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json'])); key=config['services']['booking-service']['environment']['SECRET_KEY']
    notification=h.run(h.COMPOSE+['ps','-q','notification-service'])
    assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',notification]))
    hotel,room,owner,guest=[str(uuid.uuid4()) for _ in range(4)]; ids=[]; faults=[]; stopped=False
    check_in=(datetime.date.today()+datetime.timedelta(days=10)).isoformat(); check_out=(datetime.date.today()+datetime.timedelta(days=11)).isoformat()
    def check(ok,name):
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok: raise AssertionError(name)
    def api(method,path,actor=guest,role='CUSTOMER',body=None):
        headers='Authorization: Bearer '+h.token(key,actor,role)+'\n' if actor else ''
        command=h.COMPOSE+['exec','-T','booking-service','curl','-sS','--max-time','20','-H','@-','-H','Content-Type: application/json','-X',method,'-w','\n%{http_code}']
        if body is not None: command+=['--data',json.dumps(body)]
        raw=h.run(command+['http://localhost:8084'+path],headers); text,sep,code=raw.rpartition('\n')
        if not sep: text,code='',raw
        try: value=json.loads(text) if text else None
        except ValueError: value=None
        return int(code),value
    def create(arrival=check_in, departure=check_out):
        body={'idempotencyKey':str(uuid.uuid4()),'hotelId':hotel,'checkInDate':arrival,'checkOutDate':departure,'adults':2,'children':0,
              'contactName':'Onsite fixture','contactEmail':'onsite-v1@example.invalid','contactPhone':'0000000000','acceptedCancellationPolicyVersion':'STANDARD_24H_V1',
              'items':[{'roomTypeId':room,'quantity':1,'unitPrice':1000000,'checkInDate':arrival,'checkOutDate':departure}]}
        code,b=api('POST','/api/bookings',body=body); check(code==201,'create isolated booking'); ids.append(str(uuid.UUID(b['id']))); return b
    def choose(b): return api('POST','/api/payments',body={'bookingId':b['id'],'method':'HOTEL','amount':1000000})
    def read(b): return api('GET','/api/bookings/'+b['id'])[1]
    def wait(b,state):
        deadline=time.monotonic()+75
        while time.monotonic()<deadline:
            value=read(b)
            if value and value.get('onsiteReservationStatus')==state: return value
            time.sleep(1)
        raise RuntimeError('Onsite recovery did not reach expected state')
    def wait_sql(database, query, expected):
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            observed=h.sql(database,query)
            if observed==expected: return observed
            time.sleep(0.5)
        raise RuntimeError('Expected isolated fault/recovery evidence not observed')
    def drop_fault(item):
        database,table,name=item; h.sql(database,'DROP TRIGGER IF EXISTS '+name+' ON '+table+'; DROP FUNCTION IF EXISTS '+name+'()'); faults.remove(item)
    def commit_fault(b):
        name='onsite_fault_'+uuid.uuid4().hex[:16]
        h.sql('checkinn_booking',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+b['id']+"' AND NEW.onsite_reservation_status='V1_CONFIRMED' THEN RAISE EXCEPTION 'Synthetic onsite commit fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+name+" AFTER UPDATE ON bookings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        item=('checkinn_booking','bookings',name); faults.append(item); return item
    try:
        h.sql('checkinn_hotel',"INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','onsite-v1-fixture-"+hotel+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1")
        h.sql('checkinn_hotel',"INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','onsite-v1-room','is_active',true,'base_price',1000000,'total_rooms',20,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
        deadline=time.monotonic()+120
        while api('GET','/api/bookings/hotel/'+hotel,owner,'OWNER')[0]!=200:
            if time.monotonic()>deadline: raise RuntimeError('NEEDS_ENVIRONMENT: ownership discovery not ready')
            time.sleep(1)
        b=create(); code,p=choose(b); check(code==201 and p['status']=='ONSITE_PENDING','choice is not a financial receipt')
        value=read(b) if args.baseline else wait(b,'V1_CONFIRMED')
        check(value['status']=='CONFIRMED' and value.get('onsiteReservationStatus')=='V1_CONFIRMED','inventory confirmation precedes booking success')
        check(value['paidAmount']==0 and value.get('earnedPoints') in (None,0),'confirmation never claims money or awards points')
        h.sql('checkinn_booking',"UPDATE bookings SET hold_expires_at=localtimestamp-interval '1 day' WHERE id='"+b['id']+"'")
        h.sql('checkinn_hotel',"UPDATE room_holds SET expires_at=now()-interval '1 day' WHERE id='"+b['holdId']+"'")
        with ThreadPoolExecutor(max_workers=4) as pool: replies=list(pool.map(lambda _:choose(b),range(4)))
        check(all(c==201 and x['id']==p['id'] for c,x in replies),'concurrent choice retry returns one persisted payment')
        check(h.sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+b['holdId']+"'")=='CONFIRMED','confirmed inventory survives original hold deadline')
        check(api('PUT','/api/bookings/'+b['id']+'/status?status=NO_SHOW',owner,'OWNER')[0]==400,'future arrival cannot be declared no-show')
        check(api('POST','/api/payments/'+b['id']+'/confirm')[0]==403,'customer cannot record onsite money')
        name='onsite_receipt_'+uuid.uuid4().hex[:16]
        h.sql('checkinn_booking',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+b['id']+"' AND NEW.paid_amount>0 THEN RAISE EXCEPTION 'Synthetic receipt commit fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+name+" AFTER UPDATE ON bookings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        receipt_fault=('checkinn_booking','bookings',name); faults.append(receipt_fault)
        check(api('POST','/api/payments/'+b['id']+'/confirm',owner,'OWNER')[0]==500,'receipt commit failure rolls back local money record')
        check(read(b)['paidAmount']==0,'failed receipt does not claim collected money')
        drop_fault(receipt_fault)
        code,_=api('POST','/api/payments/'+b['id']+'/confirm',owner,'OWNER'); check(code==200,'host can record actual receipt after room confirmation')
        check(api('POST','/api/payments/'+b['id']+'/confirm',owner,'OWNER')[0]==200,'receipt retry is idempotent')
        check(read(b)['paidAmount']==1000000,'receipt credits server amount exactly once')
        # Normal unpaid cancellation, retry and asynchronous release.
        c=create(); choose(c); wait(c,'V1_CONFIRMED')
        name='onsite_cancel_'+uuid.uuid4().hex[:16]
        h.sql('checkinn_booking',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+c['id']+"' AND NEW.status='CANCELLED' THEN RAISE EXCEPTION 'Synthetic onsite cancel commit fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+name+" AFTER UPDATE ON bookings DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        fault=('checkinn_booking','bookings',name); faults.append(fault)
        check(api('DELETE','/api/bookings/'+c['id'])[0]==500,'failed cancellation commit is observable')
        check(read(c)['status']=='CONFIRMED' and h.sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+c['holdId']+"'")=='CONFIRMED','rolled-back cancellation never releases inventory')
        drop_fault(fault)
        check(api('DELETE','/api/bookings/'+c['id'])[0]==204,'accepted unpaid cancellation')
        check(wait(c,'V1_RELEASED')['status']=='CANCELLED','cancelled source releases confirmed inventory')
        check(api('DELETE','/api/bookings/'+c['id'])[0]==204,'cancellation retry does not release twice')
        n=create(datetime.date.today().isoformat(),(datetime.date.today()+datetime.timedelta(days=1)).isoformat()); choose(n); wait(n,'V1_CONFIRMED')
        check(api('PUT','/api/bookings/'+n['id']+'/status?status=NO_SHOW',owner,'OWNER')[0]==200,'host manually records no-show')
        check(wait(n,'V1_RELEASED')['status']=='NO_SHOW','manual no-show releases stock without collecting money')
        check(wait_sql('checkinn_booking',"SELECT loyalty_status FROM bookings WHERE id='"+n['id']+"'",'REVERSED')=='REVERSED',
              'unpaid no-show releases point intent without earning')
        check(api('POST','/api/payments/'+n['id']+'/confirm',owner,'OWNER')[0]==400,'no-show cannot receive a new onsite receipt')
        # Remote commit after the HTTP read deadline: local state stays pending, not false success.
        t=create(); name='onsite_slow_'+uuid.uuid4().hex[:16]
        h.sql('checkinn_hotel',"CREATE FUNCTION "+name+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id='"+t['holdId']+"' AND OLD.status='HELD' AND NEW.status='CONFIRMED' THEN PERFORM pg_sleep(8); END IF; RETURN NEW; END $$; CREATE TRIGGER "+name+" BEFORE UPDATE ON room_holds FOR EACH ROW EXECUTE FUNCTION "+name+"()")
        slow=('checkinn_hotel','room_holds',name); faults.append(slow)
        code,_=choose(t)
        check(code==201 and read(t)['status']=='PENDING_PAYMENT','remote confirmation timeout never claims room confirmed')
        wait_sql('checkinn_booking',"SELECT onsite_last_error FROM bookings WHERE id='"+t['id']+"'",'INVENTORY_UNAVAILABLE')
        check(read(t)['status']=='PENDING_PAYMENT','actual timeout is persisted as unknown before recovery')
        drop_fault(slow)
        check(wait(t,'V1_CONFIRMED')['paidAmount']==0,'unknown remote outcome recovers automatically without client retry')
        # Remote confirmation succeeds but local completion commit fails.
        d=create(); fault=commit_fault(d); code,_=choose(d)
        check(code==201 and read(d)['status']=='PENDING_PAYMENT','failed local completion cannot falsely report confirmed')
        wait_sql('checkinn_hotel',"SELECT status FROM room_holds WHERE id='"+d['holdId']+"'",'CONFIRMED')
        wait_sql('checkinn_booking',"SELECT onsite_last_error FROM bookings WHERE id='"+d['id']+"'",'LOCAL_TRANSACTION_FAILED')
        check(read(d)['status']=='PENDING_PAYMENT','remote success survives failed local completion without false source confirmation')
        stopped=True; h.run(h.COMPOSE+['stop','booking-service']); drop_fault(fault); h.run(h.COMPOSE+['start','booking-service']); stopped=False
        deadline=time.monotonic()+120
        while h.run(['docker','inspect','--format','{{.State.Health.Status}}',h.run(h.COMPOSE+['ps','-q','booking-service'])])!='healthy':
            if time.monotonic()>deadline: raise RuntimeError('Test booking did not restart')
            time.sleep(1)
        check(wait(d,'V1_CONFIRMED')['paidAmount']==0,'restart recovers durable confirmation without client retry or money')
        # Cancellation must win against a queued/unknown confirmation.
        e=create(); fault=commit_fault(e); choose(e)
        wait_sql('checkinn_booking',"SELECT onsite_last_error FROM bookings WHERE id='"+e['id']+"'",'LOCAL_TRANSACTION_FAILED')
        check(api('DELETE','/api/bookings/'+e['id'])[0]==204,'cancellation accepted while confirmation completion is unknown')
        drop_fault(fault)
        check(wait(e,'V1_RELEASED')['status']=='CANCELLED','queued confirmation never reopens a cancelled booking')
        check(h.sql('checkinn_hotel',"SELECT sum(booked)::text||'|'||sum(held)::text FROM room_availability WHERE room_type_id='"+room+"'")=='3|0','exact surviving reservations and zero held supply after recovery/cancel/no-show')
        check(h.sql('checkinn_hotel',"SELECT min(held)>=0 AND min(booked)>=0 FROM room_availability WHERE room_type_id='"+room+"'")=='t','inventory counters remain nonnegative')
    finally:
        if stopped: h.run(h.COMPOSE+['start','booking-service'])
        for item in list(faults): drop_fault(item)
        discovered=h.sql('checkinn_booking',"SELECT id FROM bookings WHERE user_id='"+guest+"' AND hotel_id='"+hotel+"'")
        ids=list(set(ids+[str(uuid.UUID(x)) for x in discovered.splitlines()]))
        if ids:
            values="','".join(ids)
            # Allow the ordinary asynchronous test consumers to settle; never purge queues.
            time.sleep(2)
            h.sql('checkinn_booking',"DELETE FROM refund_requests WHERE booking_id IN ('"+values+"'); DELETE FROM payments WHERE booking_id IN ('"+values+"'); DELETE FROM booking_items WHERE booking_id IN ('"+values+"'); DELETE FROM bookings WHERE id IN ('"+values+"')")
            h.sql('checkinn_user',"DELETE FROM points_reservations WHERE user_id='"+guest+"'; DELETE FROM loyalty_commands WHERE user_id='"+guest+"'; DELETE FROM points_transactions WHERE user_id='"+guest+"'; DELETE FROM loyalty_points WHERE user_id='"+guest+"'")
            h.sql('checkinn_revenue',"DELETE FROM payment_records WHERE booking_id IN ('"+values+"'); DELETE FROM booking_status_records WHERE booking_id IN ('"+values+"')")
        h.sql('checkinn_hotel',"DELETE FROM room_holds WHERE room_type_id='"+room+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
        check(h.sql('checkinn_booking',"SELECT count(*) FROM bookings WHERE user_id='"+guest+"'")=='0','synthetic booking cleanup')

if __name__=='__main__': main()
