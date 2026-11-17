"""Explicit Batch8B hosted synthetic acceptance. No migrations or provider operations.

All mutations require the run manifest gate. Cleanup uses enumerated primary keys,
with expected row counts and the permanent administrator excluded.
"""
import base64, datetime, hashlib, importlib.util, json, os, re, secrets, subprocess, sys, time, urllib.request, urllib.error, uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'Docs/evidence/batch-8b-hosted-e2e-performance'
PRIVATE=ROOT/'.local/batch8b'
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
import psycopg
from urllib.parse import urlparse,parse_qs
spec=importlib.util.spec_from_file_location('transport',ROOT/'.local/batch6b/cleanup-fixtures.py')
t=importlib.util.module_from_spec(spec);spec.loader.exec_module(t)
PRIVATE.mkdir(exist_ok=True)
READ_CONNECTIONS={}

def write(name,value):
    (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def manifest():return json.loads((OUT/'fixture-manifest.json').read_text(encoding='utf-8'))
def save(m):write('fixture-manifest.json',m)
def record(owner,table,key,value,parent=None,scenario='API fixture',async_effects=None):
    m=manifest();assert str(value)!=m['protectedAdminId'], 'Protected ADMIN must never enter fixture set'
    if not any(r['owner']==owner and r['table']==table and r['key']==key and r['id']==str(value) for r in m['records']):
        m['records'].append(dict(owner=owner,table=table,key=key,id=str(value),parents=parent or [],scenario=scenario,asyncEffects=async_effects or [],cleanupVerification='PENDING'))
        save(m)

def sql(owner,query,write_mode=False):
    if not write_mode:
        assert json.loads((OUT/(owner+'-runtime-preflight.json')).read_text())['exitCode']==0
        if owner not in READ_CONNECTIONS:
            v=h.stack.values();url=v[owner.upper()+'_MIGRATION_DB_URL'];assert url==v[owner.upper()+'_DB_URL']
            p=urlparse(url.removeprefix('jdbc:'));opts=parse_qs(p.query);assert opts['sslmode']==['verify-full']
            params=dict(host=p.hostname,port=p.port or 5432,dbname=p.path.lstrip('/'),user=v[owner.upper()+'_MIGRATION_DB_USERNAME'],password=v[owner.upper()+'_MIGRATION_DB_PASSWORD'],sslmode='verify-full',connect_timeout=10)
            if 'sslrootcert' in opts:params['sslrootcert']=opts['sslrootcert'][0]
            READ_CONNECTIONS[owner]=psycopg.connect(**params)
        conn=READ_CONNECTIONS[owner];rows=[]
        try:
            with conn.cursor() as cursor:
                cursor.execute('SET TRANSACTION READ ONLY')
                cursor.execute('SET LOCAL statement_timeout=30000')
                for statement in query.split(';'):
                    if not statement.strip():continue
                    assert statement.lstrip().startswith('SELECT ')
                    cursor.execute(statement)
                    rows.extend(json.loads(r[0]) if isinstance(r[0],str) else r[0] for r in cursor.fetchall())
            return rows
        finally:conn.rollback()
    assert manifest()['writesAllowed']
    assert re.match(r'\s*(INSERT|UPDATE)\b',query,re.I)
    v=h.stack.values();env=h.stack.clean_parent_env()
    for suffix in ('_MIGRATION_DB_URL','_MIGRATION_DB_USERNAME','_MIGRATION_DB_PASSWORD','_DB_URL','_DB_USERNAME','_DB_PASSWORD'):env[owner.upper()+suffix]=v[owner.upper()+suffix]
    env.update(BATCH6B_MANAGEMENT_TOKEN=v[h.GROUPS[owner]],BATCH6B_TARGET_MANIFEST=str(h.PRIVATE/'targets.properties'),BATCH6B_BACKEND_ROOT=str(ROOT))
    r=subprocess.run(['java','-cp',t.CP,'com.checkinn.db.HostedAcceptanceSql',owner,'fixture'],input=query,env=env,capture_output=True,text=True,timeout=90)
    if r.returncode:raise RuntimeError('BATCH8B_SCOPED_DML_FAILED '+owner)

def api(path,method='GET',body=None,token=None,port=8080,headers=None):
    if method!='GET':assert manifest()['writesAllowed'] or path=='/api/auth/login'
    hd={'Content-Type':'application/json',**(headers or {})}
    if token:hd['Authorization']='Bearer '+token
    req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,method=method,headers=hd,data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req,timeout=45) as r:code,raw=r.status,r.read()
    except urllib.error.HTTPError as e:code,raw=e.code,e.read()
    try:d=json.loads(raw) if raw else None
    except ValueError:d=raw.decode(errors='replace')[:200]
    return code,d

def check(ok,label,code=None):
    p=OUT/'e2e-results.json';rows=json.loads(p.read_text(encoding='utf-8')) if p.exists() else []
    rows.append({'case':label,'status':'PASS' if ok else 'FAIL','httpStatus':code});write(p.name,rows)
    print(label,rows[-1]['status'],code or '',flush=True)
    if not ok:raise AssertionError(label)

def gate():
    m=manifest()
    assert not m['records']
    for owner in h.GROUPS:
        assert json.loads((OUT/(owner+'-runtime-preflight.json')).read_text())['exitCode']==0
        baseline=json.loads((OUT/(owner+'-before.json')).read_text(encoding='utf-8'));assert not baseline['baselineDifferences']
        assert baseline['triggers']==[]
    admins=sql('user',"SELECT json_build_object('id',id,'active',is_active,'role',role)::text FROM users WHERE username='admin'")
    assert len(admins)==1 and admins[0]['active'] and admins[0]['role']=='ADMIN'
    m.update(protectedAdminId=admins[0]['id'],writesAllowed=True,cleanupOrder=['booking_event_outbox','booking_items','bookings','room_holds','room_availability','room_inventory_review_states','room_inventory_review_requests','room_types','hotels','otps','user_profiles','users'])
    save(m)
    write('cleanup-feasibility.json',{'status':'PASS_SCOPED','publicTriggers':0,'foreignKeys':'Canonical FK catalog reviewed; review states deleted before requests; user profiles before users; booking items/outbox before bookings','workers':'Revenue/Regulations/Media not started. Notification local Mailpit only. Booking/Hotel/User scheduled recovery assessed; baseline has no bookings/holds/loyalty reservations. Stop owned apps before cleanup.','financialScope':'No payments/refunds created; no receipt/ledger scenario. Only unpaid bookings and cancellable holds.','publicationLimitation':'Hotel API publication requires Storage documents. Synthetic SQL publication setup is explicitly separate from API publication acceptance.','cleanup':'Enumerate exact PKs of fixture parents and descendants; expected count guarded DELETE; no wildcard or sequence reset'})

def discover():
    m=manifest();users=[r['id'] for r in m['records'] if r['table']=='users'];hotels=[r['id'] for r in m['records'] if r['table']=='hotels'];rooms=[r['id'] for r in m['records'] if r['table']=='room_types']
    def ids(v):return ','.join("'"+str(uuid.UUID(x))+"'" for x in v)
    queries=[]
    if users:
        queries += [('user','user_profiles','id',f'user_id IN ({ids(users)})'),('user','otps','id',"email IN (SELECT email FROM users WHERE id IN ("+ids(users)+"))"),('booking','bookings','id',f'user_id IN ({ids(users)})')]
        queries += [('user',table,key,f'user_id IN ({ids(users)})') for table,key in [('loyalty_commands','command_key'),('loyalty_points','id'),('points_transactions','id'),('points_reservations','booking_id')]]
    if rooms:
        queries += [('hotel','room_inventory_review_states','room_id',f'room_id IN ({ids(rooms)})'),('hotel','room_inventory_review_requests','id',f'room_id IN ({ids(rooms)})'),('hotel','room_availability','id',f'room_type_id IN ({ids(rooms)})'),('hotel','room_holds','id',f'room_type_id IN ({ids(rooms)})')]
    for owner in {q[0] for q in queries}:
        groups=[q for q in queries if q[0]==owner]
        statements=[f"SELECT json_build_object('table','{table}','key','{key}','id',{key})::text FROM {table} WHERE {predicate}" for _,table,key,predicate in groups]
        for r in sql(owner,';'.join(statements)):record(owner,r['table'],r['key'],r['id'],scenario='Discovered exact child PK of manifested fixture')
    bids=[r['id'] for r in manifest()['records'] if r['table']=='bookings']
    if bids:
        for table,key,predicate in [('booking_items','id',f'booking_id IN ({ids(bids)})'),('booking_event_outbox','id',f'booking_id IN ({ids(bids)})')]:
            for r in sql('booking',f"SELECT json_build_object('id',{key})::text FROM {table} WHERE {predicate}"):record('booking',table,key,r['id'])

def account(role,index):
    candidates=[p for p in PRIVATE.glob('b8b_'+role.lower()+'*.json') if 'token' not in json.loads(p.read_text())]
    existing=json.loads(candidates[0].read_text()) if candidates else None
    name='b8b_'+role.lower()+secrets.token_hex(5);password=secrets.token_urlsafe(20)+'Aa1!';email=name+'@example.invalid'
    if existing:name,password,email=existing['username'],existing['password'],existing['email']
    p=PRIVATE/(name+'.json');p.write_text(json.dumps({'username':name,'password':password,'email':email}))
    route='/api/auth/register-owner' if role=='OWNER' else '/api/auth/register'
    if existing:
        uid=sql('user',f"SELECT json_build_object('id',id)::text FROM users WHERE username='{name}'")[0]['id']
        code,_=api('/api/auth/otp/resend?email='+email,'POST');print('OTP resend',code,flush=True)
    else:
        code,d=api(route,'POST',dict(username=name,password=password,email=email,fullName='Synthetic Batch8B '+role));check(code==200 and bool(d.get('userId')),role+' register Gateway',code)
        uid=d['userId'];record('user','users','id',uid,async_effects=['user_profiles','otps','Notification OTP to local Mailpit'])
    # Persist exact dependent keys immediately, including OTP, before verification.
    discover()
    otp=None
    for _ in range(40):
        with urllib.request.urlopen('http://127.0.0.1:56826/api/v1/messages',timeout=5) as r:messages=json.load(r)
        for msg in messages.get('messages',[]):
            if any(x.get('Address')==email for x in msg.get('To',[])):
                with urllib.request.urlopen('http://127.0.0.1:56826/api/v1/message/'+msg['ID'],timeout=5) as r:md=json.load(r)
                match=re.search(r'(?<!\d)\d{6}(?!\d)',md.get('Text','')+' '+md.get('HTML',''))
                if match:otp=match.group();break
        if otp:break
        time.sleep(1)
    check(bool(otp),role+' OTP local Mailpit')
    code,d=api('/api/auth/otp/verify','POST',dict(email=email,otpCode=otp));check(code==200,role+' verify OTP',code)
    code,d=api('/api/auth/login','POST',dict(usernameOrEmail=name,password=password));check(code==200 and d.get('role')==role,role+' real login',code)
    a={'id':uid,'token':d['token'],'role':role,'username':name,'password':password,'email':email}
    p.write_text(json.dumps(a));return a

def setup():
    if not manifest()['records']:gate()
    accounts=[]
    completed=[json.loads(p.read_text()) for p in PRIVATE.glob('b8b_*.json') if 'token' in json.loads(p.read_text())]
    for i,r in enumerate(['OWNER','OWNER','CUSTOMER','CUSTOMER']):
        prior=next((a for a in completed if a['role']==r and a not in accounts),None)
        accounts.append(prior or account(r,i))
        (PRIVATE/'accounts.json').write_text(json.dumps(accounts))
    (PRIVATE/'accounts.json').write_text(json.dumps(accounts));write('fixture-scope.json',{'owners':2,'customers':2,'adminFixtures':0})

def cleanup():
    # Call after all owned applications are stopped: no asynchronous writer can race deletion.
    assert not h.stack.state()['processes'], 'Stop run-owned applications before deleting'
    discover();m=manifest();results=[]
    m['cleanupOrder']=['loyalty_commands','points_transactions','points_reservations','loyalty_points']+[table for table in m['cleanupOrder'] if table not in ['loyalty_commands','points_transactions','points_reservations','loyalty_points']]
    save(m)
    allowed=set(m['cleanupOrder'])
    assert all(r['table'] in allowed and r['id']!=m['protectedAdminId'] for r in m['records'])
    scripts={}
    for table in m['cleanupOrder']:
        rows=[r for r in m['records'] if r['table']==table]
        for owner in {r['owner'] for r in rows}:
            group=[r for r in rows if r['owner']==owner];key=group[0]['key']
            assert all(r['key']==key for r in group)
            for r in group:
                if key=='command_key':assert re.fullmatch(r'[A-Za-z0-9:|_-]{1,160}',r['id'])
                else:uuid.UUID(r['id'])
            ids=','.join("'"+r['id']+"'" for r in group)
            live=sql(owner,f"SELECT json_build_object('count',count(*))::text FROM public.{table} WHERE {key} IN ({ids})")[0]['count']
            assert 0<=live<=len(group)
            q=f'DELETE FROM public.{table} WHERE {key} IN ({ids}) /* expected:{live} */'
            scripts.setdefault(owner,[]).append(q)
    write('cleanup-exact-id-plan.json',scripts)
    for owner,queries in scripts.items():
        if '--libpq' in sys.argv:
            # Same identity-verified endpoint and TLS verify-full migration credentials as reads.
            # One transaction per database, with exact enumerated UUIDs and expected counts.
            sql(owner,"SELECT json_build_object('ready',true)::text");conn=READ_CONNECTIONS[owner];counts=[]
            try:
                with conn.cursor() as cursor:
                    cursor.execute('SET LOCAL statement_timeout=30000')
                    for query in queries:
                        assert re.fullmatch(r"DELETE FROM public\.[a-z_]+ WHERE [a-z_]+ IN \('[A-Za-z0-9:|_-]+'(?:,'[A-Za-z0-9:|_-]+')*\) /\* expected:\d+ \*/",query)
                        cursor.execute(query);expected=int(re.search(r'expected:(\d+)',query).group(1))
                        assert cursor.rowcount==expected, 'Exact cleanup count mismatch; rollback'
                        counts.append(cursor.rowcount)
                conn.commit()
            except Exception:conn.rollback();raise
        else:counts=t.sql(owner,';'.join(queries),'delete')
        results.append({'owner':owner,'deleted':sum(counts),'counts':counts})
        write('cleanup-progress.json',results)
    remaining=[]
    for owner in {r['owner'] for r in m['records']}:
        records=[r for r in m['records'] if r['owner']==owner]
        queries=[f"SELECT json_build_object('count',count(*))::text FROM {r['table']} WHERE {r['key']}='{r['id']}'" for r in records]
        for r,value in zip(records,sql(owner,';'.join(queries))):
            n=value['count'];r['cleanupVerification']='ABSENT' if n==0 else 'REMAINS'
            if n:remaining.append(r)
    m['writesAllowed']=False;m['cleanupStatus']='DONE' if not remaining else 'FAILED';save(m)
    write('cleanup-results.json',{'results':results,'remaining':remaining,'status':m['cleanupStatus']})

def cleanup_broker():
    import pika
    assert not h.stack.state()['processes']
    m=manifest();booking_ids={r['id'] for r in m['records'] if r['table']=='bookings'}
    emails={json.loads(p.read_text())['email'] for p in PRIVATE.glob('b8b_*.json')}
    v=h.stack.values();credentials=pika.PlainCredentials(v['SUPABASE_DEV_RABBIT_USERNAME'],v['SUPABASE_DEV_RABBIT_PASSWORD'])
    connection=pika.BlockingConnection(pika.ConnectionParameters('127.0.0.1',int(v['SUPABASE_DEV_RABBIT_PORT']),v['SUPABASE_DEV_RABBIT_VHOST'],credentials,blocked_connection_timeout=10))
    channel=connection.channel();results=[]
    try:
        for queue in ['revenue.booking.queue','notification.otp.dlq']:
            count=channel.queue_declare(queue=queue,passive=True).method.message_count;held=[];removed=[]
            for _ in range(count):
                method,properties,body=channel.basic_get(queue,auto_ack=False)
                if method is None:break
                try:payload=json.loads(body)
                except ValueError:payload={}
                own=payload.get('bookingId') in booking_ids or payload.get('email') in emails or payload.get('to') in emails
                if own:
                    channel.basic_ack(method.delivery_tag);removed.append(hashlib.sha256(body).hexdigest())
                else:held.append(method.delivery_tag)
            # Unmatched messages retain their payload/properties, and are requeued unchanged.
            for delivery in held:channel.basic_nack(delivery,requeue=True)
            results.append({'queue':queue,'removedExactFixturePayloadHashes':removed,'preservedUnmatched':len(held),'remaining':channel.queue_declare(queue=queue,passive=True).method.message_count})
    finally:connection.close()
    write('broker-cleanup.json',results)

def admin():
    code,a=api('/api/auth/login','POST',{'usernameOrEmail':'admin','password':h.stack.runtime_values()['ADMIN_PASSWORD']})
    check(code==200 and a.get('role')=='ADMIN','Permanent ADMIN login',code)
    assert a['userId']==manifest()['protectedAdminId'];return a['token']

def dataset(hotel_count,rooms_per_hotel):
    accounts=json.loads((PRIVATE/'accounts.json').read_text());owners=[a for a in accounts if a['role']=='OWNER'];at=admin()
    dp=PRIVATE/'dataset.json';ds=json.loads(dp.read_text()) if dp.exists() else {'hotels':[],'rooms':[]}
    city=sql('hotel',"SELECT json_build_object('id',id)::text FROM cities ORDER BY id LIMIT 1")[0]['id'];ds['city']=city
    for i in range(hotel_count):
        owner=owners[i%len(owners)]
        if i>=len(ds['hotels']):
            code,d=api('/hotels','POST',{'name':'Synthetic Batch8B '+manifest()['testRunId'][-8:]+' '+str(i),'description':'Synthetic temporary inventory benchmark','starRating':3,'cityId':city,'address':{'street':'Synthetic street','city':'Synthetic city','country':'VN'},'contactEmail':owner['email'],'contactPhone':'0900000000','businessLicenseNumber':'SYN-B8B-'+str(i),'taxId':'SYN-B8B','ownerIdentityNumber':'SYN-B8B','accommodationType':'HOTEL','policies':[],'questions':[],'amenityCategories':[]},owner['token'])
            check(code==200 and 'id' in d,'OWNER create hotel Gateway',code);hid=d['id'];record('hotel','hotels','id',hid,[owner['id']])
            ds['hotels'].append({'id':hid,'ownerId':owner['id']});dp.write_text(json.dumps(ds))
            # Isolated setup only, no fabricated document/Storage URLs and no production record.
            sql('hotel',f"UPDATE hotels SET approved_status='APPROVED',is_active=true WHERE id='{uuid.UUID(hid)}' AND owner_id='{uuid.UUID(owner['id'])}' AND approved_status='DRAFT'",True)
        hid=ds['hotels'][i]['id']
        while len([r for r in ds['rooms'] if r['hotelId']==hid])<rooms_per_hotel:
            j=len(ds['rooms']);code,d=api('/api/v1','POST',{'hotelId':hid,'name':'Synthetic B8B room '+str(j),'basePrice':1000000+j*1000,'capacity':{'adults':2,'children':1},'roomAmount':2,'amenities':[],'description':'Temporary synthetic benchmark'},owner['token'])
            check(code==200 and 'id' in d,'OWNER create room Gateway',code);rid=d['id'];record('hotel','room_types','id',rid,[hid]);ds['rooms'].append({'id':rid,'hotelId':hid,'ownerId':owner['id']});dp.write_text(json.dumps(ds))
            # First room left pending for negative E2E; other rooms approved through real ADMIN API.
            code,p=api('/api/v1/'+rid+'/inventory-review','POST',{'quantity':2,'revision':0,'source':'Synthetic Batch8B declaration; no physical stock assertion'},owner['token'])
            check(code==201 and p.get('status')=='PENDING','OWNER submit pending inventory',code);record('hotel','room_inventory_review_requests','id',p['id'],[rid]);record('hotel','room_inventory_review_states','room_id',rid,[rid,p['id']])
            if j!=0:
                code,d=api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',{'approve':True,'revision':p['revision'],'reason':'Synthetic Batch8B controlled acceptance'},at)
                check(code==200 and d.get('status')=='APPROVED','ADMIN approve inventory Gateway',code)
    write('dataset-current.json',{'hotels':len(ds['hotels']),'rooms':len(ds['rooms']),'requestedStage':[hotel_count,rooms_per_hotel],'publication':'SQL synthetic setup; not hotel-publication API acceptance'})
    return ds

def booking_request(customer,room,quantity=1):
    code,policy=api('/api/bookings/cancellation-policy');assert code==200
    return {'idempotencyKey':str(uuid.uuid4()),'userId':customer['id'],'hotelId':room['hotelId'],'checkInDate':'2027-02-10','checkOutDate':'2027-02-11','adults':2,'children':0,'contactName':'Synthetic Batch8B','contactEmail':customer['email'],'contactPhone':'0900000000','pointsToUse':0,'acceptedCancellationPolicyVersion':policy['version'],'items':[{'roomTypeId':room['id'],'quantity':quantity,'checkInDate':'2027-02-10','checkOutDate':'2027-02-11','unitPrice':1000000}]}

def e2e():
    from concurrent.futures import ThreadPoolExecutor
    ds=json.loads((PRIVATE/'dataset.json').read_text());ac=json.loads((PRIVATE/'accounts.json').read_text());owner=next(a for a in ac if a['id']==ds['rooms'][0]['ownerId']);other=next(a for a in ac if a['role']=='OWNER' and a['id']!=owner['id']);customers=[a for a in ac if a['role']=='CUSTOMER'];c=customers[0];room=ds['rooms'][0];rid=room['id'];at=admin()
    code,q=api('/api/hotel/admin/inventory-reviews',token=at);check(code==200,'ADMIN proposal queue Gateway',code)
    code,state=api('/api/v1/'+rid+'/inventory-review',token=owner['token']);p=state['latestRequest'];assert p['status']=='PENDING'
    before=sql('hotel',f"SELECT json_build_object('holds',(SELECT count(*) FROM room_holds WHERE room_type_id='{rid}'),'counters',(SELECT coalesce(sum(held+booked),0) FROM room_availability WHERE room_type_id='{rid}'))::text")[0]
    code,d=api('/api/bookings','POST',booking_request(c,room),c['token']);check(code==409,'Unapproved room booking conflict',code)
    after=sql('hotel',f"SELECT json_build_object('holds',(SELECT count(*) FROM room_holds WHERE room_type_id='{rid}'),'counters',(SELECT coalesce(sum(held+booked),0) FROM room_availability WHERE room_type_id='{rid}'))::text")[0];check(before==after and before['holds']==0,'Unapproved booking leaves no hold/counter write')
    for actor in [owner,c]:
        code,_=api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',{'approve':True,'revision':state['revision'],'reason':'Synthetic forbidden attempt'},actor['token']);check(code==403,actor['role']+' cannot approve',code)
    code,_=api('/hotels/'+room['hotelId'],'PUT',{'name':'Forbidden synthetic edit'},other['token']);check(code==403,'OWNER B cannot edit OWNER A hotel',code)
    code,_=api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',{'approve':True,'revision':state['revision'],'reason':'Forged header attempt'},c['token'],headers={'X-User-Role':'ADMIN','X-Role':'ADMIN'});check(code==403,'Forged ADMIN header rejected',code)
    code,d=api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',{'approve':True,'revision':state['revision'],'reason':'Synthetic Batch8B acceptance'},at);check(code==200 and d['status']=='APPROVED','ADMIN approve previously pending room',code)
    code,_=api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',{'approve':True,'revision':state['revision'],'reason':'Stale revision attempt'},at);check(code==409,'Stale proposal approval conflict',code)
    code,d=api('/api/v1/availability/check?roomTypeId='+rid+'&checkIn=2027-02-10&checkOut=2027-02-11&quantity=1');check(code==200 and d['availableRooms']==2,'Approved inventory available quantity 2',code)
    req=booking_request(c,room);code,b=api('/api/bookings','POST',req,c['token']);check(code==201,'CUSTOMER approved booking/hold Gateway',code);record('booking','bookings','id',b['id'],[c['id'],room['hotelId'],rid]);discover()
    code,retry=api('/api/bookings','POST',req,c['token']);check(code==201 and retry['id']==b['id'],'Booking retry same idempotency key',code)
    req2=dict(req);req2['adults']=1;code,_=api('/api/bookings','POST',req2,c['token']);check(code==409,'Idempotency changed payload conflict',code)
    def attempt(i):
        cu=customers[i%2];return api('/api/bookings','POST',booking_request(cu,room),cu['token'])
    with ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(attempt,range(3)))
    for status,bd in results:
        if status==201:record('booking','bookings','id',bd['id'])
    discover();check(sum(code==201 for code,d in results)==1 and all(code in (201,409) for code,d in results),'Concurrency 3 remaining capacity 1 no oversell')
    finish_e2e(room,owner,customers,at)

def finish_e2e(room=None,owner=None,customers=None,at=None):
    from concurrent.futures import ThreadPoolExecutor
    if room is None:
        ds=json.loads((PRIVATE/'dataset.json').read_text());room=ds['rooms'][0];ac=json.loads((PRIVATE/'accounts.json').read_text());owner=next(a for a in ac if a['id']==room['ownerId']);customers=[a for a in ac if a['role']=='CUSTOMER'];at=admin()
        # Recovery after read transport failure: re-observe full stock and run new bounded negatives.
        if '--no-more-concurrency' not in sys.argv:
            attempts=[]
            def full_attempt(i):return api('/api/bookings','POST',booking_request(customers[i%2],room),customers[i%2]['token'])
            with ThreadPoolExecutor(max_workers=3) as pool:attempts=list(pool.map(full_attempt,range(3)))
            write('concurrency-recovery.json',{'attemptStatuses':[x[0] for x in attempts],'note':'Previous post-race SQL probe failed 08006; new attempts against full capacity, no retries of original writes.'})
            check(all(code==409 for code,d in attempts),'Concurrency 3 full stock all conflict')
    rid=room['id']
    counters=sql('hotel',f"SELECT json_build_object('held',sum(held),'booked',sum(booked))::text FROM room_availability WHERE room_type_id='{rid}'")[0];check(counters['held']==2 and counters['booked']==0,'Concurrent counters held=2 booked=0')
    code,p2=api('/api/v1/'+rid+'/inventory-review','POST',{'quantity':1,'revision':2,'source':'Synthetic reduced stock proposal'},owner['token']);check(code==201,'Quantity change proposal with existing holds',code);record('hotel','room_inventory_review_requests','id',p2['id'])
    code,_=api('/api/hotel/admin/inventory-reviews/'+p2['id']+'/review','POST',{'approve':True,'revision':p2['revision'],'reason':'Synthetic capacity conflict'},at);check(code==409,'Cannot approve quantity below committed holds',code)
    code,d=api('/api/hotel/admin/inventory-reviews/'+p2['id']+'/review','POST',{'approve':False,'revision':p2['revision'],'reason':'Synthetic rejected quantity change'},at);check(code==200 and d['status']=='REJECTED','ADMIN reject quantity proposal',code)
    code,s=api('/api/v1/'+rid+'/inventory-review',token=owner['token']);check(code==200 and s['verified'] and s['currentQuantity']==2,'Rejected proposal preserves effective approved stock',code)
    for r in manifest()['records']:
        if r['table']=='bookings':
            # Customer tokens are tried only against manifested synthetic bookings.
            for cu in customers:
                code,_=api('/api/bookings/'+r['id'],'DELETE',token=cu['token'])
                if code==204:break
            check(code==204,'Cancel synthetic unpaid booking',code)
    counters=sql('hotel',f"SELECT json_build_object('occupied',coalesce(sum(held+booked),0))::text FROM room_availability WHERE room_type_id='{rid}'")[0];check(counters['occupied']==0,'Cancellation releases all occupied counters')
    discover()

if __name__=='__main__':
    action=sys.argv[1]
    if action=='setup':setup()
    elif action=='discover':discover()
    elif action=='cleanup':cleanup()
    elif action=='dataset':dataset(int(sys.argv[2]),int(sys.argv[3]))
    elif action=='e2e':e2e()
    elif action=='finish-e2e':finish_e2e()
    elif action=='cleanup-broker':cleanup_broker()
