"""Amenity writes/mapping compatibility, synthetic fixtures in checkinn-test only."""
import concurrent.futures
import importlib.util
import json
from pathlib import Path
import subprocess
import urllib.parse
import uuid

spec=importlib.util.spec_from_file_location('security',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
ABSENT=object()

def main():
 h.verify_running_clone('hotel-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 key=config['services']['hotel-service']['environment']['SECRET_KEY']
 hotel,foreign,owner,other,admin=[str(uuid.uuid4()) for _ in range(5)]
 code='SYNTHETIC_'+uuid.uuid4().hex.upper();passed=0
 def check(condition,label):
  nonlocal passed
  print(json.dumps({'case':label,'result':'PASS' if condition else 'FAIL'}),flush=True)
  if not condition:raise AssertionError('Amenity write regression')
  passed+=1
 def api(method,path,actor=None,body=ABSENT,role='OWNER',headers=''):
  args=h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','20','-X',method,'-H','@-','-w','\n%{http_code}']
  if body is not ABSENT:args+=['-H','Content-Type: application/json','--data',json.dumps(body)]
  auth='Authorization: Bearer '+h.token(key,actor,role)+'\n' if actor else ''
  result=subprocess.run(args+['http://localhost:8083'+path],input=auth+headers,capture_output=True,text=True,encoding='utf-8',timeout=30)
  if result.returncode:raise RuntimeError('Test HTTP transport failed')
  raw,status=result.stdout.rsplit('\n',1);return int(status),json.loads(raw) if raw else None
 def category(title,*items):return {'title':title,'amenities':[{'title':item} for item in items]}
 def put(values,actor=owner):return api('PUT','/hotels/'+hotel+'/amenities',actor,{'amenityCategories':values})
 def titles(target=hotel):
  raw=h.sql('checkinn_hotel',"SELECT a.title FROM hotel_amenities a JOIN hotel_amenity_categories c ON c.id=a.category_id WHERE c.hotel_id='"+target+"' ORDER BY a.title")
  return raw.splitlines() if raw else []
 try:
  for target,actor in [(hotel,owner),(foreign,other)]:
   made=h.sql('checkinn_hotel',"INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT '"+target+"','"+actor+"',h.city_id,'Synthetic amenity runtime hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'DRAFT',false,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1 RETURNING id")
   check(made.splitlines()[0]==target,'isolated synthetic amenity hotel created')
  initial=[category('Synthetic category','Synthetic original')]
  check(put(initial)[0]==200 and titles()==['Synthetic original'],'valid owner amenity replacement accepted')
  check(api('PUT','/hotels/'+hotel+'/amenities',None,{'amenityCategories':[]},headers='X-OWNER-ID: '+owner+'\n')[0]==401,'anonymous forged identity denied')
  check(put([],other)[0]==403 and titles()==['Synthetic original'],'other owner cannot clear known hotel')
  for body in [{},{'amenityCategories':None},{'amenityCategories':[None]},{'amenityCategories':[category(' ','Synthetic')]},{'amenityCategories':[{'title':'Synthetic','amenities':[None]}]},{'amenityCategories':[category('Synthetic',' ')]},{'amenityCategories':[category('x'*256,'Synthetic')]}]:
   status,data=api('PUT','/hotels/'+hotel+'/amenities',owner,body)
   check(status==400 and isinstance(data.get('fieldErrors'),dict) and titles()==['Synthetic original'],'malformed nested replacement rejects before changing stored data')
  check(api('POST','/hotels/'+hotel+'/amenities',owner,{'title':' '})[0]==400,'single category cannot bypass validation')
  check(api('POST','/hotels/'+hotel+'/amenities',owner,{'title':'Synthetic empty category'})[0]==200,'optional child list remains compatible')
  status,data=put(initial);raw_id=data['amenityCategories'][0]['items'][0]['id']
  check(status==200 and titles()==['Synthetic original'],'explicit valid replacement removes prior empty category')
  check(api('POST','/admin/amenity-catalog',admin,{'code':code,'label':'Synthetic catalog entry','active':True,'revision':0},'ADMIN')[0]==201,'admin creates only generated synthetic catalog entry')
  status,proposal=api('POST','/hotels/'+hotel+'/amenity-mappings/'+raw_id+'/requests',owner,{'codes':[code],'revision':0,'note':'Synthetic proposal'})
  check(status==201,'owner proposes explicit catalog mapping')
  request_id=proposal['id']
  check(api('POST','/admin/amenity-mappings/'+request_id+'/review',admin,{'approve':True,'revision':proposal['revision'],'reason':'Synthetic review'},'ADMIN')[0]==200,'admin explicitly approves synthetic mapping')
  status,preview=api('GET','/hotels/'+hotel+'/preview',owner)
  check(status==200 and preview['amenityCategories'][0]['items'][0]['verifiedCodes'][0]['code']==code,'approved code is attached to current raw identity')
  status,replaced=put(initial);new_item=replaced['amenityCategories'][0]['items'][0]
  check(status==200 and new_item['id']!=raw_id and not new_item.get('verifiedCodes'),'same-label replacement does not auto-approve a new raw identity')
  check(h.sql('checkinn_hotel',"SELECT status FROM amenity_mapping_requests WHERE id='"+request_id+"'").splitlines()[0]=='APPROVED','existing approved audit remains retained')
  embedded={'amenityCategories':[category('Synthetic embedded','Embedded synthetic item')]}
  check(api('PUT','/hotels/'+hotel,owner,embedded)[0]==200 and titles()==['Embedded synthetic item'],'embedded amenity update round-trips')
  check(api('PUT','/hotels/'+hotel,owner,{})[0]==200 and api('PUT','/hotels/'+hotel,owner,{'amenityCategories':None})[0]==200 and titles()==['Embedded synthetic item'],'omitted and null embedded fields preserve content')
  check(api('PUT','/hotels/'+hotel,owner,{'name':'Synthetic invalid change','amenityCategories':[None]})[0]==400 and titles()==['Embedded synthetic item'],'invalid embedded content cannot change hotel or amenities')
  with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
   results=list(pool.map(lambda batch:put([category('Synthetic concurrent',f'Concurrent {batch} A',f'Concurrent {batch} B')])[0],range(8)))
  rows=titles();check(results==[200]*8 and len(rows)==2 and len({text[:-2] for text in rows})==1,'existing hotel lock still serializes eight HTTP replacements')
  foreign_title='Foreign synthetic category'
  check(api('PUT','/hotels/'+foreign+'/amenities',other,{'amenityCategories':[category(foreign_title,'Foreign synthetic item')]})[0]==200,'foreign fixture saved under its owner')
  path='/hotels/'+hotel+'/amenities/'+urllib.parse.quote(foreign_title,safe='')
  check(api('DELETE',path,owner)[0]==404 and titles(foreign)==['Foreign synthetic item'],'owned parent cannot delete another hotels category')
  check(put([])[0]==200 and not titles(),'explicit empty collection clears amenities')
  check(api('PUT','/hotels/'+hotel,owner,embedded)[0]==200 and api('PUT','/hotels/'+hotel,owner,{'amenityCategories':[]})[0]==200 and not titles(),'explicit embedded empty list clears only requested content')
  print(json.dumps({'passed':passed}),flush=True)
 finally:
  ids="'"+hotel+"','"+foreign+"'"
  h.sql('checkinn_hotel',"BEGIN; DELETE FROM amenity_mapping_states WHERE hotel_id IN ("+ids+"); DELETE FROM amenity_mapping_request_codes WHERE request_id IN (SELECT id FROM amenity_mapping_requests WHERE hotel_id IN ("+ids+")); DELETE FROM amenity_mapping_requests WHERE hotel_id IN ("+ids+"); DELETE FROM amenity_catalog WHERE code='"+code+"'; DELETE FROM hotel_amenities WHERE category_id IN (SELECT id FROM hotel_amenity_categories WHERE hotel_id IN ("+ids+")); DELETE FROM hotel_amenity_categories WHERE hotel_id IN ("+ids+"); DELETE FROM hotels WHERE id IN ("+ids+"); COMMIT;")

if __name__=='__main__':main()
