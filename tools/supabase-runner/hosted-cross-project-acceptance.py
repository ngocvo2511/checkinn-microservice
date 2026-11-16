"""Five bounded negative logins with a foreign runtime role; no SQL is executed."""
import json
import os
import re
import subprocess
import acceptance_runtime as h

owners=list(h.GROUPS);v=h.stack.values();results=[]
for i,owner in enumerate(owners):
    other=owners[(i+1)%len(owners)];env=h.stack.clean_parent_env()
    for suffix in ('_MIGRATION_DB_URL','_MIGRATION_DB_USERNAME','_MIGRATION_DB_PASSWORD'):
        env[other.upper()+suffix]=v[other.upper()+suffix]
    env.update(PROBE_FOREIGN_RUNTIME_PASSWORD=v[owner.upper()+'_DB_PASSWORD'],
               BATCH6B_MANAGEMENT_TOKEN=v[h.GROUPS[other]],BATCH6B_TARGET_MANIFEST=str(h.PRIVATE/'targets.properties'),BATCH6B_BACKEND_ROOT=str(h.ROOT))
    cp=os.pathsep.join([str(h.PRIVATE/'approved-classes'),str(h.ROOT/'tools/supabase-runner/target/supabase-runner-1.3.0.jar'),str(h.ROOT/'tools/supabase-runner/target/dependency-10.22.0/*')])
    result=subprocess.run(['java','-cp',cp,'com.checkinn.db.CrossProjectLoginProbe',owner,other],env=env,capture_output=True,text=True,timeout=30)
    safe=[line for line in (result.stdout+result.stderr).splitlines() if re.fullmatch(r'[a-z]+ TO [a-z]+ CROSS_PROJECT_RUNTIME_LOGIN_REJECTED|BLOCKED [A-Za-z0-9_]+',line)]
    for line in safe:print(line,flush=True)
    results.append({'sourceOwner':owner,'targetOwner':other,'status':'PASS' if result.returncode==0 else 'FAIL','safeOutput':safe})
    if result.returncode:break
(h.ROOT/'Docs/evidence/batch-6b-supabase-local-integration/cross-project-login-negative-results.json').write_text(json.dumps({'tests':results,'foreignConnectionStatementExecuted':False,'targetRoleAbsenceVerifiedWithAuthenticatedReadOnlyQuery':True},indent=2)+'\n',encoding='utf-8')
assert len(results)==5 and all(x['status']=='PASS' for x in results)
