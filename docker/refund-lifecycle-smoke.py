import importlib.util,pathlib,uuid,json
spec=importlib.util.spec_from_file_location('s',pathlib.Path('checkinn/docker/refund-gateway-smoke.py'));s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
def login(role):
 email=s.sql('checkinn_smoke_user',f"SELECT email FROM users WHERE role='{role}' ORDER BY created_at DESC LIMIT 1").strip();code,r=s.http('POST','/api/auth/login',{'usernameOrEmail':email,'password':s.PASSWORD});assert code==200;return r
c=login('CUSTOMER');a=login('ADMIN');h=str(uuid.uuid4());room=str(uuid.uuid4())
body=dict(hotelId=h,checkInDate='2026-12-20',checkOutDate='2026-12-21',adults=1,children=0,contactName='Lifecycle Smoke',contactEmail='lifecycle@example.invalid',contactPhone='0900000000',acceptedCancellationPolicyVersion='STANDARD_24H_V1',items=[dict(roomTypeId=room,ratePlanId=room,checkInDate='2026-12-20',checkOutDate='2026-12-21',quantity=1,unitPrice=1000000)])
code,r=s.http('POST','/api/bookings',body,c['token']);print(json.dumps(dict(case='booking_creation',http=code,response=r)),flush=True)
# Orphan binding: existing schema has no payment->booking FK. Isolate one seeded payment.
p=str(uuid.uuid4());missing=str(uuid.uuid4())
s.sql('checkinn_smoke_booking',f"INSERT INTO payments SELECT (jsonb_populate_record(NULL::payments,to_jsonb(p)||jsonb_build_object('id','{p}','booking_id','{missing}','status','COMPLETED','vnpay_order_id','{p}','vnpay_transaction_no','{uuid.uuid4().hex[:14]}'))).* FROM payments p LIMIT 1")
code,r=s.http('POST',f'/api/payments/{p}/refund',None,a['token']);print(json.dumps(dict(case='orphan_response',http=code,response=r)),flush=True);assert code==404
check=s.sql('checkinn_smoke_booking',f"SELECT status,(SELECT count(*) FROM manual_refund_executions WHERE payment_id='{p}'),(SELECT count(*) FROM booking_event_outbox WHERE booking_id='{missing}') FROM payments WHERE id='{p}'").strip();assert check=='COMPLETED|0|0'
print(json.dumps(dict(case='missing_booking',http=code,financial_state=check)),flush=True)
