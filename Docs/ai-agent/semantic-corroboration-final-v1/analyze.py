"""Offline descriptive analysis of actual immutable campaigns. No provider, credentials or domain access."""
import csv
import hashlib
import json
import statistics
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
LOCAL = ROOT / 'ai-agent-service/.local/batch10b4d-final'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(name, data):
    (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def raw_semantic_expected(row, categories):
    """Independent raw-meaning scoring from the original business oracle; never invokes compiler output."""
    expected = row['expectedExtraction']
    action = expected['action']
    result = {'intent': action if action in ('SELECTED','COMPARISON') else expected['intent']}
    if expected['reference']:
        result['reference'] = expected['reference']
    inverse = {'cityName':'destination','hotelName':'hotel','checkIn':'arrival','checkOut':'departure','adults':'adults','children':'children','rooms':'rooms','starRatings':'stars'}
    removals = set()
    values = {}
    for name, change in expected['fields'].items():
        if change['operation'] == 'CLEAR':
            removals.add({'minPrice':'PRICE','maxPrice':'PRICE','starRatings':'STARS','cityName':'DESTINATION','hotelName':'HOTEL'}[name])
        else:
            values[name] = change['value']
            if name in inverse:
                result[inverse[name]] = change['value']
    if removals:
        result['remove'] = sorted(removals)
    if 'minPrice' in values or 'maxPrice' in values:
        exclusive = values.get('referencePriceExclusive', False)
        result['price'] = {'kind':'REFERENCE' if exclusive else 'STAY', 'operator':'LT' if exclusive else 'RANGE' if 'minPrice' in values and 'maxPrice' in values else 'GTE' if 'minPrice' in values else 'LTE', 'amount':values.get('minPrice',values.get('maxPrice')), 'unit':'VND' if exclusive else 'VND_PER_ROOM_NIGHT'}
        if 'minPrice' in values and 'maxPrice' in values:
            result['price']['upper'] = values['maxPrice']
    if action == 'CLARIFY':
        missing = frozenset(expected['missingSlots'])
        issues = {frozenset(['checkOut']):['DEPARTURE_MISSING'],frozenset(['checkIn']):['ARRIVAL_MISSING'],frozenset(['checkIn','checkOut']):['DATE_INVALID'],frozenset(['cityName']):['DESTINATION_UNRESOLVED'],frozenset(['adults','children','rooms']):['GUESTS_AMBIGUOUS'],frozenset(['minPrice','maxPrice']):['PRICE_RANGE_INVALID'],frozenset(['rooms','adults']):['ADULTS_INVALID','ROOMS_INVALID']}.get(missing)
        if issues is None:
            category = categories.get(row.get('sourceId',row['sampleId']))
            issues = {'unsupported-amenity':['UNSUPPORTED_FILTER'],'invalid-price':['PRICE_INVALID'],'date-inversion':['DATE_ORDER_INVALID']}.get(category,['OTHER'])
        result['issues'] = sorted(issues)
    return result


def raw_semantic_score(row, categories):
    if row.get('protocolVersion') != 'vietnamese-semantic-v1':
        return
    row['rawSemanticExpected'] = raw_semantic_expected(row,categories)
    proposal = row.get('semanticProposal')
    if not row.get('semanticSchemaValid') or not isinstance(proposal,dict):
        row['rawSemanticPass'] = False
        return
    normalized = {k: sorted(v) if k in ('remove','issues') else v for k,v in proposal.items() if not (k in ('remove','issues') and v == [])}
    row['rawSemanticPass'] = normalized == row['rawSemanticExpected']
    row['rawSemanticIntentPass'] = normalized.get('intent') == row['rawSemanticExpected']['intent']
    intent = normalized.get('intent')
    action = intent if intent in ('SELECTED','COMPARISON') else 'CLARIFY' if intent in ('CLARIFY','UNSUPPORTED','UNVERIFIABLE') else 'CRITERIA'
    row['rawSemanticActionPass'] = action == row['expectedExtraction']['action']
    row['rawMeaningCriteriaPass'] = {k:v for k,v in normalized.items() if k not in ('intent','reference','issues')} == {k:v for k,v in row['rawSemanticExpected'].items() if k not in ('intent','reference','issues')}


def metrics(rows):
    attempted = [r for r in rows if r.get('status') == 'ATTEMPTED']
    valid = [r for r in attempted if r.get('schemaValid')]
    correct = [r for r in valid if r.get('semanticPass')]
    incorrect = [r for r in valid if not r.get('semanticPass')]
    known = [r for r in attempted if r.get('totalTokens') is not None]
    latency = [r['latencyMillis'] for r in attempted if 'latencyMillis' in r]
    counts = {
        'planned': len(rows), 'attempted': len(attempted), 'notRun': len(rows) - len(attempted),
        'http200': sum(r.get('httpStatus') == 200 for r in attempted),
        'capacityFailures': sum(r.get('continuationReason') in ('PROVIDER_CAPACITY_FALLBACK', 'ALL_ELIGIBLE_MODELS_UNAVAILABLE', 'CAPACITY_FAILURE_CEILING') for r in attempted),
        'schemaValid': len(valid), 'exactBusinessSemantics': len(correct),
        'correctAccepted': sum(r.get('backendAccepted', False) for r in correct),
        'correctRejected': sum(not r.get('backendAccepted', False) for r in correct),
        'incorrectAccepted': sum(r.get('backendAccepted', False) for r in incorrect),
        'incorrectRejected': sum(not r.get('backendAccepted', False) for r in incorrect),
        'backendNotEvaluated': len(attempted) - len(valid),
        'backendAccepted': sum(r.get('backendAccepted', False) for r in attempted),
        'compilerErrors': sum(r.get('compilerResult') == 'FAIL' for r in attempted),
        'rawSemanticSchemaValid': sum(r.get('semanticSchemaValid', False) for r in attempted),
        'rawModelMissingSlots': 'NOT_APPLICABLE_FOR_B',
        'rawSemanticCorrect': sum(r.get('rawSemanticPass', False) for r in attempted),
        'rawSemanticIntentCorrect': sum(r.get('rawSemanticIntentPass', False) for r in attempted),
        'rawSemanticActionCorrect': sum(r.get('rawSemanticActionPass', False) for r in attempted),
        'rawMeaningCriteriaCorrect': sum(r.get('rawMeaningCriteriaPass', False) for r in attempted),
        'rawSemanticScoring': 'DIRECT_RAW_MEANING_VS_ORACLE_TRANSLATION; downstream missing slots not scored as model predictions',
        'knownInputTokens': sum(r['inputTokens'] for r in known), 'knownOutputTokens': sum(r['outputTokens'] for r in known),
        'knownTotalTokens': sum(r['totalTokens'] for r in known), 'knownUsageAttempts': len(known), 'unknownUsageAttempts': len(attempted)-len(known),
        'meanKnownTotalTokens': statistics.mean(r['totalTokens'] for r in known) if known else None,
        'medianLatencyMillis': statistics.median(latency) if latency else None,
        'meanLatencyMillis': statistics.mean(latency) if latency else None,
    }
    counts['rawModelMissingSlots'] = 'NOT_APPLICABLE_FOR_B' if any(r.get('protocolVersion') == 'vietnamese-semantic-v1' for r in rows) else 'APPLICABLE_FOR_A'
    thinking = [r['pipeline']['thinkingTokens'] for r in attempted if 'thinkingTokens' in r.get('pipeline',{})]
    counts['reportedThinkingTokens'] = sum(thinking) if thinking else None
    counts['thinkingReportingAttempts'] = len(thinking)
    for dim in ('action', 'intent', 'criteria', 'missingSlots', 'reference'):
        counts[dim + 'Correct'] = sum(r.get('actual') is not None and r['actual'].get(dim) == r['expected'].get(dim) for r in valid)
    counts['spuriousClearIds'] = []
    for r in attempted:
        proposal = r.get('compiledExtraction')
        if proposal is None:
            raw = r.get('actualExtractionText', r.get('actualExtraction'))
            try:
                proposal = json.loads(raw) if isinstance(raw, str) else raw
            except (ValueError, TypeError):
                proposal = None
        if proposal and any(v.get('operation') == 'CLEAR' and r['expectedExtraction'].get('fields', {}).get(k, {}).get('operation') != 'CLEAR' for k, v in proposal.get('fields', {}).items()):
            counts['spuriousClearIds'].append(r['sampleId'])
    price = [r for r in attempted if 'referencePriceExclusive' in r['expected'].get('criteria', {})]
    counts['referencePriceApplicable'] = len(price)
    counts['referencePriceCorrect'] = sum(r.get('actual') is not None and r['actual'].get('criteria') == r['expected'].get('criteria') for r in price)
    counts['falseAcceptanceRate'] = counts['incorrectAccepted']/len(incorrect) if incorrect else None
    counts['falseRejectionRate'] = counts['correctRejected']/len(correct) if correct else None
    return counts


def main():
    segments = [read(p) for p in LOCAL.glob('*.json') if p.name == 'campaign.json' or p.name.startswith('segment-') and not p.name.endswith('preflight.json')]
    segments.sort(key=lambda s: s['startedAt'])
    campaign = segments[-1]
    if any(s['stopReason'] == 'RUNNING' for s in segments):
        raise SystemExit('Campaign still running; no final evidence exported')
    unique = {}
    for segment in segments:
        for row in segment['observations']:
            # A separately planned model verification has its own identity, never replaces original smoke.
            if row['phase'] == 'MODEL_VERIFICATION':
                row['sampleId'] = 'VERIFY-' + segment['segment'] + '-' + row['sampleId']
            key = (row['phase'], row['sampleId'])
            if key not in unique or row['status'] == 'ATTEMPTED':
                unique[key] = row
    rows = list(unique.values())
    flat = []
    terminal = []
    categories = {r['id']:r['category'] for r in read(ROOT/'ai-agent-service/src/main/resources/gemini/vietnamese-nlu-evaluation-candidates-v1.json')['fixtures']}
    for row in rows:
        for attempt in row['attempts']:
            item = {**{k: row[k] for k in ('sampleId', 'sourceId', 'phase', 'protocolVersion', 'utterance', 'expected', 'expectedExtraction')}, **attempt}
            raw_semantic_score(item,categories)
            flat.append(item)
        if row['attempts']:
            item = {**row, **row['attempts'][-1], 'status': row['status']}
            raw_semantic_score(item,categories)
            terminal.append(item)
        else:
            terminal.append(row)
    write('campaign-observations-v1.json', {'segments':segments,'observations':rows,'stopReason':campaign['stopReason']})
    write('actual-attempts-v1.json', flat)
    write('logical-sample-outcomes-v1.json', terminal)
    for phase, filename in [('TECHNICAL','technical-verification-v1.json'),('REMAINING_A','remaining-a-v1.json'),('B_DIAGNOSTIC','b-diagnostics-v1.json'),('B40','b40-v1.json'),('SUPPLEMENTAL','generalization-and-retests-v1.json')]:
        selected = [r for r in terminal if r['phase'] == phase]
        write(filename, {'metrics': metrics(selected), 'observations': selected})
    write('fallback-attempts-v1.json', [r for r in flat if r['previousAttemptId'] or r.get('continuationReason') == 'PROVIDER_CAPACITY_FALLBACK'])
    historical = read(ROOT / 'Docs/ai-agent/semantic-extraction-fix-v1/baseline40-observations-v1.json')
    replay = read(OUT / 'replay-after-v1.json')
    improved = {r['sampleId']: r for r in replay['observations']}
    new_a = {r['sampleId']: {**r, **r['attempts'][0]} if r.get('attempts') else r for r in terminal if r['phase'] == 'REMAINING_A'}
    a_rows = []
    for old in historical['observations']:
        if old['status'] == 'ATTEMPTED':
            a_rows.append({**old, 'actualModel': historical['model'], 'backendAccepted': improved.get(old['sampleId'], {}).get('backendAccepted', False), 'evidence': 'HISTORICAL_LIVE_WITH_IMPROVED_BACKEND_REPLAY'})
        else:
            a_rows.append(new_a[old['sampleId']])
    write('combined-a-original40-v1.json', {'warning': 'Historical first attempts retained; retests never replace provider failures', 'observations': a_rows})
    replay_source=[]
    for item in new_a.values():
        replay_source.append({k:item[k] for k in ('sampleId','utterance','status','schemaValid','semanticPass','backendAccepted','expectedExtraction') if k in item} | ({'actualExtractionText':item['actualExtractionText']} if 'actualExtractionText' in item else {}))
    write('remaining-a-replay-source-v1.json',{'model':historical['model'],'protocolVersion':historical['protocolVersion'],'startedAt':segments[0]['startedAt'],'sourceSegmentSha256':digest(LOCAL/'campaign.json'),'observations':replay_source})
    by_model = defaultdict(list)
    for r in flat:
        by_model[r['actualModel']].append(r)
    model_metrics = {}
    for profile in campaign['modelProfiles']:
        model = profile['model']
        verified = next(p for p in read(OUT/'model-profiles-v1.json')['models'] if p['model'] == model)
        performance = metrics(by_model[model])
        performance = {k:v for k,v in performance.items() if k in ('attempted','http200','capacityFailures','knownInputTokens','knownOutputTokens','knownTotalTokens','knownUsageAttempts','unknownUsageAttempts','meanKnownTotalTokens','medianLatencyMillis','meanLatencyMillis','reportedThinkingTokens','thinkingReportingAttempts')}
        model_metrics[model] = {'profile': verified, 'lastSegmentDispatchSelection': profile.get('selectedForSegment',profile['eligible']), 'selectionMetadataNote':'Earlier immutable segment eligible flag represented selected dispatch scope; public/developer model eligibility is profile above', 'allAttemptPerformanceOnly': performance, 'qualityByPhaseAndContract': {}}
        for phase in ('TECHNICAL','REMAINING_A','B_DIAGNOSTIC','B40','SUPPLEMENTAL'):
            for protocol in campaign['contracts']:
                scope = [r for r in by_model[model] if r['phase'] == phase and r['protocolVersion'] == protocol]
                if scope:
                    model_metrics[model]['qualityByPhaseAndContract'][phase + ':' + protocol] = metrics(scope)
        model_metrics[model]['AOriginal40'] = metrics([r for r in a_rows if r.get('actualModel') == model])
    write('model-specific-metrics-v1.json', model_metrics)
    b_rows = {r['sampleId']: r for r in terminal if r['phase'] == 'B40'}
    pairs = []
    for a in a_rows:
        b = b_rows[a['sampleId']]
        pair = {'sampleId': a['sampleId'], 'actualModelA': a.get('actualModel'), 'actualModelB': b.get('actualModel'), 'aPass': a.get('semanticPass'), 'bPass': b.get('semanticPass')}
        if a['status'] != 'ATTEMPTED' or b['status'] != 'ATTEMPTED':
            pair['outcome'] = 'NOT_RUN'
        elif a.get('actualModel') != b.get('actualModel'):
            pair['outcome'] = 'CROSS_MODEL_NOT_DIRECTLY_COMPARABLE'
        elif a.get('httpStatus') != 200 or b.get('httpStatus') != 200:
            pair['outcome'] = 'NOT_COMPARABLE'; pair['reason'] = 'PROVIDER_FAILURE_PAIR'
        else:
            pair['outcome'] = ('PASS' if a.get('semanticPass') else 'FAIL') + '->' + ('PASS' if b.get('semanticPass') else 'FAIL')
        pairs.append(pair)
    supplemental_pairs = []
    for a in terminal:
        if not a['sampleId'].startswith('SUP-A-'):
            continue
        b = b_rows[a['sourceId']]
        pair = {'sampleId': a['sourceId'], 'supplementalA': a['sampleId'], 'actualModelA': a.get('actualModel'), 'actualModelB': b.get('actualModel')}
        if a['status'] != 'ATTEMPTED' or b['status'] != 'ATTEMPTED':
            pair['outcome'] = 'NOT_RUN'
        elif a.get('actualModel') != b.get('actualModel'):
            pair['outcome'] = 'CROSS_MODEL_NOT_DIRECTLY_COMPARABLE'
        elif a.get('httpStatus') != 200 or b.get('httpStatus') != 200:
            pair['outcome'] = 'NOT_COMPARABLE'
        else:
            pair['outcome'] = ('PASS' if a.get('semanticPass') else 'FAIL') + '->' + ('PASS' if b.get('semanticPass') else 'FAIL')
        supplemental_pairs.append(pair)
    write('paired-comparisons-v1.json', {'primaryCounts': dict(Counter(p['outcome'] for p in pairs)), 'primaryPairs': pairs, 'supplementalCounts': dict(Counter(p['outcome'] for p in supplemental_pairs)), 'supplementalPairs': supplemental_pairs, 'warning': 'Supplemental repeats are not additional distinct primary identities; cross-model pairs are not causal contract evidence'})
    attempts = [r for r in flat if r.get('status') == 'ATTEMPTED']
    actual_timestamps = sorted(datetime.fromisoformat(r['dispatchAt'].replace('Z','+00:00')) for r in attempts)
    def offset(row):
        return (datetime.fromisoformat(row['dispatchAt'].replace('Z','+00:00'))-actual_timestamps[0]).total_seconds()*1000
    offsets = sorted(offset(r) for r in attempts)
    minute_counts = [sum(0 <= end - t < 60000 for t in offsets) for end in offsets]
    phase_counts = Counter(r['budgetPhase'] for r in attempts)
    known = [r for r in attempts if r.get('totalTokens') is not None]
    per_model_rolling = {}
    for model, scope in by_model.items():
        starts = sorted(offset(r) for r in scope if r.get('status') == 'ATTEMPTED')
        per_model_rolling[model] = max((sum(0 <= end-t < 60000 for t in starts) for end in starts), default=0)
    budget = {'actualHttpAttempts': len(attempts), 'aggregateCap': 120, 'phaseAttempts': dict(phase_counts), 'phaseCaps': {'TECHNICAL':3,'REMAINING_A':15,'B_DIAGNOSTIC':8,'B40':40,'SUPPLEMENTAL':24,'FALLBACK_RESERVE':30}, 'reservedInputTokens': len(attempts)*16384, 'reservedOutputTokens': len(attempts)*1024, 'knownTotalTokens': sum(r['totalTokens'] for r in known), 'unknownUsageAttempts': len(attempts)-len(known), 'minimumDispatchSpacingMillis': min((offsets[i]-offsets[i-1] for i in range(1,len(offsets))),default=None), 'maximumRollingRequests': max(minute_counts,default=0), 'maximumRollingReservedTokens': max(minute_counts,default=0)*17408, 'modelMaximumRollingRequests': per_model_rolling, 'sameModelAutomaticRetries':0, 'domainCalls':0,'supabaseCalls':0, 'durationMillis':(actual_timestamps[-1]-actual_timestamps[0]).total_seconds()*1000 if attempts else 0, 'stopReason':campaign['stopReason']}
    budget['withinAggregateCap'] = len(attempts) <= 120
    budget['withinPhaseCaps'] = all(count <= budget['phaseCaps'][phase] for phase,count in phase_counts.items())
    budget['withinRollingCaps'] = budget['maximumRollingRequests'] <= 12 and budget['maximumRollingReservedTokens'] <= 200000
    write('budget-accounting-v1.json', budget)
    fallback_logical = [r for r in terminal if len(r.get('attempts',[])) > 1]
    recovery = [r for r in fallback_logical if r.get('httpStatus') == 200 and r.get('totalTokens') is not None and r.get('pipeline',{}).get('envelope') == 'PASS' and r.get('pipeline',{}).get('sdk') == 'PASS']
    availability = {'logicalPlanned': len(rows), 'logicalAttempted': sum(r['status']=='ATTEMPTED' for r in rows), 'logicalRequiringFallback': sum(any(a.get('continuationReason') in ('PROVIDER_CAPACITY_FALLBACK','ALL_ELIGIBLE_MODELS_UNAVAILABLE') for a in r['attempts']) for r in rows), 'actualFallbackAttempts': sum(bool(r['previousAttemptId']) for r in attempts), 'successfulTechnicalRecoveries':len(recovery), 'failedRecoveries':len(fallback_logical)-len(recovery), 'recoveryRate':len(recovery)/len(fallback_logical) if fallback_logical else None, 'allModelsUnavailableCount':sum(r.get('continuationReason')=='ALL_ELIGIBLE_MODELS_UNAVAILABLE' for r in flat), 'transitions':[{'attemptId':r['attemptId'],'previousAttemptId':r['previousAttemptId'],'model':r['actualModel']} for r in attempts if r['previousAttemptId']], 'attemptsPerLogicalSample':len(attempts)/sum(r['status']=='ATTEMPTED' for r in rows) if attempts else None, 'fallbackReservedTokenOverhead':sum(bool(r['previousAttemptId']) for r in attempts)*17408, 'fallbackLogicalLatenciesMillis':[r.get('logicalLatencyMillis') for r in fallback_logical], 'liveFallbackStatus':'LIVE_FALLBACK_VERIFIED' if any(r['previousAttemptId'] for r in attempts) else 'LIVE_FALLBACK_NOT_TESTED'}
    write('availability-metrics-v1.json',availability)
    write('contract-snapshots-v1.json',campaign['contracts'])
    before = read(OUT/'replay-before-v1.json')
    write('backend-before-after-matrix-v1.json', {'before':before['matrix'],'after':replay['matrix'],'changedDecisions':[{'sampleId':r['sampleId'],'oldAccepted':r['historicalBackendAccepted'],'newAccepted':r['backendAccepted']} for r in replay['observations'] if r.get('schemaValid') and r['historicalBackendAccepted']!=r['backendAccepted']], 'modelClassifications':'UNCHANGED','actualHttpAttempts':0})
    with (OUT/'observations-v1.csv').open('w',encoding='utf-8',newline='') as file:
        fields=['sampleId','phase','protocolVersion','attemptId','actualModel','httpStatus','schemaValid','semanticPass','backendAccepted','latencyMillis','inputTokens','outputTokens','totalTokens','previousAttemptId','continuationReason']
        writer=csv.DictWriter(file,fieldnames=fields,extrasaction='ignore');writer.writeheader();writer.writerows(flat)
    write('artifact-hashes-v1.json',{str(p.relative_to(ROOT)).replace('\\','/'):digest(p) for p in [*OUT.glob('*.json'),*LOCAL.glob('responses/*.json')] if p.name!='artifact-hashes-v1.json'})
    print(json.dumps({'attempts':len(attempts),'phaseAttempts':dict(phase_counts),'models':{m:len(v) for m,v in by_model.items()},'pairs':dict(Counter(p['outcome'] for p in pairs)),'stop':campaign['stopReason']},indent=2))


if __name__ == '__main__':
    main()
