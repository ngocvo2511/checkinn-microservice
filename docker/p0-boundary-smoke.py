"""Isolated clone only: media/loyalty/report boundaries. No storage/provider writes.
Creates fresh media/room/loyalty UUID fixtures; cleanup is limited to those UUIDs.
"""
import importlib.util
import json
from pathlib import Path
import subprocess
import time
import uuid

spec=importlib.util.spec_from_file_location('security',Path(__file__).with_name('booking-security-smoke.py'))
h=importlib.util.module_from_spec(spec); spec.loader.exec_module(h)


def main():
    for service in ['hotel-service','user-service','revenue-service']:
        h.verify_running_clone(service)
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    key=config['services']['hotel-service']['environment']['SECRET_KEY']
    for name in ['hotel-service','user-service','revenue-service','media-service','regulations-service']:
        container=h.run(h.COMPOSE+['ps','-q',name])
        for attempt in range(60):
            state=h.run(['docker','inspect','--format','{{.State.Health.Status}}',container])
            if state=='healthy': break
            time.sleep(2)
        else: raise RuntimeError('NEEDS_ENVIRONMENT: test service unhealthy')
    failures=0
    def case(name,actual,expected):
        nonlocal failures
        passed=actual==expected; failures+=not passed
        print(json.dumps({'case':name,'expected':expected,'actual':actual,'result':'PASS' if passed else 'FAIL'}),flush=True)
    def request(service,port,path,method='GET',credential=None,body=None,gateway=False):
        url=('http://api-gateway:8080' if gateway else 'http://localhost:'+str(port))+path
        headers='X-Is-Admin: true\nX-User-Id: '+str(uuid.uuid4())+'\n'
        if credential: headers+='Authorization: Bearer '+credential+'\n'
        args=h.COMPOSE+['exec','-T',service,'curl','-sS','--max-time','20','-X',method,'-H','@-','-o','/dev/null','-w','%{http_code}']
        if body is not None: args+=['-H','Content-Type: application/json','--data',json.dumps(body)]
        return int(h.run(args+[url],headers))
    for attempt in range(30):
        if request('hotel-service',8083,'/api/hotel/cities',gateway=True)==200: break
        time.sleep(2)
    else: raise RuntimeError('Gateway discovery not ready')
    source=h.sql('checkinn_hotel','SELECT r.id,h.id,h.owner_id FROM room_types r JOIN hotels h ON h.id=r.hotel_id WHERE h.owner_id IS NOT NULL LIMIT 1')
    if not source: raise RuntimeError('NEEDS_ENVIRONMENT: clone hotel/room fixture')
    source_room,hotel,owner=source.split('|')
    for value in [source_room,hotel,owner]: uuid.UUID(value)
    room,media,user,ledger_id=[str(uuid.uuid4()) for _ in range(4)]
    other=str(uuid.uuid4())
    own=h.token(key,owner,'OWNER'); stranger=h.token(key,other,'OWNER')
    customer=h.token(key,user,'CUSTOMER'); admin=h.token(key,other,'ADMIN')
    service=h.token(key,'booking-service','BOOKING_SERVICE')
    # Fresh rows only; uniqueness conflicts abort rather than touching existing rows.
    try:
        h.sql('checkinn_hotel',"INSERT INTO room_types SELECT (jsonb_populate_record(NULL::room_types,to_jsonb(r)||jsonb_build_object('id','"+room+"','name','synthetic-media-regression','is_active',false))).* FROM room_types r WHERE r.id='"+source_room+"'")
        h.sql('checkinn_hotel',"INSERT INTO media_assets(id,target_id,target_type,url,is_thumbnail,sort_order,created_at,updated_at) VALUES ('"+media+"','"+room+"','ROOM_TYPE','https://storage.invalid/synthetic.png',false,0,now(),now())")
        h.sql('checkinn_user',"INSERT INTO loyalty_points(id,user_id,total_points,used_points,available_points,created_at,updated_at) VALUES ('"+ledger_id+"','"+user+"',1000,0,1000,now(),now())")
        for gateway in [False,True]:
            label='gateway ' if gateway else 'direct '
            prefix='/api/hotel' if gateway else ''
            path=prefix+'/medias/'+media
            case(label+'anonymous media delete',request('hotel-service',8083,path,'DELETE',gateway=gateway),401)
            case(label+'cross owner media delete',request('hotel-service',8083,path,'DELETE',stranger,gateway=gateway),403)
            case(label+'cross owner thumbnail',request('hotel-service',8083,path+'/thumbnail','PATCH',stranger,gateway=gateway),403)
            case(label+'actual owner thumbnail',request('hotel-service',8083,path+'/thumbnail','PATCH',own,gateway=gateway),200)
            case(label+'anonymous loyalty read',request('user-service',8082,'/api/loyalty-points/'+user,gateway=gateway),401)
            case(label+'other user loyalty read',request('user-service',8082,'/api/loyalty-points/'+user,credential=stranger,gateway=gateway),403)
            case(label+'own loyalty read',request('user-service',8082,'/api/loyalty-points/'+user,credential=customer,gateway=gateway),200)
            case(label+'booking service loyalty read',request('user-service',8082,'/api/loyalty-points/'+user,credential=service,gateway=gateway),200)
            earn='/api/loyalty-points/earn?userId='+user+'&bookingId='+media+'&totalAmount=1000'
            case(label+'anonymous loyalty earn',request('user-service',8082,earn,'POST',gateway=gateway),401)
            case(label+'admin cannot earn points',request('user-service',8082,earn,'POST',admin,gateway=gateway),403)
            base='/api/reports/'
            dates='?from=2098-01-01&to=2098-01-02'
            case(label+'anonymous report',request('revenue-service',8088,base+'admin/revenue'+dates,gateway=gateway),401)
            case(label+'customer admin report',request('revenue-service',8088,base+'admin/revenue'+dates,credential=customer,gateway=gateway),403)
            case(label+'cross owner report',request('revenue-service',8088,base+'owner/summary'+dates+'&ownerId='+owner,credential=stranger,gateway=gateway),403)
            case(label+'owner systemwide report',request('revenue-service',8088,base+'revenue'+dates,credential=own,gateway=gateway),403)
            case(label+'admin aggregate report',request('revenue-service',8088,base+'admin/revenue'+dates,credential=admin,gateway=gateway),200)
            case(label+'owner hotel report',request('revenue-service',8088,base+'revenue'+dates+'&hotelId='+hotel,credential=own,gateway=gateway),200)
        case('internal earn accepted',request('user-service',8082,'/api/loyalty-points/earn?userId='+user+'&bookingId='+media+'&totalAmount=1000','POST',service),200)
        case('internal use accepted',request('user-service',8082,'/api/loyalty-points/use','POST',service,{'userId':user,'bookingId':media,'pointsToUse':1}),200)
        case('internal refund accepted',request('user-service',8082,'/api/loyalty-points/refund?userId='+user+'&bookingId='+media,'POST',service),200)
        case('media thumbnail metadata retained',h.sql('checkinn_hotel',"SELECT count(*) FROM media_assets WHERE id='"+media+"' AND is_thumbnail=true"),'1')
        proto=Path(__file__).resolve().parent.parent/'media-service/src/main/proto'
        network=config['networks']['checkinn']['name']
        for rpc,payload in [('DeleteMedia',{'fileName':'synthetic-never-stored.png'}),('GetSignedUrl',{'filePath':'synthetic-never-stored.pdf','expiresInSeconds':60})]:
            for credential in [None,admin]:
                args=['docker','run','--rm','--network',network,'--mount','type=bind,source='+str(proto)+',target=/protos,readonly','fullstorydev/grpcurl:v1.9.3','-plaintext','-max-time','20','-import-path','/protos','-proto','media.proto']
                if credential: args+=['-H','authorization: Bearer '+credential]
                result=subprocess.run(args+['-d',json.dumps(payload),'media-service:9096','media.MediaGrpcService/'+rpc],capture_output=True,text=True,timeout=45)
                expected='PermissionDenied' if credential else 'Unauthenticated'
                case('storage '+rpc+(' admin' if credential else ' anonymous'),result.returncode!=0 and expected in result.stderr,True)
    finally:
        h.sql('checkinn_hotel',"DELETE FROM media_assets WHERE id='"+media+"'; DELETE FROM room_types WHERE id='"+room+"'")
        h.sql('checkinn_user',"DELETE FROM points_transactions WHERE user_id='"+user+"'; DELETE FROM loyalty_points WHERE id='"+ledger_id+"'")
    return 1 if failures else 0

if __name__=='__main__':
    try: raise SystemExit(main())
    except Exception as error:
        print(json.dumps({'result':'ERROR','type':type(error).__name__})); raise SystemExit(1)
