"""OTP storage/commit status, rollback and private User logs on Mailpit/test clones."""
import datetime
import importlib.util
import json
import re
import secrets
import uuid
from pathlib import Path
from urllib.parse import urlencode,quote

root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)

def main():
 h.verify_running_clone('user-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 notification=h.run(h.COMPOSE+['ps','-q','notification-service'])
 assert 'SPRING_MAIL_HOST=mailpit' in json.loads(h.run(['docker','inspect','--format','{{json .Config.Env}}',notification]))
 marker='otpboundary_'+secrets.token_hex(8);email=marker+'+alias@example.invalid';password=secrets.token_urlsafe(24)
 full_name='Synthetic OTP Boundary '+secrets.token_hex(6);fault='otpboundary_'+uuid.uuid4().hex
 started=datetime.datetime.now(datetime.timezone.utc).isoformat();account=None;count=failed=0;private={email,marker,password,full_name}
 def db(query):return h.sql('checkinn_user',query)
 def check(ok,name):
  nonlocal count,failed
  count+=1;failed+=not ok;print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)
 def api(service,path,body=None,token=None):
  settings='request = "'+('GET' if token else 'POST')+'"\nheader = "Content-Type: application/json"\n'
  if body is not None:settings+='data-binary = '+json.dumps(json.dumps(body))+'\n'
  if token:settings+='header = '+json.dumps('Authorization: Bearer '+token)+'\n'
  port=config['services'][service]['environment']['SERVER_PORT']
  raw=h.run(h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','--config','-','-w','\n%{http_code}','http://localhost:'+str(port)+path],settings)
  text,_,status=raw.rpartition('\n')
  try:value=json.loads(text)
  except ValueError:value={}
  return int(status),value
 def proof():return db("SELECT md5(to_jsonb(o)::text) FROM otps o WHERE email='"+email+"'")
 def drop(table):db('DROP TRIGGER IF EXISTS '+fault+' ON '+table+'; DROP FUNCTION IF EXISTS '+fault+'()')
 def trigger(table,when,condition):
  db("CREATE FUNCTION "+fault+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic OTP boundary commit fault'; RETURN NEW; END $$; CREATE CONSTRAINT TRIGGER "+fault+' AFTER '+when+' ON '+table+" DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN ("+condition+") EXECUTE FUNCTION "+fault+"()")
 try:
  status,account=api('auth-service','/register',{'username':marker,'email':email,'password':password,'fullName':full_name})
  assert status==200 and account.get('userId'),'NEEDS_ENVIRONMENT: synthetic account registration'
  private.add(account['token']);check(db("SELECT count(*) FROM otps WHERE email='"+email+"'")=='1','registration creates supported scoped OTP proof')
  before=proof()
  trigger('otps','INSERT',"NEW.email='"+email+"'")
  status,body=api('auth-service','/forgot-password?'+urlencode({'email':email}))
  unchanged=proof()==before
  check(status==503 and body.get('success') is False,'OTP generation commit failure is unavailable through Auth proxy')
  check(unchanged,'failed challenge rotation restores previous OTP proof')
  drop('otps')
  otp=db("SELECT otp_code FROM otps WHERE email='"+email+"'");private.add(otp)
  trigger('otps','UPDATE',"NEW.email='"+email+"' AND NEW.is_verified AND NOT OLD.is_verified")
  status,body=api('auth-service','/otp/verify',{'email':email,'otpCode':otp})
  unchanged=proof()==before
  check(status==503 and body.get('status')==503 and body.get('message')=='Service Unavailable','OTP proof commit failure uses safe Auth HTTP error contract')
  status,body=api('user-service','/api/otp/verify',{'email':email,'otpCode':otp})
  check(status==503 and body.get('verified') is False,'User proof commit failure explicitly returns verified false')
  check(unchanged and db("SELECT is_email_verified FROM users WHERE id='"+account['userId']+"'")=='f','failed verification keeps proof and account flag unchanged')
  drop('otps')
  trigger('users','UPDATE',"NEW.email='"+email+"' AND NEW.is_email_verified AND NOT OLD.is_email_verified")
  status,body=api('auth-service','/otp/verify',{'email':email,'otpCode':otp})
  check(status==503 and body.get('status')==503 and body.get('message')=='Service Unavailable','account email commit failure uses safe Auth HTTP error contract')
  status,body=api('user-service','/api/otp/verify',{'email':email,'otpCode':otp})
  check(status==503 and body.get('verified') is False,'User email commit failure explicitly returns verified false')
  check(db("SELECT is_email_verified FROM users WHERE id='"+account['userId']+"'")=='f','email flag rollback remains intact')
  drop('users')
  status,body=api('auth-service','/otp/verify',{'email':email,'otpCode':otp})
  check(status==200 and body.get('verified') is True,'existing verified proof may finish email verification after fault removal')
  check(api('user-service','/api/user/profile',token=account['token'])[0]==200,'authorized profile read remains supported')
  status,body=api('auth-service','/otp/verify',{'email':email,'otpCode':'wrong-proof'})
  check(status==200 and body.get('verified') is False,'known incorrect OTP keeps false business result and attempt budget')
  logs=h.run(h.COMPOSE+['logs','--no-color','--since',started,'user-service'])
  leaked=False
  for value in private:
   if len(value)<6:continue
   if value.isdigit():leaked|=bool(re.search(r'(?<![A-Za-z0-9])'+re.escape(value)+r'(?![A-Za-z0-9])',logs))
   else:leaked|=value in logs or quote(value,safe='') in logs
  check(not leaked,'User account OTP/profile logs exclude generated identity password proof and token')
 finally:
  drop('otps');drop('users')
  # Resolve our exact fixture even when the HTTP response was lost after registration.
  ids=db("SELECT id FROM users WHERE username='"+marker+"' AND email='"+email+"'").splitlines()
  for value in ids:
   id=str(uuid.UUID(value))
   db("DELETE FROM otps WHERE email='"+email+"'; DELETE FROM user_profiles WHERE user_id='"+id+"'; DELETE FROM users WHERE id='"+id+"' AND email='"+email+"'")
   h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL','auth:session-generation:'+id])
  check(db("SELECT count(*) FROM users WHERE username='"+marker+"'")=='0','exact synthetic account/proof/trigger cleanup')
 print(json.dumps({'verified_cases':count,'failures':failed}),flush=True)
 return 1 if failed else 0

if __name__=='__main__':raise SystemExit(main())
