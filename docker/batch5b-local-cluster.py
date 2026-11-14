"""Create/start/stop only the Batch5B-owned loopback PostgreSQL cluster. No existing cluster access."""
import argparse,json,os,secrets,socket,subprocess,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=ROOT/'.local/evidence/batch-5b-canonical-flyway'
DATA=PRIVATE/'postgres-data'
BIN=Path('C:/Program Files/PostgreSQL/18/bin')
PORT=55486
OWNERS=('user','hotel','booking','revenue','regulations')
def run(name,*args,env=None):
 with tempfile.TemporaryFile(mode='w+',encoding='utf-8') as out, tempfile.TemporaryFile(mode='w+',encoding='utf-8') as err:
  result=subprocess.run([str(BIN/(name+'.exe')),*map(str,args)],stdout=out,stderr=err,text=True,env=env)
  out.seek(0);err.seek(0)
  if result.returncode:raise RuntimeError(name+' failed: '+err.read())
  return out.read()

def main():
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['start','stop','provision','restart']);a=parser.parse_args()
 PRIVATE.mkdir(parents=True,exist_ok=True)
 marker=PRIVATE/'cluster-owner.json'
 if a.action=='restart':
  config=json.loads(marker.read_text());assert Path(config['data']).resolve()==DATA.resolve() and config['port']==PORT and config['owner']=='Batch5B'
  with socket.socket() as sock:sock.bind(('127.0.0.1',PORT))
  print(run('pg_ctl','-D',DATA,'-l',PRIVATE/'postgres-server.log','-w','start'));return
 if a.action=='stop':
  config=json.loads(marker.read_text());assert Path(config['data']).resolve()==DATA.resolve() and config['port']==PORT
  print(run('pg_ctl','-D',DATA,'-m','fast','-w','stop'));return
 if a.action=='provision':
  config=json.loads(marker.read_text());assert config['databases']==[] and Path(config['data']).resolve()==DATA.resolve() and config['port']==PORT
  secret=(PRIVATE/'postgres-password.private').read_text()
 else:
  if marker.exists():raise RuntimeError('Cluster already provisioned; inspect preserved evidence before explicit restart. Never reset.')
  with socket.socket() as s:s.bind(('127.0.0.1',PORT))
  assert not DATA.exists(), 'Refuse existing data directory'
  secret=secrets.token_urlsafe(32);pw=PRIVATE/'postgres-password.private';pw.write_text(secret)
  run('initdb','-D',DATA,'-U','batch5b_admin','--pwfile',pw,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C')
  with (DATA/'postgresql.conf').open('a') as f:f.write("\nlisten_addresses='127.0.0.1'\nport=55486\ntimezone='UTC'\n")
  config={'data':str(DATA.resolve()),'port':PORT,'owner':'Batch5B','databases':[]}
  marker.write_text(json.dumps(config,indent=2))
  print(run('pg_ctl','-D',DATA,'-l',PRIVATE/'postgres-server.log','-w','start'))
 env=os.environ.copy();env['PGPASSWORD']=secret
 def sql(database,statement):return run('psql','-X','-h','127.0.0.1','-p',PORT,'-U','batch5b_admin','-d',database,'-v','ON_ERROR_STOP=1','-c',statement,env=env)
 sql('postgres','REVOKE ALL ON DATABASE postgres FROM PUBLIC; REVOKE ALL ON DATABASE template1 FROM PUBLIC;')
 settings={}
 for owner in OWNERS:
  db='batch5b_'+owner;migrator=db+'_migrator';app=db+'_app';appsecret=secrets.token_urlsafe(32)
  sql('postgres',f"CREATE ROLE {migrator} LOGIN PASSWORD '{secret}'; CREATE ROLE {app} LOGIN PASSWORD '{appsecret}';")
  sql('postgres',f'GRANT pg_read_all_settings TO {migrator};')
  sql('postgres',f'CREATE DATABASE {db} OWNER {migrator};')
  sql('postgres',f'REVOKE ALL ON DATABASE {db} FROM PUBLIC; GRANT CONNECT ON DATABASE {db} TO {app};')
  sql(db,f'REVOKE ALL ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO {app};')
  settings[owner]={'url':f'jdbc:postgresql://127.0.0.1:{PORT}/{db}?currentSchema=public','migrator':migrator,'migrationPassword':secret,'app':app,'appPassword':appsecret,'data':str(DATA.resolve())}
  config['databases'].append(db)
 (PRIVATE/'connections.private.json').write_text(json.dumps(settings,indent=2))
 marker.write_text(json.dumps(config,indent=2))
 print('Provisioned five fresh databases; private connection material is excluded from evidence.')
if __name__=='__main__':main()
