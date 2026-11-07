"""STD-016: additive migration + real HTTP/Postgres regression on checkinn-test only."""
import concurrent.futures, importlib.util, json, time, uuid
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py')
h=importlib.util.module_from_spec(spec); spec.loader.exec_module(h)

def main():
    h.verify_running_clone('hotel-service')
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    key=config['services']['hotel-service']['environment']['SECRET_KEY']
    owner,other,admin,hotel,hotel2,room,category,category2,raw,raw2,raw3=[str(uuid.uuid4()) for _ in range(11)]
    prefix='TEST_'+uuid.uuid4().hex.upper(); free=prefix+'_FREE'; paid=prefix+'_PAID'
    count=0
    def check(ok,name):
        nonlocal count
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok: raise AssertionError(name)
        count+=1
    def api(method,path,body=None,actor=owner,role='OWNER',gateway=False):
        base='http://api-gateway:8080/api/hotel' if gateway else 'http://localhost:8083'
        headers='Authorization: Bearer '+h.token(key,actor,role)+'\n' if actor else ''
        command=h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','25','-X',method,'-H','@-','-H','Content-Type: application/json','-w','\n%{http_code}']
        if body is not None: command+=['--data',json.dumps(body)]
        text=h.run(command+[base+path],headers); body,_,status=text.rpartition('\n')
        try: value=json.loads(body)
        except ValueError: value=None
        return int(status),value
    def db(query): return h.sql('checkinn_hotel',query)
    def fingerprint():
        return [db("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM "+table+' t') for table in ['hotels','room_types','hotel_amenities','hotel_amenity_categories']]
    before=fingerprint()
    def existing_count(table):
        return db('SELECT count(*) FROM '+table) if db("SELECT to_regclass('"+table+"') IS NOT NULL")=='t' else '0'
    old_counts=[existing_count(t) for t in ['amenity_catalog','amenity_mapping_requests']]
    migration=(root/'sql'/'amenity-catalog-v1-20261106.sql').read_text(encoding='utf-8')
    db(migration); db(migration)
    check(before==fingerprint(),'migration replay preserves every legacy hotel room and raw amenity fingerprint')
    check(old_counts==[existing_count(t) for t in ['amenity_catalog','amenity_mapping_requests']],'migration creates no catalog seeds or automatic legacy mappings')
    base='/hotels/'+hotel+'/amenity-mappings'
    def catalog(code,label,active=True,revision=0): return {'code':code,'label':label,'description':'Synthetic facility semantics only','active':active,'revision':revision}
    def propose(rawid,codes,revision): return api('POST',base+'/'+rawid+'/requests',{'codes':codes,'revision':revision,'note':'Checked synthetic label only'})
    def review(row,approve=True): return api('POST','/admin/amenity-mappings/'+row['id']+'/review',{'approve':approve,'revision':row['revision'],'reason':'Inspected synthetic property evidence'},admin,'ADMIN')
    def mappings():
        status,rows=api('GET',base); check(status==200,'owner reads own mapping state'); return {r['id']:r for r in rows}
    def search(codes):
        return api('GET','/hotels/offers?hotelName=amenity-fixture-'+hotel+''.join('&amenityCodes='+c for c in codes),actor=None)
    try:
        for id_,owner_ in [(hotel,owner),(hotel2,other)]:
            check(db("WITH added AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+id_+"','owner_id','"+owner_+"','name','amenity-fixture-"+id_+"','approved_status','APPROVED','is_active',true))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM added")=='1','create scoped cloned hotel fixture')
        db("INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','hotel_id','"+hotel+"','name','Synthetic room','is_active',true,'base_price',1000000,'total_rooms',2,'capacity',jsonb_build_object('adults',2,'children',0),'amenities',jsonb_build_array('Wi-Fi trả phí')))).* FROM room_types r LIMIT 1")
        db("INSERT INTO hotel_amenity_categories(id,hotel_id,title,is_active,created_at,updated_at) VALUES ('"+category+"','"+hotel+"','Synthetic facilities',true,now(),now()),('"+category2+"','"+hotel2+"','Synthetic facilities',true,now(),now()); INSERT INTO hotel_amenities(id,category_id,title,is_active,created_at) VALUES ('"+raw+"','"+category+"','Wi-Fi',true,now()),('"+raw3+"','"+category+"','Wi-Fi trả phí',true,now()),('"+raw2+"','"+category2+"','Wi-Fi',true,now())")
        raw_before=db("SELECT md5(string_agg(to_jsonb(a)::text,'' ORDER BY id)) FROM hotel_amenities a WHERE id IN ('"+raw+"','"+raw2+"','"+raw3+"')")
        for gateway in [False,True]:
            # Discovery after image restart may take a few seconds, never reinterpret 503 as success.
            deadline=time.monotonic()+120
            while gateway and api('GET','/amenity-catalog',actor=None,gateway=True)[0]==503:
                if time.monotonic()>deadline: raise RuntimeError('NEEDS_ENVIRONMENT: gateway discovery')
                time.sleep(2)
            check(api('GET','/amenity-catalog',actor=None,gateway=gateway)[0]==200,'public active catalog direct/gateway')
            check(api('GET','/admin/amenity-catalog',actor=None,gateway=gateway)[0]==401,'anonymous admin catalog denied')
            check(api('GET',base,actor=None,gateway=gateway)[0]==401,'anonymous private mapping denied')
            for role in ['OWNER','CUSTOMER','BOOKING_SERVICE']:
                check(api('POST','/admin/amenity-catalog',catalog(free,'Synthetic free Wi-Fi'),role=role,gateway=gateway)[0]==403,'non-admin catalog mutation forbidden')
            check(api('GET',base,actor=other,gateway=gateway)[0]==403,'other owner mapping access forbidden')
            check(api('POST',base+'/'+raw+'/requests',{'codes':[],'revision':0},actor=other,gateway=gateway)[0]==403,'other owner mapping proposal forbidden')
            check(api('GET',base,role='CUSTOMER',gateway=gateway)[0]==403,'customer mapping access forbidden')
        check(api('POST','/admin/amenity-catalog',catalog(free,'Synthetic free Wi-Fi'),admin,'ADMIN')[0]==201,'admin creates stable free code')
        check(api('POST','/admin/amenity-catalog',catalog(paid,'Synthetic paid Wi-Fi'),admin,'ADMIN')[0]==201,'admin creates distinct paid code')
        check(api('POST','/admin/amenity-catalog',catalog(free,'Overwrite attempt'),admin,'ADMIN')[0]==409,'duplicate code does not overwrite catalog')
        check(api('POST','/admin/amenity-catalog',catalog('bad code','Invalid'),admin,'ADMIN')[0]==400,'invalid code validation')
        check(api('PUT','/admin/amenity-catalog/'+free,catalog(paid,'Wrong path'),admin,'ADMIN')[0]==400,'immutable code rejects path/body mismatch')
        check(search([free])[0]==200 and search([free])[1]==[],'unmapped raw Wi-Fi never implies free Wi-Fi code')
        check(search([paid])[1]==[],'raw paid hotel and room labels never automatically imply paid code')
        check(search(['DOES_NOT_EXIST'])[0]==400,'unknown filter code returns explicit error')
        check(search([''])[0]==400,'empty filter code cannot silently broaden the search')
        check(propose(raw,[free],0)[0]==201,'owner proposes an existing code')
        first=mappings()[raw]['latestRequest']
        check(search([free])[1]==[],'pending proposal has no public filter effect')
        check(api('POST','/admin/amenity-mappings/'+first['id']+'/review',{'approve':True,'revision':1,'reason':'Unauthorized owner'})[0]==403,'owner cannot self-approve')
        check(review(first)[0]==200,'admin approves checked mapping')
        check(review(first)[0]==409,'review replay is stale and cannot overwrite')
        check(len(search([free])[1])==1,'approved code selects correct hotel offer')
        check(search([free,paid])[1]==[],'canonical facility filters require ALL selected codes')
        public=api('GET','/hotels/'+hotel,actor=None)[1]
        item=next(i for c in public['amenityCategories'] for i in c['items'] if i['id']==raw)
        check(item['title']=='Wi-Fi' and item['verifiedCodes']==[{'code':free,'label':'Synthetic free Wi-Fi'}],'public response preserves literal title and adds reviewed code')
        check(mappings()[raw3]['approvedCodes']==[],'same property other raw label is not silently mapped')
        foreign=api('GET','/hotels/'+hotel2+'/amenity-mappings',actor=other)[1]
        check(all(not r['approvedCodes'] for r in foreign),'same label at a different hotel stays unmapped')
        check(propose(raw2,[free],0)[0]==404,'cannot attach a foreign raw ID through owned hotel')
        check(propose(raw,['UNKNOWN'],2)[0]==400,'unknown proposal code rejected')
        # Same expected revision: only one writer wins under the hotel row lock.
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            results=list(executor.map(lambda _:propose(raw,[paid],2),range(2)))
        check(sorted(r[0] for r in results)==[201,409],'concurrent proposals serialize and stale writer receives conflict')
        latest=mappings()[raw]['latestRequest']
        check(review(latest,False)[0]==200 and len(search([free])[1])==1,'rejected replacement keeps previously approved mapping')
        check(propose(raw,[paid],4)[0]==201,'owner can submit corrected mapping after rejection')
        superseded=mappings()[raw]['latestRequest']
        check(propose(raw,[free,paid],5)[0]==201,'new proposal supersedes pending request')
        check(review(superseded)[0]==409,'superseded request cannot be approved')
        both=mappings()[raw]['latestRequest']; check(review(both)[0]==200,'explicit reviewed multi-code mapping')
        check(len(search([free,paid])[1])==1,'all-code matching succeeds only with both approved codes')
        # Disable is non-destructive and takes effect immediately.
        check(api('PUT','/admin/amenity-catalog/'+paid,catalog(paid,'Synthetic paid Wi-Fi',False),admin,'ADMIN')[0]==200,'admin soft-deactivates code')
        check(search([paid])[0]==400,'inactive selected code is explicit invalid filter')
        check(propose(raw,[paid],7)[0]==400,'owner cannot attach inactive code')
        check(all(row['code']!=paid for row in api('GET','/amenity-catalog',actor=None)[1]),'inactive code hidden from public catalog')
        check(any(row['code']==paid for row in api('GET','/admin/amenity-catalog',actor=admin,role='ADMIN')[1]),'inactive code retained for admin audit')
        check(api('PUT','/admin/amenity-catalog/'+paid,catalog(paid,'Stale overwrite',True,0),admin,'ADMIN')[0]==409,'stale catalog edit cannot reactivate')
        check(api('PUT','/admin/amenity-catalog/'+paid,catalog(paid,'Synthetic paid Wi-Fi',True,1),admin,'ADMIN')[0]==200,'explicit revision can reactivate code')
        for table,id_ in [('hotel_amenities',raw),('hotel_amenity_categories',category)]:
            db("UPDATE "+table+" SET is_active=false WHERE id='"+id_+"'")
            check(search([free])[1]==[],'inactive raw item/category cannot fulfill canonical filter')
            db("UPDATE "+table+" SET is_active=true WHERE id='"+id_+"'")
        db("UPDATE hotel_amenities SET title='Wi-Fi changed after approval' WHERE id='"+raw+"'")
        check(search([free])[1]==[],'edited raw title invalidates prior mapping snapshot')
        db("UPDATE hotel_amenities SET title='Wi-Fi' WHERE id='"+raw+"'")
        check(propose(raw,[],7)[0]==201,'empty selection explicitly requests mapping removal')
        clear=mappings()[raw]['latestRequest'];check(review(clear)[0]==200 and search([free])[1]==[],'approved removal clears codes and preserves raw record')
        check(raw_before==db("SELECT md5(string_agg(to_jsonb(a)::text,'' ORDER BY id)) FROM hotel_amenities a WHERE id IN ('"+raw+"','"+raw2+"','"+raw3+"')"),'all catalog and mapping writes preserve legacy raw labels exactly')
        check(propose(raw,[free],9)[0]==201,'new proposal before raw replacement')
        orphan=mappings()[raw]['latestRequest']
        # Exercise legacy owner CRUD: physical raw replacement is still compatible.
        check(api('PUT','/hotels/'+hotel+'/amenities',{'amenityCategories':[{'title':'Replacement','amenities':[{'title':'Wi-Fi'}]}]})[0]==200,'legacy literal amenities update remains supported')
        check(review(orphan)[0]==404,'deleted raw target cannot be approved from stale snapshot')
        check(db("SELECT count(*) FROM amenity_mapping_requests WHERE hotel_id='"+hotel+"'")!='0','legacy replacement keeps mapping audit history')
        check(search([free])[1]==[],'replacement raw UUID never inherits old mapping')
        check(review(orphan,False)[0]==200,'admin can reject orphan pending request without attaching codes')
        history_status,history=api('GET','/admin/amenity-mappings/history',actor=admin,role='ADMIN')
        audited=next((row for row in history if row['id']==orphan['id']),{})
        check(history_status==200 and audited.get('status')=='REJECTED' and audited.get('reviewed_by')==admin and audited.get('requested_by')==owner and bool(audited.get('review_reason')),'admin history retains original actor and reason after raw deletion')
        check(api('GET','/admin/amenity-mappings/history')[0]==403,'owner cannot read global mapping history')
    finally:
        # Only UUID-scoped records created by this run, no shared rows or volumes.
        db("DELETE FROM amenity_mapping_states WHERE hotel_id IN ('"+hotel+"','"+hotel2+"'); DELETE FROM amenity_mapping_request_codes WHERE request_id IN (SELECT id FROM amenity_mapping_requests WHERE hotel_id IN ('"+hotel+"','"+hotel2+"')); DELETE FROM amenity_mapping_requests WHERE hotel_id IN ('"+hotel+"','"+hotel2+"'); DELETE FROM amenity_catalog WHERE code IN ('"+free+"','"+paid+"'); DELETE FROM hotel_amenities WHERE category_id IN (SELECT id FROM hotel_amenity_categories WHERE hotel_id IN ('"+hotel+"','"+hotel2+"')); DELETE FROM hotel_amenity_categories WHERE hotel_id IN ('"+hotel+"','"+hotel2+"'); DELETE FROM room_types WHERE id='"+room+"' AND hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id IN ('"+hotel+"','"+hotel2+"') AND name LIKE 'amenity-fixture-%'")
        check(db("SELECT count(*) FROM hotels WHERE id IN ('"+hotel+"','"+hotel2+"')")=='0','scoped catalog fixture cleanup')
    print(json.dumps({'verified_cases':count}),flush=True)

if __name__=='__main__': main()
