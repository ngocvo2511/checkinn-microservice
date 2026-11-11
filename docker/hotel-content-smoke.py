"""Hotel policy/FAQ contracts on generated fixtures in verified checkinn-test only."""
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import subprocess
import uuid

spec=importlib.util.spec_from_file_location('security',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
ABSENT=object()

def main():
 h.verify_running_clone('hotel-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 key=config['services']['hotel-service']['environment']['SECRET_KEY']
 hotel,foreign,owner,other,category=[str(uuid.uuid4()) for _ in range(5)]
 category_name='Synthetic content '+category
 passed=0
 def check(condition,label):
  nonlocal passed
  print(json.dumps({'case':label,'result':'PASS' if condition else 'FAIL'}),flush=True)
  if not condition:raise AssertionError('Hotel content regression')
  passed+=1
 def api(method,path,actor=None,body=ABSENT,headers=''):
  args=h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','20','-X',method,'-H','@-','-w','\n%{http_code}']
  if body is not ABSENT:args+=['-H','Content-Type: application/json','--data',json.dumps(body)]
  auth='Authorization: Bearer '+h.token(key,actor,'OWNER')+'\n' if actor else ''
  result=subprocess.run(args+['http://localhost:8083/hotels/'+path],input=auth+headers,capture_output=True,text=True,encoding='utf-8',timeout=30)
  if result.returncode:raise RuntimeError('Test HTTP transport failed')
  raw,code=result.stdout.rsplit('\n',1)
  return int(code),json.loads(raw) if raw else None
 def q(text):return {'question':text,'answer':'Synthetic answer'}
 def p(text):return {'category':category_name,'content':text}
 def rows(kind,target=hotel):
  table,field=('hotel_questions','question') if kind=='questions' else ('hotel_policies','content')
  raw=h.sql('checkinn_hotel',f"SELECT {field} FROM {table} WHERE hotel_id='{target}' ORDER BY {field}")
  return raw.splitlines() if raw else []
 try:
  for target,actor in [(hotel,owner),(foreign,other)]:
   made=h.sql('checkinn_hotel',"INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT '"+target+"','"+actor+"',h.city_id,'Synthetic content runtime hotel','{\"street\":\"Synthetic street\",\"city\":\"Synthetic city\",\"country\":\"VN\",\"coordinateSource\":\"UNKNOWN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1 RETURNING id")
   check(made.splitlines()[0]==target,'isolated synthetic hotel created')
  h.sql('checkinn_hotel',"INSERT INTO policy_category_types(id,name) VALUES ('"+category+"','"+category_name+"')")
  for kind,body in [('questions',[q('Original synthetic FAQ')]),('policies',[p('Original synthetic policy')])]:
   check(api('PUT',hotel+'/'+kind,owner,body)[0]==200 and api('PUT',foreign+'/'+kind,other,body)[0]==200,kind+' valid owner writes accepted')
   check(api('PUT',hotel+'/'+kind,None,[],headers='X-OWNER-ID: '+owner+'\n')[0]==401,kind+' anonymous forged identity denied')
   check(api('PUT',hotel+'/'+kind,other,[],headers='X-OWNER-ID: '+owner+'\n')[0]==403,kind+' unrelated owner denied despite forged header')
   before=rows(kind)
   check(api('PUT',hotel+'/'+kind,owner,None)[0]==400 and api('PUT',hotel+'/'+kind,owner,[None])[0]==400,kind+' null collection and null element rejected')
   invalid=[q('x'*256)] if kind=='questions' else [{'category':category_name,'content':' '}]
   check(api('PUT',hotel+'/'+kind,owner,invalid)[0]==400 and rows(kind)==before,kind+' malformed content rejected without clearing stored rows')
   with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    codes=list(pool.map(lambda batch:api('PUT',hotel+'/'+kind,owner,
     [q(f'Concurrent batch {batch} A'),q(f'Concurrent batch {batch} B')] if kind=='questions' else [p(f'Concurrent batch {batch} A'),p(f'Concurrent batch {batch} B')])[0],range(8)))
   saved=rows(kind)
   check(codes==[200]*8 and len(saved)==2 and len({text[:-2] for text in saved})==1,kind+' eight real HTTP replacements leave one complete submitted list')
   table='hotel_questions' if kind=='questions' else 'hotel_policies'
   child=h.sql('checkinn_hotel',f"SELECT id FROM {table} WHERE hotel_id='{foreign}' LIMIT 1").splitlines()[0]
   check(api('DELETE',hotel+'/'+kind+'/'+child,owner)[0]==404 and len(rows(kind,foreign))==1,kind+' foreign child cannot be deleted through owned parent')
  marker='synthetic-private-category-'+str(uuid.uuid4())
  before=rows('policies')
  code,error=api('PUT',hotel+'/policies',owner,[{'category':marker,'content':'Synthetic content'}])
  check(code==404 and marker not in json.dumps(error) and rows('policies')==before,'unknown category rolls back and is not echoed')
  check(api('PUT',hotel,owner,{'policies':[p('Embedded synthetic policy')],'questions':[q('Embedded synthetic FAQ')]})[0]==200
   and rows('questions')==['Embedded synthetic FAQ'] and rows('policies')==['Embedded synthetic policy'],'embedded hotel content fields round-trip instead of being ignored')
  check(api('PUT',hotel,owner,{})[0]==200 and rows('questions')==['Embedded synthetic FAQ'],'omitted embedded lists retain existing content')
  check(api('PUT',hotel,owner,{'questions':[]})[0]==200 and not rows('questions') and rows('policies')==['Embedded synthetic policy'],'explicit embedded empty list clears only requested collection')
  check(api('PUT',hotel+'/questions',owner,[q('Synthetic FAQ before invalid nested request')])[0]==200,'FAQ rollback fixture saved')
  check(api('PUT',hotel,owner,{'name':'Synthetic invalid change','questions':[{'question':'Synthetic','answer':None}]})[0]==400
   and rows('questions')==['Synthetic FAQ before invalid nested request'],'nested invalid hotel update leaves content unchanged')
  for kind in ['questions','policies']:
   check(api('DELETE',hotel+'/'+kind,owner)[0]==200 and not rows(kind),kind+' explicit authorized clear works')
  print(json.dumps({'passed':passed}),flush=True)
 finally:
  ids="'"+hotel+"','"+foreign+"'"
  h.sql('checkinn_hotel',"BEGIN; DELETE FROM hotel_questions WHERE hotel_id IN ("+ids+"); DELETE FROM hotel_policies WHERE hotel_id IN ("+ids+"); DELETE FROM hotels WHERE id IN ("+ids+"); DELETE FROM policy_category_types WHERE id='"+category+"'; COMMIT;")

if __name__=='__main__':main()
