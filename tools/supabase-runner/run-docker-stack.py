"""Docker application runtime for the already approved/prepared Supabase session.

Reuses that session's broker/cache, never creates/migrates a database. Generated
Compose configuration contains credentials and stays under ignored .local.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import shutil
import time
import urllib.request
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

spec=importlib.util.spec_from_file_location('local_stack',Path(__file__).with_name('run-local-stack.py'))
local=importlib.util.module_from_spec(spec);spec.loader.exec_module(local)
ROOT=local.ROOT;PRIVATE=local.PRIVATE
COMPOSE=PRIVATE/'docker-compose.private.json'

def run(args, **kwargs):
    return subprocess.run(args,check=True,**kwargs)

def prepare(require_healthy=False):
    v=local.values();state=local.state()
    runtime=local.runtime_values()
    assert len(runtime.get('SECRET_KEY',''))>=32 and runtime.get('NEXTAUTH_SECRET'), 'Configure application secrets in docker/.env'
    for owner in local.OWNERS:
        for suffix in ('URL','USERNAME','PASSWORD','PROJECT_REF','RUNTIME_ROLE','POOL_MAX','CONNECTION_BUDGET'):
            assert runtime.get(owner.upper()+'_DB_'+suffix), 'Configure Supabase runtime values in docker/.env'
    assert runtime.get('CHECKINN_ENVIRONMENT')=='supabase', 'docker/.env must select supabase'
    assert state.get('prepared') and v['SUPABASE_DEV_DATABASES_READY']==v['SUPABASE_DEV_INFRA_ISOLATED']=='true'
    network=state['composeProject']+'_default'
    for container in (state['rabbitContainer'],state['redisContainer']):
        assert local.docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',container)==state['session']
        if require_healthy:
            assert local.docker('inspect','--format','{{.State.Health.Status}}',container)=='healthy'
    mail=state['session']+'-mailpit'
    assert local.docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',mail)==state['session']
    networks=json.loads(local.docker('inspect','--format','{{json .NetworkSettings.Networks}}',mail))
    if require_healthy and network not in networks:local.docker('network','connect','--alias','mailpit',network,mail)
    services={}
    for owner,module in local.MODULES.items():
        port=state['ports'][owner]
        env=dict(SECRET_KEY=v['SECRET_KEY'],SPRING_PROFILES_ACTIVE='supabase-dev',
                 SUPABASE_DEV_CONTAINER='true',JAVA_TOOL_OPTIONS='-Xmx256m -Duser.timezone=UTC',
                 SPRING_CLOUD_CONFIG_ENABLED='false',SPRING_CONFIG_IMPORT='',
                 SPRING_BOOT_ADMIN_CLIENT_ENABLED='false',SERVER_PORT=str(port),SERVER_ADDRESS='0.0.0.0',
                 EUREKA_CLIENT_SERVICEURL_DEFAULTZONE='http://eureka-server:8761/eureka/',
                 EUREKA_INSTANCE_HOSTNAME=module,EUREKA_INSTANCE_PREFER_IP_ADDRESS='false',
                 SPRING_DATA_REDIS_HOST='redis',SPRING_DATA_REDIS_PORT='6379',SPRING_DATA_REDIS_DATABASE='0',
                 SPRING_DATA_REDIS_PASSWORD='',SPRING_RABBITMQ_HOST='rabbitmq',SPRING_RABBITMQ_PORT='5672',
                 SPRING_RABBITMQ_USERNAME=v['SUPABASE_DEV_RABBIT_USERNAME'],
                 SPRING_RABBITMQ_PASSWORD=v['SUPABASE_DEV_RABBIT_PASSWORD'],
                 SPRING_RABBITMQ_VIRTUALHOST=v['SUPABASE_DEV_RABBIT_VHOST'],
                 USER_SERVICE_URL='http://user-service:8082',HOTEL_SERVICE_URL='http://hotel-service',
                 REGULATIONS_SERVICE_URL='http://regulations-service:8091',
                 BOOKING_GRPC_ADDRESS='booking-service:9093',REFUND_INTERNAL_ENABLED=v.get('REFUND_INTERNAL_ENABLED','false'),
                 GRPC_SERVER_ADDRESS='0.0.0.0',GRPC_SERVER_PORT=str(local.GRPC.get(owner,9199)))
        for target in ('user','hotel','booking','media'):
            env['GRPC_CLIENT_'+target.upper()+'_SERVICE_ADDRESS']='static://'+target+'-service:'+str(local.GRPC[target])
        volumes=[]
        if owner in local.OWNERS:
            prefix=owner.upper()+'_DB_'
            # No management tokens or migration passwords enter application containers.
            for suffix in ('URL','USERNAME','PASSWORD','PROJECT_REF','RUNTIME_ROLE','POOL_MAX','CONNECTION_BUDGET'):
                env[prefix+suffix]=v[prefix+suffix]
            env['SUPABASE_DEV_DATABASES_READY']=v['SUPABASE_DEV_DATABASES_READY']
            env['SUPABASE_DEV_INFRA_ISOLATED']=v['SUPABASE_DEV_INFRA_ISOLATED']
            parts=urlsplit(env[prefix+'URL'][5:]);query=dict(parse_qsl(parts.query))
            cert=Path(query['sslrootcert']);assert cert.is_file()
            query['sslrootcert']='/run/checkinn-ca/root.crt'
            env[prefix+'URL']='jdbc:'+urlunsplit(parts._replace(query=urlencode(query)))
            volumes=[str(cert.resolve()).replace('\\','/')+':/run/checkinn-ca/root.crt:ro']
        if owner=='booking':env.update(local.sandbox_environment(v))
        if owner=='media':env.update(local.storage_environment(v))
        if owner=='revenue':env['HOTEL_SERVICE_URL']='http://hotel-service:8083'
        if owner=='notification':
            env.update(SPRING_MAIL_HOST='mailpit',SPRING_MAIL_PORT='1025',
                       SPRING_MAIL_USERNAME='batch6b@example.invalid',SPRING_MAIL_PASSWORD='',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH='false',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE='false',
                       SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED='false')
        service=dict(image='checkinn/'+module+':supabase-dev',
                     build=dict(context=str(ROOT),dockerfile='docker/Dockerfile.supabase-dev',
                                args=dict(BASE_IMAGE='checkinn/'+module+':local',SERVICE=module)),
                     init=True,restart='unless-stopped',networks=['session'],environment=env,
                     labels={'checkinn.session':state['session'],'checkinn.environment':'supabase-dev'},
                     logging=dict(driver='json-file',options={'max-size':'10m','max-file':'3'}))
        # Preserve hyphenated gRPC client map keys; env relaxed binding can lose them.
        service['command']=['--grpc.client.'+target+'-service.address=static://'+target+'-service:'+str(local.GRPC[target])
                            for target in ('user','hotel','booking','media')]
        if volumes:service['volumes']=volumes
        if owner in ('eureka','gateway'):service['ports']=['127.0.0.1:'+str(port)+':'+str(port)]
        if owner=='notification':
            service.setdefault('volumes',[]).append(str(ROOT/'docker/notification-health.sh').replace('\\','/')+':/usr/local/bin/checkinn-notification-health.sh:ro')
            test=['CMD','bash','/usr/local/bin/checkinn-notification-health.sh']
        else:test=['CMD-SHELL','curl -fsS http://localhost:'+str(port)+'/actuator/health >/dev/null || exit 1']
        service['healthcheck']=dict(test=test,interval='15s',timeout='15s',retries=16,start_period='90s')
        if owner!='eureka':service['depends_on']={'eureka-server':{'condition':'service_healthy'}}
        services[module]=service
    services['frontend']=dict(image='checkinn/frontend:supabase-dev',
        build=dict(context=str(ROOT.parent/'checkinn-frontend'),args={'NEXT_PUBLIC_API_BASE_URL':'http://localhost:8080'}),
        init=True,restart='unless-stopped',networks=['session'],ports=['127.0.0.1:3000:3000'],
        environment=dict(API_INTERNAL_URL='http://api-gateway:8080',NEXTAUTH_URL='http://localhost:3000',NEXTAUTH_SECRET=v['NEXTAUTH_SECRET']),
        depends_on={'api-gateway':{'condition':'service_healthy'}},
        healthcheck=dict(test=['CMD','node','-e',"fetch('http://127.0.0.1:3000/api/auth/providers').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"],interval='15s',timeout='5s',retries=12,start_period='60s'))
    # Compose interpolation applies to JSON too: preserve literal dollar signs in secrets.
    def escape(x):
        if isinstance(x,str):return x.replace('$','$$')
        if isinstance(x,dict):return {k:escape(value) for k,value in x.items()}
        if isinstance(x,list):return [escape(value) for value in x]
        return x
    config=dict(name=state['composeProject']+'-apps',services=services,networks={'session':dict(external=True,name=network)})
    COMPOSE.write_text(json.dumps(escape(config),indent=2)+'\n',encoding='utf-8')
    print('Prepared private Docker config: 10 JVM services + frontend; existing broker/cache/mail retained.',flush=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['prepare','build','up','status','down'])
    args=parser.parse_args()
    if args.action=='up':
        assert not local.state()['processes'],'Stop the owned host processes before Docker startup'
        project=local.state()['composeProject']+'-apps'
        for port in ('8080','3000','8761'):
            projects=local.docker('ps','--filter','publish='+port,'--format','{{.Label "com.docker.compose.project"}}').splitlines()
            assert all(other==project for other in projects), 'Stop the other application stack before starting Supabase'
        local.dependencies(True)
        deadline=time.monotonic()+90
        for container in (local.state()['rabbitContainer'],local.state()['redisContainer'],local.state()['mailpit']):
            while local.docker('inspect','--format','{{.State.Health.Status}}',container)!='healthy':
                assert time.monotonic()<deadline,'Owned infrastructure failed health checks'
                time.sleep(1)
    if args.action in ('prepare','build','up'):prepare(require_healthy=args.action=='up')
    compose=['docker','compose','-f',str(COMPOSE)]
    if args.action=='prepare':
        run(compose+['config','--quiet'])
        return
    if args.action=='build':
        for owner,module in local.MODULES.items():
            command=[shutil.which('mvn') or shutil.which('mvn.cmd') or 'mvn','-B','-ntp']
            command+=['-Dtest=SupabaseDevEnvironmentGuardTest'] if owner in local.OWNERS else ['-Dmaven.test.skip=true']
            command+=['package']
            with (PRIVATE/(owner+'.docker-build.private.log')).open('w',encoding='utf-8') as log:
                run(command,cwd=ROOT/module,stdout=log,stderr=subprocess.STDOUT)
            print(module+' packaged',flush=True)
        run(compose+['build'])
    elif args.action=='up':
        run(compose+['up','-d','--wait','--wait-timeout','420'])
        # Gateway actuator can be UP before its first Eureka registry refresh.
        deadline=time.monotonic()+120
        while True:
            try:
                with urllib.request.urlopen('http://127.0.0.1:8080/cities/all-locations',timeout=10) as response:
                    assert response.status==200 and isinstance(json.load(response),list)
                break
            except Exception:
                if time.monotonic()>=deadline:raise RuntimeError('Container health passed but Gateway discovery route is not ready') from None
                time.sleep(2)
        print('Docker health and Gateway hotel discovery route ready.',flush=True)
    elif args.action=='status':run(compose+['ps'])
    elif args.action=='down':run(compose+['down']) # External session network/volumes are preserved.

if __name__=='__main__':main()
