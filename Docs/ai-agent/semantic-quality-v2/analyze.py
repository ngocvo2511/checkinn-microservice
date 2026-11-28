"""Descriptive analysis of retained synthetic observations; never dispatches a request."""
import csv,hashlib,importlib.util,json,math,statistics,sys
from collections import Counter,defaultdict
from datetime import datetime
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]; OUT=Path(__file__).resolve().parent
LOCAL=ROOT/'ai-agent-service/.local/semantic-quality-v2'
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def write(name,obj): (OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
spec=importlib.util.spec_from_file_location('frozen_analysis',ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/analyze.py')
sys.dont_write_bytecode=True
old=importlib.util.module_from_spec(spec);spec.loader.exec_module(old)
categories={r['id']:r['category'] for r in read(ROOT/'ai-agent-service/src/main/resources/gemini/vietnamese-nlu-evaluation-candidates-v1.json')['fixtures']}
categories.update({r['id']:r['category'] for r in read(OUT/'fresh-dataset-v2.json')['fixtures']})
causes=read(OUT/'original-compiler-rejections-v1.json')
for r in causes:
    intent=r['actualProposal']['intent']
    r['action']=intent if intent in ('SELECTED','COMPARISON') else 'CLARIFY' if intent in ('CLARIFY','UNSUPPORTED','UNVERIFIABLE') else 'CRITERIA'
write('original-compiler-rejections-v1.json',causes)
def enrich(row):
    result=dict(row)
    if result.get('protocolVersion')=='vietnamese-semantic-v2':
        result['protocolVersion']='vietnamese-semantic-v1';old.raw_semantic_score(result,categories);result['protocolVersion']='vietnamese-semantic-v2'
    elif result.get('protocolVersion')=='vietnamese-semantic-v1':old.raw_semantic_score(result,categories)
    return result
def fraction(n,d):return {'numerator':n,'denominator':d,'rate':n/d if d else None}
def distribution(values):
    values=sorted(values)
    return dict(n=len(values),mean=statistics.mean(values) if values else None,median=statistics.median(values) if values else None,p95=values[math.ceil(.95*len(values))-1] if values else None,maximum=max(values) if values else None,percentileMethod='nearest rank')
def metrics(rows):
    m=old.metrics(rows);attempted=[r for r in rows if r.get('status')=='ATTEMPTED']; valid=[r for r in attempted if r.get('schemaValid')]
    m['rawModelMissingSlots']='NOT_APPLICABLE_FOR_B'
    m['backendClassificationAccuracy']=fraction(m['correctAccepted']+m['incorrectRejected'],len(valid))
    m['correctAdmissionPrecision']=fraction(m['correctAccepted'],m['backendAccepted'])
    m['endToEndCorrectAdmission']=fraction(m['correctAccepted'],len(attempted))
    m['attemptLatencyMillis']=distribution([r['latencyMillis'] for r in attempted if 'latencyMillis' in r])
    m['logicalLatencyMillis']=distribution([r['logicalLatencyMillis'] for r in attempted if 'logicalLatencyMillis' in r])
    m['providerOnlyLatencyMillis']='NOT_SEPARATELY_RECORDED; attempt latency includes SDK/validation'
    m['providerStatusCounts']={str(code):sum(r.get('httpStatus')==code for r in attempted) for code in (200,400,401,403,429,503)}
    m['timeouts']=sum(r.get('category')=='TIMEOUT' for r in attempted)
    m['providerFailures']=sum(r.get('httpStatus')!=200 for r in attempted)
    m['thinkingStatus']='REPORTED' if m['thinkingReportingAttempts'] else 'NOT_REPORTED'
    for name,keys in {'dateRetention':('arrival','departure'),'occupancyRetention':('adults','children','rooms'),'destinationCorrectness':('destination',),'priceOperatorCorrectness':('price.operator',),'priceValueCorrectness':('price.amount','price.upper'),'referenceRecognition':('intent','reference'),'explicitRemovalCorrectness':('remove',),'unsupportedConstraintRecognition':('issues',)}.items():
        def value(obj,key):
            for part in key.split('.'):obj=obj.get(part,{}) if isinstance(obj,dict) else {}
            return obj
        subset=[]
        for r in attempted:
            expected=r.get('rawSemanticExpected',{})
            applicable=any(value(expected,k)!={} for k in keys)
            if name=='referenceRecognition':applicable=expected.get('intent') in ('SELECTED','COMPARISON')
            if name=='unsupportedConstraintRecognition':applicable='UNSUPPORTED_FILTER' in expected.get('issues',[])
            if applicable:subset.append(r)
        m[name]=fraction(sum(all(value(r.get('semanticProposal',{}),k)==value(r.get('rawSemanticExpected',{}),k) for k in keys) for r in subset),len(subset))
    for key in ('unsafe','confirmation','unauthorizedTool'):
        m[key+'Observed']=sum(bool(r.get(key)) for r in attempted)
        m[key+'Accepted']=sum(bool(r.get(key)) and r.get('backendAccepted',False) for r in attempted)
    extra=[]
    for r in attempted:
        if r.get('expectedExtraction',{}).get('action')!='CRITERIA':continue
        proposal=r.get('semanticProposal') or {}; expected=r.get('rawSemanticExpected',{})
        invented=[k for k in ('destination','hotel','arrival','departure','adults','children','rooms','stars','price') if k in proposal and k not in expected]
        if invented:extra.append(dict(sampleId=r['sampleId'],fields=invented,accepted=r.get('backendAccepted',False)))
    m['unsafeInferenceExtraOrdinaryFacts']=extra
    m['unsafeInferenceAcceptance']=sum(r['accepted'] for r in extra)
    m['unsafeInferenceDefinition']='Extra supported facts on ordinary criteria requests absent from the frozen explicit-fact oracle; omissions/substitutions remain separate semantic failures. Not a general inference detector.'
    m['compilerRejectionCategories']=dict(Counter(r.get('pipeline',{}).get('failureLocation','NOT_RECORDED') for r in attempted if r.get('compilerResult')=='FAIL'))
    return m
segments=[]
if LOCAL.exists():
    for p in LOCAL.glob('*.json'):
        data=read(p)
        if data.get('version')=='local-semantic-quality-v2' and data.get('stopReason')!='OFFLINE_PREFLIGHT':segments.append(data)
segments.sort(key=lambda s:s['startedAt'])
terminal=[]; attempts=[]
for segment in segments:
    for row in segment['observations']:
        for attempt in row['attempts']:attempts.append(enrich({**row,**attempt,'segment':segment['segment']}))
        terminal.append(enrich({**row,**row['attempts'][-1], 'status':row['status'],'segment':segment['segment']}) if row['attempts'] else {**row,'segment':segment['segment']})
if (OUT/'frozen-plan-final-v3.json').exists():
    planned=read(OUT/'frozen-plan-final-v3.json')
    for row in planned['observations']:
        if row['phase'] in ('FROZEN40','FRESH') and not any(r['phase']==row['phase'] and r['sampleId']==row['sampleId'] for r in terminal):
            terminal.append({**row,'segment':'NOT_DISPATCHED_AFTER_CRITICAL_STOP'})
write('segments-v2.json',segments);write('actual-attempts-v2.json',attempts)
cohorts={}
for phase,name in [('DIAGNOSTIC','diagnostic'),('FROZEN40','frozen40'),('FRESH','fresh')]:
    rows=[r for r in terminal if r['phase']==phase];cohorts[phase]=rows
    write(name+'-observations-v2.json',{'metrics':metrics(rows),'observations':rows})
permodel={}
for model in sorted({r.get('actualModel') for r in attempts}):
    permodel[model]={phase:metrics([r for r in rows if r.get('actualModel')==model]) for phase,rows in cohorts.items()}
write('model-specific-metrics-v2.json',permodel)
write('diagnostic-revision-metrics-v2.json',{s['segment']:metrics([r for r in cohorts['DIAGNOSTIC'] if r['segment']==s['segment']]) for s in segments if any(r['phase']=='DIAGNOSTIC' for r in s['observations'])})
original=read(ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/b40-v1.json')
scored={}
for revision,filename in [('before','b1-replay-before-v1.json'),('after','b1-replay-after-v2.json')]:
    replay=read(OUT/filename); replay['observations']=[enrich(r) for r in replay['observations']]
    replay['rawSemanticMetrics']={key:sum(r.get(field,False) for r in replay['observations']) for key,field in [('rawSchemaValid','semanticSchemaValid'),('rawActionCorrect','rawSemanticActionPass'),('rawIntentCorrect','rawSemanticIntentPass'),('rawCriteriaMeaningCorrect','rawMeaningCriteriaPass'),('rawExactMeaning','rawSemanticPass')]}
    replay['modelMissingSlots']='NOT_APPLICABLE; derived by Java'
    valid=[r for r in replay['observations'] if r.get('schemaValid')]
    for dim in ('action','intent','criteria','missingSlots','reference'):
        replay['metrics'][dim+'Correct']=sum(r.get('actual',{}).get(dim)==r.get('expected',{}).get(dim) for r in valid)
    replay['metrics']['backendNotEvaluated']=40-len(valid)
    replay['metrics']['http200']=40
    replay['metrics']['rawSemanticCorrect']=replay['rawSemanticMetrics']['rawExactMeaning']
    counts=replay['metrics'];correct=counts['correctAccepted']+counts['correctRejected'];incorrect=counts['incorrectAccepted']+counts['incorrectRejected']
    counts['falseAcceptanceRate']=fraction(counts['incorrectAccepted'],incorrect)
    counts['falseRejectionRate']=fraction(counts['correctRejected'],correct)
    counts['backendClassificationAccuracy']=fraction(counts['correctAccepted']+counts['incorrectRejected'],len(valid))
    counts['correctAdmissionPrecision']=fraction(counts['correctAccepted'],counts['correctAccepted']+counts['incorrectAccepted'])
    counts['endToEndCorrectAdmission']=fraction(counts['correctAccepted'],40)
    scored[revision]=replay
    write('b1-scored-replay-'+revision+'-v2.json',replay)
write('backend-before-after-matrix-v2.json',dict(before=scored['before']['metrics'],after=scored['after']['metrics'],rawModelBefore=scored['before']['rawSemanticMetrics'],rawModelAfter=scored['after']['rawSemanticMetrics'],changedDecisions=[dict(sampleId=a['sampleId'],before=b['backendAccepted'],after=a['backendAccepted'],cause='H5 generalized independent discovery grammar') for b,a in zip(scored['before']['observations'],scored['after']['observations']) if b['backendAccepted']!=a['backendAccepted']],actualNewHttpAttempts=0))
pairs=defaultdict(list,{k:[] for k in ('FAIL -> PASS','PASS -> FAIL','PASS -> PASS','FAIL -> FAIL','NOT_COMPARABLE')});after={r['sampleId']:r for r in cohorts['FROZEN40']}
for b1 in original['observations']:
    b2=after.get(b1['sampleId'])
    if not b2 or b2.get('status')!='ATTEMPTED' or b2.get('httpStatus')!=200 or b2.get('actualModel')!=b1['actualModel']:group='NOT_COMPARABLE'
    else:group=('PASS' if b1.get('semanticPass') else 'FAIL')+' -> '+('PASS' if b2.get('semanticPass') else 'FAIL')
    pairs[group].append(b1['sampleId'])
write('paired-comparisons-v2.json',dict(pairs))
write('comparison-v3.json',{'A3Frozen':read(ROOT/'Docs/ai-agent/semantic-corroboration-final-v1/model-specific-metrics-v1.json')['gemini-3.5-flash-lite']['AOriginal40'],'B1Frozen':original['metrics'],'B1BackendReplay':scored['after']['metrics'],'B2Frozen40':metrics(cohorts['FROZEN40']),'B2Fresh':metrics(cohorts['FRESH'])})
budgets=Counter(a.get('budgetPhase') for a in attempts);models=Counter(a.get('actualModel') for a in attempts)
known=[a for a in attempts if a.get('totalTokens') is not None]
dispatches=sorted((datetime.fromisoformat(a['dispatchAt'].replace('Z','+00:00')).timestamp(),a) for a in attempts if a.get('dispatchAt'))
maximum=max((sum(0<=t-u<60 for u,_ in dispatches) for t,_ in dispatches),default=0)
minspace=min((b[0]-a[0] for a,b in zip(dispatches,dispatches[1:])),default=None)
elapsed=dispatches[-1][0]-dispatches[0][0] if dispatches else 0
write('budget-accounting-v2.json',dict(actualHttpAttempts=len(attempts),cap=120,phaseAttempts=dict(budgets),modelAttempts=dict(models),knownInputTokens=sum(a['inputTokens'] for a in known),knownCandidateOutputTokens=sum(a['outputTokens'] for a in known),knownTotalTokens=sum(a['totalTokens'] for a in known),reportedThinkingTokens=sum(a.get('pipeline',{}).get('thinkingTokens',0) for a in known) if any('thinkingTokens' in a.get('pipeline',{}) for a in known) else 'NOT_REPORTED',unknownUsageAttempts=len(attempts)-len(known),reservedInputTokens=len(attempts)*16384,reservedOutputTokens=len(attempts)*1024,reservedTotalTokens=len(attempts)*17408,maximumRollingRequests=maximum,maximumRollingReservedTokens=maximum*17408,minimumDispatchSpacingSeconds=minspace,firstToLastDispatchSeconds=elapsed,withinAggregateBudget=len(attempts)<=120,withinRollingLimits=maximum<=12 and maximum*17408<=200000,withinSpacing=minspace is None or minspace>=5,withinDeadline=elapsed<=3600,sameModelAutomaticRetries=0,concurrency=1,limiterWaitMillis=sum(s.get('rateLimitWaitMillis',0) for s in segments),unknownAccountWideDailyUsage=True))
fallback=[a for a in attempts if a.get('previousAttemptId')]
required=[r for r in terminal if len(r.get('attempts',[]))>1]
write('fallback-metrics-v2.json',dict(logicalSamplesRequiringFallback=len(required),additionalAttempts=len(fallback),transitions=dict(Counter(a['actualModel'] for a in fallback)),technicalRecovery=fraction(sum(r.get('httpStatus')==200 for r in required),len(required)),semanticRecovery=fraction(sum(r.get('semanticPass',False) for r in required),len(required)),allModelsUnavailable=sum(s.get('stopReason')=='ALL_ELIGIBLE_MODELS_UNAVAILABLE' for s in segments),cooldownSeconds=sum(a.get('cooldownSeconds',0) for a in attempts),attemptsPerLogicalSample=len(attempts)/sum(r.get('status')=='ATTEMPTED' for r in terminal) if attempts else None))
with (OUT/'observations-v2.csv').open('w',encoding='utf-8',newline='') as f:
    fields=['sampleId','phase','actualModel','httpStatus','semanticSchemaValid','compilerResult','schemaValid','rawSemanticPass','semanticPass','backendAccepted','latencyMillis','totalTokens']
    w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore');w.writeheader();w.writerows(terminal)
write('remaining-failures-v3.json',[r for r in terminal if r.get('status')=='ATTEMPTED' and (not r.get('semanticPass') or not r.get('backendAccepted'))])
print(json.dumps({'segments':len(segments),'attempts':len(attempts),'cohorts':{p:metrics(r)['exactBusinessSemantics'] for p,r in cohorts.items()},'pairs':dict(pairs)},ensure_ascii=False))
