"""Final offline integrity and executed-check record. Never reads credentials."""
import hashlib,json,re,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(name,value):(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT,text=True,encoding='utf-8')
log=(ROOT/'ai-agent-service/.local/quality-final-package-v2.log').read_text(encoding='utf-8',errors='replace')
summaries=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',log)
assert summaries and 'BUILD SUCCESS' in log
final=dict(zip(('tests','failures','errors','skipped'),map(int,summaries[-1])))
assert final['failures']==final['errors']==final['skipped']==0
final.update(command='mvn -o -B -ntp -pl ai-agent-service package',buildSuccess=True,log='ai-agent-service/.local/quality-final-package-v2.log')
development={}
for p in (ROOT/'ai-agent-service/.local').glob('quality-*.log'):
    text=p.read_text(encoding='utf-8',errors='replace');m=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',text)
    development[p.name]=dict(lastSummary=list(map(int,m[-1])) if m else None,buildSuccess='BUILD SUCCESS' in text,buildFailure='BUILD FAILURE' in text,sha256=sha(p))
previous=read(OUT/'historical-preservation-before.json')
preserved=[dict(path=p['path'],sha256=sha(ROOT/p['path']),preserved=sha(ROOT/p['path'])==p['sha256']) for p in previous]
assert all(p['preserved'] for p in preserved)
write('historical-preservation-final-v2.json',preserved)
before=read(OUT/'source-before-v1.json')
changedExisting=[p['path'].replace('\\','/') for p in before['files'] if sha(ROOT/p['path'])!=p['sha256']]
status=git('status','--porcelain=v1');(OUT/'final-status.txt').write_text(status,encoding='utf-8')
oldstatus=(OUT/'preexisting-status.txt').read_text(encoding='utf-8-sig')
capturedOldPaths={line[3:] for line in oldstatus.splitlines() if len(line)>3}
# Redirecting the initial status into this newly created directory made it list its own output.
# Keep the raw record untouched; do not classify this batch's evidence directory as prior user work.
oldpaths=capturedOldPaths-{'Docs/ai-agent/semantic-quality-v2/'};currentpaths={line[3:] for line in status.splitlines() if len(line)>3}
allstatus=git('status','--porcelain=v1','--untracked-files=all')
newfiles=[line[3:] for line in allstatus.splitlines() if line.startswith('?? ') and not any(line[3:]==old or old.endswith('/') and line[3:].startswith(old) for old in oldpaths)]
write('file-inventory-v2.json',dict(changedExistingMainFilesSincePreflight=changedExisting,otherEditedExistingFiles=['ai-agent-service/README.md','ai-agent-service/src/test/java/com/example/aiagent/model/GeminiCampaignExecutionTest.java','ai-agent-service/src/test/java/com/example/aiagent/model/GeminiEvaluationTestSupport.java'],newFiles=sorted(newfiles),newStatusEntries=sorted(currentpaths-oldpaths),preexistingStatusEntriesPreserved=sorted(oldpaths),preexistingPathsStillPresent=all((ROOT/p.rstrip('/')).exists() for p in oldpaths)))
suites={}
for p in (ROOT/'ai-agent-service/target/surefire-reports').glob('TEST-*.xml'):
    tree=ET.parse(p).getroot();suites[tree.get('name')]={k:int(tree.get(k,0)) for k in ('tests','failures','errors','skipped')}
write('test-suites-v2.json',suites)
patterns=[re.compile(rb'AIza[0-9A-Za-z_-]{25,}'),re.compile(rb'eyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{10,}'),re.compile(rb'Bearer\s+[A-Za-z0-9._~-]{20,}')]
scan=[]
for base in (ROOT/'ai-agent-service/src',OUT,ROOT/'ai-agent-service/.local/semantic-quality-v2'):
    for p in base.rglob('*'):
        if p.is_file() and p.suffix in ('.java','.json','.md','.txt','.csv','.yaml','.yml','.py'):
            data=p.read_bytes()
            if any(pattern.search(data) for pattern in patterns):scan.append(str(p.relative_to(ROOT)))
assert not scan,scan
record=dict(baseline=dict(tests=1080,failures=0,errors=0,skipped=0,buildSuccess=True,command=final['command'],evidence='Executed at start of this batch; tool transcript retained'),final=final,newTests=final['tests']-1080,developmentChecks=development,historical=dict(PASS=23,FAIL=16,NOT_APPLICABLE=0,NOT_RUN=1),historicalPrintedInFinalLog='"PASS":23,"FAIL":16,"NOT_APPLICABLE":0,"NOT_RUN":1' in log,branch=git('branch','--show-current').strip(),head=git('rev-parse','HEAD').strip(),scope=dict(domainCalls=0,supabaseCalls=0,writeTools=0,ordinaryRuntime='OFF/FAKE/liveOFF defaults unchanged; no ordinary app started',dependencies='No pom/lockfile changes introduced',frontendStatus=subprocess.check_output(['git','status','--porcelain=v1'],cwd=ROOT.parent/'checkinn-frontend',text=True).strip()),credentialPatternScan=dict(matches=scan,scannedScope='ai-agent-service/src, this evidence directory, new local campaign; no credential lookup',activeKeyComparison='separate boolean-only PowerShell scan'),historicalArtifactsPreserved=len(preserved),activeLiveProcesses='separate process-check-v2.json',notRun=dict(frozen40=40,fresh20=20,remainingDiagnostics=5,targetResolution=True,toolExecution=True,FULL_AGENT_E2E=True),terminalReason='UNKNOWN_IN_FLIGHT',status='LIVE_TECHNICAL_BLOCKED')
write('verification-v2.json',record)
plan=read(OUT/'frozen-plan-final-v3.json');early=read(OUT/'diagnostic-plan-v2.json')
freshhash=sha(OUT/'fresh-dataset-v2.json')
assert freshhash==plan['freshDatasetHash']==early['freshDatasetHash']==sha(ROOT/'ai-agent-service/src/main/resources/gemini/semantic-quality-fresh-v2.json')
write('fresh-freeze-integrity-v2.json',dict(sha256=freshhash,identicalBeforeFirstDispatchAndFinal=True,classification='FRESH_DEVELOPMENT_GENERALIZATION',expectedOutcomesUnchanged=True,dispatched=0))
print(json.dumps(dict(final=final,changedExistingMain=len(changedExisting),historicalPreserved=len(preserved),credentialPatternMatches=len(scan))))
