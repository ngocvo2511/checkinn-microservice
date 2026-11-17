"""Exact Redis fixture-key cleanup and public-evidence secret scan."""
import importlib.util,json,re,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]
s=importlib.util.spec_from_file_location('d',R/'tools/batch8c-diagnostics.py');d=importlib.util.module_from_spec(s);s.loader.exec_module(d)
def redis_cleanup():
 assert not d.h.stack.state()['processes']
 m=json.loads((d.OUT/'fixture-manifest.json').read_text());state=d.h.stack.state();container=state['redisContainer']
 assert d.h.stack.docker('inspect','--format','{{index .Config.Labels "checkinn.session"}}',container)==state['session']
 keys=['auth:session-generation:'+r['id'] for r in m['records'] if r['table']=='users']
 assert all(not key.endswith(m['protectedAdminId']) for key in keys)
 removed=[]
 for key in keys:
  n=d.h.stack.docker('exec',container,'redis-cli','-n','0','DEL',key);left=d.h.stack.docker('exec',container,'redis-cli','-n','0','EXISTS',key)
  assert left=='0';removed.append(dict(key=key,deleted=int(n),remaining=int(left)))
 d.write('redis-cleanup.json',dict(exactKeys=removed,adminExcluded=True,flushPerformed=False))
def inventory_check():
 m=json.loads((d.OUT/'fixture-manifest.json').read_text());users=[r['id'] for r in m['records'] if r['table']=='users'];hotels=[r['id'] for r in m['records'] if r['table']=='hotels'];rooms=[r['id'] for r in m['records'] if r['table']=='room_types']
 import uuid
 users=[uuid.UUID(x) for x in users];hotels=[uuid.UUID(x) for x in hotels]
 with d.connect('hotel') as conn:
  with conn.cursor() as cur:
   cur.execute('SET TRANSACTION READ ONLY')
   cur.execute('SELECT id::text FROM hotels WHERE owner_id=ANY(%s)',(users,));live_hotels={r[0] for r in cur.fetchall()}
   cur.execute('SELECT id::text FROM room_types WHERE hotel_id=ANY(%s)',(hotels,));live_rooms={r[0] for r in cur.fetchall()}
 missing=dict(hotels=sorted(live_hotels-set(str(x) for x in hotels)),rooms=sorted(live_rooms-set(rooms)))
 d.write('manifest-parent-boundary-verification.json',dict(status='PASS' if not any(missing.values()) else 'BLOCKED',unmanifestedExactIds=missing,hotelCount=len(live_hotels),roomCount=len(live_rooms),scope='Read-only exact fixture-owner/parent ID predicates; no deletion scope expansion'))
 assert not any(missing.values()),'Unmanifested exact descendants: stop cleanup; do not expand deletion scope'
 print('MANIFEST_PARENT_BOUNDARY_PASS',len(live_hotels),len(live_rooms),flush=True)

def scan():
 v=d.h.stack.values();v.update(d.h.stack.runtime_values())
 secrets={k:x for k,x in v.items() if any(word in k for word in ('PASSWORD','TOKEN','SECRET','DB_URL')) and len(x)>=10}
 for p in (R/'.local/batch8c').glob('b8c_*.json'):
  x=json.loads(p.read_text());secrets.update({p.stem+'_'+key:x[key] for key in ('password','token') if x.get(key)})
 violations=[];patterns=[]
 for p in d.OUT.rglob('*'):
  if not p.is_file():continue
  text=p.read_bytes().decode('utf-8',errors='replace')
  for key,value in secrets.items():
   if value in text:violations.append(dict(file=p.relative_to(d.OUT).as_posix(),secretKeyName=key))
  if re.search(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.',text) or re.search(r'\$2[aby]\$\d\d\$[./A-Za-z0-9]{20}',text):patterns.append(p.relative_to(d.OUT).as_posix())
 d.write('secret-handling-verification.json',dict(status='PASS' if not violations and not patterns else 'FAIL',scannedKeyCount=len(secrets),exactSecretMatches=violations,jwtOrBcryptPatternFiles=patterns))
 assert not violations and not patterns,'Public evidence contains secret match'
 print('PUBLIC_EVIDENCE_SECRET_SCAN_PASS',flush=True)
if __name__=='__main__':
 if sys.argv[1]=='redis':redis_cleanup()
 elif sys.argv[1]=='inventory':inventory_check()
 else:scan()
