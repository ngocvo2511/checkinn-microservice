"""Start/check/stop only the explicitly prepared Batch6B local session. No migration option."""
from pathlib import Path
import argparse
import datetime
import json
import os
import subprocess
import urllib.error
import urllib.request
from provider_config import sandbox_environment, storage_environment

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = ROOT/'.local/batch6b'
OWNERS = ('user', 'hotel', 'booking', 'revenue', 'regulations')
MODULES = {'eureka':'eureka-server', 'gateway':'api-gateway', 'auth':'auth-service',
           'media':'media-service', 'notification':'notification-service',
           **{owner:owner+'-service' for owner in OWNERS}}
GRPC = {'auth':9091, 'hotel':9092, 'booking':9093, 'media':9096, 'user':9097,
        'notification':9098, 'regulations':9094, 'revenue':9099}


def read_env(path):
    result={}
    for line in path.read_text(encoding='utf-8-sig').splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key,value=line.split('=',1)
            if len(value)>1 and value[0]==value[-1] and value[0] in ('"', "'"):value=value[1:-1]
            result[key.strip()]=value
    return result


def runtime_values():
    """Daily application configuration has one visible source."""
    return read_env(ROOT/'docker/.env')


def values():
    # Migration credentials/management tokens remain private operational inputs.
    result=read_env(PRIVATE/'.env')
    runtime=runtime_values()
    keys={'SECRET_KEY','NEXTAUTH_SECRET','SUPABASE_DEV_VNPAY_MODE','SUPABASE_DEV_STORAGE_MODE',
          'SUPABASE_STORAGE_PROJECT_REF','SUPABASE_URL','SUPABASE_SERVICE_ROLE_KEY',
          'SUPABASE_BUCKET','SUPABASE_PRIVATE_BUCKET','VNPAY_TMN_CODE','VNPAY_HASH_SECRET',
          'VNPAY_PAY_URL','VNPAY_RETURN_URL','REFUND_INTERNAL_ENABLED'}
    keys.update(owner.upper()+'_DB_'+suffix for owner in OWNERS
                for suffix in ('URL','USERNAME','PASSWORD','PROJECT_REF','RUNTIME_ROLE','POOL_MAX','CONNECTION_BUDGET'))
    # Missing application inputs must not silently fall back to an old private copy.
    for key in keys:result.pop(key,None)
    result.update({key:value for key,value in runtime.items() if key in keys})
    return result


def state():return json.loads((PRIVATE/'runtime.private.json').read_text(encoding='utf-8'))
def save(data):(PRIVATE/'runtime.private.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')


def docker(*args):
    result=subprocess.run(['docker',*args],capture_output=True,text=True,timeout=40)
    if result.returncode:raise ValueError('OWNED_DOCKER_OPERATION_FAILED')
    return result.stdout.strip()


def prepare():
    data=state();v=values()
    assert not data['processes'] and not data.get('prepared'), 'Do not recertify a populated session as fresh'
    security=json.loads((PRIVATE/'independent-security-config-read.json').read_text())
    for owner in OWNERS:
        pool=json.loads((PRIVATE/(owner+'.pool-verification.json')).read_text())
        assert pool['status']=='PASS' and pool['runtimeRoleCap']>=2 and pool['maximumPoolSize']==2
        assert pool['sqlMaxConnections']-pool['sqlSuperuserReserved']>=7
        assert json.loads((PRIVATE/'migration-review-2026-11-16'/(owner+'.postmigration-verification.json')).read_text())['status']=='PASS'
        ssl=next(x for x in security if x['owner']==owner and x['endpoint']=='ssl-enforcement')
        api=next(x for x in security if x['owner']==owner and x['endpoint']=='postgrest')
        assert ssl['httpStatus']==200 and ssl['currentConfig']['database'] is True and ssl['appliedSuccessfully'] is True
        assert api['httpStatus']==200 and api['db_schema']==''
    containers=[json.loads(line) for line in docker('ps','-a','--filter','label=com.docker.compose.project='+data['composeProject'],'--format','json').splitlines()]
    rabbit=next(x['Names'] for x in containers if 'rabbitmq' in x['Names'])
    redis=next(x['Names'] for x in containers if 'redis' in x['Names'])
    for name in (rabbit,redis):
        assert docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',name)==data['session']
        assert docker('inspect','--format','{{.State.Health.Status}}',name)=='healthy'
    queues=docker('exec',rabbit,'rabbitmqctl','-q','list_queues','-p',v['SUPABASE_DEV_RABBIT_VHOST'],'name','messages')
    assert not queues.strip(), 'Fresh vhost unexpectedly contains queues'
    assert docker('exec',redis,'redis-cli','-n','0','DBSIZE')=='0'
    data['rabbitContainer']=rabbit;data['redisContainer']=redis
    data['prepared']=True;data['freshInfrastructureCheckedAtUtc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    data['securityEvidence']='authenticated SSL enforcement and empty exposed schemas; actual grants verified'
    # Readiness values are recorded only after actual database/pool/fresh-infrastructure checks.
    text=(PRIVATE/'.env').read_text(encoding='utf-8')
    for key in ('SUPABASE_DEV_DATABASES_READY','SUPABASE_DEV_INFRA_ISOLATED'):
        assert key+'=false' in text
        text=text.replace(key+'=false',key+'=true')
    (PRIVATE/'.env').write_text(text,encoding='utf-8');save(data)
    print('DATABASE_POOL_SECURITY_AND_FRESH_INFRA_READY')


def clean_parent_env():
    # Avoid accidentally inheriting other projects' datasource/provider credentials or Spring overrides.
    prefixes=('SPRING_','EUREKA_','GRPC_','SUPABASE_','DB_','USER_DB_','HOTEL_DB_',
              'BOOKING_DB_','REVENUE_DB_','REGULATIONS_DB_','RABBIT','REDIS_','VNPAY_','MAIL_')
    return {k:v for k,v in os.environ.items() if not k.startswith(prefixes)
            and not any(word in k.upper() for word in ('PASSWORD','TOKEN','SECRET'))}


def start(names):
    data=state();v=values();assert data.get('prepared')
    assert not docker('ps','--filter','label=com.docker.compose.project='+data['composeProject']+'-apps','--format','{{.ID}}'), 'Docker apps are running; stop them before starting host JVMs'
    assert v['SUPABASE_DEV_DATABASES_READY']==v['SUPABASE_DEV_INFRA_ISOLATED']=='true'
    for container in (data['rabbitContainer'],data['redisContainer']):
        assert docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',container)==data['session']
        assert docker('inspect','--format','{{.State.Health.Status}}',container)=='healthy'
    for owner in names:
        assert owner not in data['processes'], 'Stop/review the recorded process before restarting'
        provider_env=sandbox_environment(v) if owner=='booking' else storage_environment(v) if owner=='media' else {}
        env=clean_parent_env()
        env.update(SECRET_KEY=v['SECRET_KEY'],SPRING_PROFILES_ACTIVE='supabase-dev',
                   SPRING_CLOUD_CONFIG_ENABLED='false',SPRING_CONFIG_IMPORT='',
                   SPRING_BOOT_ADMIN_CLIENT_ENABLED='false',
                   EUREKA_CLIENT_SERVICEURL_DEFAULTZONE='http://127.0.0.1:8761/eureka/',
                   EUREKA_INSTANCE_IP_ADDRESS='127.0.0.1',EUREKA_INSTANCE_PREFER_IP_ADDRESS='true',
                   SPRING_DATA_REDIS_HOST='localhost',SPRING_DATA_REDIS_PORT=v['SUPABASE_DEV_REDIS_PORT'],
                   SPRING_DATA_REDIS_DATABASE='0',SPRING_DATA_REDIS_PASSWORD='',
                   SPRING_RABBITMQ_HOST='localhost',SPRING_RABBITMQ_PORT=v['SUPABASE_DEV_RABBIT_PORT'],
                   SPRING_RABBITMQ_USERNAME=v['SUPABASE_DEV_RABBIT_USERNAME'],SPRING_RABBITMQ_PASSWORD=v['SUPABASE_DEV_RABBIT_PASSWORD'],
                   SPRING_RABBITMQ_VIRTUALHOST=v['SUPABASE_DEV_RABBIT_VHOST'],
                   USER_SERVICE_URL='http://127.0.0.1:8082',HOTEL_SERVICE_URL='http://hotel-service',
                   BOOKING_GRPC_ADDRESS='127.0.0.1:9093',REFUND_INTERNAL_ENABLED=v.get('REFUND_INTERNAL_ENABLED','false'))
        if owner in OWNERS:
            for key,value in v.items():
                if key.startswith('SUPABASE_DEV_') or key.startswith(owner.upper()+'_DB_'):env[key]=value
        if owner=='revenue':env['HOTEL_SERVICE_URL']='http://127.0.0.1:8083'
        env.update(provider_env)
        if owner=='notification':
            env.update(SPRING_MAIL_HOST='127.0.0.1',SPRING_MAIL_PORT='56625',
                       SPRING_MAIL_USERNAME='batch6b@example.invalid',SPRING_MAIL_PASSWORD='',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH='false',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE='false',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED='false')
        port=data['ports'][owner]
        if owner=='frontend':
            env.update(NEXT_PUBLIC_API_BASE_URL='http://localhost:8080',API_INTERNAL_URL='http://127.0.0.1:8080',
                       NEXTAUTH_URL='http://localhost:3000',NEXTAUTH_SECRET=v['NEXTAUTH_SECRET'],
                       NEXT_TELEMETRY_DISABLED='1')
            cwd=ROOT.parent/'checkinn-frontend'
            # This workspace has a reproduced Turbopack route-compilation stall; use the
            # supported Webpack dev mode already used by its browser acceptance runners.
            command=['node',str(cwd/'node_modules/next/dist/bin/next'),'dev','--webpack','--hostname','127.0.0.1','--port',str(port)]
        else:
            module=MODULES[owner];jars=list((ROOT/module/'target').glob('*.jar'));assert len(jars)==1
            cwd=ROOT
            command=['java','-Xmx256m','-Duser.timezone=UTC','-jar',str(jars[0]),
                     '--spring.profiles.active=supabase-dev','--server.address=127.0.0.1','--server.port='+str(port),
                     '--logging.file.name='+str(PRIVATE/(owner+'.application.private.log')),
                     '--logging.level.org.hibernate.SQL=OFF','--logging.level.org.hibernate.orm.jdbc.bind=OFF',
                     '--logging.level.com.zaxxer.hikari=OFF',
                     '--eureka.instance.ip-address=127.0.0.1','--eureka.instance.hostname=localhost','--eureka.instance.prefer-ip-address=true',
                     '--grpc.server.address=127.0.0.1','--grpc.server.port='+str(GRPC.get(owner,9199)),
                     '--grpc.client.user-service.address=static://127.0.0.1:9097',
                     '--grpc.client.hotel-service.address=static://127.0.0.1:9092',
                     '--grpc.client.booking-service.address=static://127.0.0.1:9093',
                     '--grpc.client.media-service.address=static://127.0.0.1:9096',
                     '--regulations.service.url=http://127.0.0.1:8091']
        with (PRIVATE/(owner+'.console.private.log')).open('a',encoding='utf-8') as log:
            process=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,
                                     creationflags=subprocess.CREATE_NO_WINDOW)
        data['processes'][owner]={'pid':process.pid,'port':port,'startedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()}
        save(data);print(owner+' STARTED_PID='+str(process.pid),flush=True)


def stop(names):
    data=state()
    for owner in names:
        proc=data['processes'][owner]
        result=subprocess.run(['powershell','-NoProfile','-Command',
          f'(Get-CimInstance Win32_Process -Filter "ProcessId={proc["pid"]}").CommandLine'],capture_output=True,text=True)
        observed=result.stdout.strip()
        if observed:
            if owner=='frontend':assert str(ROOT.parent/'checkinn-frontend') in observed and 'next' in observed and str(proc['port']) in observed
            else:assert '--server.port='+str(proc['port']) in observed and str(PRIVATE) in observed
            killed=subprocess.run(['taskkill','/PID',str(proc['pid']),'/T','/F'],capture_output=True,text=True)
            assert killed.returncode==0
        del data['processes'][owner];save(data);print(owner+' OWNED_PROCESS_STOPPED')


def dependencies(starting):
    data=state();assert data.get('prepared')
    if not starting:
        assert not data['processes'], 'Stop the recorded application processes first'
        assert not docker('ps','--filter','label=com.docker.compose.project='+data['composeProject']+'-apps','--format','{{.ID}}'), 'Stop Docker apps before stopping their broker/cache'
    for container in (data['rabbitContainer'],data['redisContainer'],data['mailpit']):
        assert docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',container)==data['session']
        docker(*(['start',container] if starting else ['stop','-t','5',container]))
    print('OWNED_DEPENDENCIES_STARTED_WAIT_FOR_HEALTH' if starting else 'OWNED_DEPENDENCIES_STOPPED_DATA_RETAINED')


def check():
    data=state();results=[]
    for owner,proc in data['processes'].items():
        if owner=='notification':
            # This worker is intentionally non-web; verify its real Rabbit consumers instead.
            consumers=docker('exec',data['rabbitContainer'],'rabbitmqctl','-q','list_consumers',
                             '-p',values()['SUPABASE_DEV_RABBIT_VHOST'],'queue_name')
            results.append({'service':owner,'port':None,'httpStatus':None,
                            'healthy':'notification.otp.queue' in consumers and 'notification.payment.queue' in consumers,
                            'probe':'headless RabbitMQ consumers'})
            continue
        path='/' if owner=='frontend' else '/actuator/health'
        try:
            with urllib.request.urlopen('http://127.0.0.1:'+str(proc['port'])+path,timeout=4) as response:
                code=response.status;body=json.load(response) if owner!='frontend' else None
                healthy=code==200 and (owner=='frontend' or body.get('status')=='UP')
        except urllib.error.HTTPError as ex:code=ex.code;healthy=False
        except Exception:code=0;healthy=False
        results.append({'service':owner,'port':proc['port'],'httpStatus':code,'healthy':healthy})
    (PRIVATE/'runtime-health.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(results))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=('prepare','start','check','stop','start-deps','stop-deps'))
    parser.add_argument('services',nargs='*',choices=[*MODULES,'frontend'])
    args=parser.parse_args()
    if args.action=='prepare':prepare()
    elif args.action=='start':start(args.services)
    elif args.action=='stop':stop(args.services or list(state()['processes']))
    elif args.action in ('start-deps','stop-deps'):dependencies(args.action=='start-deps')
    else:check()


if __name__=='__main__':main()
