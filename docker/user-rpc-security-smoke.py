"""Real RPC/password-reset regression: synthetic accounts in test clones + Mailpit only."""
import concurrent.futures,hashlib,hmac,importlib.util,json,os,re,secrets,subprocess,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
    h.verify_running_clone('user-service');h.verify_running_clone('hotel-service')
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    notification=h.run(h.COMPOSE+['ps','-q','notification-service'])
    assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',notification]))
    key=config['services']['hotel-service']['environment']['SECRET_KEY'];prefix='rpccheck_'+secrets.token_hex(8)
    accounts=[];redis_keys=[];hotel=str(uuid.uuid4());fault='rpccheck_'+uuid.uuid4().hex;count=0
    def check(ok,name):
        nonlocal count
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
        count+=1
    def db(query):return h.sql('checkinn_user',query)
    def token(subject,role,expired=False):
        payload={'sub':subject,'role':role,'iat':int(time.time())-600 if expired else int(time.time()),'exp':int(time.time())-1 if expired else int(time.time())+300,'jti':str(uuid.uuid4())}
        try:uuid.UUID(subject)
        except ValueError:pass
        else:
            generation=h.run(h.COMPOSE+['exec','-T','redis','redis-cli','--raw','GET','auth:session-generation:'+subject])
            if generation:payload['sessionGeneration']=generation
        data=h.encode(b'{"alg":"HS256","typ":"JWT"}')+b'.'+h.encode(json.dumps(payload).encode())
        return (data+b'.'+h.encode(hmac.new(key.encode(),data,hashlib.sha256).digest())).decode()
    def api(method,path,body=None,credential=None,hotel_api=False):
        service='hotel-service' if hotel_api else 'auth-service';port='8083' if hotel_api else '8081'
        # Payload/credentials go over stdin, never process arguments or diagnostics.
        command=h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-X',method,'-H','Content-Type: application/json','-w','\n%{http_code}']
        if body is not None:command+=['--data-binary','@-'];input_=json.dumps(body)
        else:command+=['-H','@-'];input_='Authorization: Bearer '+credential+'\n' if credential else ''
        raw=h.run(command+['http://localhost:'+port+path],input_);text,_,status=raw.rpartition('\n')
        try:result=json.loads(text)
        except ValueError:result={}
        return int(status),result
    def rpc(method,body,credential=None,forged=False):
        command=['docker','run','--rm','-i','--network',config['networks']['checkinn']['name'],'--mount','type=bind,source='+str(root.parent/'user-service/src/main/proto')+',target=/protos,readonly']
        env=dict(os.environ)
        if credential:command+=['--env','CHECKINN_RPC_BEARER'];env['CHECKINN_RPC_BEARER']='Bearer '+credential
        command+=['fullstorydev/grpcurl:v1.9.3','-plaintext','-max-time','15','-import-path','/protos','-proto','user.proto']
        if credential:command+=['-expand-headers','-H','authorization: ${CHECKINN_RPC_BEARER}']
        if forged:command+=['-H','x-user-id: '+str(uuid.uuid4())]
        command+=['-d','@','user-service:9097','UserService/'+method]
        result=subprocess.run(command,input=json.dumps(body),capture_output=True,text=True,encoding='utf-8',env=env,timeout=40)
        match=re.search(r'Code:\s*(\w+)',result.stderr)
        status='OK' if result.returncode==0 else match.group(1) if match else 'TRANSPORT_ERROR'
        try:value=json.loads(result.stdout)
        except ValueError:value={}
        return status,value
    def password_hash(account):return db("SELECT md5(password) FROM users WHERE id='"+account['userId']+"' AND email='"+account['email']+"'")
    try:
        # Read-only readiness: an unavailable discovery cache must be503, not bad credentials.
        deadline=time.monotonic()+120
        while True:
            status,_=api('POST','/login',{'usernameOrEmail':prefix+'_missing@example.invalid','password':secrets.token_urlsafe(24)})
            if status==401:break
            if status!=503 or time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: auth-to-user RPC readiness')
            time.sleep(2)
        for index,path in enumerate(['/register','/register-owner']):
            email=prefix+str(index)+'@example.invalid';password=secrets.token_urlsafe(24)
            status,account=api('POST',path,{'username':prefix+str(index),'email':email,'password':password,'fullName':'Synthetic RPC fixture'})
            check(status==200 and bool(account.get('userId')),'authenticated service registration remains functional')
            account['email']=email;account['password']=password;accounts.append(account)
        customer,owner=accounts;target={'email':customer['email'],'newPassword':secrets.token_urlsafe(24)};before=password_hash(customer)
        for credential,forged,expected,name in [(None,False,'Unauthenticated','anonymous reset denied'),(None,True,'Unauthenticated','forged identity header denied'),('synthetic-malformed',False,'Unauthenticated','malformed bearer denied'),(token('auth-service','AUTH_SERVICE',True),False,'Unauthenticated','expired service bearer denied')]:
            check(rpc('ResetPassword',target,credential,forged)[0]==expected,name)
        for subject,role in [(customer['userId'],'CUSTOMER'),(owner['userId'],'OWNER'),(customer['userId'],'ADMIN'),(customer['userId'],'AUTH_SERVICE'),('hotel-service','HOTEL_SERVICE'),('booking-service','BOOKING_SERVICE')]:
            check(rpc('ResetPassword',target,token(subject,role))[0]=='PermissionDenied','wrong role/service cannot call reset even with valid signature')
        check(before==password_hash(customer),'all denied reset calls preserve original password')
        check(rpc('GetUserById',{'id':customer['userId']})[0]=='Unauthenticated','anonymous full account read denied')
        check(rpc('GetUserById',{'id':customer['userId']},token(owner['userId'],'OWNER'))[0]=='PermissionDenied','cross-user full account read denied')
        for credential in [token(customer['userId'],'CUSTOMER'),token(owner['userId'],'ADMIN'),token('hotel-service','HOTEL_SERVICE')]:
            status,result=rpc('GetUserById',{'id':customer['userId']},credential)
            check(status=='OK' and result.get('id')==customer['userId'],'self/admin/exact hotel identity read allowed')
        auth=token('auth-service','AUTH_SERVICE');redis_key='auth:revoked-token:'+hashlib.sha256(auth.encode()).hexdigest();redis_keys.append(redis_key)
        check(api('POST','/logout',credential=auth)[0]==200,'test service credential revoked through normal logout path')
        check(rpc('ResetPassword',target,auth)[0]=='Unauthenticated' and password_hash(customer)==before,'revoked service token cannot mutate password')
        check(rpc('ResetPassword',target,token('auth-service','AUTH_SERVICE'))[0]=='InvalidArgument' and password_hash(customer)==before,'trusted service still requires verified stored OTP proof')
        status,result=api('POST','/reset-password',{**target,'otpCode':'invalid-synthetic-code'})
        check(status==200 and result.get('success') is False and password_hash(customer)==before,'HTTP invalid OTP is rejected without mutation')
        otp=db("SELECT otp_code FROM otps WHERE email='"+customer['email']+"'");assert otp,'Missing synthetic OTP'
        status,result=api('POST','/otp/verify',{'email':customer['email'],'otpCode':otp})
        check(status==200 and result.get('verified') is True and db("SELECT count(*) FROM otps WHERE email='"+customer['email']+"'")=='1','separate UI verification step keeps proof available for the final reset')
        # A deferred commit failure must restore both the password and consumed proof.
        db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic reset commit fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON users DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id='"+customer['userId']+"'::uuid AND NEW.password IS DISTINCT FROM OLD.password) EXECUTE FUNCTION "+fault+"()")
        status,result=api('POST','/reset-password',{**target,'otpCode':otp})
        password_unchanged=password_hash(customer)==before
        check(status==503 and result.get('success') is False and password_unchanged,'password commit failure reports unavailable and preserves password')
        check(db("SELECT count(*) FROM otps WHERE email='"+customer['email']+"' AND is_verified")=='1' and db("SELECT is_email_verified FROM users WHERE id='"+customer['userId']+"'")=='t','password commit rollback restores proof after email verification already succeeded')
        db('DROP TRIGGER '+fault+' ON users; DROP FUNCTION '+fault+'()')
        status,result=api('POST','/reset-password',{**target,'otpCode':otp})
        check(status==200 and result.get('success') is True and password_hash(customer)!=before,'valid HTTP OTP flow commits password and consumes proof')
        check(db("SELECT count(*) FROM otps WHERE email='"+customer['email']+"'")=='0','successful reset consumes stored proof')
        after=password_hash(customer)
        check(api('POST','/reset-password',{'email':customer['email'],'newPassword':secrets.token_urlsafe(24),'otpCode':otp})[1].get('success') is False and password_hash(customer)==after,'same OTP cannot reset password a second time')
        status,fresh_login=api('POST','/login',{'usernameOrEmail':customer['email'],'password':target['newPassword']})
        check(status==200,'new password authenticates through actual auth-to-user RPC')
        check(rpc('GetUserById',{'id':customer['userId']},customer['token'])[0]=='Unauthenticated','old user session is revoked at the gRPC boundary after reset')
        rpc_status,rpc_user=rpc('GetUserById',{'id':customer['userId']},fresh_login.get('token'))
        check(rpc_status=='OK' and rpc_user.get('id')==customer['userId'],'actual new-generation login session can read self over gRPC')
        check(api('POST','/login',{'usernameOrEmail':customer['email'],'password':customer['password']})[0]==401,'old password no longer authenticates')
        status,result=api('POST','/forgot-password?email='+customer['email'])
        check(status==200 and result.get('success') is True,'new reset challenge generated through supported query-parameter flow')
        otp=db("SELECT otp_code FROM otps WHERE email='"+customer['email']+"'")
        candidates=[secrets.token_urlsafe(24) for _ in range(2)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda password:api('POST','/reset-password',{'email':customer['email'],'otpCode':otp,'newPassword':password}),candidates))
        check(sum(status==200 and body.get('success') is True for status,body in results)==1,'concurrent resets consume one proof and produce exactly one success')
        check(db("SELECT count(*) FROM otps WHERE email='"+customer['email']+"'")=='0','concurrent reset leaves no reusable proof')
        winner=next(password for password,(status,body) in zip(candidates,results) if status==200 and body.get('success') is True)
        check(api('POST','/login',{'usernameOrEmail':customer['email'],'password':winner})[0]==200,'concurrent email verification never restores a stale password over the winning reset')
        for verified in [False,True]:
            status,result=api('POST','/forgot-password?email='+customer['email'])
            check(status==200 and result.get('success') is True,'fresh challenge available for attempt-limit regression')
            challenge=db("SELECT otp_code FROM otps WHERE email='"+customer['email']+"'")
            if verified:
                check(api('POST','/otp/verify',{'email':customer['email'],'otpCode':challenge})[1].get('verified') is True,'intermediate verified proof retains the same attempt limit')
            with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
                denied=list(pool.map(lambda _:api('POST','/otp/verify',{'email':customer['email'],'otpCode':'invalid-synthetic-code'}),range(5)))
            check(all(status==200 and result.get('verified') is False for status,result in denied) and db("SELECT attempt_count FROM otps WHERE email='"+customer['email']+"'")=='5','concurrent invalid verifications persist five attempts for verified='+str(verified))
            check(api('POST','/otp/verify',{'email':customer['email'],'otpCode':challenge})[1].get('verified') is False and db("SELECT count(*) FROM otps WHERE email='"+customer['email']+"'")=='0','spent attempt budget denies correct OTP for verified='+str(verified))
        # Actual hotel enrichment must send its own narrow service identity.
        check(h.sql('checkinn_hotel',"WITH added AS (INSERT INTO hotels SELECT (jsonb_populate_record(NULL::hotels,to_jsonb(h)||jsonb_build_object('id','"+hotel+"','owner_id','"+owner['userId']+"','name','rpccheck-fixture-"+hotel+"','approved_status','PENDING'))).* FROM hotels h LIMIT 1 RETURNING id) SELECT count(*) FROM added")=='1','create scoped pending property')
        status,result=api('GET','/hotels/pending/'+hotel,credential=token(customer['userId'],'ADMIN'),hotel_api=True)
        check(status==200,'admin pending detail still resolves owner through authenticated hotel RPC status='+str(status))
    finally:
        db('DROP TRIGGER IF EXISTS '+fault+' ON users; DROP FUNCTION IF EXISTS '+fault+'()')
        h.sql('checkinn_hotel',"DELETE FROM hotels WHERE id='"+hotel+"' AND name='rpccheck-fixture-"+hotel+"'")
        for account in accounts:
            h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL','auth:session-generation:'+account['userId']])
            db("DELETE FROM otps WHERE email='"+account['email']+"'; DELETE FROM user_profiles WHERE user_id='"+account['userId']+"'; DELETE FROM users WHERE id='"+account['userId']+"' AND email='"+account['email']+"'")
        for key_ in redis_keys:h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL',key_])
        db("DELETE FROM otps WHERE email IN ('"+prefix+"0@example.invalid','"+prefix+"1@example.invalid'); DELETE FROM user_profiles WHERE user_id IN (SELECT id FROM users WHERE username IN ('"+prefix+"0','"+prefix+"1')); DELETE FROM users WHERE username IN ('"+prefix+"0','"+prefix+"1') AND email IN ('"+prefix+"0@example.invalid','"+prefix+"1@example.invalid')")
        check(db("SELECT count(*) FROM users WHERE username IN ('"+prefix+"0','"+prefix+"1')")=='0','synthetic accounts and their scoped proof data removed')
    print(json.dumps({'verified_cases':count}),flush=True)

if __name__=='__main__':main()
