"""Actual Gateway chain and Hikari measurements. Credentials stay in memory."""
import importlib.util,json,sys,time,urllib.request,urllib.error
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('diag',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
def api(port,path,body=None,token=None):
 headers={'Content-Type':'application/json','Accept':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 request=urllib.request.Request('http://127.0.0.1:'+str(port)+path,headers=headers,data=json.dumps(body).encode() if body is not None else None)
 try:
  with urllib.request.urlopen(request,timeout=20) as r:return r.status,json.load(r)
 except urllib.error.HTTPError as e:return e.code,None
 except Exception as e:return 0,{'error':type(e).__name__}
def smoke(label):
 rows=[]
 protected=json.loads((d.OUT/'fixture-manifest.json').read_text()).get('protectedAdminId')
 code,auth=api(8080,'/api/auth/login',{'usernameOrEmail':'admin','password':d.h.stack.runtime_values()['ADMIN_PASSWORD']})
 rows.append(dict(case='ADMIN real Gateway login',httpStatus=code,passed=code==200 and auth.get('role')=='ADMIN' and (protected is None or auth.get('userId')==protected)))
 if rows[-1]['passed']:
  code,q=api(8080,'/api/hotel/admin/inventory-reviews',token=auth['token']);rows.append(dict(case='ADMIN queue contract',httpStatus=code,responseType=type(q).__name__,passed=code==200 and isinstance(q,list)))
 for name,path,expected in [('hotel search','/hotels/offers?cityId=00000000-0000-0000-0000-000000000000',200),('booking authorization','/api/bookings',401)]:
  code,_=api(8080,path);rows.append(dict(case=name,httpStatus=code,passed=code==expected))
 code,registry=api(8761,'/eureka/apps')
 registered=[]
 if code==200:
  for app in registry['applications'].get('application',[]):
   for instance in app.get('instance',[]):
    if instance['status']=='UP':registered.append(app['name'])
 expected={'USER-SERVICE','HOTEL-SERVICE','BOOKING-SERVICE','AUTH-SERVICE','API-GATEWAY'}
 rows.append(dict(case='Eureka registrations',registered=registered,passed=expected<=set(registered)))
 d.write('smoke-'+label+'.json',rows)
 print(json.dumps(rows),flush=True)
 return all(r['passed'] for r in rows)
def metrics(label):
 rows={}
 for name in ('user','hotel','booking'):
  metrics={}
  for key in ('active','idle','pending','max','min','timeout','acquire','usage','creation'):
   code,result=api(d.h.stack.state()['ports'][name],'/actuator/metrics/hikaricp.connections.'+key)
   metrics[key]=result if code==200 else {'httpStatus':code}
  rows[name]=metrics
 d.write('hikari-'+label+'.json',rows)
if __name__=='__main__':
 label=sys.argv[1];ok=smoke(label);metrics(label);sys.exit(0 if ok else 1)

