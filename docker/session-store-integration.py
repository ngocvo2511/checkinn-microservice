"""Opt-in host Spring/JPA/Redis tests via a temporary loopback transport to checkinn-test."""
import importlib.util,json,os,re,secrets,socket,subprocess,time,uuid
from pathlib import Path
root=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(s);s.loader.exec_module(h)

def main():
    h.verify_running_clone('user-service')
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    run=str(uuid.uuid4());username='sessionit_'+run.replace('-','');acl='session-test-'+uuid.uuid4().hex;password=secrets.token_urlsafe(32)
    assert h.sql('checkinn_user',"SELECT count(*) FROM users WHERE id='"+run+"'")=='0'
    container=None;acl_created=False
    try:
        command=['docker','run','--rm','-d','--label','checkinn.test.transport=true','--network',config['networks']['checkinn']['name'],
            '-p','127.0.0.1::16379','-p','127.0.0.1::15432','--mount','type=bind,source='+str(root/'TestTransportForward.java')+',target=/tmp/TestTransportForward.java,readonly',
            '--entrypoint','java','maven:3.9.9-eclipse-temurin-21','/tmp/TestTransportForward.java']
        result=subprocess.run(command,capture_output=True,text=True,timeout=240)
        if result.returncode:raise RuntimeError('Test transport startup failed')
        container=result.stdout.strip();assert re.fullmatch('[a-f0-9]{64}',container)
        redis_port=h.run(['docker','port',container,'16379/tcp']).rsplit(':',1)[1]
        db_port=h.run(['docker','port',container,'15432/tcp']).rsplit(':',1)[1]
        deadline=time.monotonic()+60
        while True:
            try:
                with socket.create_connection(('127.0.0.1',int(redis_port)),timeout=2) as connection:
                    connection.sendall(b'*1\r\n$4\r\nPING\r\n');assert connection.recv(64).startswith(b'+PONG')
                break
            except (OSError,AssertionError):
                if time.monotonic()>deadline:raise RuntimeError('Test transport readiness timeout')
                time.sleep(1)
        h.run(h.COMPOSE+['exec','-T','redis','redis-cli','-x','ACL','SETUSER',acl,'on','~*','+@read','+@connection','-@write'],'>'+password);acl_created=True
        env=dict(os.environ);env.update({'CHECKINN_SESSION_TEST':'true','CHECKINN_SESSION_RUN_UUID':run,
            'CHECKINN_SESSION_REDIS_PORT':redis_port,'CHECKINN_SESSION_REDIS_USER':acl,'CHECKINN_SESSION_REDIS_PASSWORD':password,
            'CHECKINN_SESSION_DB_URL':'jdbc:postgresql://127.0.0.1:'+db_port+'/checkinn_user',
            'CHECKINN_SESSION_DB_PASSWORD':config['services']['user-service']['environment']['SPRING_DATASOURCE_PASSWORD']})
        maven=os.environ.get('MAVEN_CMD') or str(Path.home()/'Downloads/apache-maven-3.9.9-bin/apache-maven-3.9.9/bin/mvn.cmd')
        with (root.parent.parent/'logs/reset-session-store-integration.log').open('w',encoding='utf-8') as log:
            result=subprocess.run([maven,'-o','-B','-ntp','-pl','user-service','-Dtest=SessionGenerationRedisIntegrationTest,PasswordResetSessionStoreIntegrationTest','test'],cwd=root.parent,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=900)
        if result.returncode:raise RuntimeError('Session integration failed; inspect reset-session-store-integration.log')
        print('PASS: durable generation ack, denied Redis write, real password/OTP transaction rollback')
    finally:
        # All targets were generated here and verified absent before testing.
        h.sql('checkinn_user',"DELETE FROM otps WHERE email='"+username+"@example.invalid'; DELETE FROM user_profiles WHERE user_id='"+run+"'; DELETE FROM users WHERE id='"+run+"' AND username='"+username+"'")
        h.run(h.COMPOSE+['exec','-T','redis','redis-cli','DEL','auth:session-generation:'+run])
        if acl_created:h.run(h.COMPOSE+['exec','-T','redis','redis-cli','ACL','DELUSER',acl])
        if container:
            assert h.run(['docker','inspect','--format','{{index .Config.Labels "checkinn.test.transport"}}',container])=='true'
            h.run(['docker','rm','-f',container])

if __name__=='__main__':main()
