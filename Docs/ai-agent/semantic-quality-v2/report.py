"""Render the requested batch reports from actual evidence. No provider access."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]; OUT=Path(__file__).resolve().parent; DOCS=OUT.parent
def read(name):return json.loads((OUT/name).read_text(encoding='utf-8-sig'))
def dump(x):return json.dumps(x,ensure_ascii=False,indent=2)
def save(name,text):(DOCS/name).write_text(text.strip()+'\n',encoding='utf-8')
comparison=read('comparison-v3.json'); frozen=read('frozen40-observations-v2.json');fresh=read('fresh-observations-v2.json')
b=frozen['metrics'];g=fresh['metrics'];budget=read('budget-accounting-v2.json'); pairs=read('paired-comparisons-v2.json')
segments=read('segments-v2.json');failures=read('remaining-failures-v3.json');verification=read('verification-v2.json')
complete=b['attempted']==40 and g['attempted']==20
primary=all(r.get('actualModel')=='gemini-3.5-flash-lite' and r.get('httpStatus')==200 for r in frozen['observations']+fresh['observations'])
critical={'VNE1-003','VNE1-004','VNE1-007','VNE1-013','VNE1-033','VNE1-049'}
critical_ok=all(r.get('semanticPass') and r.get('backendAccepted') for r in frozen['observations'] if r['sampleId'] in critical)
targets=complete and primary and b['rawSemanticSchemaValid']==40 and all(b[k]>=38 for k in ('schemaValid','exactBusinessSemantics','actionCorrect','intentCorrect','criteriaCorrect','missingSlotsCorrect')) and b['referencePriceCorrect']==3 and not b['spuriousClearIds'] and b['incorrectAccepted']==0 and g['exactBusinessSemantics']>=19 and g['incorrectAccepted']==0 and critical_ok
technical_stop=any(s.get('stopReason')=='UNKNOWN_IN_FLIGHT' for s in segments)
status='LIVE_TECHNICAL_BLOCKED' if technical_stop else 'SEMANTIC_QUALITY_TARGET_MET' if targets else 'SEMANTIC_QUALITY_EVALUATION_PARTIAL' if not complete or not primary else 'SEMANTIC_QUALITY_IMPROVED_TARGET_NOT_MET' if b['exactBusinessSemantics']>22 or b['correctAccepted']>18 else 'SEMANTIC_QUALITY_EVALUATION_DONE_NO_MATERIAL_IMPROVEMENT'
def table():
    metrics=[('HTTP200','http200'),('Raw schema valid','rawSemanticSchemaValid'),('Raw exact meaning','rawSemanticCorrect'),('Canonical typed valid','schemaValid'),('Canonical action','actionCorrect'),('Canonical intent','intentCorrect'),('Canonical criteria','criteriaCorrect'),('Business missing slots','missingSlotsCorrect'),('Exact business semantics','exactBusinessSemantics'),('Correct accepted','correctAccepted'),('Correct rejected','correctRejected'),('Incorrect accepted','incorrectAccepted'),('Incorrect rejected','incorrectRejected'),('Backend NOT_EVALUATED','backendNotEvaluated'),('Compiler rejections','compilerErrors')]
    text='|Metric|A v3 frozen /40|B v1 frozen /40|B v1 backend replay /40|B v2 frozen /40|B v2 fresh /20|\n|---|---:|---:|---:|---:|---:|\n'
    replay=comparison['B1BackendReplay']; aliases={'rawSemanticSchemaValid':'rawSchemaValid','schemaValid':'canonicalTypedValid','compilerErrors':'compilerRejections'}
    for label,key in metrics:
        a='N/A' if key in ('rawSemanticCorrect','compilerErrors') else comparison['A3Frozen'].get('schemaValid') if key=='rawSemanticSchemaValid' else comparison['A3Frozen'].get(key,'N/A');v1=comparison['B1Frozen'].get(key,'N/A')
        rr=replay.get(aliases.get(key,key),'unchanged model' if key.startswith('raw') else 'N/A')
        text+=f"|{label}|{a}|{v1}|{rr}|{b.get(key) if b['attempted'] else 'NOT_RUN'}|{g.get(key) if g['attempted'] else 'NOT_RUN'}|\n"
    return text
limits='Concurrency1; aggregate≤120; global≤12/rolling60s; spacing≥5s; conservative17408 reserved tokens/attempt and≤200000/rolling60s; input16384/output1024; configured per-call deadline10s; zero automatic same-model retry; campaign≤60min from first dispatch. The timeout attempt elapsed10021ms including SDK/cleanup and logical12056ms; this is not proof that every measured elapsed duration was≤10s. Quota/credential/safety/unknown-in-flight failures stop. Capacity-only fallback retains every failed attempt and requires terminated503/UNAVAILABLE plus the existing allowlisted overload body and≥15s cooldown.'
boundary='Ordinary runtime remains OFF/FAKE/liveOFF; v2 is not promoted. No domain/Gateway/Supabase/payment/refund calls, WRITE tools, database/schema/frontend/dependency changes, commits/staging/push/reset/checkout. Recognition is evaluated; trusted target resolution, tool execution and FULL_AGENT_E2E are NOT_RUN. D07/D08 remain unaccepted; E30 remains NOT_RUN without authenticated ordered ingress. No production readiness claim.'
recommendation='Retain B v2 as a local development candidate. Do not promote or begin context-aware runtime implementation while critical single-utterance failures remain. Next work should target the actual remaining semantic failures below, particularly full explicit-fact retention or action consistency when observed. The flat optional contract still permits contradictory combinations; consider a small action-specific serving representation if failures persist, rather than expanding the independent grammar without bound. No authorization/infrastructure batch is recommended.' if not targets else 'The observed development targets are met; retain ordinary runtime settings and propose BATCH 10B.4E — CONTEXT-AWARE VIETNAMESE NLU as a separate future batch with trusted conversation revisions and target authority. This is development acceptance only.'
pairtext='\n'.join(f'- {k}: {len(v)} — '+', '.join(v) for k,v in pairs.items())
main=f'''# Batch10B4D-QUALITY semantic consistency and Vietnamese NLU

24/11/2026, Asia/Ho_Chi_Minh. **{status}**.

Baseline: 1080 tests PASS; final {verification['final']['tests']} tests PASS, failures0/errors0/skipped0. New actual Gemini HTTP attempts conservatively charged **{budget['actualHttpAttempts']}/120**, per-model {dump(budget['modelAttempts'])}. **Frozen40 and fresh20 are NOT_RUN** after critical technical stop at ATT-015. No new primary/fresh accuracy can be claimed. The completed first10 diagnostics gave8 exact/5 admitted; the later revision attempted5/10 (2 exact/2 admitted,2 compiler rejections,1 timeout), retaining the other5 as NOT_RUN. Offline B v1 replay fixes four backend false rejections. Raw schema validity is reported separately and is not semantic accuracy.

ATT-015 had status0, TIMEOUT, unknown usage and no retained response/termination proof. The existing policy sealed `segment-01-diagnostic-r2.json` as UNKNOWN_IN_FLIGHT. No fallback, retry, new campaign directory, primary40/fresh20 dispatch or budget reset followed. All15 ledger attempts retain full reservations, even though provider receipt of the last request cannot be verified. A local wrapper could discard proven termination metadata when status0 throws; a post-stop offline fix preserves that metadata for future explicitly authorized work without inferring termination. It does not relabel or reopen this sealed campaign. A regression asserts unknown-stopped campaigns cannot continue or read credentials. Live compatibility of this post-stop metadata fix is NOT_RUN. The final offline audit also preserves original fixture category/split metadata in the frozen plan builder; utterances, oracle expectations and wire meaning are unchanged, and old journals are not rewritten.

## Baseline and preservation

Backend `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`; frontend clean. Root AGENTS applies; no nested guide was found. Existing tracked/untracked service, test, report and IDE work was preserved, with [initial status](semantic-quality-v2/preexisting-status.txt), [source snapshot](semantic-quality-v2/source-before-v1.json) and [historical artifact hashes](semantic-quality-v2/historical-preservation-before.json). No Git mutation was performed. Actual source matched the reported baseline; dated README sections describe older separate signed paths, while current local evaluation uses explicit authorization. No new signature/allocation infrastructure was added.

Read previous final/corroboration/fallback/A-B reports, frozen contracts, actual retained envelopes and observations, current local runner/compiler/parser/routing/scoring/transport/SDK tests and original dataset/oracle. VEX1/VNE1/historical/oracle hashes remain unchanged; [final preservation](semantic-quality-v2/preserved-hashes-final-v3.json). Original provider failures and campaign records were never replaced by replays/retests.

## Actual replay and root causes

Before implementation, all40 retained B envelopes were replayed through envelope validation, the actual SDK with an in-memory exchange, strict raw schema, compiler, canonical parser and independent corroboration at the exact original B clock `2026-11-24T10:18:48.242851600Z`. It reproduced40 raw-valid,26 typed-valid,22 exact,18 correct accepted,4 correct rejected,4 incorrect rejected and14 compiler rejections. [Before](semantic-quality-v2/b1-replay-before-v1.json), [after](semantic-quality-v2/b1-replay-after-v2.json).

The14 were contradictory/incomplete model proposals, primarily H1/H2, not14 confirmed compiler defects. Strict rejection remains. Discovery wording caused H5 false rejections016/041/042/048; generalized bounded rules fixed all four. The same B v1 model output now has22 correct accepted,0 correct rejected,0 incorrect accepted,4 incorrect rejected; model semantics stay22/40. Label spelling/casing remains wrong under frozen B v1 exact scoring; v2 adds finite reviewed normalization without retroactive credit.

Historical20 typed-valid+3 invalid replay retains7/0/0/13 and3 strict rejections. Remaining A, old diagnostics, supplemental responses and actual2.5 fallback/probe envelopes were also replayed; [replay files](semantic-quality-v2/). Non200 attempts without a usable envelope are explicitly not replayable, never fabricated. No provider/key/domain call was used for offline replay.

## Implementation and evaluation freeze

V2 uses one structured call and one flat semantic proposal. Intent-first compatibility rules prohibit unrelated references, SEARCH+issues, CLARIFY+criteria, invented hotel names/default budgets, implicit occupancy and arbitrary removals. Java derives missing business slots and strict reference-price fields; it never adds missing user facts. Schema changes are descriptive field annotations only; original strict structural constraints remain. Model-specific serving adaptation/fallback order and ordinary defaults are unchanged.

Exact reviewed labels support casing/NFC/bounded whitespace and reviewed accentless aliases. Unreviewed accented spellings, unknown places, conflicts and wrong literal destinations reject. Discovery wording is generalized by bounded clauses and edge politeness, with unconsumed suffix and unsupported-filter checks retained. Selected/reference recognition does not grant target IDs/state or tool authority.

First diagnostic revision:10 actual attempts,8 exact,5 accepted; two full-stay failures and three correct clarifications rejected. Evidence justified one pre-freeze revision: field descriptions, one separate synthetic full-stay example, explicit generic-hotel/default-price prohibition, and candidate-only independently corroborated invalid-input clarification. An actual response exposed `VND mỗi phòng mỗi đêm` grammar coverage; the bounded unit clause now parses, while unknown currency/suffix tests reject. The same retained diagnostic responses replay8 exact/8 accepted, with only009/010/040 admission changes; no wrong proposal is repaired. The second revision planned10 diagnostics but attempted5 before critical stop; all attempted and NOT_RUN observations are retained separately with revision hashes and controls. See [diagnostic cohorts](semantic-quality-v2/diagnostic-revision-metrics-v2.json).

Final candidate prompt/schema/backend/source/wire hashes and planned order are captured in an offline freeze after controlled stop: [plan](semantic-quality-v2/frozen-plan-final-v3.json), [source](semantic-quality-v2/source-frozen-final-v3.json). This is not evidence of a frozen40 dispatch: all40 and fresh20 remain NOT_RUN. Fresh20 expectations were authored before the first new response and use the same pinned clock/candidate in the plan. They are **FRESH_DEVELOPMENT_GENERALIZATION**, not a blinded or external holdout; no fresh utterance was placed in prompt examples. Optional later candidate/retest was not run; no best-of-attempt substitution.

## Results and comparison

{table()}

Same-model/same-identity/original-oracle B v1→B v2 exact semantics:

{pairtext}

All provider/compiler failures remain in attempted denominators. Missing business slots are Java-derived, not raw-model predictions. Fixed-model cohorts and fallback availability are separate in [model metrics](semantic-quality-v2/model-specific-metrics-v2.json). See [detailed comparison](VIETNAMESE_NLU_CONTRACT_B_QUALITY_COMPARISON_V3.md).

## Budget, fallback and performance

{limits}

Actual phase/model/usage accounting and dispatch timing: [budget](semantic-quality-v2/budget-accounting-v2.json). Known input {budget['knownInputTokens']}, candidate output {budget['knownCandidateOutputTokens']}, total {budget['knownTotalTokens']}; unknown usage attempts {budget['unknownUsageAttempts']}; thinking {budget['reportedThinkingTokens']}. Full conservative reservation {budget['reservedTotalTokens']}, never refunded. Global observed rolling max {budget['maximumRollingRequests']} requests/{budget['maximumRollingReservedTokens']} reserved tokens; minimum spacing {budget['minimumDispatchSpacingSeconds']}s; first-to-last dispatch {budget['firstToLastDispatchSeconds']}s. Earliest deadline/counters imported across segments. Previous same-day retained usage98 (91/1/5/1 by model) was accounted for in local RPD headroom; this is not account-wide quota proof or a zero-cost guarantee. Quota failures cannot trigger fallback.

[Fallback metrics](semantic-quality-v2/fallback-metrics-v2.json) distinguish additional attempts, cooldown, technical and correct semantic recovery. No capacity failure was deliberately induced; unused fallback reserve is not a claim of newly verified fallback quality. Attempt latency includes SDK/validation; provider-only latency is NOT_SEPARATELY_RECORDED. Mean/median/p95/max, logical latency and known token usage are in each cohort's metrics; absent thinking is NOT_REPORTED, not zero.

## Offline checks, safety and limits

Executed required `mvn -o -B -ntp -pl ai-agent-service package` at baseline, pre-live and final. Final{verification['final']['tests']} tests, +{verification['final']['tests']-1080}; no failed/error/skipped checks in the final run. Focused/generalized tests cover compatibility, exact facts/omissions/additions, dates/occupancy, normalization, selected references, unknown suffixes, explicit units, removals, strict prices, unsafe fields and exact SDK wires. Actual recorded B40 and diagnostic responses are retained regression fixtures. Tests use synthetic/mock/loopback transport and never read a real API key or call Gemini. [Verification](semantic-quality-v2/verification-v2.json) lists intermediate failures separately; existing assertions were not weakened to hide them. The old041 rejection test now has a positive acceptance assertion and its separate classification test retains an explicitly test-owned invalid-calendar expectation; frozen business oracle files are unchanged.

{boundary}

No live evaluation process remains. Credential scan results, source/dependency/scope integrity, historical hashes and final changed/new file inventory are in [verification](semantic-quality-v2/verification-v2.json) and [artifact hashes](semantic-quality-v2/artifact-hashes-v2.json). Raw sanitized synthetic provider envelopes remain under gitignored `ai-agent-service/.local/semantic-quality-v2/responses/`, with attempt identity/provenance in exported segments. No real hotel/customer data was sent.

## Quality targets and next batch

Primary/fresh quality targets: **NOT_ASSESSED**, because40+20 planned samples were not dispatched. Critical regressions003/004 remain failures in both diagnostic revisions; no broad semantic improvement claim is made. Offline backend improvement is verified independently. Remaining defects and their precise proposals/stages are in [failure analysis](VIETNAMESE_NLU_REMAINING_FAILURE_ANALYSIS_V3.md).

{recommendation}
'''
save('BATCH10B4D_QUALITY_SEMANTIC_CONSISTENCY_AND_NLU_IMPROVEMENT_REPORT.md',main)
matrix='''|Semantic intent|Canonical action/intent|Criteria/remove|Issues|Reference|Missing business information|
|---|---|---|---|---|---|
|SEARCH/BROWSE|CRITERIA, same intent|All explicit supported facts; no inferred/default facts|Empty/absent|Absent|Java derives missing prerequisites for stays; undated discovery needs none|
|REFINE|CRITERIA/REFINE|Explicit replacements/keeps/removals only, no conflicting operations|Empty/absent|Absent|No invented conversation state|
|CLARIFY|CLARIFY/CLARIFY|Empty|Nonempty grounded issue; no contradiction repair|Absent|Issue mapping independently corroborated|
|SELECTED/COMPARISON|Same action/CLARIFY|Empty|Empty/absent|Exact whole original utterance|Recognition only; trusted target/tool execution NOT_RUN|
|UNSUPPORTED/UNVERIFIABLE|CLARIFY, same intent|Empty|No mutation|Absent|No factual assertion/tool authority; selected intent is not unverifiable solely for missing state|'''
detail=''
for r in read('original-compiler-rejections-v1.json'):
    detail+=f"\n### {r['sampleId']}\n\nUtterance: {r['utterance']}\n\nExpected:\n```json\n{dump(r['expected'])}\n```\nActual raw response:\n```json\n{r['actualRaw']}\n```\n\nRule: {r['compilerRule']}. Original exact stage: `{r['exactRejectionStage']}`. Root cause H1/H2 (model contradiction/omission and insufficiently effective instruction); no confirmed H4 compiler defect. Intent, criteria, issues and reference are preserved verbatim in the JSON above. Proposed fix: {r['proposedFix']}. Regression risk: {r['regressionRisk']}.\n"
save('VIETNAMESE_SEMANTIC_CONSISTENCY_FIX_V2.md',f'''# Vietnamese semantic consistency fix v2

24/11/2026. {status}. This report separates model meaning, canonical compilation and independent backend admission.

## Compatibility and design

{matrix}

No new action-classification call, redundant action field, tool/schema framework or authority abstraction was introduced. V1/A contracts and observations remain frozen. V2 retains the flat representation with descriptive schema annotations; strict validation is authoritative and provider keywords are not assumed enforced. Original contradictory proposals still reject. Normalization is finite reviewed equivalence, followed by independent full field/op/value/intent/missing-slot checks. Derived missing slots and strict-price flag are not model-success credit. Generic people never become adults; absent children/rooms never become0/1.

Diagnostic revision1 and final revision hashes are distinct in [snapshots](semantic-quality-v2/). The single full-stay prompt example is separately authored synthetic text, not a frozen/fresh utterance or oracle/sample-ID lookup. No model-proposed IDs/USER_CONFIRMED/provenance can pass schema or authorize state. No date/calendar/range repair occurs.

Four natural-discovery false rejections were fixed with generalized clauses, retaining all unsupported/unconsumed constraints. Unknown-currency and unknown-suffix tests protect unit/politeness normalization. Candidate-only invalid-input corroboration derives invalid facts from the independent parser, admits only exact empty clarification and never mutates state; original signed/ordinary compatibility remains intact. Historical7/0/0/13 and strict-invalid3 are preserved. Actual B40 replay becomes22/0/0/4 with14 compiler failures unchanged; this is backend improvement only.

Date/occupancy omission/addition tests, missing endpoints/children/rooms/destination, invalid calendar/order, explicit zero, generic/conflicting guests, selected/ordinal references, canonical/case/NFC/no-accent labels and unreviewed typo/unknown/conflicting labels are covered in `VietnameseSemanticQualityTest`. SDK capture tests verify all four exact model endpoints and wire serialization against the local schema. See [verification](semantic-quality-v2/verification-v2.json).

## All14 original compiler rejections

Original B40 retained actual envelopes, clock and expectations were used; no provider call or fabricated response. The initial replay exactly reproduced the baseline. See [machine root causes](semantic-quality-v2/original-compiler-rejections-v1.json) for full action/intent/criteria/issues/reference/stage/risk fields. H3/H6/H7/H8 were considered but do not explain these observed raw contradictions; no oracle change or compiler output repair is justified.
{detail}

{boundary}
''')
save('VIETNAMESE_NLU_CONTRACT_B_QUALITY_COMPARISON_V3.md',f'''# Vietnamese NLU contract B quality comparison v3

24/11/2026. **{status}**. Main comparison: B v1→B v2. A v3 is a frozen comparator; no new A40 campaign was run.

{table()}

Raw action/intent/criteria/exact meaning, date/occupancy retention, destination, price operator/value, reference/removal/unsupported recognition, schema/compiler, derived missing slots and backend confusion matrices are independently recorded in [frozen40](semantic-quality-v2/frozen40-observations-v2.json) and [fresh20](semantic-quality-v2/fresh-observations-v2.json). The raw scorer is the unchanged original strict oracle translation; it does not call the compiler to award model facts. A value normalized by Java is canonical credit, not retroactive raw spelling credit. Missing-slot accuracy is a Java/compiler dimension for B. No normalized-equivalence score replaces frozen primary scoring.

## Paired outcomes

{pairtext}

Only same actual model/sample/oracle and successful provider observations are comparable. Failed provider attempts remain failures/NOT_COMPARABLE rather than being replaced by fallback or a better retest. Primary and fallback quality cohorts remain separate; see [per-model metrics](semantic-quality-v2/model-specific-metrics-v2.json). Diagnostics revisions are developmental overlapping subsets, not extra independent primary samples. Optional later candidate targeted retest: NOT_RUN.

## Backend decisions and denominators

B v2 frozen backend: {dump({k:b[k] for k in ('correctAccepted','correctRejected','incorrectAccepted','incorrectRejected','backendNotEvaluated','falseAcceptanceRate','falseRejectionRate','backendClassificationAccuracy','correctAdmissionPrecision','endToEndCorrectAdmission')})}.

Fresh backend: {dump({k:g[k] for k in ('correctAccepted','correctRejected','incorrectAccepted','incorrectRejected','backendNotEvaluated','falseAcceptanceRate','falseRejectionRate','backendClassificationAccuracy','correctAdmissionPrecision','endToEndCorrectAdmission')})}.

Here correct/incorrect labels describe proposal semantics; “correct rejected” is a false rejection, while “incorrect rejected” is a correct backend decision. Classification accuracy=(correct accepted+incorrect rejected)/typed evaluated. Admission precision=correct accepted/all accepted; end-to-end correct admission=correct accepted/all attempted. Compiler/provider failures remain in overall attempted denominators and separately NOT_EVALUATED for backend classification. Rates with zero applicable denominator are null/N/A, never fabricated zero success.

## Performance, fallback and targets

Frozen attempt latency: {dump(b['attemptLatencyMillis'])}; fresh: {dump(g['attemptLatencyMillis'])}. Logical distributions and mean known tokens are in the same machine metrics. Provider-only latency is not separately recorded. Thinking absence is NOT_REPORTED. [Budget/usage](semantic-quality-v2/budget-accounting-v2.json), [fallback](semantic-quality-v2/fallback-metrics-v2.json). Baseline B v1 median1175.5ms and mean known total478.35 tokens/request are historical descriptive values, not a controlled latency experiment. New prompt/schema size and run timing differ.

Targets: frozen raw40/40, typed/action/intent/criteria/missing/exact≥38/40, reference-price3/3, spurious CLEAR0, incorrect/unsafe/confirmation acceptance0; fresh exact≥19/20. Primary/fresh target assessment: **NOT_ASSESSED** after controlled stop. These are development targets, never grounds for oracle changes or dropping failed attempts. The20 fresh samples were frozen before any new response, use the same candidate/clock and are FRESH_DEVELOPMENT_GENERALIZATION, not external/blinded evaluation.

{recommendation}

{boundary}
''')
failuretext=''
for r in failures:
    if r['phase'] not in ('FROZEN40','FRESH') and (complete or r.get('segment')!=segments[-1]['segment']):continue
    stage='PROVIDER' if r.get('httpStatus')!=200 else 'RAW_SCHEMA' if not r.get('semanticSchemaValid') else 'COMPILER' if r.get('compilerResult')=='FAIL' else 'MODEL_SEMANTICS' if not r.get('semanticPass') else 'INDEPENDENT_CORROBORATION'
    cause='H8' if stage=='PROVIDER' else 'H5' if stage=='INDEPENDENT_CORROBORATION' else 'H1/H2 (H3 representation risk when contradictions repeat)'
    specifics={
        'DIAG-VNE1-003':'The model copied an ordinary stay request into reference and omitted both explicit dates plus adults/children/rooms. The compiler correctly rejects the unrelated reference. Both diagnostic revisions fail despite explicit retention instructions; the added full-stay example did not establish improvement.',
        'DIAG-VNE1-004':'The model invented Hà Nội and a strict nightly budget absent from the utterance, omitted explicit adults/children and retained only dates/rooms. Unsupported STAY LT is correctly rejected. This is uncorroborated inference, not a compiler defect; no invented value was admitted.',
        'DIAG-VNE1-010':'No response exists for ATT-015. Status0, TIMEOUT and missing termination evidence caused the required UNKNOWN_IN_FLIGHT stop. Do not infer a model answer or retry. A post-stop wrapper metadata fix is offline-tested only and does not reopen this campaign.'
    }
    cause += '. '+specifics.get(r['sampleId'],'Assess the exact raw/canonical difference shown below; no output repair or oracle adjustment is authorized')
    context=r['expectedExtraction']['action'] in ('SELECTED','COMPARISON')
    failuretext+=f"\n## {r['sampleId']} ({r['phase']}, {r.get('segment')}, {r.get('actualModel')})\n\nUtterance: {r['utterance']}\n\nExpected:\n```json\n{dump(r['expectedExtraction'])}\n```\nActual raw proposal:\n```json\n{r.get('actualExtractionText','NO_RETAINED_EXTRACTION')}\n```\nCanonical proposal:\n```json\n{dump(r.get('compiledExtraction'))}\n```\n\nFailure stage: {stage}; root cause {cause}. Exact pipeline: `{r.get('pipeline')}`. Backend accepted: {r.get('backendAccepted',False)}. Safety implication: no execution authority is granted; missing/altered facts or ungrounded mutations must continue to reject. Next action: analyze this observed semantic/coverage defect with generalized offline controls before any new bounded retest; do not correct the model response or change the oracle. Context required: {'trusted target resolution requires future context, recognition itself does not' if context else 'no trusted context required for these literal single-utterance facts'}.\n"
save('VIETNAMESE_NLU_REMAINING_FAILURE_ANALYSIS_V3.md',f'''# Vietnamese NLU remaining failure analysis v3

24/11/2026. **{status}**. Frozen40/fresh20 are NOT_RUN. The latest attempted diagnostic failures are listed below with actual retained text and unchanged expected semantics. Compiler failures remain failures; no retest replaces them. Diagnostic failures are separately retained in [diagnostic observations](semantic-quality-v2/diagnostic-observations-v2.json) and [revision metrics](semantic-quality-v2/diagnostic-revision-metrics-v2.json).

{failuretext or 'No attempted frozen/fresh semantic or admission failures were observed. This finite development result does not establish general correctness.'}

## Remaining scope and recommendation

{recommendation}

{boundary}
''')
(OUT/'acceptance-v2.json').write_text(dump(dict(status=status,targetsMet=targets,criticalRegressionsFixed=critical_ok,primaryComplete=primary and complete,recommendation=recommendation))+'\n',encoding='utf-8')
print(status)
