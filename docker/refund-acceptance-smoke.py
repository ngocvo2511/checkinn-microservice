"""Batch 1.6: isolated Gateway acceptance; no provider requests or JWT fabrication."""
from decimal import Decimal
from zoneinfo import ZoneInfo
import importlib.util,pathlib,uuid,threading,concurrent.futures,time,json,socket,http.server,urllib.request,datetime
spec=importlib.util.spec_from_file_location('s',pathlib.Path(__file__).with_name('refund-gateway-smoke.py'));s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
results=[]
def evidence(case,**facts):
    row=dict(case=case,**facts);results.append(row);print(json.dumps(row),flush=True)
def login(role):
    email=s.sql('checkinn_smoke_user',f"SELECT email FROM users WHERE role='{role}' ORDER BY created_at DESC LIMIT 1").strip()
    code,r=s.http('POST','/api/auth/login',{'usernameOrEmail':email,'password':s.PASSWORD});assert code==200 and r['role']==role;return r
# Fail before mutation unless database container AND all named mounts belong to this smoke project.
info=json.loads(s.run(['docker','inspect','checkinn-refund-smoke-postgres-smoke-1']))[0]
assert info['Config']['Labels']['com.docker.compose.project']==s.PROJECT
for mount in info['Mounts']:
    if mount['Type']=='volume':assert json.loads(s.run(['docker','volume','inspect',mount['Name']]))[0]['Labels']['com.docker.compose.project']==s.PROJECT
s.wait();c=login('CUSTOMER');a=login('ADMIN')
tag=uuid.uuid4().hex[:12];owner=str(uuid.uuid4())
s.sql('checkinn_smoke_user',f"INSERT INTO users(id,username,email,password,role,is_email_verified,is_active,created_at) VALUES ('{owner}','owner{tag}','owner-{tag}@example.invalid',crypt('{s.PASSWORD}',gen_salt('bf')),'OWNER',true,true,now())")
o=login('OWNER')
def fixture():
    b,p,h=str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4());receipt='final-'+p;txn=uuid.uuid4().hex[:14]
    s.sql('checkinn_smoke_booking',f"INSERT INTO bookings SELECT (jsonb_populate_record(NULL::bookings,to_jsonb(b)||jsonb_build_object('id','{b}','hotel_id','{h}','user_id','{c['userId']}','paid_amount',1000000,'status','CANCELLED','idempotency_key_hash',NULL))).* FROM bookings b LIMIT 1; INSERT INTO payments SELECT (jsonb_populate_record(NULL::payments,to_jsonb(p)||jsonb_build_object('id','{p}','booking_id','{b}','status','COMPLETED','amount',1000000,'transaction_id','{receipt}','vnpay_order_id','{p}','vnpay_transaction_no','{txn}'))).* FROM payments p LIMIT 1; INSERT INTO booking_items SELECT (jsonb_populate_record(NULL::booking_items,to_jsonb(i)||jsonb_build_object('id','{uuid.uuid4()}','booking_id','{b}'))).* FROM booking_items i LIMIT 1;")
    s.sql('checkinn_smoke_revenue',f"INSERT INTO revenue_cash_transactions SELECT (jsonb_populate_record(NULL::revenue_cash_transactions,to_jsonb(t)||jsonb_build_object('id','{uuid.uuid4()}','source_event_id','{uuid.uuid4()}','payment_id','{p}','booking_id','{b}','hotel_id','{h}','customer_id','{c['userId']}','transaction_id','{receipt}','amount',1000000,'occurred_at',now()))).* FROM revenue_cash_transactions t WHERE kind='CREDIT' LIMIT 1")
    assert s.http('POST',f'/api/payments/{p}/refund-request',{'reason':'Batch 1.6 isolated fixture'},c['token'])[0]==201
    return b,p,h,receipt,txn
def approve(f):assert s.http('POST',f'/api/payments/{f[1]}/refund-request/approve',None,a['token'])[0]==200
def execute(f,token=None):return s.http('POST',f'/api/payments/{f[1]}/refund',None,token or a['token'])
def facts(f):
    b,p,h,receipt,txn=f
    return s.sql('checkinn_smoke_booking',f"SELECT p.status,b.status,b.paid_amount,r.status,p.transaction_id,p.vnpay_transaction_no,(SELECT count(*) FROM manual_refund_executions WHERE payment_id=p.id AND state='REFUNDED'),(SELECT count(*) FROM manual_refund_audit x JOIN manual_refund_executions e ON e.id=x.execution_id WHERE e.payment_id=p.id AND x.action='SANDBOX_INTERNAL_REFUNDED'),(SELECT count(*) FROM booking_event_outbox WHERE booking_id=b.id AND routing_key='payment.refunded') FROM payments p JOIN bookings b ON b.id=p.booking_id JOIN refund_requests r ON r.payment_id=p.id WHERE p.id='{p}'").strip().split('|')
def settled(f):
    b,p,h,receipt,txn=f;end=time.monotonic()+60
    while time.monotonic()<end:
        revenue=s.sql('checkinn_smoke_revenue',f"SELECT count(*),count(*) FILTER(WHERE kind='CREDIT'),count(*) FILTER(WHERE kind='DEBIT'),sum(CASE WHEN kind='CREDIT' THEN amount ELSE -amount END),count(*) FILTER(WHERE kind='DEBIT' AND execution_mode='SANDBOX_INTERNAL'),count(*) FILTER(WHERE kind='CREDIT' AND transaction_id='{receipt}') FROM revenue_cash_transactions WHERE payment_id='{p}'").strip().split('|')
        delivered=s.sql('checkinn_smoke_booking',f"SELECT count(*) FROM booking_event_outbox WHERE booking_id='{b}' AND routing_key='payment.refunded' AND state='DELIVERED'").strip()
        if revenue[:3]==['2','1','1'] and Decimal(revenue[3])==0 and revenue[4:]==['1','1'] and delivered=='1':break
        time.sleep(.3)
    assert revenue[:3]==['2','1','1'] and Decimal(revenue[3])==0 and revenue[4:]==['1','1'] and delivered=='1',(revenue,delivered)
    state=facts(f);assert state==['REFUNDED','CANCELLED','0.00','REFUNDED',receipt,txn,'1','1','1'],state
    evidence('settled',booking=b,payment=p,hotel=h,state=state,revenue=revenue,delivered=delivered)
    today=datetime.datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).date().isoformat();code,r=s.http('GET',f'/api/reports/revenue?hotelId={h}&from={today}&to={today}',None,a['token'])
    evidence('revenue_report',payment=p,http=code,response=r)
    assert code==200 and r['data'] and all(float(x['amount'])==0 for x in r['data']),r
f=fixture();approve(f);assert facts(f)[3]=='APPROVED_PENDING_EXECUTION'
barrier=threading.Barrier(2)
def client(i):
    barrier.wait();start=time.monotonic_ns();code,_=execute(f);return dict(client=i,start_ns=start,end_ns=time.monotonic_ns(),http=code)
with concurrent.futures.ThreadPoolExecutor(2) as pool:responses=list(pool.map(client,range(2)))
evidence('true_concurrency',initial='APPROVED_PENDING_EXECUTION',clients=responses);assert all(r['http'] in (200,409) for r in responses) and any(r['http']==200 for r in responses);settled(f)
f=fixture();approve(f)
for role,token,expected in [('ANONYMOUS',None,401),('CUSTOMER',c['token'],403),('OWNER',o['token'],403)]:
    before=facts(f);code,_=s.http('POST',f'/api/payments/{f[1]}/refund',None,token);assert code==expected and facts(f)==before;evidence('authorization',role=role,http=code,unchanged=True)
assert execute(f)[0]==200;settled(f);before=facts(f);assert execute(f)[0]==200 and facts(f)==before;evidence('refunded_replay',http=200,unchanged=True)
code,_=s.http('POST',f'/api/payments/{uuid.uuid4()}/refund',None,a['token']);assert code==404;evidence('missing_payment',http=code)
for kind in ['unapproved','rejected','unpaid','binding','booking','paid_amount']:
    f=fixture();b,p,*_=f
    if kind!='unapproved' and kind!='rejected':approve(f)
    if kind=='rejected':assert s.http('POST',f'/api/payments/{p}/refund-request/reject',None,a['token'])[0]==200
    queries={'unpaid':f"UPDATE payments SET status='PENDING' WHERE id='{p}'",'binding':f"UPDATE refund_requests SET booking_id='invalid-binding' WHERE payment_id='{p}'",'booking':f"UPDATE bookings SET status='CONFIRMED' WHERE id='{b}'",'paid_amount':f"UPDATE bookings SET paid_amount=0 WHERE id='{b}'"}
    if kind in queries:s.sql('checkinn_smoke_booking',queries[kind])
    before=facts(f);code,r=execute(f);assert code==409 and facts(f)==before,(kind,code,r)
    assert s.sql('checkinn_smoke_revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{p}' AND kind='DEBIT'").strip()=='0'
    evidence('invalid_'+kind,http=code,unchanged=True)
# Authenticated customer accessing a distinct customer's payment.
s.sql('checkinn_smoke_booking',f"UPDATE bookings SET user_id='{uuid.uuid4()}' WHERE id='{b}'")
code,_=s.http('POST',f'/api/payments/{p}/refund-request',{'reason':'foreign'},c['token']);assert code==403;evidence('foreign_customer',http=code)
# Test-only forwarding proxy consumes upstream response, then closes without sending HTTP response.
f=fixture();approve(f);observed={}
class Drop(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        observed['upstream_http']=execute(f)[0];observed['commit_state']=facts(f);self.close_connection=True;self.connection.shutdown(socket.SHUT_RDWR);self.connection.close()
    def log_message(self,*args):pass
server=http.server.HTTPServer(('127.0.0.1',0),Drop);thread=threading.Thread(target=server.handle_request);thread.start()
try:
    urllib.request.urlopen(urllib.request.Request(f'http://127.0.0.1:{server.server_port}/execute',data=b'',method='POST'),timeout=30);raise AssertionError('response was not lost')
except (ConnectionError,__import__('http.client',fromlist=['RemoteDisconnected']).RemoteDisconnected) as e:observed['client_error']=type(e).__name__
thread.join(30);server.server_close();assert observed['upstream_http']==200 and observed['commit_state'][0]=='REFUNDED';assert execute(f)[0]==200;evidence('lost_response_after_commit',**observed,retry_http=200);settled(f)
path=pathlib.Path(__file__).parents[2]/'BATCH1_6_gateway_evidence.json';path.write_text(json.dumps(results,indent=2));print('BATCH1_6_GATEWAY_PASS')
