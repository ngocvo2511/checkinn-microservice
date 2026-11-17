"""Batch8C reuses scoped Batch8B contracts with separate manifests and private identities."""
import importlib.util,json,secrets,sys,uuid
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('acceptance',R/'tools/batch8b-acceptance.py');a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
a.OUT=R/'Docs/evidence/batch-8c-runtime-stability';a.PRIVATE=R/'.local/batch8c';a.PRIVATE.mkdir(exist_ok=True)

def account(role,index):
 name='b8c_'+role.lower()+secrets.token_hex(5);password=secrets.token_urlsafe(20)+'Aa1!';email=name+'@example.invalid'
 secret=dict(username=name,password=password,email=email,role=role)
 p=a.PRIVATE/(name+'.json');p.write_text(json.dumps(secret))
 route='/api/auth/register-owner' if role=='OWNER' else '/api/auth/register'
 code,result=a.api(route,'POST',dict(username=name,password=password,email=email,fullName='Synthetic Batch8C '+role))
 a.check(code==200 and bool(result.get('userId')),role+' register Gateway',code)
 uid=result['userId'];a.record('user','users','id',uid,async_effects=['user_profiles','otps','Rabbit OTP message; no SMTP consumer started'])
 a.discover()
 # Read only the OTP of the newly manifested synthetic identity; never export OTP.
 otp=a.sql('user',f"SELECT json_build_object('otp',otp_code)::text FROM otps WHERE email='{email}'")[0]['otp']
 code,_=a.api('/api/auth/otp/verify','POST',dict(email=email,otpCode=otp));a.check(code==200,role+' API verify exact synthetic OTP (DB-assisted setup)',code)
 code,result=a.api('/api/auth/login','POST',dict(usernameOrEmail=name,password=password));a.check(code==200 and result.get('role')==role,role+' real Gateway login',code)
 secret.update(id=uid,token=result['token']);p.write_text(json.dumps(secret));return secret

a.account=account

def approve_first():
 ds=json.loads((a.PRIVATE/'dataset.json').read_text());room=ds['rooms'][0];ac=json.loads((a.PRIVATE/'accounts.json').read_text());owner=next(v for v in ac if v['id']==room['ownerId'])
 code,state=a.api('/api/v1/'+room['id']+'/inventory-review',token=owner['token']);assert code==200
 p=state['latestRequest']
 if p['status']=='PENDING':
  code,result=a.api('/api/hotel/admin/inventory-reviews/'+p['id']+'/review','POST',dict(approve=True,revision=state['revision'],reason='Synthetic Batch8C benchmark setup'),a.admin());a.check(code==200,'Approve first benchmark room',code)

def main():
 try:
  action=sys.argv[1]
  if action=='setup':
   assert json.loads((a.OUT/'benchmark-readiness-gate.json').read_text())['passed']
   a.setup()
  elif action=='dataset':
   spec=importlib.util.spec_from_file_location('smoke',R/'tools/batch8c-smoke.py');live=importlib.util.module_from_spec(spec);spec.loader.exec_module(live)
   assert live.smoke('dataset-gate-'+sys.argv[2]+'x'+sys.argv[3]),'Live full-chain readiness must pass before dataset writes'
   smoke=json.loads((a.OUT/'smoke-instrumented-recheck.json').read_text())
   assert all(row['passed'] for row in smoke),'Chain readiness must pass after instrumentation restart'
   assert json.loads((a.OUT/'benchmark-readiness-gate.json').read_text())['passed']
   ds=a.dataset(int(sys.argv[2]),int(sys.argv[3]));approve_first();a.discover();a.write('dataset-'+sys.argv[2]+'x'+sys.argv[3]+'.json',dict(dataset=ds,totals=[int(sys.argv[2]),int(sys.argv[3])],reconstructedAfterStage=False))
  elif action=='discover':a.discover()
  elif action=='cleanup':a.cleanup()
  elif action=='cleanup-broker':
   # Original helper selects b8b_ files; exact private Batch8C names are supplied by a scoped glob adapter.
   original=a.PRIVATE
   class Names:
    def glob(self,pattern):return original.glob('b8c_*.json' if pattern=='b8b_*.json' else pattern)
   a.PRIVATE=Names()
   try:a.cleanup_broker()
   finally:a.PRIVATE=original
 finally:
  for c in a.READ_CONNECTIONS.values():c.close()
if __name__=='__main__':main()
