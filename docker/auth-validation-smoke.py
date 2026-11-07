"""Actual Auth ValidateToken/logout/generation contract on verified test-network fixtures."""
import hashlib
import hmac
import importlib.util
import json
import re
import subprocess
import time
import uuid
from pathlib import Path

root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('h',root/'booking-security-smoke.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def grpc_validate(config,value,port=None):
    if port is None:
        port=config['services']['auth-service']['environment'].get('GRPC_SERVER_PORT')
        if port is None:
            source=(root.parent/'auth-service/src/main/resources/application.yml').read_text(encoding='utf-8')
            configured=re.search(r'grpc:\s+server:\s+port:\s*(\d+)',source)
            if not configured:raise RuntimeError('NEEDS_ENVIRONMENT: auth gRPC port is not resolved')
            port=configured.group(1)
    port=str(port);assert port.isdigit()
    command=['docker','run','--rm','-i','--network',config['networks']['checkinn']['name'],
        '--mount','type=bind,source='+str(root.parent/'auth-service/src/main/proto')+',target=/protos,readonly',
        'fullstorydev/grpcurl:v1.9.3','-plaintext','-max-time','15','-import-path','/protos','-proto','auth.proto',
        '-d','@','auth-service:'+port,'AuthService/ValidateToken']
    reply=subprocess.run(command,input=json.dumps({'token':value}),capture_output=True,text=True,encoding='utf-8',timeout=40)
    match=re.search(r'Code:\s*(\w+)',reply.stderr)
    return 'OK' if reply.returncode==0 else match.group(1) if match else 'TRANSPORT_ERROR'


def main():
    h.verify_running_clone('user-service')
    config=json.loads(h.run(h.COMPOSE+['config','--format','json']))
    auth=h.run(h.COMPOSE+['ps','-q','auth-service'])
    assert h.run(['docker','inspect','--format','{{index .Config.Labels "com.docker.compose.project"}}',auth])=='checkinn-test'
    environment=config['services']['auth-service']['environment']
    port=environment.get('GRPC_SERVER_PORT')
    if port is None:
        source=(root.parent/'auth-service/src/main/resources/application.yml').read_text(encoding='utf-8')
        configured=re.search(r'grpc:\s+server:\s+port:\s*(\d+)',source)
        if not configured:raise RuntimeError('NEEDS_ENVIRONMENT: auth gRPC port is not resolved')
        port=configured.group(1)
    port=str(port);assert port.isdigit()
    secret=environment['SECRET_KEY'];user=str(uuid.uuid4());key='auth:session-generation:'+user;revoked=[];count=failures=0

    def check(ok,name):
        nonlocal count,failures
        count+=1;failures+=not ok
        print(json.dumps({'case':name,'result':'PASS' if ok else 'FAIL'}),flush=True)

    def redis(*arguments):
        return h.run(h.COMPOSE+['exec','-T','redis','redis-cli','--raw']+list(arguments))

    def token(generation=None,role='CUSTOMER',expiry=True):
        payload={'sub':user,'role':role,'iat':int(time.time()),'jti':str(uuid.uuid4())}
        if expiry:payload['exp']=int(time.time())+300
        if generation is not None:payload['sessionGeneration']=generation
        raw=h.encode(b'{"alg":"HS256","typ":"JWT"}')+b'.'+h.encode(json.dumps(payload).encode())
        return (raw+b'.'+h.encode(hmac.new(secret.encode(),raw,hashlib.sha256).digest())).decode()

    def validate(value):
        return grpc_validate(config,value,port)

    assert redis('EXISTS',key)=='0'
    try:
        legacy=token();check(validate(legacy)=='OK','legacy signed session validates before any rotation')
        revocation='auth:revoked-token:'+hashlib.sha256(legacy.encode()).hexdigest();assert redis('EXISTS',revocation)=='0';revoked.append(revocation)
        settings='request = "POST"\nheader = '+json.dumps('Authorization: Bearer '+legacy)+'\n'
        status=h.run(h.COMPOSE+['exec','-T','auth-service','curl','-sS','--max-time','15','--config','-','-o','/dev/null','-w','%{http_code}','http://localhost:8081/logout'],settings)
        check(status=='200' and redis('EXISTS',revocation)=='1','supported logout writes scoped token revocation')
        check(validate(legacy)=='Unauthenticated','gRPC validation rejects actual HTTP-logged-out token')
        generation=str(uuid.uuid4());assert redis('SET',key,generation)=='OK'
        check(validate(token())=='Unauthenticated','rotation rejects missing legacy generation in gRPC')
        check(validate(token(str(uuid.uuid4())))=='Unauthenticated','previous generation cannot be revalidated')
        check(validate(token(generation))=='OK','current generation remains valid')
        check(validate(token(generation,role='UNKNOWN_ROLE'))=='Unauthenticated','signed unknown role cannot become a validated user')
        check(validate(token(generation,expiry=False))=='Unauthenticated','signed token without expiry is rejected')
        check(validate('not-a-token')=='Unauthenticated','malformed token has typed unauthenticated status')
        redis('SET',key,'not-a-generation')
        check(validate(token())=='Unavailable','invalid stored generation fails closed as unavailable')
    finally:
        redis('DEL',key,*revoked)
        check(redis('EXISTS',key,*revoked)=='0','only generated validation fixture keys cleaned up')
    print(json.dumps({'cases':count,'failures':failures}),flush=True)
    return int(bool(failures))


if __name__=='__main__':raise SystemExit(main())
