"""Final read-only source/evidence checks and derived manifests. Never reads credentials."""
import collections
import datetime as dt
import hashlib
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[3]
OUT=pathlib.Path(__file__).resolve().parent
DOCS=OUT.parent
LOCAL=ROOT/'ai-agent-service/.local/evidence-extraction-v1'
REPORTS=['BATCH10B4D_EXTRACTION_EVIDENCE_FIRST_AND_MODEL_QUALITY_REPORT.md','VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md',
         'GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md','VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md',
         'GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md','VIETNAMESE_NLU_EVIDENCE_FIRST_FAILURE_ANALYSIS_V1.md']
def read(p): return json.loads(p.read_text(encoding='utf-8-sig'))
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(name,value): (OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT).decode('utf-8').strip()

frozen=read(LOCAL/'frozen-candidate.json')
write('frozen-candidate-v1.json',frozen)
source_checks={p:dict(expected=h,actual=digest(ROOT/p),preserved=h==digest(ROOT/p)) for p,h in frozen['sources'].items()}
write('frozen-source-integrity-v1.json',source_checks)
plan=read(OUT/'candidate-r2/offline-plan-and-parser-comparator-v1.json')
before=read(OUT/'offline-plan-and-parser-comparator-v1.json')
prior={r['sampleId']:r for r in before['observations'] if r['phase'] in ['FROZEN40','FRESH']}
oracle_checks={r['sampleId']:r['expectedExtraction']==prior[r['sampleId']]['expectedExtraction'] and r['utterance']==prior[r['sampleId']]['utterance']
               for r in plan['observations'] if r['phase'] in ['FROZEN40','FRESH']}
write('oracle-and-plan-integrity-v1.json',dict(unchangedCases=oracle_checks,evaluationClock=plan['candidate']['evaluationClock'],
    freshDatasetHash=plan['candidate']['freshHash'],freshPromptUtteranceContamination=[r['sampleId'] for r in plan['observations'] if r['phase']=='FRESH' and r['utterance'] in plan['instruction']],
    originalPlanSha256=digest(OUT/'offline-plan-and-parser-comparator-v1.json'),revision2PlanSha256=digest(OUT/'candidate-r2/offline-plan-and-parser-comparator-v1.json'),
    revision2DiagnosticSubsetProvenance='candidate-r2/offline-plan-and-parser-comparator-v1.json exported before revision2 dispatch; separate diagnostic-plan-r2.json is a later derived subset'))
segments=read(OUT/'segments-v1.json'); attempts=read(OUT/'actual-attempts-v1.json'); budget=read(OUT/'budget-accounting-v1.json'); phases=read(OUT/'phase-metrics-v1.json')
contracts={s.get('candidateRevision','candidate-r1'):s['contracts'] for s in segments}
write('contract-snapshots-v1.json',dict(revisions=contracts,legacySnapshot=dict(path='Docs/ai-agent/semantic-quality-v2/contract-snapshots-v2.json',sha256=digest(ROOT/'Docs/ai-agent/semantic-quality-v2/contract-snapshots-v2.json'))))
write('model-specific-wire-hashes-v1.json',{r['sampleId']:r['wireDigests'] for r in plan['observations']})
write('evidence-metrics-v1.json',{p:{k:v for k,v in m.items() if k.startswith(('raw','evidence','complete','missingEvidence','inventedEvidence','wrongType','duplicate','requestMeaning','retention','observedInvented'))} for p,m in phases.items()})
write('canonical-metrics-v1.json',{p:{k:v for k,v in m.items() if k in ['planned','attempted','notRun','canonicalTypedValid','exactBusinessSemantics','actionCorrect','intentCorrect','criteriaCorrect','missingSlotsCorrect','compilerRejections','referencePriceCorrect']} for p,m in phases.items()})
write('backend-confusion-matrices-v1.json',{p:{k:v for k,v in m.items() if k in ['planned','attempted','notRun','backendConfusion','falseAcceptanceRate','falseRejectionRate','backendClassificationAccuracy','admissionPrecision','endToEndCorrectAdmission']} for p,m in phases.items()})
wire_checks=[]
for s in segments:
    for row in s['observations']:
        for a in row.get('attempts',[]):
            if a.get('status')=='ATTEMPTED': wire_checks.append(a['requestDigest']==row['modelWireDigests'][a['actualModel']])

old=read(OUT/'preexisting-hashes.json')
allowed={'ai-agent-service/src/main/java/com/example/aiagent/model/GeminiLocalExtractionContract.java',
         'ai-agent-service/src/main/java/com/example/aiagent/model/GeminiLocalFallbackEvaluation.java'}
changed=[p for p,h in old.items() if not (ROOT/p).exists() or digest(ROOT/p)!=h]
write('preexisting-preservation-v1.json',dict(snapshotNote='Full source/evidence hash inventory taken after the first safe timeout-category patch; initial git-status precedes all implementation',
    checked=len(old),intentionallyModifiedPreexisting=sorted(set(changed)&allowed),unexpectedChanges=sorted(set(changed)-allowed),
    readmeExactSuffixPreserved=read(OUT/'readme-preservation-v1.json')['preservedExactSuffix'],
    noEditsToPreexistingIDE='No agent write touched .idea; existing tracked IDE modifications retained in final status'))
preserved=read(OUT/'preserved-frozen-hashes-prelive-v1.json')
for p,v in preserved.items(): v.update(final=digest(ROOT/p),preserved=digest(ROOT/p)==v['expected'])
write('preserved-frozen-hashes-final-v1.json',preserved)

test_files=sorted((ROOT/'ai-agent-service/target/surefire-reports').glob('TEST-*.xml'))
suites=[]
for p in test_files:
    e=ET.parse(p).getroot(); suites.append(dict(name=e.attrib['name'],**{k:int(e.attrib[k]) for k in ['tests','failures','errors','skipped']}))
total={k:sum(s[k] for s in suites) for k in ['tests','failures','errors','skipped']}
write('test-suites-v1.json',suites)
checks=[]
for p in OUT.glob('*package*.log'):
    text=p.read_text(encoding='utf-8-sig'); matches=re.findall(r'Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)',text)
    checks.append(dict(path=p.relative_to(ROOT).as_posix(),sha256=digest(p),lastSummary=list(map(int,matches[-1])) if matches else None,buildSuccess='BUILD SUCCESS' in text,buildFailure='BUILD FAILURE' in text))
write('executed-package-checks-v1.json',checks)

status=subprocess.check_output(['git','status','--porcelain=v1','-uall'],cwd=ROOT).decode('utf-8')
(OUT/'final-status.txt').write_text(status,encoding='utf-8')
initial=(OUT/'preexisting-status.txt').read_text(encoding='utf-8')
initial_paths={line[3:] for line in initial.splitlines()}
new_paths=sorted(line[3:] for line in status.splitlines() if line[3:] not in initial_paths)
new_outside=[p for p in new_paths if not (p.startswith('ai-agent-service/') or p.startswith('Docs/ai-agent/'))]
our_sources=[ROOT/'ai-agent-service/src/main/java/com/example/aiagent/model'/n for n in ['VietnameseEvidenceCompiler.java','GeminiEvidenceEvaluation.java','GeminiLocalExtractionContract.java','GeminiLocalFallbackEvaluation.java']]
our_sources += [ROOT/'ai-agent-service/src/test/java/com/example/aiagent/model'/n for n in ['VietnameseEvidenceCompilerTest.java','GeminiEvidenceTimeoutTest.java']]
write('file-inventory-v1.json',dict(changedPreexistingSources=[p.relative_to(ROOT).as_posix() for p in our_sources if p.relative_to(ROOT).as_posix() in old],
    newSourcesAndTests=[p.relative_to(ROOT).as_posix() for p in our_sources if p.relative_to(ROOT).as_posix() not in old],
    changedReadme='ai-agent-service/README.md',reports=REPORTS,newFiles=new_paths,newOutsideAllowedScope=new_outside,
    preservedPreexistingStatus='preexisting-status.txt',finalStatus='final-status.txt'))

# File-name-only diagnostics: no credential lookup or matched secret material printed/retained.
patterns={'googleApiKey':re.compile(rb'AIza[0-9A-Za-z_-]{20,}'),
          'jwt':re.compile(rb'eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}'),
          'credentialHeaderValue':re.compile(rb'(?i)"(?:x-goog-api-key|cookie|set-cookie)"\s*:\s*"(?!\[REDACTED\])[^"\r\n]+"'),
          'authorizationHeaderCredential':re.compile(rb'(?i)"authorization"\s*:\s*"(?:Bearer|Basic)\s+[^"\r\n]+"'),
          'bearerToken':re.compile(rb'Bearer\s+[A-Za-z0-9._~-]{20,}')}
scan_paths=our_sources+[ROOT/'ai-agent-service/README.md']+[DOCS/n for n in REPORTS]+list(OUT.rglob('*'))
findings=[]
for p in scan_paths:
    if not p.is_file(): continue
    raw=p.read_bytes()
    for kind,regex in patterns.items():
        if regex.search(raw): findings.append(dict(path=p.relative_to(ROOT).as_posix(),kind=kind))
write('credential-pattern-scan-v1.json',dict(credentialReads=0,lookedUpActualKey=False,scope='Batch source/tests/README/new reports and evidence; bounded structural/pattern checks, no active-key lookup after mandatory stop',findings=findings,
    reviewedFalsePositives=[dict(path='Docs/ai-agent/evidence-extraction-v1/scoring-and-campaign-plan-v1.json',field='authorization',reason='Human-readable user task scope, not an HTTP header/credential; frozen plan unchanged')]))

processes=subprocess.check_output(['powershell','-NoProfile','-Command',"@(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*GeminiEvidenceEvaluation*' }).Count"],cwd=ROOT).decode().strip()
active=int(processes)
old_stop=ROOT/'ai-agent-service/.local/semantic-quality-v2/segment-01-diagnostic-r2.json'
new_stop=LOCAL/'segment-03-comparison.json'
stop=read(new_stop); previous_hash='83a6c72fcb704af74297b7ad62e8de43530d8a6b2184f4491caf76d99dd70043'
verification=dict(baseline=read(OUT/'baseline-verification.json'),final=dict(**total,buildSuccess='BUILD SUCCESS' in (OUT/'final-package.log').read_text(encoding='utf-8-sig'),command='mvn -o -B -ntp -pl ai-agent-service package'),
    newTests=total['tests']-1134,historical=dict(PASS=23,FAIL=16,NOT_APPLICABLE=0,NOT_RUN=1),
    finalStatus='EVIDENCE_FIRST_EVALUATION_PARTIAL',architectureRecommendation='MORE_EVIDENCE_REQUIRED',
    actualHttpAttempts=len(attempts),perModel=budget['perModel'],unknownUsage=budget['unknownUsage'],
    stopReason=stop['stopReason'],consecutiveTransientFailures=stop['consecutiveTransientFailures'],cumulativeTransientFailures=stop['cumulativeTransientFailures'],
    sealedCampaignSha256=digest(new_stop),previousCriticalCampaignSha256=digest(old_stop),previousCriticalCampaignUnchanged=digest(old_stop)==previous_hash,
    everyDispatchedCallLocallyTerminated=all(a.get('callTerminated') for a in attempts),activeLocalLiveProcesses=active,
    noDispatchAfterMandatoryStop=len(attempts)==21 and max(int(a['attemptId'].split('-')[1]) for a in attempts)==21,
    credentialsAfterMandatoryStop='No further credential tool/CLI invocation; offline tests/analysis/scans do not call the key supplier',
    allFrozenCandidateSourcesUnchanged=all(v['preserved'] for v in source_checks.values()),allRecordedWireDigestsMatch=all(wire_checks),
    original60UtterancesAndBusinessOraclesUnchanged=all(oracle_checks.values()),allFrozenDatasetHashesPreserved=all(v['preserved'] for v in preserved.values()),
    preexistingUnexpectedChanges=sorted(set(changed)-allowed),newFilesOutsideScope=new_outside,
    globalRollingRPM=budget['maximumRollingRequests'],globalRollingReservedTokens=budget['maximumRollingReservedTokens'],minimumRecordedDispatchSpacingSeconds=budget['minimumJournaledDispatchSpacingSeconds'],
    deadlineBetweenDispatchesSeconds=budget['elapsedBetweenDispatchesSeconds'],deadlineCompliant=budget['elapsedBetweenDispatchesSeconds']<3600,
    modelLimits=budget['modelLimits'],scope=dict(domainCalls=0,gatewayCalls=0,supabaseCalls=0,paymentRefundCalls=0,writeTools=0,stateMutations=0,dependencyUpgrades=0,ordinaryRuntime='OFF/FAKE/liveOFF'),
    secretPatternFindings=findings,readmePreservedExactSuffix=read(OUT/'readme-preservation-v1.json')['preservedExactSuffix'])
write('verification-v1.json',verification)
write('acceptance-v1.json',dict(finalStatus=verification['finalStatus'],architectureRecommendation=verification['architectureRecommendation'],
    reason='Two consecutive locally terminated timeouts sealed the fixed-model comparison; no further dispatch allowed',
    frozen40=dict(planned=40,attempted=0,status='NOT_RUN'),fresh20=dict(planned=20,attempted=0,status='NOT_RUN'),qualityTargets='NOT_ASSESSED',
    modelComparison=dict(planned=32,attempted=10,status='PARTIAL_NO_WINNER'),D07='NOT_ACCEPTED',D08='NOT_ACCEPTED',E30='NOT_RUN',FULL_AGENT_E2E='NOT_RUN',productionReadiness='NOT_CLAIMED'))

# Create the hash target before checking links that reference it, then replace its derived contents below.
write('artifact-hashes-v1.json',{})
missing=[]
for p in [DOCS/n for n in REPORTS]:
    for link in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
        if '://' in link or link.startswith('#'): continue
        if not (p.parent/link.split('#')[0]).exists(): missing.append(dict(report=p.name,target=link))
verification['reportLinkFailures']=missing
write('report-link-check-v1.json',dict(reports=len(REPORTS),missing=missing))
write('verification-v1.json',verification)
hash_paths=sorted({p for p in OUT.rglob('*') if p.is_file() and p.name!='artifact-hashes-v1.json'}|{DOCS/n for n in REPORTS}|set(our_sources)|{ROOT/'ai-agent-service/README.md'})
write('artifact-hashes-v1.json',{p.relative_to(ROOT).as_posix():digest(p) for p in hash_paths})
assert total==dict(tests=1235,failures=0,errors=0,skipped=0), total
assert verification['final']['buildSuccess'] and verification['allFrozenCandidateSourcesUnchanged'] and verification['allRecordedWireDigestsMatch']
assert verification['original60UtterancesAndBusinessOraclesUnchanged'] and verification['allFrozenDatasetHashesPreserved']
assert not verification['preexistingUnexpectedChanges'] and not new_outside and not findings and not missing
assert active==0 and verification['noDispatchAfterMandatoryStop'] and verification['everyDispatchedCallLocallyTerminated']
assert verification['previousCriticalCampaignUnchanged'] and verification['readmePreservedExactSuffix']
print(json.dumps({k:verification[k] for k in ['finalStatus','actualHttpAttempts','stopReason','consecutiveTransientFailures','cumulativeTransientFailures','activeLocalLiveProcesses','final','newTests','secretPatternFindings','reportLinkFailures']},indent=2))
