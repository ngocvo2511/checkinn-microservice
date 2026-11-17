"""Shared helpers for explicitly authorized synthetic hosted-development acceptance."""
from pathlib import Path
import base64
import hashlib
import hmac
import importlib.util
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parents[2]
PRIVATE=ROOT/'.local/batch6b'
GROUPS={'user':'USER_REGULATION_TOKEN','regulations':'USER_REGULATION_TOKEN',
        'hotel':'HOTEL_BOOKING_TOKEN','booking':'HOTEL_BOOKING_TOKEN','revenue':'REVENUE_TOKEN'}
spec=importlib.util.spec_from_file_location('batch6b_stack',ROOT/'tools/supabase-runner/run-local-stack.py')
stack=importlib.util.module_from_spec(spec);spec.loader.exec_module(stack)


def require_open_acceptance():
    if stack.state().get('acceptanceCompleted'):
        raise RuntimeError('This acceptance session is complete; preserve its fixtures. Prepare a new explicitly authorized development run before repeating writes.')


def token(role,subject):
    enc=lambda b:base64.urlsafe_b64encode(b).rstrip(b'=')
    payload=enc(b'{"alg":"HS256","typ":"JWT"}')+b'.'+enc(json.dumps({'sub':subject,'role':role,'iat':int(time.time()),'exp':int(time.time())+1800}).encode())
    return (payload+b'.'+enc(hmac.new(stack.values()['SECRET_KEY'].encode(),payload,hashlib.sha256).digest())).decode()


def api(owner,path,method='GET',body=None,role='USER',subject=None,bearer=None,anonymous=False,timeout=30):
    state=stack.state();headers={'Content-Type':'application/json'}
    if not anonymous:headers['Authorization']='Bearer '+(bearer or token(role,subject or str(uuid.UUID(int=1))))
    request=urllib.request.Request('http://127.0.0.1:'+str(state['ports'][owner])+path,
       data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=timeout) as response:raw=response.read();code=response.status
    except urllib.error.HTTPError as error:raw=error.read();code=error.code
    try:result=json.loads(raw) if raw else None
    except ValueError:result={'unparsedResponse':True}
    return code,result


def sql(owner,statement,fixture=False):
    assert owner in GROUPS
    if fixture:require_open_acceptance()
    v=stack.values();env=stack.clean_parent_env()
    for suffix in ('_MIGRATION_DB_URL','_MIGRATION_DB_USERNAME','_MIGRATION_DB_PASSWORD','_DB_URL','_DB_USERNAME','_DB_PASSWORD'):
        env[owner.upper()+suffix]=v[owner.upper()+suffix]
    env.update(BATCH6B_MANAGEMENT_TOKEN=v[GROUPS[owner]],BATCH6B_TARGET_MANIFEST=str(PRIVATE/'targets.properties'),BATCH6B_BACKEND_ROOT=str(ROOT))
    cp=os.pathsep.join([str(PRIVATE/'approved-classes'),str(ROOT/'tools/supabase-runner/target/supabase-runner-1.3.0.jar'),str(ROOT/'tools/supabase-runner/target/dependency-10.22.0/*')])
    result=subprocess.run(['java','-cp',cp,'com.checkinn.db.HostedAcceptanceSql',owner,'fixture' if fixture else 'readonly'],input=statement,env=env,capture_output=True,text=True,encoding='utf-8',timeout=60)
    if result.returncode:
        # Never surface arbitrary driver output; retain no SQL credentials in public reports.
        safe=[line for line in (result.stdout+result.stderr).splitlines() if line.startswith('BLOCKED ')]
        raise RuntimeError('HOSTED_SQL_PROBE_FAILED '+','.join(safe))
    return '\n'.join(base64.b64decode(line.split(' ',1)[1]).decode() for line in result.stdout.splitlines() if line.startswith('RESULT_BASE64 '))


def wait(predicate,label,seconds=60):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        if predicate():return
        time.sleep(1)
    raise AssertionError(label+' timed out')
