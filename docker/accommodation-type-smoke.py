"""Full accommodation workflow on generated UUIDs in the verified existing clone only."""
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from urllib.parse import urlencode
s=importlib.util.spec_from_file_location('h',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
def run(args,data=None):
    p=subprocess.run(args,input=data,capture_output=True,text=True,encoding='utf-8',timeout=45)
    if p.returncode: raise RuntimeError('Isolated accommodation command failed')
    return p.stdout.strip()
h.run=run

def main():
    h.verify_running_clone('hotel-service')
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    assert config['services']['hotel-service']['environment']['SPRING_DATASOURCE_URL']=='jdbc:postgresql://postgres-test:5432/checkinn_hotel'
    cid=h.run(h.COMPOSE+['ps','-q','hotel-service'])
    key=config['services']['hotel-service']['environment']['SECRET_KEY']
    owner,admin,other=[str(uuid.uuid4()) for _ in range(3)]
    tokens={'owner':h.token(key,owner,'OWNER'),'admin':h.token(key,admin,'ADMIN'),'other':h.token(key,other,'OWNER')}
    city=str(uuid.UUID(h.sql('checkinn_hotel','SELECT id FROM cities ORDER BY id LIMIT 1')))
    ids=[str(uuid.uuid4()) for _ in range(6)];roomids=[str(uuid.uuid4()) for _ in ids];created=[]
    marker='accommodation-test-'+str(uuid.uuid4())
    fingerprint="SELECT md5(coalesce(string_agg(to_jsonb(h)::text,'' ORDER BY id),'')) FROM hotels h"
    before=h.sql('checkinn_hotel',fingerprint)
    checks=0
    def check(value,name):
        nonlocal checks
        checks+=1
        print(json.dumps({'case':name,'result':'PASS' if value else 'FAIL'}),flush=True)
        if not value: raise AssertionError(name)
    def call(path,method='GET',body=None,who=None,gateway=False):
        base='http://api-gateway:8080/api/hotel' if gateway else 'http://localhost:8083'
        headers='Content-Type: application/json\n'+('Authorization: Bearer '+tokens[who]+'\n' if who else '')
        command=['docker','exec','-i',cid,'curl','-sS','--max-time','20','-X',method,'-H','@-','-w','\n%{http_code}',base+path]
        if body is not None: command+=['--data-binary',json.dumps(body)]
        result=h.run(command,headers);data,code=result.rsplit('\n',1) if '\n' in result else ('',result)
        try:data=json.loads(data)
        except ValueError:pass
        return int(code),data
    def propose(code,hotel=None,who='owner'):
        return call('/hotels/'+(hotel or ids[0])+'/accommodation-type-changes','POST',{'accommodationType':code},who)
    def review(request,approve=True,who='admin'):
        return call('/hotels/accommodation-type-changes/'+request+'/'+('approve' if approve else 'reject'),'POST',{'reason':'Synthetic classification reason'},who)
    def public_type(hotel=ids[0]):return call('/hotels/'+hotel)[1].get('accommodationType')
    def search(types=None,**extra):
        params=[('hotelName',marker)]+[('accommodationTypes',x) for x in types or []]+list(extra.items())
        return call('/hotels/offers?'+urlencode(params))
    def raced(actions):
        # All HTTP actions queue behind a real row lock, then contend for the same hotel.
        with ThreadPoolExecutor(max_workers=len(actions)+1) as pool:
            lock=pool.submit(h.sql,'checkinn_hotel',"BEGIN; SELECT id FROM hotels WHERE id='"+ids[0]+"' FOR UPDATE; SELECT pg_sleep(3) /*accommodation-race-lock*/; COMMIT;")
            for _ in range(30):
                if h.sql('checkinn_hotel',"SELECT count(*) FROM pg_stat_activity WHERE pid<>pg_backend_pid() AND state='active' AND query LIKE '%/*accommodation-race-lock*/%'")!='0':break
                time.sleep(.05)
            results=[pool.submit(action) for action in actions]
            lock.result()
            return [r.result() for r in results]
    for _ in range(30):
        if call('/hotels/accommodation-types',gateway=True)[0]==200: break
        time.sleep(2)
    else: raise RuntimeError('Gateway discovery did not converge before fixture creation')
    try:
        for i,(hotel,room) in enumerate(zip(ids,roomids)):
            code=['HOTEL','VILLA','APARTMENT','GUEST_HOUSE',None,'HOTEL'][i]
            data=json.dumps({'id':hotel,'owner_id':owner,'city_id':city,'name':marker+'-'+str(i),'is_active':True,'approved_status':'APPROVED','accommodation_type':code,'revision':0,'business_license_file_path':None,'operation_license_file_path':None,'owner_identity_front_path':None,'owner_identity_back_path':None})
            count=h.sql('checkinn_hotel',"WITH x AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||'"+data+"'::jsonb)).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM x")
            check(count=='1','generated hotel '+str(i))
            data=json.dumps({'id':room,'hotel_id':hotel,'name':'Synthetic room','base_price':200000,'total_rooms':2,'capacity':{'adults':2,'children':1},'amenities':[],'is_active':True})
            count=h.sql('checkinn_hotel',"WITH x AS (INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||'"+data+"'::jsonb)).* FROM room_types r LIMIT 1 RETURNING id) SELECT count(*) FROM x")
            check(count=='1','generated room '+str(i))
        # copied fields and foreign rows are never changed; only generated IDs below are written.
        for gateway in [False,True]:
            code,data=call('/hotels/accommodation-types',gateway=gateway)
            check(code==200 and [(x['code'],x['label']) for x in data]==[('HOTEL','Hotel'),('VILLA','Villa'),('APARTMENT','Apartment'),('GUEST_HOUSE','Guest House')],'catalog direct/gateway '+str(gateway))
        payload={'name':marker+'-new','cityId':city,'address':{'street':'Synthetic street','city':'Synthetic city','country':'Vietnam'},'businessLicenseNumber':'Synthetic','taxId':'Synthetic','ownerIdentityNumber':'Synthetic'}
        for v in [None,'','ALIEN',123,True,{},[]]:
            code,_=call('/hotels','POST',{**payload,'accommodationType':v},'owner')
            check(code==400,'new draft rejects null/invalid type '+str(type(v).__name__))
        check(call('/hotels','POST',payload,'owner')[0]==400,'new draft rejects omitted type')
        for v in ['HOTEL','VILLA','APARTMENT','GUEST_HOUSE']:
            code,data=call('/hotels','POST',{**payload,'accommodationType':v},'owner',True)
            if code==200:created.append(str(uuid.UUID(data['id'])))
            check(code==200 and data['accommodationType']==v and data['approvedStatus']=='DRAFT','create draft round trip '+v)
        code,data=search();check(code==200 and len(data)==6,'legacy NULL included when unfiltered')
        for types,expected in [(['HOTEL'],2),(['VILLA','APARTMENT'],2),(['GUEST_HOUSE'],1),(['HOTEL','VILLA','APARTMENT','GUEST_HOUSE'],5)]:
            code,data=search(types);check(code==200 and len(data)==expected,'OR filter '+','.join(types))
        check(search(['HOTEL'],maxPrice=199999)[1]==[],'type AND price retains same-room constraint')
        check(search(['HOTEL'],adults=5)[1]==[],'type AND capacity')
        check(search(['HOTEL'],cityId=str(uuid.uuid4()))[1]==[],'type AND stable destination')
        check(search(['HOTEL'],checkIn='2027-01-10',checkOut='2027-01-12')[0]==200,'type AND dated room offers')
        for types in [['ALIEN'],['HOTEL','ALIEN'],['']]:check(search(types)[0]==400,'invalid filter is400')
        check(propose('VILLA',who=None)[0]==401,'anonymous cannot propose')
        check(propose('VILLA',who='other')[0]==403,'cross-owner cannot propose')
        check(call('/hotels/'+ids[0]+'/accommodation-type-changes')[0]==401,'history requires authentication')
        check(call('/hotels/'+ids[0]+'/accommodation-type-changes',who='other')[0]==403,'private history cross-owner forbidden')
        check(call('/hotels/accommodation-type-changes/pending',who='owner')[0]==403,'owner cannot read admin queue')
        results=raced([lambda:propose('VILLA') for _ in range(4)])
        check(all(x[0]==200 for x in results) and len({x[1]['id'] for x in results})==1,'concurrent identical proposals share one pending request')
        first=results[0][1]['id'];check(public_type()=='HOTEL','proposal keeps public HOTEL')
        code,data=call('/hotels/'+ids[0]);check('requestedType' not in json.dumps(data) and 'requesterId' not in json.dumps(data),'public DTO contains no pending metadata')
        check(len(search(['VILLA'])[1])==1,'pending VILLA does not leak into search')
        check(review(first,who='owner')[0]==403,'owner cannot approve')
        code,new=propose('APARTMENT');second=new['id'];check(code==200 and second!=first,'replacement creates new request')
        check(review(first)[0]==409,'superseded approval is409')
        check(review(second,False)[0]==200 and public_type()=='HOTEL','rejection preserves published type')
        check(review(second,False)[0]==200,'duplicate rejection is idempotent')
        code,new=propose('VILLA');third=new['id']
        # Simulate the separately authorized manual DB classification only on this fresh fixture.
        h.sql('checkinn_hotel',"UPDATE hotels SET accommodation_type='GUEST_HOUSE' WHERE id='"+ids[0]+"'")
        check(review(third)[0]==409 and public_type()=='GUEST_HOUSE','manual snapshot mismatch blocks stale approval')
        code,new=propose('APARTMENT');fourth=new['id']
        revision=int(h.sql('checkinn_hotel',"SELECT revision FROM hotels WHERE id='"+ids[0]+"'"))
        results=raced([lambda:review(fourth),lambda:review(fourth)])
        check(all(x[0]==200 for x in results) and public_type()=='APARTMENT','concurrent identical approval applies once')
        check(int(h.sql('checkinn_hotel',"SELECT revision FROM hotels WHERE id='"+ids[0]+"'"))==revision+1,'approval increments hotel version once')
        code,new=propose('VILLA');fifth=new['id']
        results=raced([lambda:review(fifth),lambda:review(fifth,False)])
        check(sorted(x[0] for x in results)==[200,409],'concurrent approve/reject has one winner')
        # Generic update uses the exact same proposal workflow.
        old=public_type();target='HOTEL' if old!='HOTEL' else 'VILLA'
        code,data=call('/hotels/'+ids[0],'PUT',{'accommodationType':target},'owner')
        check(code==200 and data['accommodationType']==old,'generic update cannot bypass type review')
        check(propose(old)[0]==200 and h.sql('checkinn_hotel',"SELECT count(*) FROM hotel_accommodation_type_changes WHERE hotel_id='"+ids[0]+"' AND status='PENDING'")=='0','selecting current cancels pending proposal')
        old=public_type();candidate='APARTMENT' if old!='APARTMENT' else 'VILLA'
        code,new=propose(candidate);race_id=new['id']
        replacement='GUEST_HOUSE' if candidate!='GUEST_HOUSE' else 'HOTEL'
        results=raced([lambda:review(race_id),lambda:propose(replacement)])
        check(results[0][0] in [200,409] and results[1][0]==200,'concurrent replacement and approval serialize')
        current=public_type()
        row=results[1][1]
        check(current!=replacement and row['previousType']==current,'replacement remains pending against actual published snapshot')
        check(h.sql('checkinn_hotel',"SELECT count(*) FROM hotel_accommodation_type_changes WHERE hotel_id='"+ids[0]+"' AND status='PENDING'")=='1','one pending request after replacement/approval race')
        fresh=str(uuid.uuid4())
        h.sql('checkinn_hotel',"DO $test$ BEGIN BEGIN INSERT INTO hotel_accommodation_type_changes SELECT (jsonb_populate_record(NULL::hotel_accommodation_type_changes,to_jsonb(c)||jsonb_build_object('id','"+fresh+"'))).* FROM hotel_accommodation_type_changes c WHERE id='"+row['id']+"'; RAISE EXCEPTION 'duplicate pending accepted'; EXCEPTION WHEN unique_violation THEN NULL; END; END $test$;")
        check(True,'partial unique index rejects a second pending request')
        check(propose(current)[0]==200,'owner can clear the remaining race proposal')
        check(review(row['id'],who=None)[0]==401,'anonymous cannot approve')
        check(call('/hotels/accommodation-type-changes/pending',gateway=True,who='owner')[0]==403,'gateway enforces admin queue authorization')
        for body in [{},{'accommodationType':None}]:
            code,data=call('/hotels/'+ids[4],'PUT',body,'owner');check(code==200 and data['accommodationType'] is None,'legacy omitted/null update retains NULL')
        # Initial approval must re-read revision after edits while PENDING.
        h.sql('checkinn_hotel',"UPDATE hotels SET approved_status='PENDING',is_active=false WHERE id='"+ids[5]+"'")
        rev=int(h.sql('checkinn_hotel',"SELECT revision FROM hotels WHERE id='"+ids[5]+"'"))
        check(propose('GUEST_HOUSE',ids[5])[0]==200,'PENDING registration changes its reviewed content')
        check(call('/hotels/'+ids[5]+'/approve?revision='+str(rev),'PUT',who='admin')[0]==409,'initial approval rejects stale revision')
        check(call('/hotels/'+ids[5]+'/approve?revision='+str(rev+1),'PUT',who='admin')[0]==200,'initial approval accepts freshly reviewed revision')
        check(public_type(ids[5])=='GUEST_HOUSE','initial approval uses current type')
        check(h.sql('checkinn_hotel',"SELECT count(*) FROM hotels WHERE id IN ('"+"','".join(ids)+"') AND is_active AND approved_status='APPROVED'")=='6','review never closes listings')
        # Database constraints reject invalid direct writes and duplicate pending rows within rolled-back subtransactions.
        h.sql('checkinn_hotel',"DO $test$ BEGIN BEGIN UPDATE hotels SET accommodation_type='ALIEN' WHERE id='"+ids[0]+"'; RAISE EXCEPTION 'invalid code accepted'; EXCEPTION WHEN check_violation THEN NULL; END; END $test$;")
        check(True,'database CHECK rejects invalid code')
    finally:
        for hotel in ids+created:
            h.sql('checkinn_hotel',"DELETE FROM hotel_accommodation_type_changes WHERE hotel_id='"+hotel+"'")
        for room in roomids:
            h.sql('checkinn_hotel',"DELETE FROM room_availability WHERE room_type_id='"+room+"'; DELETE FROM room_types WHERE id='"+room+"'")
        for hotel in ids+created:h.sql('checkinn_hotel',"DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'")
        check(h.sql('checkinn_hotel',fingerprint)==before,'cleanup and all pre-existing hotel values identical')
    print(json.dumps({'checks':checks,'result':'PASS'}))
if __name__=='__main__':main()
