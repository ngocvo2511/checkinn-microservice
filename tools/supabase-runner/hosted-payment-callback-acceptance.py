"""Local signed callback simulation using an ephemeral fake merchant key. Never contacts VNPay."""
import datetime
import hashlib
import hmac
import json
import secrets
import urllib.parse
import uuid
import acceptance_runtime as h

checks=[];out=h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/payment-callback-hosted-results.json'
fixture=json.loads((h.PRIVATE/'http-fixture-identifiers.json').read_text());guest=fixture['user'];admin=str(uuid.uuid4());test_transaction=str(1000000000000+secrets.randbelow(8000000000000))


def check(ok,label,code=None):
    checks.append({'case':label,'status':'PASS' if ok else 'FAIL',**({'httpStatus':code} if code is not None else {})})
    out.write_text(json.dumps({'hostedDatabase':True,'callbackTransport':'local HTTP only','merchant':'ephemeral fake test key','vnPayProviderContacted':False,'realRefundProviderCalled':False,'checks':checks},indent=2)+'\n',encoding='utf-8')
    print(label+' '+checks[-1]['status'],flush=True)
    if not ok:raise AssertionError(label)


def booking(date):
    code,policy=h.api('booking','/api/bookings/cancellation-policy');assert code==200
    body={'idempotencyKey':str(uuid.uuid4()),'userId':guest,'hotelId':fixture['hotel'],'checkInDate':date,'checkOutDate':date[:-2]+str(int(date[-2:])+1).zfill(2),
      'adults':2,'children':0,'contactName':'Synthetic callback','contactEmail':'synthetic@example.invalid','contactPhone':'0900000000','pointsToUse':0,'acceptedCancellationPolicyVersion':policy['version'],
      'items':[{'roomTypeId':fixture['room'],'quantity':1,'checkInDate':date,'checkOutDate':date[:-2]+str(int(date[-2:])+1).zfill(2),'unitPrice':1000000}]}
    code,result=h.api('booking','/api/bookings','POST',body,subject=guest,timeout=60)
    check(code==201,'callback fixture Booking creation',code)
    return result['id']


def init(bid):
    code,result=h.api('booking','/api/payments/vnpay/create?bookingId='+bid,'POST',subject=guest,timeout=45)
    check(code==200,'VNPay attempt initialization without navigating provider URL',code)
    check(result['redirectUrl'].startswith('https://sandbox.vnpayment.vn/'),'redirect targets sandbox only; URL not requested')
    check(uuid.UUID(result['orderId']).version==4,'payment attempt UUIDv4')
    return result['orderId']


def callback(order,success=True,transaction=None,amount='100000000',bad_signature=False):
    transaction=transaction or test_transaction
    params={'vnp_TxnRef':order,'vnp_ResponseCode':'00' if success else '24','vnp_TransactionNo':transaction if success else str(int(test_transaction)+2),
      'vnp_Amount':amount,'vnp_TransactionStatus':'00' if success else '02','vnp_TmnCode':'B6BTEST1',
      'vnp_PayDate':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=7))).strftime('%Y%m%d%H%M%S')}
    # Freeze timestamps per attempt so a duplicate means the identical provider receipt.
    callback.times.setdefault(order,params['vnp_PayDate']);params['vnp_PayDate']=callback.times[order]
    encoded=urllib.parse.urlencode(sorted(params.items()))
    params['vnp_SecureHash']='invalid' if bad_signature else hmac.new(h.stack.values()['BATCH6B_TEST_VNPAY_HASH_SECRET'].encode(),encoded.encode(),hashlib.sha512).hexdigest()
    return h.api('booking','/api/payments/vnpay/return?'+urllib.parse.urlencode(params),anonymous=True,timeout=50)
callback.times={}


def main():
    h.require_open_acceptance()
    if h.stack.values().get('SUPABASE_DEV_VNPAY_MODE','simulated')!='simulated':
        raise RuntimeError('Local fake callbacks must never target the configured real Sandbox merchant')
    bid=booking('2027-05-'+str(14+secrets.randbelow(9)));first=init(bid)
    code,body=callback(first,success=False)
    check(code==200 and body.get('status')=='FAILED','signed failed callback preserves failed attempt for retry',code)
    second=init(bid);check(second!=first,'retry creates a distinct payment attempt')
    code,_=callback(second,bad_signature=True);check(code==400,'invalid callback signature rejected',code)
    code,_=callback(second,amount='1');check(code==400,'signed callback amount mismatch rejected',code)
    code,paid=callback(second);check(code==200 and paid.get('status')=='COMPLETED','signed successful callback completes hosted payment',code)
    original=h.sql('booking',f"SELECT json_build_object('transaction_id',transaction_id,'vnpay_transaction_no',vnpay_transaction_no,'amount',amount,'paid_at',paid_at,'transaction_occurred_at',transaction_occurred_at) FROM payments WHERE id='{second}'")
    code,again=callback(second);check(code==200 and again.get('id')==second,'identical signed callback is idempotent',code)
    code,_=callback(second,transaction=str(int(test_transaction)+1));check(code==404,'conflicting receipt rejected with existing HTTP404 callback contract',code)
    check(h.sql('booking',f"SELECT status FROM payments WHERE id='{first}'")=='FAILED','original failed attempt remains preserved after retry')
    h.wait(lambda:h.sql('revenue',f"SELECT count(*) FROM revenue_cash_transactions WHERE payment_id='{second}' AND kind='CREDIT'")=='1','one CREDIT after callback replay',80)
    check(True,'callback replay produces exactly one Revenue CREDIT')
    code,_=h.api('booking','/api/bookings/'+bid+'/cancel-reviewed','POST',{'reason':'Synthetic rejected refund acceptance'},role='ADMIN',subject=admin)
    check(code==204,'paid callback Booking reviewed cancellation',code)
    code,_=h.api('booking','/api/payments/'+second+'/refund-request','POST',{'reason':'Synthetic rejection case'},subject=guest)
    check(code==201,'refund rejection fixture request',code)
    code,rejected=h.api('booking','/api/payments/'+second+'/refund-request/reject','POST',role='ADMIN',subject=admin)
    check(code==200 and rejected.get('status')=='REJECTED','ADMIN refund rejection',code)
    code,_=h.api('booking','/api/payments/'+second+'/refund','POST',role='ADMIN',subject=admin)
    check(code==409,'rejected refund cannot execute internal transfer',code)
    check(h.sql('booking',f"SELECT count(*) FROM manual_refund_executions WHERE payment_id='{second}'")=='0','rejected refund creates no execution')
    check(h.sql('booking',f"SELECT json_build_object('transaction_id',transaction_id,'vnpay_transaction_no',vnpay_transaction_no,'amount',amount,'paid_at',paid_at,'transaction_occurred_at',transaction_occurred_at) FROM payments WHERE id='{second}'")==original,'callback payment receipt survives rejected refund exactly')
    expired=booking('2027-06-12')
    h.sql('booking',f"UPDATE bookings SET hold_expires_at=now()-interval '1 minute' WHERE id='{expired}'",fixture=True)
    code,_=h.api('booking','/api/payments/vnpay/create?bookingId='+expired,'POST',subject=guest)
    check(code==409,'expired inventory hold cannot start a payment',code)
    check(h.sql('booking',f"SELECT count(*) FROM payments WHERE booking_id='{expired}'")=='0','expired attempt creates no payment rows')
    code,_=h.api('booking','/api/bookings/'+expired,'DELETE',subject=guest)
    check(code==204,'expired synthetic Booking cancelled through normal API',code)
    (h.PRIVATE/'callback-fixture-identifiers.json').write_text(json.dumps({'booking':bid,'failedPayment':first,'completedPayment':second,'expiredBooking':expired},indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':main()
