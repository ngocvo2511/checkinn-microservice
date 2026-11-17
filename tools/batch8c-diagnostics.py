"""Batch8C bounded read-only connection diagnostics. Never hosted mutations."""
import datetime,hashlib,importlib.util,json,subprocess,sys,uuid
from pathlib import Path
from urllib.parse import urlparse,parse_qs
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'Docs/evidence/batch-8c-runtime-stability';OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(ROOT/'tools/supabase-runner'))
import acceptance_runtime as h
import psycopg

def write(n,v): (OUT/n).write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def git(path):
 def run(*args):return subprocess.run(['git',*args],cwd=path,capture_output=True,text=True,encoding='utf-8',errors='replace',check=True).stdout
 return dict(branch=run('branch','--show-current').strip(),head=run('rev-parse','HEAD').strip(),status=run('status','--porcelain=v1'),stagedSha256=hashlib.sha256(run('diff','--cached','--binary').encode()).hexdigest(),unstagedSha256=hashlib.sha256(run('diff','--binary').encode()).hexdigest())
def connect(owner,runtime=False):
 v=h.stack.values();prefix=owner.upper()+('_DB_' if runtime else '_MIGRATION_DB_');p=urlparse(v[prefix+'URL'].removeprefix('jdbc:'));q=parse_qs(p.query);assert q['sslmode']==['verify-full'] and p.port==5432
 kw=dict(host=p.hostname,port=p.port,dbname=p.path.lstrip('/'),user=v[prefix+'USERNAME'],password=v[prefix+'PASSWORD'],sslmode='verify-full',connect_timeout=10,application_name='batch8c-readonly')
 if 'sslrootcert' in q:kw['sslrootcert']=q['sslrootcert'][0]
 return psycopg.connect(**kw)
def diagnostic(owner,runtime=False):
 try:
  with connect(owner,runtime) as c:
   with c.cursor() as cur:
    cur.execute('SET TRANSACTION READ ONLY');cur.execute('SET LOCAL statement_timeout=10000')
    cur.execute("SELECT current_database(),current_setting('server_version'),current_setting('max_connections'),current_setting('superuser_reserved_connections'),(SELECT rolconnlimit FROM pg_roles WHERE rolname=current_user),(SELECT rolconnlimit FROM pg_roles WHERE rolname=%s)",(h.stack.values()[owner.upper()+'_DB_RUNTIME_ROLE'],))
    db,version,maximum,reserved,cap,runtimecap=cur.fetchone()
    cur.execute("SELECT usename,application_name,state,count(*),count(backend_start),count(query_start),count(*) FILTER (WHERE state='idle in transaction') FROM pg_stat_activity GROUP BY usename,application_name,state ORDER BY usename,application_name,state")
    sessions=[dict(role=r[0],application=r[1],state=r[2],backendRows=r[3],visibleBackendStart=r[4],visibleQueryStart=r[5],idleInTransaction=r[6]) for r in cur.fetchall()]
    return dict(status='PASS',database=db,version=version,maxConnections=int(maximum),reserved=int(reserved),loginRoleCap=cap,runtimeRoleCap=runtimecap,clientTls=c.pgconn.ssl_in_use,backendActivity=sessions,visibility='pg_stat_activity describes server backends; Supavisor application label does not establish client/JVM ownership')
 except Exception as e:
  # Export only classified errors, never driver strings/endpoints.
  s=str(e).lower();kind='ROLE_CONNECTION_LIMIT' if 'too many connections for role' in s else 'CONNECTION_LIMIT' if 'too many' in s else 'TIMEOUT' if 'timeout' in s else type(e).__name__
  return dict(status='BLOCKED',error=kind,sqlstate=getattr(e,'sqlstate',None))
def main():
 phase=sys.argv[1] if len(sys.argv)>1 else 'before'
 if phase=='before':
  for name,path in [('backend',ROOT),('frontend',ROOT.parent/'checkinn-frontend')]:write('initial-git-'+name+'.json',git(path))
  write('fixture-manifest.json',dict(testRunId='batch8c-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8],writesAllowed=False,records=[],protectedAdmin='admin',reason='Runtime stability gate not yet passed'))
  data=h.stack.state();write('runtime-initial.json',dict(processes=data['processes'],ports=data['ports']))
 for owner in h.GROUPS:
  d=diagnostic(owner);write(owner+'-sessions-'+phase+'.json',d);print(owner,'migration',d['status'],flush=True)
  d=diagnostic(owner,True);write(owner+'-runtime-connect-'+phase+'.json',d);print(owner,'runtime',d['status'],d.get('error',''),flush=True)
if __name__=='__main__':main()


