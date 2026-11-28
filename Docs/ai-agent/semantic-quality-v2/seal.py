"""Seal batch-specific evidence indexes. Never changes provider journals or historical files."""
import hashlib,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];OUT=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write(name,obj):(OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
plan=read(OUT/'frozen-plan-final-v3.json')
original=read(ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/contract-snapshots-v1.json')
write('contract-snapshots-v2.json',dict(frozenComparators=original,diagnosticRevision1=read(OUT/'diagnostic-plan-v2.json')['contracts'],diagnosticRevision2=read(OUT/'diagnostic-r2-plan-v2.json')['contracts'],finalCandidate=plan['contracts'],finalLiveEvaluation='NOT_RUN'))
write('model-specific-wire-hashes-v2.json',[dict(sampleId=r['sampleId'],phase=r['phase'],wireDigests=r['modelWireDigests']) for r in plan['observations']])
write('frozen-evaluation-plan-v2.json',dict(status='NOT_RUN_AFTER_UNKNOWN_IN_FLIGHT',candidate='vietnamese-semantic-v2',sdk='LangChain4j 1.17.0',datasetHash=plan['datasetHash'],oracleHash=plan['oracleHash'],freshDatasetHash=plan['freshDatasetHash'],evaluationClock='2026-11-24T10:18:48.242851600Z',plannedOrder=[dict(sampleId=r['sampleId'],phase=r['phase']) for r in plan['observations']],contractSnapshots='contract-snapshots-v2.json',sourceSnapshot='source-frozen-final-v3.json',sourceSnapshotHash=sha(OUT/'source-frozen-final-v3.json'),phaseCaps=dict(DIAGNOSTIC=20,FROZEN40=40,FRESH=20,RETEST=20,FALLBACK_RESERVE=20),aggregateHttpCap=120,concurrency=1,minimumSpacingSeconds=5,rollingRequestsPer60Seconds=12,rollingReservedTokensPer60Seconds=200000,reservedInputPerAttempt=16384,reservedOutputPerAttempt=1024,configuredCallDeadlineSeconds=10,campaignDeadlineSeconds=3600,automaticSameModelRetries=0,fallbackOrder=['gemini-3.5-flash-lite','gemini-3.1-flash-lite','gemini-2.5-flash-lite','gemini-2.5-flash'],scoring='Unchanged frozen business oracle and direct raw-meaning scorer; no best-of-attempt substitution',freshClassification='FRESH_DEVELOPMENT_GENERALIZATION'))
before=read(OUT/'b1-scored-replay-before-v2.json');after=read(OUT/'b1-scored-replay-after-v2.json')
assert len(before['observations'])==len(after['observations'])==40
assert all(b['sampleId']==a['sampleId'] and b['utterance']==a['utterance'] and b['expectedExtraction']==a['expectedExtraction'] and b['actualExtractionText']==a['actualExtractionText'] and b['envelopeSha256']==a['envelopeSha256'] for b,a in zip(before['observations'],after['observations']))
assert before['rawSemanticMetrics']==after['rawSemanticMetrics']==dict(rawSchemaValid=40,rawActionCorrect=32,rawIntentCorrect=32,rawCriteriaMeaningCorrect=25,rawExactMeaning=22)
current=read(OUT/'source-final-v3.json')
assert all(sha(ROOT/p)==v['sha256'] for p,v in current['sources'].items())
assert sha(ROOT/'ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar')==current['jarSha256']
process=read(OUT/'process-check-v2.json');assert process['activeLiveProcesses']==0
keyscan=read(OUT/'active-key-scan-v2.json');assert keyscan['exactMatches']==0
write('final-integrity-v2.json',dict(original40ReplayResponsesUtterancesAndOraclesIdentical=True,rawBaselineReproduced=True,currentSourcesMatchFinalSnapshot=True,currentJarMatchesFinalSnapshot=True,activeLiveProcesses=0,exactActiveKeyMatches=0,sealedCriticalJournalHash=sha(ROOT/'ai-agent-service/.local/semantic-quality-v2/segment-01-diagnostic-r2.json'),sealedCriticalReason='UNKNOWN_IN_FLIGHT',newHttpAttempts=15,noPostStopDispatch=True))
reports=[OUT.parent/name for name in ('BATCH10B4D_QUALITY_SEMANTIC_CONSISTENCY_AND_NLU_IMPROVEMENT_REPORT.md','VIETNAMESE_SEMANTIC_CONSISTENCY_FIX_V2.md','VIETNAMESE_NLU_CONTRACT_B_QUALITY_COMPARISON_V3.md','VIETNAMESE_NLU_REMAINING_FAILURE_ANALYSIS_V3.md')]
missing=[]
for p in reports:
    for link in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
        if '://' not in link and not (p.parent/link.split('#')[0]).exists() and not link.endswith('artifact-hashes-v2.json'):missing.append(dict(file=p.name,link=link))
assert not missing,missing
write('report-link-check-v2.json',dict(reportsChecked=4,missingLinks=missing))
paths=[p for p in OUT.rglob('*') if p.is_file() and p.name!='artifact-hashes-v2.json' and '__pycache__' not in p.parts]+reports
paths += [p for p in (ROOT/'ai-agent-service/.local/semantic-quality-v2').rglob('*') if p.is_file()]
paths += list((ROOT/'ai-agent-service/.local').glob('quality-*.log'))
paths += [ROOT/'ai-agent-service/README.md']
write('artifact-hashes-v2.json',{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in sorted(paths)})
print('sealed',len(paths),'artifacts; responses/oracles/source/jar unchanged; links valid')
