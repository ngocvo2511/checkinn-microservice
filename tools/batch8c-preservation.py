"""Read-only HMAC preservation proof; never exports account rows or password hashes."""
import hashlib,hmac,importlib.util,json,secrets,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('diag',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
P=R/'.local/batch8c';P.mkdir(exist_ok=True);keypath=P/'digest-key.private'
if not keypath.exists():keypath.write_bytes(secrets.token_bytes(32))
key=keypath.read_bytes();phase=sys.argv[1];rows=[]
for owner in d.h.GROUPS:
 tables=sorted(json.loads((d.OUT/(owner+'-before.json')).read_text())['counts'])
 digests={}
 with d.connect(owner) as c:
  with c.cursor() as cur:
   cur.execute('SET TRANSACTION READ ONLY')
   for table in tables:
    cur.execute('SELECT row_to_json(t)::text FROM public.'+table+' t')
    vals=sorted(hmac.new(key,r[0].encode(),hashlib.sha256).hexdigest() for r in cur.fetchall())
    digests[table]=dict(count=len(vals),digest=hashlib.sha256(json.dumps(vals).encode()).hexdigest())
   if owner=='user':
    cur.execute("SELECT id::text,role,is_active,username FROM users WHERE username='admin'")
    admin=cur.fetchall();assert len(admin)==1 and admin[0][1:]==('ADMIN',True,'admin')
    d.write('admin-preservation-'+phase+'.json',dict(identityHmac=hmac.new(key,admin[0][0].encode(),hashlib.sha256).hexdigest(),role='ADMIN',active=True,exists=True,completeUsersTableDigest=digests['users']))
 d.write(owner+'-digests-'+phase+'.json',digests);print(owner,'preservation snapshot',phase,flush=True)
