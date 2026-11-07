"""Outbox restart, broker outage, real cash receipt and ordered projection on verified clones.

Run alone. Only test RabbitMQ/booking containers are restarted; provider/storage are never called.
Faults and cleanup target exact generated UUIDs; never purge existing queues or copied records.
"""
import base64
import hashlib
import hmac
import datetime
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.parse
import uuid

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',ROOT/'booking-security-smoke.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
    for service in ['booking-service','hotel-service','user-service','revenue-service']:h.verify_running_clone(service)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    assert config['services']['notification-service']['environment']['SPRING_MAIL_HOST']=='mailpit'
    key=config['services']['booking-service']['environment']['SECRET_KEY']
    hotel,room,owner,guest,order=[str(uuid.uuid4()) for _ in range(5)]
    email='eventprobe-'+uuid.uuid4().hex+'@example.invalid'
    fault='events_probe_'+uuid.uuid4().hex
    ids=[];stopped=False;booking_stopped=False;count=0;unroutable=str(uuid.uuid4())
    today=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(hours=7)).date()
    arrival=(today+datetime.timedelta(days=10)).isoformat();departure=(today+datetime.timedelta(days=11)).isoformat()
    def check(ok,name):
        nonlocal count
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
        count+=1
    def db(database,query):return h.sql('checkinn_'+database,query)
    def wait(predicate,name,seconds=90):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            if predicate():return
            time.sleep(.5)
        raise RuntimeError('Timed out: '+name)
    def api(method,path,body=None,actor=guest,role='CUSTOMER',service='booking-service'):
        port='8088' if service=='revenue-service' else '8084'
        settings='request = "'+method+'"\nheader = "Content-Type: application/json"\nheader = '+json.dumps('Authorization: Bearer '+h.token(key,actor,role))+'\n'
        if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
        result=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','25','--config','-','-w','\n%{http_code}','http://localhost:'+port+path],settings)
        text,_,code=result.rpartition('\n')
        try:value=json.loads(text)
        except ValueError:value=None
        return int(code),value
    def create():
        return api('POST','/api/bookings',{'idempotencyKey':str(uuid.uuid4()),'hotelId':hotel,
            'checkInDate':arrival,'checkOutDate':departure,'adults':2,'children':0,'contactName':'Synthetic event guest',
            'contactEmail':email,'contactPhone':'0000000000','acceptedCancellationPolicyVersion':'STANDARD_24H_V1',
            'items':[{'roomTypeId':room,'quantity':1,'unitPrice':1000000,'checkInDate':arrival,'checkOutDate':departure}]})
    def drop():
        db('booking','DROP TRIGGER IF EXISTS '+fault+' ON booking_event_outbox; DROP FUNCTION IF EXISTS '+fault+'()')
    def healthy(service):
        container=h.run(h.COMPOSE+['ps','-q',service])
        return bool(container) and h.run(['docker','inspect','--format','{{.State.Health.Status}}',container])=='healthy'
    rabbit=config['services']['rabbitmq']['environment']
    auth='Basic '+base64.b64encode((rabbit['RABBITMQ_DEFAULT_USER']+':'+rabbit['RABBITMQ_DEFAULT_PASS']).encode()).decode()
    def broker(path,body):
        req=urllib.request.Request('http://127.0.0.1:15672'+path,data=json.dumps(body).encode(),headers={'Authorization':auth,'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)
    try:
        db('hotel',"INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner+"','name','Synthetic event hotel','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1")
        db('hotel',"INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic event room','is_active',true,'base_price',1000000,'total_rooms',5,'capacity',jsonb_build_object('adults',2,'children',0)))).* FROM room_types r LIMIT 1")
        db('booking',"CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF EXISTS(SELECT 1 FROM bookings b WHERE b.id=NEW.booking_id AND b.user_id='"+guest+"') THEN RAISE EXCEPTION 'Synthetic source outbox commit fault'; END IF; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER INSERT ON booking_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION "+fault+"()")
        status,_=create();check(status==500,'outbox commit fault is visible to booking caller')
        check(db('booking',"SELECT count(*) FROM bookings WHERE user_id='"+guest+"'")=='0','source booking rolls back with outbox commit fault')
        check(db('hotel',"SELECT COALESCE(sum(held),0) FROM room_availability WHERE room_type_id='"+room+"'")=='0','failed source transaction compensates only its own hold')
        drop()
        h.run(h.COMPOSE+['stop','rabbitmq']);stopped=True
        status,booking=create();check(status==201,'booking commits durable event while broker is offline')
        id=str(uuid.UUID(booking['id']));ids.append(id)
        wait(lambda:db('booking',"SELECT EXISTS(SELECT 1 FROM booking_event_outbox WHERE booking_id='"+id+"' AND state='PENDING' AND attempts>0)")=='t','durable pending delivery')
        check(db('revenue',"SELECT count(*) FROM booking_status_records WHERE booking_id='"+id+"'")=='0','offline broker cannot falsely claim consumer delivery')
        h.run(h.COMPOSE+['stop','booking-service']);booking_stopped=True
        h.run(h.COMPOSE+['start','booking-service']);booking_stopped=False
        h.run(h.COMPOSE+['start','rabbitmq']);stopped=False
        wait(lambda:healthy('rabbitmq') and healthy('booking-service'),'restarted broker/source healthy',120)
        wait(lambda:db('booking',"SELECT count(*) FROM booking_event_outbox WHERE booking_id='"+id+"' AND state='PENDING'")=='0'
             and db('revenue',"SELECT count(*) FROM booking_status_records WHERE booking_id='"+id+"'")=='1','restart delivery and projection')
        check(True,'source restart recovers committed pending events without caller retry')
        check(db('revenue',"SELECT event_version IS NOT NULL AND last_event_id IS NOT NULL FROM booking_status_records WHERE booking_id='"+id+"'")=='t','consumer stores actual source version and event identity')
        db('booking',"INSERT INTO booking_event_outbox(id,booking_id,event_version,exchange_name,routing_key,payload) VALUES ('"+unroutable+"','"+id+"',nextval('booking_event_outbox_version_seq'),'hotel.events','outbox_unroutable_"+unroutable+"','{}')")
        wait(lambda:db('booking',"SELECT EXISTS(SELECT 1 FROM booking_event_outbox WHERE id='"+unroutable+"' AND state='PENDING' AND attempts>0 AND last_error='UNROUTABLE_EVENT')")=='t','real mandatory publisher return')
        check(True,'broker ACK without a route remains pending instead of losing the outbox event')
        db('booking',"DELETE FROM booking_event_outbox WHERE id='"+unroutable+"'")
        status,_=api('POST','/api/payments',{'bookingId':id,'method':'HOTEL','amount':1000000})
        check(status==201,'onsite choice persists its state transition')
        wait(lambda:db('booking',"SELECT status FROM bookings WHERE id='"+id+"'")=='CONFIRMED','onsite source confirmation')
        wait(lambda:db('revenue',"SELECT booking_status FROM booking_status_records WHERE booking_id='"+id+"'")=='CONFIRMED','onsite projection confirmation')
        check(True,'recovery confirmation enqueues event inside its source transaction')
        db('booking',"CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic delivery acknowledgement commit fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON booking_event_outbox DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.booking_id='"+id+"' AND NEW.routing_key='payment.completed' AND NEW.state='DELIVERED') EXECUTE FUNCTION "+fault+"()")
        check(api('POST','/api/payments/'+id+'/confirm',actor=owner,role='OWNER')[0]==200,'authorized host records a real local cash receipt')
        wait(lambda:db('revenue',"SELECT count(*) FROM revenue_cash_transactions WHERE booking_id='"+id+"'")=='1','real broker cash ingestion')
        check(db('booking',"SELECT count(*) FROM booking_event_outbox WHERE booking_id='"+id+"' AND routing_key='payment.completed' AND state='PENDING'")=='1','broker acceptance with local acknowledgement failure stays pending')
        drop()
        wait(lambda:db('booking',"SELECT count(*) FROM booking_event_outbox WHERE booking_id='"+id+"' AND state='PENDING'")=='0','delivery replay committed')
        check(db('revenue',"SELECT count(*)||'|'||sum(amount) FROM revenue_cash_transactions WHERE booking_id='"+id+"'")=='1|1000000','actual broker replay does not collect or report the receipt twice')
        path='/api/reports/revenue?hotelId='+hotel+'&from='+today.isoformat()+'&to='+today.isoformat()
        status,body=api('GET',path,actor=owner,role='OWNER',service='revenue-service')
        check(status==200 and sum(point['amount'] for point in body['data'])==1000000,'authorized report reads committed cash on its transaction day')
        owner_dates='?ownerId='+owner+'&from='+today.isoformat()+'&to='+today.isoformat()
        status,summary=api('GET','/api/reports/owner/summary'+owner_dates,actor=owner,role='OWNER',service='revenue-service')
        check(status==200 and summary.get('totalRevenue')==1000000 and summary.get('commissionRate') is not None,'actual owner summary keeps cash scope and authoritative rate')
        status,series=api('GET','/api/reports/owner/revenue'+owner_dates,actor=owner,role='OWNER',service='revenue-service')
        check(status==200 and series.get('totalRevenue')==1000000,'actual owner chart reads its own cash transactions')
        check(api('GET',path,actor=guest,role='CUSTOMER',service='revenue-service')[0]==403,'customer cannot read host financial report')
        # No provider network call: init builds a URL; test signs a callback in memory.
        status,online=create()
        check(status==201,'fresh booking for signed provider transaction-date regression')
        online_id=online['id'];ids.append(online_id)
        status,init=api('POST','/api/payments/vnpay/create?bookingId='+online_id)
        check(status==200,'provider initialization persists payment without contacting provider')
        env=config['services']['booking-service']['environment']
        params={'vnp_TxnRef':init['orderId'],'vnp_ResponseCode':'00','vnp_TransactionNo':str(int(uuid.uuid4().hex[:12],16)),
                'vnp_TmnCode':env['VNPAY_TMN_CODE'],'vnp_Amount':'100000000','vnp_TransactionStatus':'00','vnp_PayDate':'20260301000100'}
        def callback(fields):
            query=urllib.parse.urlencode(sorted(fields.items()))
            signature=hmac.new(env['VNPAY_HASH_SECRET'].encode(),query.encode(),hashlib.sha512).hexdigest()
            return api('GET','/api/payments/vnpay/return?'+query+'&vnp_SecureHash='+signature)
        check(callback({**params,'vnp_PayDate':'20260230000100'})[0]==400,'signed impossible provider date cannot mutate payment')
        check(callback(params)[0]==200,'delayed signed callback records its proven provider transaction time')
        wait(lambda:db('revenue',"SELECT count(*) FROM revenue_cash_transactions WHERE booking_id='"+online_id+"'")=='1','provider-dated cash event ingestion')
        check(db('booking',"SELECT transaction_occurred_at=TIMESTAMPTZ '2026-02-28T17:01:00Z' FROM payments WHERE booking_id='"+online_id+"'")=='t',
              'provider GMT+7 time is persisted independently of receipt processing time')
        status,dated=api('GET','/api/reports/revenue?hotelId='+hotel+'&from=2026-03-01&to=2026-03-01',actor=owner,role='OWNER',service='revenue-service')
        check(status==200 and sum(p['amount'] for p in dated['data'])==1000000,'delayed callback reports cash on provider day instead of callback day')
        check(callback(params)[0]==200 and callback({**params,'vnp_PayDate':'20260301000101'})[0]==409,
              'callback replay is idempotent and conflicting proven time requires reconciliation')
        check(db('revenue',"SELECT count(*) FROM revenue_cash_transactions WHERE booking_id='"+online_id+"'")=='1',
              'repeated signed callback cannot duplicate the cash movement')
        newer={'bookingId':order,'hotelId':hotel,'roomTypeId':room,'checkInDate':arrival,'checkOutDate':departure,'nights':1,'rooms':1,'bookingStatus':'CANCELLED','eventAt':'2027-01-01T12:00:00','eventVersion':20,'eventId':str(uuid.uuid4())}
        older={**newer,'bookingStatus':'CONFIRMED','eventAt':'2027-01-02T12:00:00','eventVersion':10,'eventId':str(uuid.uuid4())}
        def publish(event):
            check(broker('/api/exchanges/checkinn/hotel.events/publish',{'properties':{'content_type':'application/json'},'routing_key':'booking.status.changed','payload':json.dumps(event),'payload_encoding':'string'})['routed'],'synthetic ordered event routed')
        publish(newer);publish(older);publish(newer)
        wait(lambda:db('revenue',"SELECT count(*) FROM revenue_event_receipts WHERE booking_id='"+order+"'")=='2','ordered projection receipts')
        check(db('revenue',"SELECT booking_status||'|'||event_version FROM booking_status_records WHERE booking_id='"+order+"'")=='CANCELLED|20','late and duplicate events cannot resurrect a cancelled projection')
        check(db('revenue',"SELECT count(*) FROM booking_status_records WHERE booking_id='"+order+"'")=='1','replayed broker events keep one projection')
        publish({**newer,'eventId':str(uuid.uuid4()),'bookingStatus':'CONFIRMED'})
        wait(lambda:db('revenue',"SELECT count(*) FROM revenue_event_receipts WHERE booking_id='"+order+"' AND disposition='REVIEW_REQUIRED'")=='1','same-version conflict quarantine')
        check(db('revenue',"SELECT booking_status FROM booking_status_records WHERE booking_id='"+order+"'")=='CANCELLED','ambiguous same-version event is retained without overwriting state')
        # Fresh synthetic legacy row must be explicit reconciliation, not fake historical revenue.
        legacy=str(uuid.uuid4());ids.append(legacy)
        db('revenue',"INSERT INTO payment_records SELECT (jsonb_populate_record(NULL::payment_records,to_jsonb(p)||jsonb_build_object('id','"+str(uuid.uuid4())+"','booking_id','"+legacy+"','hotel_id','"+hotel+"','event_version',NULL,'last_event_id',NULL))).* FROM payment_records p LIMIT 1")
        status,body=api('GET',path,actor=owner,role='OWNER',service='revenue-service')
        check(status==503 and body.get('code')=='REPORT_RECONCILIATION_REQUIRED','legacy latest snapshots cannot masquerade as complete cash history')
    finally:
        if booking_stopped:h.run(h.COMPOSE+['start','booking-service'])
        if stopped:h.run(h.COMPOSE+['start','rabbitmq'])
        drop()
        db('booking',"DELETE FROM booking_event_outbox WHERE id='"+unroutable+"'")
        discovered=db('booking',"SELECT id FROM bookings WHERE user_id='"+guest+"' AND hotel_id='"+hotel+"'").splitlines()
        ids=list(set(ids+[str(uuid.UUID(value)) for value in discovered]+[order]))
        values="','".join(ids)
        # Wait for only this fixture's committed outbox work before removing its source.
        wait(lambda:db('booking',"SELECT count(*) FROM booking_event_outbox WHERE booking_id IN ('"+values+"') AND state='PENDING'")=='0','fixture event drain')
        time.sleep(2)
        db('booking',"DELETE FROM booking_event_outbox WHERE booking_id IN ('"+values+"'); DELETE FROM refund_requests WHERE booking_id IN ('"+values+"'); DELETE FROM payments WHERE booking_id IN ('"+values+"'); DELETE FROM booking_items WHERE booking_id IN ('"+values+"'); DELETE FROM bookings WHERE user_id='"+guest+"' AND hotel_id='"+hotel+"'")
        db('user',"DELETE FROM points_reservations WHERE user_id='"+guest+"'; DELETE FROM loyalty_commands WHERE user_id='"+guest+"'; DELETE FROM points_transactions WHERE user_id='"+guest+"'; DELETE FROM loyalty_points WHERE user_id='"+guest+"'")
        db('revenue',"DELETE FROM revenue_cashflow_gaps WHERE booking_id IN ('"+values+"'); DELETE FROM revenue_cash_transactions WHERE booking_id IN ('"+values+"'); DELETE FROM revenue_event_receipts WHERE booking_id IN ('"+values+"'); DELETE FROM payment_records WHERE booking_id IN ('"+values+"'); DELETE FROM booking_status_records WHERE booking_id IN ('"+values+"')")
        db('hotel',"DELETE FROM room_holds WHERE room_type_id='"+room+"'; DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
        # Delete only the exact generated recipient's Mailpit messages, never an empty-ID delete.
        with urllib.request.urlopen('http://127.0.0.1:8025/api/v1/search?query=to:'+email,timeout=10) as response:messages=json.load(response).get('messages',[])
        if messages:
            req=urllib.request.Request('http://127.0.0.1:8025/api/v1/messages',method='DELETE',headers={'Content-Type':'application/json'},data=json.dumps({'IDs':list({m['ID'] for m in messages})}).encode())
            with urllib.request.urlopen(req,timeout=10):pass
        check(db('booking',"SELECT count(*) FROM bookings WHERE user_id='"+guest+"'")=='0','exact synthetic booking/event/cash cleanup')
    print(json.dumps({'verified_cases':count,'failures':0}),flush=True)

if __name__=='__main__':
    try:main()
    except Exception as error:
        print(json.dumps({'result':'FAIL','error_type':type(error).__name__}),flush=True)
        raise SystemExit(1)
