"""Approved reset policy across five resource services plus Auth validation, with private log probes."""
import concurrent.futures,datetime,hashlib,importlib.util,json,secrets,time,uuid,re
from pathlib import Path
from urllib.parse import urlencode,quote
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)
s=importlib.util.spec_from_file_location('av',root/'auth-validation-smoke.py');av=importlib.util.module_from_spec(s);s.loader.exec_module(av)

def main():
    services=['user-service','booking-service','hotel-service','revenue-service','regulations-service']
    for service in services:h.verify_running_clone(service)
    compose_config=json.loads(h.run(h.COMPOSE+['config','--format','json']));config=compose_config['services']
    started=datetime.datetime.now(datetime.timezone.utc).isoformat();private_values=set()
    notification=h.run(h.COMPOSE+['ps','-q','notification-service'])
    assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',notification]))
    prefix='sessioncheck_'+secrets.token_hex(8);accounts=[];revoked_keys=[];fault='sessioncheck_'+uuid.uuid4().hex;count=0;redis_stopped=False
    def db(query):return h.sql('checkinn_user',query)
    def check(ok,name):
        nonlocal count
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
        count+=1
    def api(service,method,path,body=None,credential=None,raw_body=None):
        if credential:private_values.add(credential)
        if service=='auth-service' and isinstance(body,dict):
            for key_,value in body.items():
                if key_ in ['email','username','usernameOrEmail','password','newPassword','otpCode','fullName'] and isinstance(value,str):private_values.add(value)
        port=config[service]['environment']['SERVER_PORT']
        settings='request = '+json.dumps(method)+'\nheader = "Content-Type: application/json"\n'
        if credential:settings+='header = '+json.dumps('Authorization: Bearer '+credential)+'\n'
        if raw_body is not None or body is not None:settings+='data-binary = '+json.dumps(raw_body if raw_body is not None else json.dumps(body))+'\n'
        raw=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','--config','-','-w','\n%{http_code}','http://localhost:'+str(port)+path],settings)
        text,_,status=raw.rpartition('\n')
        try:value=json.loads(text)
        except ValueError:value={}
        return int(status),value
    def auth(path,body=None,credential=None):return api('auth-service','POST',path,body,credential)
    def generation(user):return h.run(h.COMPOSE+['exec','-T','redis','redis-cli','--raw','GET','auth:session-generation:'+user])
    def claims(token):return json.loads(__import__('base64').urlsafe_b64decode(token.split('.')[1]+'=='))
    def login(account,password):
        status,result=auth('/login',{'usernameOrEmail':account['email'],'password':password});check(status==200 and bool(result.get('token')),'actual credentials issue a new session')
        return result['token']
    def challenge(account):
        status,result=api('auth-service','POST','/forgot-password?'+urlencode({'email':account['email']}));check(status==200 and result.get('success') is True,'plus-alias OTP challenge generated through supported API')
        return db("SELECT otp_code FROM otps WHERE email='"+account['email']+"'")
    def check_access(token,user,denied):
        today=datetime.date.today();start=today-datetime.timedelta(days=14)
        cases=[('user-service','GET','/api/user/profile',200,None),
            ('booking-service','GET','/api/bookings/user/'+user,200,None),
            ('hotel-service','GET','/hotels/pending',200,None),
            ('revenue-service','GET','/api/reports/admin/summary?from='+start.isoformat()+'&to='+today.isoformat(),200,None),
            # Malformed JSON must stop before any configuration write, after the ADMIN gate.
            ('regulations-service','PUT','/api/regulations/SESSION_TEST_NO_WRITE',400,'{')]
        for service,method,path,expected,raw in cases:
            status,_=api(service,method,path,credential=token,raw_body=raw)
            check(status==(401 if denied else expected),service+' '+('denies obsolete session' if denied else 'accepts current session without changing business data')+' status='+str(status))
        private_values.add(token)
        check(av.grpc_validate(compose_config,token)==('Unauthenticated' if denied else 'OK'),'auth gRPC validation follows the same session family policy')
    def user_list(token,user):return api('booking-service','GET','/api/bookings/user/'+user,credential=token)[0]
    try:
        deadline=time.monotonic()+120
        while True:
            status,_=auth('/login',{'usernameOrEmail':prefix+'_missing@example.invalid','password':secrets.token_urlsafe(24)})
            if status==401:break
            if status!=503 or time.monotonic()>deadline:raise RuntimeError('NEEDS_ENVIRONMENT: auth RPC discovery')
            time.sleep(2)
        for index in range(2):
            password=secrets.token_urlsafe(24);email=prefix+str(index)+'+alias@example.invalid'
            status,account=auth('/register',{'username':prefix+str(index),'email':email,'password':password,'fullName':'Synthetic session fixture'})
            check(status==200 and bool(account.get('userId')),'create isolated synthetic account')
            account.update({'email':email,'password':password});accounts.append(account)
        target,other=accounts;user=target['userId'];key='auth:session-generation:'+user
        check(user_list(target['token'],user)==200 and user_list(other['token'],other['userId'])==200,'independent initial sessions are usable')
        otp=db("SELECT otp_code FROM otps WHERE email='"+target['email']+"'")
        check(auth('/otp/verify',{'email':target['email'],'otpCode':otp})[1].get('verified') is True,'intermediate OTP verification stays compatible')
        db("UPDATE users SET role='ADMIN' WHERE id='"+user+"' AND username='"+prefix+"0'")
        first,second=login(target,target['password']),login(target,target['password'])
        check(not first==second and bool(claims(first).get('jti')),'separate sessions have distinct token IDs')
        check_access(first,user,False)
        legacy=h.token(config['auth-service']['environment']['SECRET_KEY'],user,'ADMIN')
        check_access(legacy,user,False)
        regulation_before=api('regulations-service','GET','/api/regulations')[1]
        original=db("SELECT md5(password) FROM users WHERE id='"+user+"'");new_password=secrets.token_urlsafe(24)
        db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic session reset fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+" AFTER UPDATE ON users DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id='"+user+"'::uuid AND NEW.password IS DISTINCT FROM OLD.password) EXECUTE FUNCTION "+fault+"()")
        status,result=auth('/reset-password',{'email':target['email'],'otpCode':otp,'newPassword':new_password})
        password_unchanged=db("SELECT md5(password) FROM users WHERE id='"+user+"'")==original
        if status!=503:print(json.dumps({'diagnostic':'reset commit fault HTTP status','status':status,'passwordUnchanged':password_unchanged}),flush=True)
        check(status==503 and result.get('success') is False and password_unchanged,'database commit fault keeps503 and leaves password unchanged')
        check(db("SELECT count(*) FROM otps WHERE email='"+target['email']+"' AND is_verified")=='1','database rollback restores consumed proof')
        failed_family=generation(user);check(bool(failed_family),'acknowledged revocation is retained on a later DB rollback')
        for token in [first,second,legacy]:check_access(token,user,True)
        check(user_list(target['token'],user)==401,'pre-reset registration session is also revoked')
        check(user_list(other['token'],other['userId'])==200 and not generation(other['userId']),'another account is not logged out')
        recovered=login(target,target['password']);check_access(recovered,user,False)
        check(claims(recovered).get('sessionGeneration')==failed_family,'old password can recover with a new family after failed reset')
        db('DROP TRIGGER '+fault+' ON users; DROP FUNCTION '+fault+'()')
        status,result=auth('/reset-password',{'email':target['email'],'otpCode':otp,'newPassword':new_password})
        check(status==200 and result.get('success') is True and generation(user)!=failed_family,'successful reset advances the family again')
        check_access(recovered,user,True)
        current=login(target,new_password);check_access(current,user,False)
        family=generation(user);check(claims(current).get('sessionGeneration')==family,'new credentials carry the validated family')
        check(h.run(h.COMPOSE+['exec','-T','redis','redis-cli','TTL',key])=='-1','security generation has no expiry')
        # Changing visible metadata does not repair a revoked token's signature.
        parts=first.split('.');payload=claims(first);payload['sessionGeneration']=family
        tampered=parts[0]+'.'+h.encode(json.dumps(payload).encode()).decode()+'.'+parts[2]
        check_access(tampered,user,True)
        before_replay=generation(user)
        check(auth('/reset-password',{'email':target['email'],'otpCode':otp,'newPassword':secrets.token_urlsafe(24)})[1].get('success') is False and generation(user)==before_replay,'invalid or spent OTP cannot rotate sessions')
        check_access(current,user,False)
        # Hard restart only the isolated Redis, without deleting volumes, keys or queues.
        h.run(h.COMPOSE+['kill','-s','SIGKILL','redis']);redis_stopped=True
        check(av.grpc_validate(compose_config,current)=='Unavailable','auth validation fails closed during actual Redis outage')
        h.run(h.COMPOSE+['start','redis']);redis_stopped=False
        deadline=time.monotonic()+60
        while True:
            try:
                if h.run(h.COMPOSE+['exec','-T','redis','redis-cli','PING'])=='PONG':break
            except RuntimeError:pass
            if time.monotonic()>deadline:raise RuntimeError('Test Redis restart timeout')
            time.sleep(1)
        check(generation(user)==family,'fsync-acknowledged family survives a Redis process crash')
        deadline=time.monotonic()+60
        while user_list(current,user)!=200:
            if time.monotonic()>deadline:raise RuntimeError('Test clients did not reconnect to Redis')
            time.sleep(1)
        check_access(first,user,True);check_access(current,user,False)
        additional=login(target,new_password)
        check(auth('/logout',credential=current)[0]==200,'individual logout remains available')
        revoked_keys.append('auth:revoked-token:'+hashlib.sha256(current.encode()).hexdigest())
        check(user_list(current,user)==401 and user_list(additional,user)==200,'individual logout leaves another current-family session valid')
        # The login must wait for a concurrent reset's account lock, not validate an old
        # password and then issue a token in the new family before the commit.
        otp=challenge(target);next_password=secrets.token_urlsafe(24);before_race=generation(user)
        db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(2); RETURN NEW; END $$; CREATE TRIGGER "+fault+" BEFORE UPDATE OF password ON users FOR EACH ROW WHEN (NEW.id='"+user+"'::uuid AND NEW.password IS DISTINCT FROM OLD.password) EXECUTE FUNCTION "+fault+"()")
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            resetting=pool.submit(auth,'/reset-password',{'email':target['email'],'otpCode':otp,'newPassword':next_password})
            deadline=time.monotonic()+10
            while generation(user)==before_race:
                if resetting.done() or time.monotonic()>deadline:raise RuntimeError('Reset did not enter revocation phase')
                time.sleep(.05)
            check(not resetting.done(),'reset is still in flight when competing login starts')
            signing_in=pool.submit(auth,'/login',{'usernameOrEmail':target['email'],'password':new_password})
            observed_wait=False
            while not resetting.done():
                waiting=db("SELECT count(*) FROM pg_stat_activity WHERE datname='checkinn_user' AND wait_event_type='Lock' AND query ILIKE '%from users%' AND query ILIKE '%username%'")
                observed_wait|=int(waiting)>0
                if observed_wait:break
                time.sleep(.05)
            reset_status,reset_body=resetting.result();login_status,_=signing_in.result()
        check(observed_wait,'real PostgreSQL account lock serializes credential validation against reset')
        check(reset_status==200 and reset_body.get('success') is True and login_status==401,'old credentials cannot issue a new-family session during reset')
        check_access(additional,user,True);final=login(target,next_password);check_access(final,user,False)
        check(api('regulations-service','GET','/api/regulations')[1]==regulation_before,'authorization probes never modify regulation data')
        status,body=api('user-service','GET','/api/test/generate-token/'+str(uuid.uuid4()))
        check(status==404 and not body.get('token'),'unsafe anonymous debug token issuer is absent')
        # Inspect in memory only; never print or persist raw logs, addresses, passwords, OTPs or JWTs.
        logs=h.run(h.COMPOSE+['logs','--no-color','--since',started,'auth-service'])
        leaked=False
        for value in private_values:
            if len(value)<6:continue
            if value.isdigit():leaked|=bool(re.search(r'(?<![A-Za-z0-9])'+re.escape(value)+r'(?![A-Za-z0-9])',logs))
            else:leaked|=value in logs or quote(value,safe='') in logs
        check(not leaked,'auth request/error logs exclude generated identity credentials OTPs and JWTs')
    finally:
        if redis_stopped:h.run(h.COMPOSE+['start','redis'])
        db('DROP TRIGGER IF EXISTS '+fault+' ON users; DROP FUNCTION IF EXISTS '+fault+'()')
        for account in accounts:
            db("DELETE FROM otps WHERE email='"+account['email']+"'; DELETE FROM user_profiles WHERE user_id='"+account['userId']+"'; DELETE FROM users WHERE id='"+account['userId']+"' AND email='"+account['email']+"'")
            h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL','auth:session-generation:'+account['userId']])
        for key_ in revoked_keys:h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL',key_])
        check(db("SELECT count(*) FROM users WHERE username IN ('"+prefix+"0','"+prefix+"1')")=='0','synthetic session accounts and scoped keys removed')
    print(json.dumps({'verified_cases':count}),flush=True)

if __name__=='__main__':main()
