"""Batch 1.5 Gateway acceptance on fresh, project-scoped Docker resources only."""
import json, os, secrets, subprocess, sys, time, urllib.error, urllib.request, uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parent
ENV=ROOT/'.env.refund-smoke'
PROJECT='checkinn-refund-smoke'
COMPOSE=['docker','compose','-p',PROJECT,'--project-directory',str(ROOT),'--env-file',str(ENV),'-f',str(ROOT/'docker-compose.yml'),'-f',str(ROOT/'docker-compose.refund-smoke.yml'),'--profile','app']
PASSWORD='SmokePass-Refund-2026!'

def run(args, data=None, timeout=180):
    p=subprocess.run(args,input=data,capture_output=True,text=True,timeout=timeout)
    if p.returncode: raise RuntimeError('command failed: '+ ' '.join(args[:4]))
    return p.stdout
def sql(db,q): return run(COMPOSE+['exec','-T','postgres-smoke','psql','-X','-v','ON_ERROR_STOP=1','-U','smoke_runner','-d',db,'-Atc',q])
def apply(db,file): run(COMPOSE+['exec','-T','postgres-smoke','psql','-X','-v','ON_ERROR_STOP=1','-U','smoke_runner','-d',db],Path(file).read_text(),60)
def http(method,path,body=None,token=None):
    b=None if body is None else json.dumps(body).encode(); h={'Content-Type':'application/json'}
    if token:h['Authorization']='Bearer '+token
    r=urllib.request.Request('http://127.0.0.1:18080'+path,data=b,headers=h,method=method)
    try:
        with urllib.request.urlopen(r,timeout=25) as x:return x.status,json.loads(x.read() or b'null')
    except urllib.error.HTTPError as e:return e.code,json.loads(e.read() or b'null')
def wait(path='/actuator/health',secs=150):
    end=time.monotonic()+secs
    while time.monotonic()<end:
        try:
            if http('GET',path)[0]==200:return
        except Exception:pass
        time.sleep(2)
    raise RuntimeError('readiness timeout')
def check(ok,label):
    print(json.dumps({'case':label,'result':'PASS' if ok else 'FAIL'}),flush=True)
    if not ok: raise AssertionError(label)
def main():
    if not ENV.exists(): ENV.write_text('\n'.join([
      'REFUND_SMOKE_POSTGRES_PASSWORD='+secrets.token_urlsafe(24),'SECRET_KEY='+secrets.token_urlsafe(40),
      'RABBIT_USER=smoke_runner','RABBIT_PASS='+secrets.token_urlsafe(24),'GATEWAY_PORT=18080','EUREKA_PORT=18761','ADMIN_PORT=19095','RABBIT_MANAGEMENT_PORT=25672','SPRING_CLOUD_CONFIG_ENABLED=false','SPRING_CONFIG_IMPORT=',''])+'\n')
    # Each service starts from an empty named volume. Hibernate is only bootstrap; migrations below are real SQL.
    if os.environ.get('SMOKE_SKIP_UP')!='1':
        run(COMPOSE+['up','-d','--build','postgres-smoke','redis','rabbitmq','eureka-server','user-service','auth-service','booking-service','revenue-service','api-gateway'],timeout=900)
    wait(); wait('/api/auth/login') if False else None
    # JPA created baseline tables in empty smoke DBs. Apply business migrations in dependency order.
    for f in ['V3__Cancellation_Policy_And_Refund_Requests.sql']:
        apply('checkinn_smoke_booking',ROOT.parent/'booking-service/src/main/resources/db/migration'/f)
    for f in ['booking-idempotency-20261105.sql','onsite-reservation-v1-20261105.sql','paid-reservation-v1-20261105.sql','loyalty-settlement-upgrade.sql','booking-transaction-time-v1-20261107.sql','booking-event-outbox-v1-20261107.sql','manual-refund-v1-20261112.sql','sandbox-internal-refund-v1-20261112.sql']:
        apply('checkinn_smoke_booking',ROOT/'sql'/f)
    for f in ['revenue-source-versions-v1-20261107.sql','revenue-cash-transactions-v1-20261107.sql','revenue-event-receipts-v1-20261107.sql','revenue-history-gaps-v1-20261107.sql','revenue-sandbox-refund-v1-20261112.sql']:
        apply('checkinn_smoke_revenue',ROOT/'sql'/f)
    # Customer is created/login through Auth. Email verification is the legitimate persisted account state needed to login.
    tag=uuid.uuid4().hex[:12]; customer_email='customer-'+tag+'@example.invalid'; customer_name='customer'+tag
    s,r=http('POST','/api/auth/register',{'username':customer_name,'email':customer_email,'password':PASSWORD,'fullName':'Refund Smoke Customer'})
    check(s in (200,201),'customer registration through gateway')
    customer_id=r['userId']; sql('checkinn_smoke_user',"UPDATE users SET is_email_verified=true WHERE id='"+customer_id+"'")
    s,r=http('POST','/api/auth/login',{'usernameOrEmail':customer_email,'password':PASSWORD}); check(s==200 and r.get('role')=='CUSTOMER','customer login through gateway'); customer_token=r['token']
    # Test-only ADMIN provisioning is direct only to the fresh smoke database, with PostgreSQL bcrypt compatible with BCryptPasswordEncoder.
    admin_id=str(uuid.uuid4()); admin_email='admin-'+tag+'@example.invalid';
    sql('checkinn_smoke_user',"CREATE EXTENSION IF NOT EXISTS pgcrypto; INSERT INTO users(id,username,email,password,role,is_email_verified,is_active,created_at) VALUES ('"+admin_id+"','admin"+tag+"','"+admin_email+"',crypt('"+PASSWORD+"',gen_salt('bf')),'ADMIN',true,true,now())")
    s,r=http('POST','/api/auth/login',{'usernameOrEmail':admin_email,'password':PASSWORD}); check(s==200 and r.get('role')=='ADMIN','admin login through gateway'); admin_token=r['token']
    # SEEDED_COMPLETED_PAYMENT fixture: all booking/payment/refund invariants and receipt facts exist in fresh DB; no provider callback/API is used.
    booking_id=str(uuid.uuid4()); payment_id=str(uuid.uuid4()); hotel_id=str(uuid.uuid4()); room_id=str(uuid.uuid4()); now="now()"; amount='1000000.00'; receipt='smoke-original-'+tag; transaction_no=str(int(time.time()*1000000))[-15:]
    q=f"INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,cancellation_policy_version,check_in_at,free_cancellation_until,created_at,updated_at) VALUES ('{booking_id}','{customer_id}','{hotel_id}','Smoke Hotel','2026-12-20','2026-12-21',1,0,'CANCELLED',{amount},{amount},'Refund Smoke Customer','{customer_email}','0900000000','STANDARD_24H_V1',now()+interval '30 days',now()+interval '20 days',now(),now()); INSERT INTO booking_items(id,booking_id,room_type_id,room_type_name,rate_plan_id,check_in_date,check_out_date,quantity,unit_price,nights,subtotal) VALUES ('{uuid.uuid4()}','{booking_id}','{room_id}','Smoke Room','{room_id}','2026-12-20','2026-12-21',1,{amount},1,{amount}); INSERT INTO payments(id,booking_id,amount,method,status,transaction_id,vnpay_order_id,vnpay_response_code,vnpay_transaction_no,paid_at,transaction_occurred_at,created_at,updated_at) VALUES ('{payment_id}','{booking_id}',{amount},'VNPAY','COMPLETED','{receipt}','order-{tag}','00','{transaction_no}',now(),now(),now(),now())"
    sql('checkinn_smoke_booking',q)
    # Retain original CREDIT as a fixture; refund event must add exactly one immutable DEBIT.
    sql('checkinn_smoke_revenue',f"INSERT INTO revenue_cash_transactions(id,source_event_id,source_version,payment_id,kind,transaction_id,occurred_at,booking_id,customer_id,hotel_id,room_type_id,check_in_date,check_out_date,nights,rooms,amount,payment_method,execution_mode) VALUES ('{uuid.uuid4()}','{uuid.uuid4()}',1,'{payment_id}','CREDIT','{receipt}',now(),'{booking_id}','{customer_id}','{hotel_id}','{room_id}','2026-12-20','2026-12-21',1,1,{amount},'VNPAY',NULL)")
    s,r=http('POST',f'/api/payments/{payment_id}/refund-request',{'reason':'Smoke cancellation'},customer_token); check(s==201 and r['status']=='REQUESTED','customer refund request via gateway')
    s,r=http('POST',f'/api/payments/{payment_id}/refund-request/approve',None,admin_token); check(s==200 and r['status']=='APPROVED_PENDING_EXECUTION','admin approval via gateway')
    # Authorization before execute.
    check(http('POST',f'/api/payments/{payment_id}/refund',None,customer_token)[0]==403,'customer execution denied')
    s,r=http('POST',f'/api/payments/{payment_id}/refund',None,admin_token); check(s==200 and r['status']=='REFUNDED','admin execute via gateway')
    # Retry and concurrent HTTP execute: all responses remain successful replay, financial effects remain singleton.
    check(http('POST',f'/api/payments/{payment_id}/refund',None,admin_token)[0]==200,'idempotent gateway retry')
    import concurrent.futures
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex: codes=list(ex.map(lambda _:http('POST',f'/api/payments/{payment_id}/refund',None,admin_token)[0],range(2)))
    check(codes==[200,200],'concurrent gateway execution replay')
    end=time.monotonic()+60
    while time.monotonic()<end:
        debit=sql('checkinn_smoke_revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{payment_id}' AND kind='DEBIT'").strip()
        delivered=sql('checkinn_smoke_booking',f"SELECT count(*) FROM booking_event_outbox WHERE booking_id='{booking_id}' AND routing_key='payment.refunded' AND state='DELIVERED'").strip()
        if debit=='1' and delivered=='1':break
        time.sleep(.5)
    check(debit=='1' and delivered=='1','outbox broker revenue debit')
    facts=sql('checkinn_smoke_booking',f"SELECT p.status,b.status,b.paid_amount,p.vnpay_transaction_no,(SELECT status FROM refund_requests WHERE payment_id=p.id), (SELECT count(*) FROM manual_refund_audit a JOIN manual_refund_executions e ON a.execution_id=e.id WHERE e.payment_id=p.id) FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.id='{payment_id}'").strip().split('|')
    check(facts==['REFUNDED','CANCELLED','0.00',transaction_no,'REFUNDED','1'],'financial state and original receipt')
    check(sql('checkinn_smoke_revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{payment_id}'").strip()=='2','one credit plus one debit')
    # Recreate only smoke Booking/Revenue with the flag disabled, then use a distinct approved fixture.
    with ENV.open('a') as env: env.write('REFUND_SMOKE_INTERNAL_ENABLED=false\n')
    run(COMPOSE+['up','-d','--force-recreate','booking-service','revenue-service'],timeout=180)
    end=time.monotonic()+90
    while time.monotonic()<end:
        try:
            if run(COMPOSE+['exec','-T','booking-service','curl','-fsS','http://localhost:8084/actuator/health'],timeout=10): break
        except Exception: time.sleep(2)
    else: raise RuntimeError('disabled Booking service readiness timeout')
    disabled_booking=str(uuid.uuid4()); disabled_payment=str(uuid.uuid4())
    sql('checkinn_smoke_booking',f"INSERT INTO bookings(id,user_id,hotel_id,hotel_name,check_in_date,check_out_date,adults,children,status,total_amount,paid_amount,contact_name,contact_email,contact_phone,cancellation_policy_version,check_in_at,free_cancellation_until,created_at,updated_at) VALUES ('{disabled_booking}','{customer_id}','{hotel_id}','Smoke Hotel','2026-12-22','2026-12-23',1,0,'CANCELLED',{amount},{amount},'Refund Smoke Customer','{customer_email}','0900000000','STANDARD_24H_V1',now()+interval '30 days',now()+interval '20 days',now(),now()); INSERT INTO booking_items(id,booking_id,room_type_id,room_type_name,rate_plan_id,check_in_date,check_out_date,quantity,unit_price,nights,subtotal) VALUES ('{uuid.uuid4()}','{disabled_booking}','{room_id}','Smoke Room','{room_id}','2026-12-22','2026-12-23',1,{amount},1,{amount}); INSERT INTO payments(id,booking_id,amount,method,status,transaction_id,vnpay_order_id,vnpay_response_code,vnpay_transaction_no,paid_at,transaction_occurred_at,created_at,updated_at) VALUES ('{disabled_payment}','{disabled_booking}',{amount},'VNPAY','COMPLETED','disabled-{tag}','disabled-order-{tag}','00','123456789013',now(),now(),now(),now())")
    check(http('POST',f'/api/payments/{disabled_payment}/refund-request',{'reason':'Disabled flag'},customer_token)[0]==201,'disabled fixture refund request')
    check(http('POST',f'/api/payments/{disabled_payment}/refund-request/approve',None,admin_token)[0]==200,'disabled fixture approval')
    check(http('POST',f'/api/payments/{disabled_payment}/refund',None,admin_token)[0]==503,'disabled flag denies execution')
    check(sql('checkinn_smoke_booking',f"SELECT status FROM payments WHERE id='{disabled_payment}'").strip()=='COMPLETED' and sql('checkinn_smoke_booking',f"SELECT count(*) FROM booking_event_outbox WHERE booking_id='{disabled_booking}' AND routing_key='payment.refunded'").strip()=='0','disabled flag leaves no financial mutation')
    print(json.dumps({'result':'PASS','project':PROJECT,'payment_fixture':'SEEDED_COMPLETED_PAYMENT'}))
if __name__=='__main__':
    try: main()
    except Exception as e: print(json.dumps({'result':'FAIL','type':type(e).__name__})); raise
