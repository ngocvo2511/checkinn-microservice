"""Additional Batch 1.6 cases; only the verified disposable Batch 1.5 project."""
import importlib.util,pathlib,uuid,threading,concurrent.futures,time,json
spec=importlib.util.spec_from_file_location('s',pathlib.Path(__file__).with_name('refund-gateway-smoke.py'));s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
def login(role):
    email=s.sql('checkinn_smoke_user',f"SELECT email FROM users WHERE role='{role}' ORDER BY created_at DESC LIMIT 1").strip()
    code,r=s.http('POST','/api/auth/login',{'usernameOrEmail':email,'password':s.PASSWORD});s.check(code==200,'login '+role);return r
c=login('CUSTOMER');a=login('ADMIN')
def fixture():
    b,p=str(uuid.uuid4()),str(uuid.uuid4())
    s.sql('checkinn_smoke_booking',f"INSERT INTO bookings SELECT (jsonb_populate_record(NULL::bookings,to_jsonb(b)||jsonb_build_object('id','{b}','user_id','{c['userId']}','paid_amount',1000000,'status','CANCELLED','idempotency_key_hash',NULL))).* FROM bookings b LIMIT 1; INSERT INTO payments SELECT (jsonb_populate_record(NULL::payments,to_jsonb(p)||jsonb_build_object('id','{p}','booking_id','{b}','status','COMPLETED','vnpay_order_id','{p}','vnpay_transaction_no','{uuid.uuid4().hex[:14]}'))).* FROM payments p LIMIT 1;")
    code,r=s.http('POST',f'/api/payments/{p}/refund-request',{'reason':'Final acceptance'},c['token']);s.check(code==201,'new isolated request');return b,p
for kind in ['unapproved','rejected','unpaid','binding','booking']:
    b,p=fixture()
    if kind=='rejected':s.http('POST',f'/api/payments/{p}/refund-request/reject',None,a['token'])
    if kind=='unpaid':s.sql('checkinn_smoke_booking',f"UPDATE payments SET status='PENDING' WHERE id='{p}'")
    if kind=='binding':s.sql('checkinn_smoke_booking',f"UPDATE refund_requests SET booking_id='invalid-binding' WHERE payment_id='{p}'")
    if kind=='booking':s.sql('checkinn_smoke_booking',f"UPDATE bookings SET status='CONFIRMED' WHERE id='{b}'")
    code,_=s.http('POST',f'/api/payments/{p}/refund',None,a['token']);s.check(code==409,kind+' rejected 409')
    s.check(s.sql('checkinn_smoke_booking',f"SELECT count(*) FROM booking_event_outbox WHERE booking_id='{b}' AND routing_key='payment.refunded'").strip()=='0',kind+' no outbox')
print(json.dumps({'result':'PASS','scope':'five invalid-state Gateway cases'}))
