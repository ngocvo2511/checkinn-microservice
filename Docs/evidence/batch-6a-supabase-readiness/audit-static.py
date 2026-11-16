"""Read-only workspace inspection. Never connects to databases or prints values.
Run from backend: python Docs/evidence/batch-6a-supabase-readiness/audit-static.py
Output files contain paths, hashes, versions and finding categories only.
"""
from pathlib import Path
import hashlib, json, re, subprocess, xml.etree.ElementTree as ET, zipfile, os

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
OWNERS = ['user', 'hotel', 'booking', 'revenue', 'regulations']
def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name, obj): (OUT/name).write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
repos = [ROOT, ROOT.parent/'checkinn-frontend']
if not (OUT/'initial-state.json').exists():
    save('initial-state.json', {p.name: {'branch':git(p,'branch','--show-current'), 'head':git(p,'rev-parse','HEAD'), 'status':git(p,'status','--short'), 'index':git(p,'diff','--cached','--name-status')} for p in repos})
protected = {}
for root in repos:
    for rel in git(root,'ls-files').splitlines():
        p = root/rel
        if p.is_file(): protected[str(p.relative_to(ROOT.parent)).replace('\\','/')] = sha(p)
if not (OUT/'protected-before.json').exists(): save('protected-before.json', protected)
required = ['BATCH5B_CANONICAL_FLYWAY_IMPLEMENTATION_2026-11-14.md','BATCH5A_UUID_CONTRACT_IMPLEMENTATION_2026-11-13.md','BATCH4_1_UUID_AND_SUPABASE_ARCHITECTURE_REVISION_2026-11-13.md','database-design/SUPABASE_DATABASE_ARCHITECTURE.md','database-design/CANONICAL_DATABASE_SCHEMA_V1.md','database-design/MIGRATION_IMPLEMENTATION_PLAN.md','database-design/ID_STANDARDIZATION_CONTRACT.md','evidence/batch-5b-canonical-flyway/REPRODUCE.md','evidence/batch-5b-canonical-flyway/migration-inventory.md','evidence/batch-5b-canonical-flyway/security-role-results.md','evidence/batch-5b-canonical-flyway/remaining-risks.md','standardization/PROJECT_STANDARDIZATION_PLAN.md','standardization/PROJECT_STANDARDIZATION_AUDIT_2026-11-12.md']
docs=[]
for rel in required:
    p=ROOT/'Docs'/rel; text=p.read_text(encoding='utf-8-sig')
    docs.append({'path':'Docs/'+rel,'sha256':sha(p),'lines':len(text.splitlines()),'bytes':p.stat().st_size,'headings':re.findall(r'^#{1,6} .+$',text,re.M)})
if not (OUT/'required-inputs.json').exists(): save('required-inputs.json',docs)
ns={'m':'http://maven.apache.org/POM/4.0.0'}
inventory=[]
for o in OWNERS:
    base=ROOT/(o+'-service'); p=base/'src/main/resources/db/canonical/V1__canonical_schema.sql'; s=p.read_text()
    pom=ET.parse(base/'pom.xml').getroot()
    deps=[]
    for d in pom.findall('m:dependencies/m:dependency',ns):
        a=d.findtext('m:artifactId',namespaces=ns)
        if a in ['flyway-core','flyway-database-postgresql','postgresql','micrometer-registry-prometheus']:
            deps.append({'artifact':a,'version':d.findtext('m:version',default='managed',namespaces=ns),'scope':d.findtext('m:scope',default='compile',namespaces=ns)})
    jarinfo=[]
    for jar in (base/'target').glob('*.jar'):
        if zipfile.is_zipfile(jar):
            with zipfile.ZipFile(jar) as z:
                libs=[x.rsplit('/',1)[-1] for x in z.namelist() if x.startswith('BOOT-INF/lib/') and re.search(r'/(?:postgresql|hibernate-core|HikariCP|flyway-[^/]+|spring-boot)-[0-9]',x)]
            if libs: jarinfo.append({'path':str(jar.relative_to(ROOT)),'sha256':sha(jar),'note':'existing artifact, not rebuilt in 6A','libraries':libs})
    inventory.append({'owner':o,'path':str(p.relative_to(ROOT)).replace('\\','/'),'sha256':sha(p),'tables':re.findall(r'CREATE TABLE\s+(?:public\.)?(\w+)',s,re.I),'references':re.findall(r'REFERENCES\s+([^\s(]+)',s,re.I),'dangerous_sql':re.findall(r'\b(?:DROP|TRUNCATE|CREATE EXTENSION|CREATE FUNCTION|CREATE POLICY|ENABLE ROW LEVEL SECURITY)\b',re.sub(r'--[^\n]*','',s),re.I),'spring_boot':pom.findtext('m:parent/m:version',namespaces=ns),'dependencies':deps,'existing_jars':jarinfo})
save('source-inventory.json',inventory)
findings=[]; signals=[]
for root in repos:
    tracked=set(git(root,'ls-files').splitlines())
    # Enumerate project files only; exclude generated/private evidence and dependencies.
    paths=[]
    for directory, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ['.git','node_modules','target','.next','.local','logs','.idea','evidence']]
        paths.extend(Path(directory)/f for f in files)
    for p in paths:
        if not p.is_file() or any(x in p.parts for x in ['.git','node_modules','target','.next','.local','logs','.idea']): continue
        rel=p.relative_to(root).as_posix()
        if p.suffix.lower() not in ['.yml','.yaml','.properties','.xml','.json','.java','.ts','.tsx','.py','.ps1','.sh','.example','.test'] and not p.name.startswith('.env'):continue
        if p.stat().st_size>2_000_000:continue
        try:s=p.read_text(encoding='utf-8-sig')
        except UnicodeError:continue
        for n,line in enumerate(s.splitlines(),1):
            cats=[]
            if re.search(r'(?i)(?:password|secret|service.role.key|api.key|access.key)\s*[=:]',line):
                # Only classification, never values; candidates need human context/rotation assessment.
                cats.append('secret-setting-placeholder' if '${' in line or 'process.env' in line or 'System.getenv' in line else 'literal-secret-candidate')
            if re.search(r'jdbc:postgresql://',line): cats.append('jdbc-url-location')
            if re.search(r'https?://[^\s]+\.supabase\.(?:co|com)',line): cats.append('supabase-endpoint-location')
            for cat in cats:findings.append({'repo':root.name,'path':rel,'line':n,'category':cat,'tracked':rel in tracked})
            for label,pat in [('scheduler',r'@Scheduled'),('consumer',r'@RabbitListener'),('advisory-lock',r'pg_advisory'),('cross-db-feature',r'\b(?:dblink|postgres_fdw|PREPARE TRANSACTION|JtaTransaction|ChainedTransaction)\b'),('log-sensitive-candidate',r'log\.(?:info|debug|warn|error).*?(?:password|token|signature|SecureHash|email|phone|otp|params|payload|request\))')]:
                if re.search(pat,line,re.I):signals.append({'repo':root.name,'path':rel,'line':n,'category':label})
save('secret-locations.json',findings);save('source-signals.json',signals)
print(json.dumps({'owners':[{k:v for k,v in x.items() if k in ['owner','sha256','spring_boot','dependencies','existing_jars','dangerous_sql']} for x in inventory], 'required_docs_full_file_read':len(docs),'secret_candidates':sum(x['category']=='literal-secret-candidate' for x in findings),'signals':len(signals)},indent=2))
