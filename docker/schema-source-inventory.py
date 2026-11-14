from pathlib import Path
import re,json,hashlib,subprocess
root=Path.cwd(); workspace=root.parent;out=root/'Docs/evidence/batch-3-schema'
def git(*a): return subprocess.run(['git',*a],capture_output=True,text=True,encoding='utf-8').stdout.strip()
initial={'branch':git('branch','--show-current'),'head':git('rev-parse','HEAD'),'preexisting':[' M .idea/compiler.xml',' M .idea/encodings.xml',' D Docs/BATCH2_PR_REVIEW_2026-11-13.md'],'frontend_head':subprocess.run(['git','-C',str(workspace/'checkinn-frontend'),'rev-parse','HEAD'],capture_output=True,text=True).stdout.strip(),'frontend_initial_status':'clean','staged_initial':False,'production_connections':'NONE','environment_variable_names':['DB_URL','DB_PASSWORD','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SPRING_JPA_HIBERNATE_DDL_AUTO','SPRING_FLYWAY_BASELINE_ON_MIGRATE','USER_DB_URL','HOTEL_DB_URL','BOOKING_DB_URL','REVENUE_DB_URL','REGULATIONS_DB_URL'],'values_collected':False}
(out/'initial-state.json').write_text(json.dumps(initial,indent=2)+'\n')
files=[]
for p in workspace.rglob('*'):
 if not p.is_file() or any(x in p.parts for x in ['.git','node_modules','target','.next','.local']): continue
 if p.suffix=='.sql' or (p.suffix=='.java' and ('@Entity' in p.read_text(encoding='utf-8-sig',errors='replace') or '@Embeddable' in p.read_text(encoding='utf-8-sig',errors='replace'))) or (p.name.startswith('application') and p.suffix in ['.yml','.properties']) or p.name.startswith('docker-compose') or (('migration' in p.name or p.name.startswith('schema-')) and p.suffix=='.py'):
  files.append(p)
entries=[]
for p in sorted(files):
 rel=p.relative_to(workspace).as_posix();s=p.read_text(encoding='utf-8-sig',errors='replace');low=s.lower();typ='SQL' if p.suffix=='.sql' else 'entity' if p.suffix=='.java' else 'harness' if p.suffix=='.py' else 'configuration'
 service=next((x.replace('-service','') for x in p.parts if x.endswith('-service')),None)
 if not service and typ=='SQL':
  service=next((v for k,v in [('REVENUE','revenue'),('BOOKING','booking'),('HOTEL','hotel'),('USER','user'),('REGULATION','regulations')] if re.search(r'--[^\n]*'+k,s[:800])),None)
  if not service: service=next((v for k,v in [('revenue','revenue'),('booking','booking'),('refund','booking'),('loyalty','booking'),('reservation','booking'),('hold','hotel'),('room','hotel'),('hotel','hotel'),('review','hotel'),('amenity','hotel'),('media','hotel'),('policy','hotel'),('regulation','regulations')] if k in p.name), 'UNKNOWN')
 service=service or 'multiple/none'
 objs=sorted(set(re.findall(r'(?i)(?:CREATE TABLE(?: IF NOT EXISTS)?|ALTER TABLE|UPDATE|REFERENCES)\s+([a-z_][a-z_0-9]*)',s))) if typ=='SQL' else sorted(set(re.findall(r'@Table\(name\s*=\s*"([^"]+)"',s)))
 testonly=any(x in p.name for x in ['smoke','test']) or '/logs/' in '/'+rel
 mechanism='Flyway (User only); SQL files not automatically Flyway elsewhere' if '/db/migration/' in rel else 'Hibernate entity mapping' if typ=='entity' else 'explicit/manual psql; no global authoritative ordering' if typ=='SQL' else 'configuration / harness; inspect before execution'
 dangers=[]
 if re.search(r'(?i)\b(drop|truncate|delete from)\b',s): dangers.append('destructive statements present')
 if re.search(r'(?i)\bupdate\s',s): dangers.append('DML may rewrite existing rows')
 if 'total_rooms = 10' in s: dangers.append('ACTIVE NULL-to-10 stock backfill; DO NOT EXECUTE')
 if 'ADD CONSTRAINT IF NOT EXISTS' in s: dangers.append('unsupported PostgreSQL syntax')
 if not dangers: dangers=['baseline prerequisites / schema equivalence require verification']
 req=sorted(set(re.findall(r'(?i)(?:ALTER TABLE|UPDATE|REFERENCES)\s+([a-z_][a-z_0-9]*)',s)))
 entries.append({'path':rel,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'service':service,'database':('checkinn_'+service if service not in ['UNKNOWN','multiple/none','auth','media'] else 'UNKNOWN / see ownership review'),'objects':objs,'type':typ,'mechanism':mechanism,'ordering':p.name.split('__')[0] if '__' in p.name else 'explicit prerequisites; no source-wide order','required_predecessor_objects':req,'replay':'NOT VERIFIED; IF NOT EXISTS is not proof of whole-script replay','change_kind':('DDL+DML' if re.search(r'(?i)\b(insert into|update|delete from)\b',s) else 'DDL') if typ=='SQL' else 'mapping/configuration','data_assumptions': 'target relation/column/type and existing rows must satisfy constraints; see SQL','potential_conflicts':dangers,'status':['SOURCE_PRESENT']+(['TEST_ONLY'] if testonly else []),'applied_evidence':'see applied-history-manifest; file existence is not application evidence','production_applied_history':'UNKNOWN'})
(out/'schema-sources.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
lines=['# Schema source manifest — Batch 3','', 'Full per-file SHA-256, prerequisites, assumptions, replay status, conflicts and history fields: [schema-sources.json](schema-sources.json). Inventory includes reachable workspace SQL snapshots outside the repository; these remain in place. Place is metadata-only, never executed.','', '| Service | Database | Source path | Schema object | Migration type | Execution mechanism | Ordering | Current status | Risk |','|---|---|---|---|---|---|---|---|---|']
for e in entries:
 lines.append('| '+' | '.join(str(x).replace('|','/').replace('\n',' ') for x in [e['service'],e['database'],e['path'],', '.join(e['objects']) or 'see source',e['type']+' '+e['change_kind'],e['mechanism'],e['ordering'],', '.join(e['status']),'; '.join(e['potential_conflicts'])])+' |')
(out/'schema-source-manifest.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print(len(entries),'sources inventoried')
