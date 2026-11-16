"""Small synthetic no-oversell, compensation, policy-version and frontend-session probes."""
from concurrent.futures import ThreadPoolExecutor
import copy
import http.cookiejar
import json
import secrets
import urllib.parse
import urllib.request
import uuid
import acceptance_runtime as h

checks=[];out=h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/extended-hosted-results.json'
fixtures=json.loads((h.PRIVATE/'http-fixture-identifiers.json').read_text())
ids=json.loads((h.PRIVATE/'api-fixture-identifiers.json').read_text())
guest,hotel,room=fixtures['user'],fixtures['hotel'],fixtures['room']
admin=str(uuid.uuid4())


def check(ok,label,detail=None):
    checks.append({'case':label,'status':'PASS' if ok else 'FAIL',**({'detail':detail} if detail is not None else {})})
    out.write_text(json.dumps({'hosted':True,'checks':checks,'cleanup':'none'},indent=2)+'\n',encoding='utf-8')
    print(label+' '+checks[-1]['status'],flush=True)
    if not ok:raise AssertionError(label)


def request(date,quantity=1):
    code,policy=h.api('booking','/api/bookings/cancellation-policy')
    assert code==200
    return {'idempotencyKey':str(uuid.uuid4()),'userId':guest,'hotelId':hotel,'checkInDate':date,'checkOutDate':date[:-2]+str(int(date[-2:])+1).zfill(2),
            'adults':2,'children':0,'contactName':'Synthetic Batch6B','contactEmail':'synthetic@example.invalid','contactPhone':'0900000000','pointsToUse':0,
            'acceptedCancellationPolicyVersion':policy['version'],'items':[{'roomTypeId':room,'quantity':quantity,'checkInDate':date,'checkOutDate':date[:-2]+str(int(date[-2:])+1).zfill(2),'unitPrice':1000000}]}


def main():
    h.require_open_acceptance()
    bodies=[request('2027-03-10',2) for _ in range(2)]
    with ThreadPoolExecutor(max_workers=2) as pool:results=list(pool.map(lambda b:h.api('booking','/api/bookings','POST',b,subject=guest,timeout=60),bodies))
    codes=[x[0] for x in results]
    check(codes.count(201)==1 and sum(x in (400,409) for x in codes)==1,'concurrent inventory requests prevent oversell',codes)
    winning=next(x[1] for x in results if x[0]==201);index=codes.index(201)
    check(h.sql('hotel',f"SELECT coalesce(sum(held+booked),0) FROM room_availability WHERE room_type_id='{room}' AND date='2027-03-10'")=='2','inventory stays at two rooms under contention')
    changed=copy.deepcopy(bodies[index]);changed['items'][0]['quantity']=1
    code,_=h.api('booking','/api/bookings','POST',changed,subject=guest)
    check(code==409,'changed payload cannot reuse idempotency key',code)
    code,_=h.api('booking','/api/bookings/'+winning['id'],'DELETE',subject=guest)
    check(code==204,'unpaid cancellation compensates inventory',code)
    h.wait(lambda:h.sql('hotel',f"SELECT coalesce(sum(held+booked),0) FROM room_availability WHERE room_type_id='{room}' AND date='2027-03-10'")=='0','compensation releases held inventory')
    check(True,'held inventory released after compensation')
    key='BATCH6B_VERSION_'+secrets.token_hex(6).upper()
    body={'regulationKey':key,'name':'Synthetic version test','value':'1','description':'Synthetic retained policy','active':True,'version':0}
    code,created=h.api('regulations','/api/regulations/'+key,'PUT',body,role='ADMIN',subject=admin)
    check(code==200,'Regulations API create commits source and snapshot',code)
    body['value']='2';body['version']=created['version']
    code,updated=h.api('regulations','/api/regulations/'+key,'PUT',body,role='ADMIN',subject=admin)
    check(code==200 and updated['version']>created['version'],'Regulations version update',code)
    code,_=h.api('regulations','/api/regulations/'+key,'PUT',body,role='ADMIN',subject=admin)
    check(code==409,'stale policy version rejected',code)
    check(h.sql('regulations',f"SELECT count(*) FROM regulation_snapshots WHERE regulation_key='{key}'")=='2','version history has exactly two immutable snapshots')
    code,rows=h.api('regulations','/api/regulations/snapshots',role='ADMIN',subject=admin)
    check(code==200 and sum(x.get('regulationKey')==key for x in rows)==2,'policy snapshot API round trip',code)
    code,points=h.api('regulations','/api/regulations/typed/points',role='ADMIN',subject=admin)
    check(code==200,'seeded points policy lookup',code)
    code,_=h.api('gateway','/api/reports/admin/summary?from=2026-11-01&to=2026-11-30',role='ADMIN',subject=admin)
    check(code==200,'Revenue report query through Gateway',code)
    check(int(h.sql('revenue',f"SELECT count(*) FROM revenue_event_receipts WHERE booking_id='{fixtures['booking']}'"))>=2,'Revenue event receipts persisted for paid/refunded fixture')
    # Exercise actual frontend NextAuth credential proxy and signed session cookies.
    account=json.loads((h.PRIVATE/'auth-fixture.private.json').read_text())
    jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    with opener.open('http://localhost:3000/api/auth/csrf',timeout=30) as response:csrf=json.load(response)['csrfToken']
    form=urllib.parse.urlencode({'csrfToken':csrf,'email':account['email'],'password':account['password'],'callbackUrl':'http://localhost:3000','json':'true'}).encode()
    with opener.open(urllib.request.Request('http://localhost:3000/api/auth/callback/credentials',data=form,headers={'Content-Type':'application/x-www-form-urlencoded'}),timeout=40) as response:response.read()
    with opener.open('http://localhost:3000/api/auth/session',timeout=30) as response:session=json.load(response)
    check(session.get('user',{}).get('id')==guest and session.get('provider')=='credentials','Next.js NextAuth to Gateway/Auth/User/Supabase login')
    check(bool(session.get('accessToken')),'frontend session contains issued internal JWT')
    (h.PRIVATE/'extended-fixture-identifiers.json').write_text(json.dumps({'versionPolicy':key,'oversellBooking':winning['id']},indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':main()
