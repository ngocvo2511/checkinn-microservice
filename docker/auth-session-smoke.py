"""Real credentials/JWT/revocation contract on clones; no external OAuth or real email."""
import base64,hashlib,importlib.util,json,secrets,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
    for service in ['user-service','booking-service']:h.verify_running_clone(service)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    assert config['services']['notification-service']['environment']['SPRING_MAIL_HOST']=='mailpit'
    container=h.run(h.COMPOSE+['ps','-q','notification-service'])
    assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',container]))
    suffix=uuid.uuid4().hex[:16];prefix='authcheck_'+suffix;password=secrets.token_urlsafe(24);tokens=[]
    def api(service,method,path,body=None,token=None,gateway=False):
        port='8081' if service=='auth-service' else '8084'
        base='http://api-gateway:8080' if gateway else 'http://localhost:'+port
        if gateway and service=='auth-service':path='/api/auth'+path
        command=h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-X',method,'-w','\n%{http_code}']
        data=None
        if body is not None:command+=['-H','Content-Type: application/json','--data-binary','@-'];data=json.dumps(body)
        elif token:command+=['-H','@-'];data='Authorization: Bearer '+token+'\n'
        raw=h.run(command+[base+path],data);text,_,code=raw.rpartition('\n')
        try:value=json.loads(text)
        except ValueError:value=None
        return int(code),value
    def check(ok,name):
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
        if not ok:raise AssertionError(name)
    try:
        for index,role in enumerate(['CUSTOMER','OWNER','ADMIN']):
            username=prefix+str(index);email=username+'@example.invalid'
            path='/register-owner' if role=='OWNER' else '/register'
            code,value=api('auth-service','POST',path,{'username':username,'email':email,'password':password,'fullName':'Synthetic auth fixture'},gateway=True)
            if code!=200:print(json.dumps({'diagnostic':'registration HTTP status','status':code}),flush=True)
            check(code==200 and isinstance(value.get('userId') if isinstance(value,dict) else None,str),'registration returns string internal user identity')
            user=str(uuid.UUID(value['userId']));tokens.append(value['token'])
            check(api('auth-service','POST','/login',{'usernameOrEmail':email,'password':password},gateway=True)[0]==401,'unverified fixture cannot log in')
            # Fixture preparation only: no claim of OTP delivery/verification or real admin provisioning.
            h.sql('checkinn_user',"UPDATE users SET is_email_verified=true,role='"+role+"' WHERE id='"+user+"' AND username='"+username+"'")
            for identifier,gateway in [(email,False),(username,True)]:
                code,value=api('auth-service','POST','/login',{'usernameOrEmail':identifier,'password':password,'role':'ADMIN'},gateway=gateway)
                check(code==200 and value['userId']==user and value['role']==role,'login uses canonical usernameOrEmail and persisted role')
                token=value['token'];tokens.append(token)
                claims=json.loads(base64.urlsafe_b64decode(token.split('.')[1]+'=='))
                check(claims['sub']==user and claims['role']==role,'issued internal JWT binds user and role')
            check(api('auth-service','POST','/login',{'usernameOrEmail':email,'password':'synthetic-invalid-password'},gateway=True)[0]==401,'wrong password never authenticates')
            check(api('booking-service','GET','/api/bookings/user/'+user,token=token,gateway=True)[0]==200,'real internal JWT authorizes own booking list')
            other=str(uuid.uuid4())
            if role!='ADMIN':check(api('booking-service','GET','/api/bookings/user/'+other,token=token,gateway=True)[0]==403,'real customer or owner JWT cannot list another user bookings')
            check(api('auth-service','POST','/logout',token=token,gateway=True)[0]==200,'logout revokes actual internal JWT')
            check(api('booking-service','GET','/api/bookings/user/'+user,token=token,gateway=True)[0]==401,'revoked internal JWT cannot be reused across services')
    finally:
        # Scope cleanup by generated username prefix; never delete copied accounts or shared queues.
        h.sql('checkinn_user',"DELETE FROM otps WHERE email LIKE '"+prefix+"%@example.invalid'; DELETE FROM user_profiles WHERE user_id IN (SELECT id FROM users WHERE username LIKE '"+prefix+"%'); DELETE FROM users WHERE username LIKE '"+prefix+"%'")
        for token in set(tokens):
            digest=hashlib.sha256(token.encode()).hexdigest()
            h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL','auth:revoked-token:'+digest])
        check(h.sql('checkinn_user',"SELECT count(*) FROM users WHERE username LIKE '"+prefix+"%'")=='0','synthetic auth accounts cleaned up')

if __name__=='__main__':main()
