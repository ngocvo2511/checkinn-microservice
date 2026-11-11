"""Native public catalog gRPC checks, synthetic fixtures in verified checkinn-test only."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import uuid

spec=importlib.util.spec_from_file_location('security',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
 h.verify_running_clone('hotel-service')
 config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
 hotel,owner=[str(uuid.uuid4()) for _ in range(2)]
 rooms=[str(uuid.uuid4()) for _ in range(3)]
 proto=Path(__file__).resolve().parent.parent/'hotel-service/src/main/proto'
 command=['docker','run','--rm','--network',config['networks']['checkinn']['name'],'--mount',
  'type=bind,source='+str(proto)+',target=/protos,readonly','fullstorydev/grpcurl:v1.9.3',
  '-plaintext','-max-time','10','-import-path','/protos','-proto','hotel.proto']
 passed=0
 def check(condition,label):
  nonlocal passed
  print(json.dumps({'case':label,'result':'PASS' if condition else 'FAIL'}),flush=True)
  if not condition:raise AssertionError('Catalog RPC regression')
  passed+=1
 def rpc(method,body):
  result=subprocess.run(command+['-d',json.dumps(body),'hotel-service:9092','hotel.HotelGrpcService/'+method],capture_output=True,text=True,encoding='utf-8',timeout=30)
  if result.returncode==0:return 'OK',json.loads(result.stdout) if result.stdout.strip() else {}
  match=re.search(r'Code: ([A-Za-z]+)',result.stderr)
  return match.group(1) if match else 'TRANSPORT_ERROR',None
 def update(field,value):
  # Field names are constants in this test, never external input.
  if field not in ['capacity','amenities']:raise AssertionError('Unsupported fixture field')
  literal=json.dumps(value).replace("'","''")
  h.sql('checkinn_hotel',f"UPDATE room_types SET {field}='{literal}'::jsonb WHERE id='{rooms[0]}' AND hotel_id='{hotel}'")
 try:
  made=h.sql('checkinn_hotel',"INSERT INTO hotels(id,owner_id,city_id,name,address,approved_status,is_active,business_license_number,created_at,updated_at,accommodation_type) SELECT '"+hotel+"','"+owner+"',h.city_id,'Synthetic native catalog hotel','{\"street\":\"Synthetic\",\"city\":\"Synthetic\",\"country\":\"VN\"}'::jsonb,'APPROVED',true,'SYNTHETIC',now(),now(),'HOTEL' FROM hotels h LIMIT 1 RETURNING id")
  check(made.splitlines()[0]==hotel,'isolated synthetic catalog hotel created')
  for index,room in enumerate(rooms):
   active='true' if index<2 else 'false'
   h.sql('checkinn_hotel',"INSERT INTO room_types(id,hotel_id,name,base_price,capacity,amenities,is_active,total_rooms,created_at,updated_at) VALUES ('"+room+"','"+hotel+"','Synthetic native room',125000.50,'{\"adults\":2,\"children\":0}'::jsonb,'null'::jsonb,"+active+",3,now(),now()); INSERT INTO media_assets(id,target_id,target_type,url,is_thumbnail,sort_order,created_at,updated_at) VALUES ('"+str(uuid.uuid4())+"','"+room+"','ROOM_TYPE','https://example.invalid/"+room+".jpg',false,0,now(),now());")
  status,result=rpc('GetRoomTypeById',{'roomTypeId':rooms[0]})
  check(status=='OK' and result['roomType']['capacity']['adults']==2 and not result['roomType'].get('amenities'), 'optional JSON null amenities remain readable without invented occupancy')
  status,result=rpc('GetRoomTypesByHotel',{'hotelId':hotel})
  check(status=='OK' and len(result['roomTypes'])==2 and all(len(room['images'])==1 and room['images'][0]['url'].endswith(room['id']+'.jpg') for room in result['roomTypes']), 'batched images preserve room identity and exclude inactive room')
  status,result=rpc('GetHotelById',{'hotelId':hotel})
  check(status=='OK' and len(result['roomTypes'])==2 and all(room['total']==3 and room['basePrice']==125000.50 for room in result['roomTypes']), 'configured inventory and price retain existing wire values')
  for method,key in [('GetHotelById','hotelId'),('GetRoomTypesByHotel','hotelId'),('GetRoomTypeById','roomTypeId')]:
   check(rpc(method,{key:'1-1-1-1-1'})[0]=='InvalidArgument',method+' rejects abbreviated identifier')
  for capacity in [{'adults':2.5,'children':0},{'adults':'synthetic-private-capacity-marker','children':0},{}, {'adults':2147483648,'children':0}]:
   update('capacity',capacity)
   check(rpc('GetRoomTypeById',{'roomTypeId':rooms[0]})[0]=='DataLoss','invalid stored capacity is data loss rather than fabricated integers')
  update('capacity',None);status,result=rpc('GetRoomTypeById',{'roomTypeId':rooms[0]})
  check(status=='OK' and 'capacity' not in result['roomType'],'unknown capacity is an absent protobuf message')
  update('capacity',{'adults':2,'children':0});update('amenities',[7])
  check(rpc('GetRoomTypeById',{'roomTypeId':rooms[0]})[0]=='DataLoss','numeric amenity cannot be coerced to text')
  update('amenities',['Synthetic WiFi']);status,result=rpc('GetRoomTypeById',{'roomTypeId':rooms[0]})
  check(status=='OK' and result['roomType']['amenities']==['Synthetic WiFi'],'valid amenity text round-trips')
  h.sql('checkinn_hotel',"UPDATE room_types SET total_rooms=NULL WHERE id='"+rooms[0]+"' AND hotel_id='"+hotel+"'")
  check(rpc('GetHotelById',{'hotelId':hotel})[0]=='FailedPrecondition','unconfigured inventory does not become zero or ten')
  body={'hotelId':hotel,'name':'Synthetic rejected room','basePrice':125000.50,'capacity':{'adults':2,'children':0},'roomAmount':3,'amenities':[None]}
  auth='Authorization: Bearer '+h.token(config['services']['hotel-service']['environment']['SECRET_KEY'],owner,'OWNER')+'\n'
  result=subprocess.run(h.COMPOSE+['exec','-T','hotel-service','curl','-sS','--max-time','15','-X','POST','-H','@-','-H','Content-Type: application/json','--data',json.dumps(body),'-o','/dev/null','-w','%{http_code}','http://localhost:8083/api/v1'],input=auth,capture_output=True,text=True,encoding='utf-8',timeout=25)
  check(result.returncode==0 and result.stdout=='400','REST writer rejects null amenity elements before persisting')
  h.sql('checkinn_hotel',"UPDATE room_types SET total_rooms=3 WHERE id='"+rooms[0]+"'; UPDATE hotels SET approved_status='DRAFT' WHERE id='"+hotel+"'")
  for method,body in [('GetHotelById',{'hotelId':hotel}),('GetRoomTypesByHotel',{'hotelId':hotel}),('GetRoomTypeById',{'roomTypeId':rooms[0]})]:
   check(rpc(method,body)[0]=='NotFound',method+' retains unpublished catalog guard')
  logs=h.run(h.COMPOSE+['logs','--tail','300','hotel-service'])
  check('synthetic-private-capacity-marker' not in logs,'catalog failures never log stored private content')
  print(json.dumps({'passed':passed}),flush=True)
 finally:
  ids=','.join("'"+room+"'" for room in rooms)
  h.sql('checkinn_hotel',"BEGIN; DELETE FROM media_assets WHERE target_type='ROOM_TYPE' AND target_id IN (SELECT id FROM room_types WHERE hotel_id='"+hotel+"'); DELETE FROM room_types WHERE hotel_id='"+hotel+"'; DELETE FROM hotels WHERE id='"+hotel+"' AND owner_id='"+owner+"'; COMMIT;")

if __name__=='__main__':main()
