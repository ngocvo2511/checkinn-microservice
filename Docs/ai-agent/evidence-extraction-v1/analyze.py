"""Batch-specific derivation from retained synthetic journals. No network or credential access."""
import collections
import csv
import datetime as dt
import hashlib
import json
import math
import pathlib
import statistics
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = pathlib.Path(__file__).resolve().parent
LOCAL = ROOT / 'ai-agent-service/.local/evidence-extraction-v1'

def read(p):
    return json.loads(p.read_text(encoding='utf-8-sig'))

def write(name, data):
    (OUT/name).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def ratio(n, d):
    return dict(numerator=n, denominator=d, rate=n/d if d else None)

def distribution(values):
    a=sorted(values)
    return dict(n=len(a), mean=statistics.mean(a) if a else None, median=statistics.median(a) if a else None,
                p95=a[math.ceil(.95*len(a))-1] if a else None, maximum=max(a) if a else None, percentileMethod='nearest rank')

def canonical(e):
    return dict(action=e.get('action'),intent=e.get('intent'),criteria=e.get('fields',e.get('criteria',{})),reference=e.get('reference',''),
                missingSlots=sorted(e.get('missingSlots',[])),inferredFields=sorted(e.get('inferredFields',[])))

def normalize(s):
    return ' '.join(''.join(c for c in unicodedata.normalize('NFD',s.lower().replace('đ','d')) if not unicodedata.combining(c)).split())

def evidence_units(row, attempt):
    expected=row['expectedEvidenceWitnesses']; found=set(); valid_facts=0; membership=0; ranges=[]
    for fact in attempt.get('evidenceProposal',{}).get('facts',[]):
        text=fact.get('text',''); start=row['utterance'].find(text)
        if start<0: continue
        membership+=1; end=start+len(text)
        overlap=any(start<b and end>a for a,b in ranges); ranges.append((start,end))
        hits=[i for i,w in enumerate(expected) if w['text'] in normalize(text)]
        if not overlap and hits and all(expected[i]['type']==fact.get('type') for i in hits) and not any(i in found for i in hits):
            valid_facts+=1; found.update(hits)
    return found, valid_facts, membership

plan_file=OUT/'candidate-r2/offline-plan-and-parser-comparator-v1.json'
if not plan_file.exists(): plan_file=OUT/'offline-plan-and-parser-comparator-v1.json'
plan=read(plan_file)
segments=[]; by_id={}; all_attempts=[]
for p in sorted(LOCAL.glob('segment-*.json')):
    segment=read(p); segment['sourceArtifact']=p.relative_to(ROOT).as_posix(); segment['sourceSha256']=digest(p); segments.append(segment)
    for row in segment.get('observations',[]):
        by_id[row['sampleId']]=row
        for a in row.get('attempts',[]):
            if a.get('status')!='ATTEMPTED': continue
            a=dict(a,sampleId=row['sampleId'],sourceId=row.get('sourceId'),phase=row['phase'],segment=segment['segment'],
                   candidateRevision=segment.get('candidateRevision','candidate-r1'),sourceArtifact=p.relative_to(ROOT).as_posix())
            path=LOCAL/'responses'/f"{a['attemptId']}.json"
            if path.exists():
                envelope=read(path); usage=envelope.get('usageMetadata',{})
                a['reportedThinkingTokens']=usage.get('thoughtsTokenCount','NOT_REPORTED'); a['finishReason']=envelope.get('candidates',[{}])[0].get('finishReason','NOT_REPORTED')
                a['responseSha256']=digest(path)
            else: a['reportedThinkingTokens']='NOT_REPORTED'
            status=a.get('httpStatus',0); ended=a.get('callTerminated',False); category=a.get('category','')
            a['localTerminationClassification']=('LOCALLY_TERMINATED_TIMEOUT' if ended and category in ('TIMEOUT','PROVIDER_TIMEOUT')
                else 'LOCAL_CANCELLATION_COMPLETED' if ended and category=='CANCELLED' else 'SUCCESSFUL_RESPONSE' if ended and status==200
                else 'TERMINATED_HTTP_FAILURE' if ended and status>=400 else 'UNKNOWN_LOCAL_TERMINATION')
            all_attempts.append(a)

rows=[]
for expected in plan['observations']:
    row=dict(expected); observed=by_id.get(row['sampleId'])
    if observed:
        # The witness/contract snapshot actually dispatched is authoritative for its own revision.
        row.update(observed)
        row['candidateRevision']=next((s.get('candidateRevision','candidate-r1') for s in segments if any(x['sampleId']==row['sampleId'] for x in s.get('observations',[]))), 'NOT_RUN')
    rows.append(row)

def metrics(cohort):
    attempted=[r for r in cohort if r.get('status')=='ATTEMPTED' and r.get('attempts')]
    primary=[(r,r['attempts'][0]) for r in attempted]
    n=len(primary); out=dict(planned=len(cohort),attempted=n,notRun=len(cohort)-n,
        primaryHttp200=sum(a.get('httpStatus')==200 for r,a in primary),rawSchemaValid=sum(a.get('evidenceSchemaValid',False) for r,a in primary),
        completeEvidence=sum(a.get('evidenceSchemaValid',False) and a.get('evidenceMetrics',{}).get('missing',1)==0 for r,a in primary),
        requestMeaningCorrect=sum(a.get('evidenceMetrics',{}).get('requestCorrect',False) for r,a in primary),
        rawExact=sum(a.get('rawEvidenceExact',False) for r,a in primary),canonicalTypedValid=sum(a.get('schemaValid',False) for r,a in primary),
        exactBusinessSemantics=sum(a.get('semanticPass',False) for r,a in primary),
        compilerRejections=sum(a.get('compilerResult')=='FAIL' for r,a in primary),
        evidenceValidationRejections=sum(a.get('evidenceSchemaValid',False) and not a.get('rawEvidenceExact',False) for r,a in primary),
        completenessRejections=sum(a.get('evidenceMetrics',{}).get('missing',0)>0 for r,a in primary),
        missingEvidence=sum(a.get('evidenceMetrics',{}).get('missing',0) for r,a in primary),
        inventedEvidence=sum(a.get('evidenceMetrics',{}).get('invented',0) for r,a in primary),
        wrongTypeEvidence=sum(a.get('evidenceMetrics',{}).get('wrongType',0) for r,a in primary),
        duplicateOrOverlappingEvidence=sum(a.get('evidenceMetrics',{}).get('duplicates',0) for r,a in primary))
    expected_units=matched_units=proposed=valid_proposed=member=0; per_type=collections.defaultdict(lambda:[0,0])
    for r,a in primary:
        found,valid,membership=evidence_units(r,a)
        expected_units+=len(r['expectedEvidenceWitnesses']); matched_units+=len(found); valid_proposed+=valid; member+=membership
        proposed+=len(a.get('evidenceProposal',{}).get('facts',[]))
        for i,w in enumerate(r['expectedEvidenceWitnesses']):
            per_type[w['type']][1]+=1; per_type[w['type']][0]+=i in found
    out.update(evidencePrecision=ratio(valid_proposed,proposed),evidenceRecall=ratio(matched_units,expected_units),
               evidenceMembership=ratio(member,proposed),evidenceTypeAccuracy=ratio(valid_proposed,member),
               retentionByType={t:ratio(*v) for t,v in per_type.items()})
    matrix=dict(correctAccepted=0,correctRejected=0,incorrectAccepted=0,incorrectRejected=0,NOT_EVALUATED=0)
    for r,a in primary:
        if not a.get('schemaValid'): matrix['NOT_EVALUATED']+=1; continue
        correct=a.get('semanticPass',False); accepted=a.get('backendAccepted',False)
        matrix[('correct' if correct else 'incorrect')+('Accepted' if accepted else 'Rejected')]+=1
    out['backendConfusion']=matrix
    ca,cr,ia,ir=[matrix[k] for k in ('correctAccepted','correctRejected','incorrectAccepted','incorrectRejected')]
    out.update(falseAcceptanceRate=ratio(ia,ia+ir),falseRejectionRate=ratio(cr,ca+cr),backendClassificationAccuracy=ratio(ca+ir,ca+cr+ia+ir),
               admissionPrecision=ratio(ca,ca+ia),endToEndCorrectAdmission=ratio(ca,n))
    for label,key in [('actionCorrect','action'),('intentCorrect','intent'),('criteriaCorrect','criteria'),('missingSlotsCorrect','missingSlots')]:
        out[label]=sum(a.get('schemaValid',False) and a.get('actual',{}).get(key)==canonical(r['expectedExtraction'])[key] for r,a in primary)
    prices=[(r,a) for r,a in primary if r['expectedExtraction']['fields'].get('referencePriceExclusive',{}).get('value') is True]
    out['referencePriceCorrect']=ratio(sum(a.get('semanticPass',False) for r,a in prices),len(prices))
    out['unsafeAccepted']=sum(a.get('backendAccepted',False) and (a.get('unsafe') or a.get('unauthorizedTool') or a.get('confirmation')) for r,a in primary)
    out['primaryAttemptLatencyMillis']=distribution([a.get('latencyMillis',0) for r,a in primary])
    out['logicalLatencyMillis']=distribution([r['logicalLatencyMillis'] for r in attempted if 'logicalLatencyMillis' in r])
    attempts=[a for r in attempted for a in r['attempts'] if a.get('status')=='ATTEMPTED']
    out.update(actualHttpAttempts=len(attempts),providerFailures=sum(a.get('httpStatus')!=200 for a in attempts),
               timeouts=sum(a.get('category') in ['TIMEOUT','PROVIDER_TIMEOUT'] for a in attempts),unknownUsage=sum(a.get('actualUsageStatus')!='KNOWN' for a in attempts),
               knownInputTokens=sum(a.get('inputTokens') or 0 for a in attempts),knownCandidateOutputTokens=sum(a.get('outputTokens') or 0 for a in attempts),
               knownTotalTokens=sum(a.get('totalTokens') or 0 for a in attempts),
               actualReportedModelIdentities=sorted(set(a.get('providerModelVersion','NOT_REPORTED') for a in attempts)),
               observedInventedEvidenceAccepted=sum(a.get('backendAccepted',False) and a.get('evidenceMetrics',{}).get('invented',0)>0 for a in attempts))
    out['overallAttemptedDenominator']=n
    return out

phase_metrics={phase:metrics([r for r in rows if r['phase']==phase]) for phase in ['TECHNICAL','DIAGNOSTIC','DIAGNOSTIC_R2','COMPARISON','FROZEN40','FRESH']}
model_metrics={model:metrics([r for r in rows if r['phase']=='COMPARISON' and r.get('fixedModel')==model or r['phase']=='COMPARISON' and f"CMP-{name}-" in r['sampleId']])
               for model,name in [('gemini-3.5-flash-lite','FLASH_35_LITE'),('gemini-3.1-flash-lite','FLASH_31_LITE'),('gemini-2.5-flash-lite','FLASH_25_LITE'),('gemini-2.5-flash','FLASH_25')]}
write('phase-metrics-v1.json',phase_metrics); write('model-specific-metrics-v1.json',model_metrics)
for phase,name in [('FROZEN40','frozen40'),('FRESH','fresh20'),('COMPARISON','model-comparison'),('DIAGNOSTIC','diagnostic-r1'),('DIAGNOSTIC_R2','diagnostic-r2'),('TECHNICAL','technical')]:
    write(f'{name}-observations-v1.json',dict(version='evidence-extraction-observations-v1',classification='FRESH_DEVELOPMENT_GENERALIZATION' if phase=='FRESH' else phase,
          metrics=phase_metrics[phase],observations=[r for r in rows if r['phase']==phase]))
    with (OUT/f'{name}-observations-v1.csv').open('w',encoding='utf-8',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=['sampleId','sourceId','status','httpStatus','rawSchemaValid','rawExact','canonicalTypedValid','semanticPass','backendAccepted','category'])
        writer.writeheader()
        for r in rows:
            if r['phase']!=phase: continue
            a=r.get('attempts',[{}])[0] if r.get('attempts') else {}
            writer.writerow(dict(sampleId=r['sampleId'],sourceId=r['sourceId'],status=r['status'],httpStatus=a.get('httpStatus','NOT_RUN'),rawSchemaValid=a.get('evidenceSchemaValid','NOT_RUN'),
                rawExact=a.get('rawEvidenceExact','NOT_RUN'),canonicalTypedValid=a.get('schemaValid','NOT_RUN'),semanticPass=a.get('semanticPass','NOT_RUN'),backendAccepted=a.get('backendAccepted','NOT_RUN'),category=a.get('category','NOT_RUN')))

times=sorted(dt.datetime.fromisoformat(a['dispatchAt'].replace('Z','+00:00')) for a in all_attempts)
rolling=max((sum(0<=(t-p).total_seconds()<60 for p in times) for t in times),default=0)
spacing=min(((b-a).total_seconds() for a,b in zip(times,times[1:])),default=None)
per_model_limits={}
for model in ['gemini-3.5-flash-lite','gemini-3.1-flash-lite','gemini-2.5-flash-lite','gemini-2.5-flash']:
    ts=sorted(dt.datetime.fromisoformat(a['dispatchAt'].replace('Z','+00:00')) for a in all_attempts if a['actualModel']==model)
    per_model_limits[model]=dict(attempts=len(ts),maximumRollingMinute=max((sum(0<=(t-p).total_seconds()<60 for p in ts) for t in ts),default=0))
budget=dict(actualHttpAttempts=len(all_attempts),hardCap=120,phaseAttempts=dict(collections.Counter(a['budgetPhase'] for a in all_attempts)),
    perModel=dict(collections.Counter(a['actualModel'] for a in all_attempts)),knownInputTokens=sum(a.get('inputTokens') or 0 for a in all_attempts),
    knownCandidateOutputTokens=sum(a.get('outputTokens') or 0 for a in all_attempts),knownTotalTokens=sum(a.get('totalTokens') or 0 for a in all_attempts),
    unknownUsage=sum(a.get('actualUsageStatus')!='KNOWN' for a in all_attempts),reportedThinkingTokens=sum(a['reportedThinkingTokens'] for a in all_attempts if isinstance(a['reportedThinkingTokens'],int)),
    thinkingMetadataReportedAttempts=sum(isinstance(a['reportedThinkingTokens'],int) for a in all_attempts),thinkingNotReportedAttempts=sum(a['reportedThinkingTokens']=='NOT_REPORTED' for a in all_attempts),
    reservedInputTokens=len(all_attempts)*16384,reservedOutputTokens=len(all_attempts)*1024,fullConservativeReservations=len(all_attempts)*17408,
    firstDispatch=times[0].isoformat() if times else None,lastDispatch=times[-1].isoformat() if times else None,
    elapsedBetweenDispatchesSeconds=(times[-1]-times[0]).total_seconds() if times else None,
    maximumRollingRequests=rolling,maximumRollingReservedTokens=rolling*17408,minimumJournaledDispatchSpacingSeconds=spacing,
    modelLimits=per_model_limits,globalLimiterWaitingMillis=sum(s.get('rateLimitWaitMillis',0) for s in segments),
    modelSpecificLimiterWait='NOT_SEPARATELY_RECORDED',providerOnlyLatency='NOT_SEPARATELY_RECORDED',
    attemptLatencyMillis=distribution([a['latencyMillis'] for a in all_attempts]),
    fallbackCooldownSeconds=sum(a.get('cooldownSeconds',0) for a in all_attempts if a.get('previousAttemptId')),
    allRecordedCooldownSeconds=sum(a.get('cooldownSeconds',0) for a in all_attempts),automaticRetries=0,
    localConcurrency='1 synchronous dispatch/exchange',remoteInferenceConcurrency='NOT_VERIFIED',costGuarantee='NONE')
if not budget['thinkingMetadataReportedAttempts']: budget['reportedThinkingTokens']='NOT_REPORTED'
write('budget-accounting-v1.json',budget); write('actual-attempts-v1.json',all_attempts)
write('timeout-classifications-v1.json',dict(observations=[{k:a.get(k) for k in ['sampleId','attemptId','httpStatus','category','callTerminated','localTerminationClassification','actualUsageStatus','latencyMillis','continuationReason']} for a in all_attempts],
    liveTimeoutPath='OBSERVED' if any(a['localTerminationClassification']=='LOCALLY_TERMINATED_TIMEOUT' for a in all_attempts) else 'LIVE_TIMEOUT_PATH_NOT_OBSERVED',remoteTermination='NOT_VERIFIABLE_BY_THIS_API'))
write('fallback-metrics-v1.json',dict(transitions=[a for a in all_attempts if a.get('previousAttemptId')],failedAttempts=[a for a in all_attempts if a.get('httpStatus')!=200],
    actualHighDemand503=sum(a.get('httpStatus')==503 and 'high demand' in a.get('providerErrorBody','').lower() for a in all_attempts),
    capacitySelectorFailures=sum(s.get('capacityFailures',0) for s in segments),
    cumulativeActualTransientFailures=sum(a.get('httpStatus') in [500,502,503,504] or a.get('category') in ['TIMEOUT','PROVIDER_TIMEOUT'] for a in all_attempts)))

b=read(ROOT/'Docs/ai-agent/semantic-quality-v2/b1-scored-replay-after-v2.json')
brows=b['observations'] if isinstance(b,dict) else b
paired={k:[] for k in ['FAIL -> PASS','PASS -> FAIL','PASS -> PASS','FAIL -> FAIL','NOT_COMPARABLE']}
for r in [x for x in rows if x['phase']=='FROZEN40']:
    old=next(x for x in brows if x['sampleId']==r['sampleId']); a=r.get('attempts',[{}])[0] if r.get('attempts') else {}
    old_model=old.get('providerModelVersion',old.get('actualModel'))
    if not a or a.get('httpStatus')!=200 or not a.get('providerModelVersion') or old_model!=a['providerModelVersion']:
        paired['NOT_COMPARABLE'].append(r['sampleId']); continue
    key=('PASS' if old.get('semanticPass') else 'FAIL')+' -> '+('PASS' if a.get('semanticPass') else 'FAIL'); paired[key].append(r['sampleId'])
write('paired-comparisons-v1.json',paired)
write('parser-only-comparator-v1.json',dict(actualHttpAttempts=0,credentialReads=0,cohorts={phase:dict(planned=len(v:=[r for r in rows if r['phase']==phase]),exact=sum(r.get('deterministicExact',False) for r in v),observations=[{k:r.get(k) for k in ['sampleId','utterance','expectedExtraction','deterministicExtraction','deterministicExact']} for r in v]) for phase in ['FROZEN40','FRESH']},classification='OFFLINE_DETERMINISTIC_ONLY_NOT_MODEL_ACCURACY'))

failures=[]
for r in rows:
    if r.get('status')!='ATTEMPTED': continue
    for a in r.get('attempts',[]):
        if a.get('semanticPass') and a.get('backendAccepted'): continue
        provider=a.get('httpStatus')!=200 or a.get('actualUsageStatus')!='KNOWN'
        ev=a.get('evidenceMetrics',{})
        root=['H8'] if provider else ['H1','H2'] if ev.get('wrongType') or ev.get('invented') or ev.get('missing') or not ev.get('requestCorrect',False) else ['REQUIRES_MANUAL_H3_H4_H5_REVIEW']
        failures.append(dict(sampleId=r['sampleId'],sourceId=r['sourceId'],phase=r['phase'],utterance=r['utterance'],expected=r['expectedExtraction'],
            actualRawEvidence=a.get('evidenceProposal'),rawText=a.get('actualExtractionText'),evidenceMetrics=ev,canonical=a.get('compiledExtraction'),pipeline=a.get('pipeline'),
            category=a.get('category'),backendAccepted=a.get('backendAccepted',False),rootCause=root,semanticEvaluation='NOT_EVALUATED' if provider else 'EVALUATED',
            safetyImplication='No state or tool authority granted' if not a.get('backendAccepted') else 'REVIEW_ACCEPTANCE',
            recommendedNextAction='Retain failure; inspect provider/transport evidence without same-sample retry' if provider else 'Review meaning/span completeness; do not weaken validation or alter frozen oracle'))
write('remaining-failures-v1.json',failures)
write('segments-v1.json',segments)
print(json.dumps(dict(phases={k:{x:v[x] for x in ['planned','attempted','notRun','rawSchemaValid','rawExact','exactBusinessSemantics']} for k,v in phase_metrics.items()},budget=budget,paired={k:len(v) for k,v in paired.items()},failures=len(failures)),ensure_ascii=False,indent=2))
