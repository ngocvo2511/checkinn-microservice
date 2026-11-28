"""Write this batch's reports from its retained synthetic evidence; no provider access."""
import json
import pathlib

OUT=pathlib.Path(__file__).resolve().parent
DOCS=OUT.parent
def read(name): return json.loads((OUT/name).read_text(encoding='utf-8-sig'))
def write(name,text): (DOCS/name).write_text(text.strip()+'\n',encoding='utf-8')
budget=read('budget-accounting-v1.json')
phases=read('phase-metrics-v1.json')
models=read('model-specific-metrics-v1.json')
failures=read('remaining-failures-v1.json')

write('BATCH10B4D_EXTRACTION_EVIDENCE_FIRST_AND_MODEL_QUALITY_REPORT.md',f'''
# Batch 10B.4D-EXTRACTION: evidence-first and model quality

24/11/2026, Asia/Ho_Chi_Minh. Final status: **EVIDENCE_FIRST_EVALUATION_PARTIAL**. Architecture decision: **MORE_EVIDENCE_REQUIRED**.

## Executive summary

Implemented local Contract C, strict source membership/type/completeness verification, deterministic canonical compilation and a parser-only comparator. Fixed timeout category propagation while preserving the earlier termination-metadata fix. The baseline and final offline builds pass. Actual new Gemini attempts: **21/120** (19 Gemini3.5 Lite,2 Gemini3.1 Lite). **17 HTTP200,1 HTTP503 High Demand,3 locally terminated timeouts;4 UNKNOWN usages**. All21 attempts retained full reservations. No fallback or retry occurred.

The fixed-model comparison stopped on **two consecutive logical timeouts**, ATT-020/021. Cumulative actual transient failures were4, below the separate5-failure ceiling. The mandatory stop is sealed; no further dispatch, credential lookup, directory restart or budget reset followed. **Frozen40:0/40 NOT_RUN; fresh20:0/20 NOT_RUN.** No quality-target acceptance or complete four-model comparison is claimed.

## Baseline and preservation

Verified backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Root is not a Git repository; work was confined to backend ai-agent-service and Docs/ai-agent. No nested AGENTS.md was found. Existing dirty/untracked source, tests, historical reports/fixtures and IDE modifications were preserved. No stage, commit, push, stash, checkout, reset or clean.

Required command, executed before changes and after evaluation: `mvn -o -B -ntp -pl ai-agent-service package`. Baseline **1134**; final **1235**; **101 new test cases**; failures0/errors0/skipped0; BUILD SUCCESS. Historical E01–E40 remains **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Maven checks were offline, using sentinels/fakes/loopback only. [Final log](evidence-extraction-v1/final-package.log), [verification](evidence-extraction-v1/verification-v1.json).

Development failures were retained in the work transcript: an inaccessible package-private number helper at compilation, a negated-discovery request classification check, and a cold-start loopback test whose400ms deadline expired before dispatch. The helper call was removed in favor of the existing parser result; discovery meaning was separated from refinement policy; the loopback test now admits startup time while still proving socket closure. No existing assertion was weakened. Later full-package runs passed1224,1234 and1235 cases; final candidate/full verification both passed1235.

All original VEX1/VNE1/historical/oracle hashes and the original fresh20 hash `6cf41670b74d44f52d27beb8be5519e5911d11fe0ab445801308e59edcf3e42a` remain unchanged. [Preserved hashes](evidence-extraction-v1/preserved-frozen-hashes-prelive-v1.json). Fresh20 was reused from the prior pre-response dataset; no fresh response was obtained, no fresh utterance became a prompt example, and no business oracle was changed.

Two discrepancies were verified rather than copied forward: README's top “current” checkpoint was older than the actual1134-test implementation; the old quality runner's91 primary daily-attempt offset did not include the subsequent15-attempt campaign. The retained local journals give prior counts106/1/5/1; these are not account-wide complete usage. The new campaign uses those conservative known daily counts and a separate new120-attempt allowance, never the old unused105.

## Timeout/cancellation

ATT-015 from the previous campaign remains sealed UNKNOWN_IN_FLIGHT with its original hash. The post-stop wrapper already preserved a returned transport's termination boolean. A remaining category defect discarded the caught ProviderException category and converted a no-response terminated timeout to generic UNAVAILABLE. Reply now carries the safe typed failure, and the runner propagates it without inferring termination.

New loopback/mock checks cover timeout before dispatch, timeout after dispatch, completed cancellation/socket EOF, unknown termination blocking the next dispatch/reopen/key lookup, full unknown-usage reservations and cooldown. Existing transport/fallback/failure-policy cases remain passing. The local path is synchronous: transport return follows response/client/pool closure; SDK retries0 and HTTP retries disabled. No Future.cancel/interrupt-only termination claim is used.

Live ATT-017,020,021 naturally returned status0/TIMEOUT with callTerminated=true. ATT-017 safely advanced to a distinct sample after15s; ATT-020 advanced once; ATT-021 triggered the threshold. **The local timeout-metadata/continuation path was observed live**, beyond the initial successful technical smoke. Remote inference termination and remote concurrency remain unverified. [Focused timeout report](GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md).

## Contract C and implementation

One model call proposes request meaning and typed exact-substring facts. Java verifies membership, complete typed witnesses, role/operator/unit/negation, duplicates/overlap and unconsumed constraints before any canonical compilation. Missing facts are rejected, never recovered and counted as model success. Price exclusivity, normalized values, missing slots and business action/intent are Java credit. [Contract specification](VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md).

The compiler deliberately gates the existing deterministic original-utterance parser with complete evidence verification. It is not a general semantic normalizer beyond that parser's coverage. Existing canonical parsing and quality corroboration remain authoritative. A/B code paths, original prompts/schemas and signed/ordinary compatibility remain intact. The new CLI reuses existing SDK serialization, profiles, transport, fallback/accounting and limiter. No framework, dependency, runtime route or database change was added.

Revision1 diagnostics:8/8 HTTP200 and raw-schema valid;7/8 complete/exact/correctly admitted. Precision19/21 evidence facts; recall19/20 independently expected witnesses; request meaning8/8. VNE1-003/004/007 retained all dates/explicit occupancy;004 invented no destination or budget. One selected-room proposal split “phòng này” into ROOMS “phòng” and REFERENCE “này”; it was correctly rejected before canonical admission. The compatibility category EXTRACTION_SCHEMA_INVALID here denotes the extraction boundary, **not invalid raw JSON schema**.

Revision2 added only a target-noun/reference clarification and explicit numeric ROOMS requirement to the prompt. The validator was not relaxed. Two separately labeled checks passed, including “phòng đã chọn”. Revision1's failure remains in its own cohort; it was not replaced. Revision2 source/prompt/schema/wires and primary development model were frozen before comparison. No candidate tuning occurred afterward. [Revision1](evidence-extraction-v1/diagnostic-r1-observations-v1.json), [revision2](evidence-extraction-v1/diagnostic-r2-observations-v1.json), [frozen candidate](evidence-extraction-v1/frozen-candidate-v1.json).

## Offline replay and comparator

- Historical20 typed-valid actual responses:7 correct accepted /0 correct rejected /0 incorrect accepted /13 incorrect rejected; three typed-invalid actual proposals still strictly reject. Original missing provider responses were not fabricated.
- B v1 actual40 response replay:40 raw-schema valid,26 typed-valid,22 exact,22 correct accepted,0 correct rejected,0 incorrect accepted,4 incorrect rejected,14 compiler rejections. Previously fixed016/041/042/048 admissions remain passing.
- B v2 diagnostic revision1 recorded-response regression:8 exact/8 correct admissions,0 wrong admissions. Prior revision2 remains a separate5-attempt diagnostic cohort, not a frozen score.
- Parser-only offline comparison:40/40 frozen and20/20 fresh exact canonical outcomes using the existing supported grammar. This is **not Gemini or Contract C model accuracy**. The present data demonstrates no additional case coverage from adding the LLM to that deterministic path.

[Historical replay](evidence-extraction-v1/historical-actual-response-replay-v1.json), [B1 replay](evidence-extraction-v1/b1-actual-response-replay-v1.json), [parser comparator](evidence-extraction-v1/parser-only-comparator-v1.json).

## Controlled model comparison and unavailable cohorts

Balanced plan:8 shared identities ×4 exact model profiles, fixed-model/no fallback. Actual comparison:10/32 attempts. Gemini3.5 Lite:6 exact/correct admissions from8 attempts; the other2 were503/timeout, with semantic correctness NOT_EVALUATED. All6 received proposals were correct; that conditional observation does not replace the6/8 overall attempted result. Gemini3.1 Lite:2 timeouts,0 usable responses,6 NOT_RUN; intrinsic model quality cannot be scored. Both2.5 models:8 NOT_RUN each. No model winner, model change or statistical superiority is established. [Model report](GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md).

Frozen40 and fresh20 have complete immutable plans and machine-readable NOT_RUN observations, including original utterances/oracles/exclusions/clock. All40 B1/C pairs are NOT_COMPARABLE. Applicable95%/98%/reference-price targets remain **NOT_ASSESSED**, not zero-quality estimates or passes. [Frozen40](evidence-extraction-v1/frozen40-observations-v1.json), [fresh20](evidence-extraction-v1/fresh20-observations-v1.json), [comparison](VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md).

## Budget, performance and safety

Known input6787 + candidate output1222 = known total8009 tokens.4 usages UNKNOWN; thinking **NOT_REPORTED** for all21 attempts. Full reservations344064 input +21504 output =**365568**, including69632 retained for the four unknown-usage attempts. No refund or zero-cost guarantee.

Recorded dispatches19:24:01.084–19:34:43.483 local time,24/11/2026:642.400 seconds between first/last, within the60-minute aggregate deadline. Minimum journaled dispatch spacing5.002857s; maximum8/rolling60s and139264 reserved tokens/rolling60s. Model maxima8 RPM primary and2 RPM3.1 are below their15 RPM snapshots. Known same-day totals125/3/5/1 are local evidence only. Single local synchronous dispatch/exchange; remote inference concurrency not verified.

Attempt latency mean4822.48ms, median2513ms, p9510007ms, max10008ms. Configured10s deadlines do not imply wall latency≤10s: the observed maximum exceeds that by8ms; provider-only and SDK/cleanup components were not separately instrumented. Global limiter wait23537ms; model-specific waiting NOT_SEPARATELY_RECORDED. Three continuation cooldowns totaled45s; fallback cooldown0. [Accounting/performance](evidence-extraction-v1/budget-accounting-v1.json).

Observed incorrect canonical acceptance0; invented evidence acceptance0; unsafe inference/fabricated confirmation/unauthorized tools0. Upstream provider/evidence/compiler failures remain in overall attempted denominators and backend NOT_EVALUATED, not silently discarded. No Gateway/domain/Supabase/payment/refund/WRITE call, tool execution or state mutation occurred. No credential/header/JWT/cookie/real hotel/customer data was added to reports/source. Final scans and source/hash/process checks are in verification. Ordinary runtime remains OFF/FAKE/liveOFF; no primary model or dependencies changed.

## Architecture decision and limitations

**MORE_EVIDENCE_REQUIRED**. Evidence-first has a useful diagnostic retention signal, but frozen40/fresh20 and a balanced four-model comparison were not completed. Do not promote C or select a different model from these observations. Existing deterministic grammar already matches all60 development business outcomes; incremental LLM coverage is unproven. A future deterministic-first/hybrid responsibility review should require examples where an LLM adds value beyond that parser, rather than growing prompts/rules indefinitely. This is a proposal, not a runtime migration.

Do not start10B.4E context-aware NLU from this partial acceptance. D07/D08 remain unaccepted; E30 and FULL_AGENT_E2E remain NOT_RUN. Selected/refinement/comparison work here is recognition only; trusted context/target resolution and tools remain NOT_RUN. No production readiness claim.

All deliverables and failure-specific next actions: [contract](VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md), [model comparison](GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md), [contract comparison](VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md), [timeouts](GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md), [remaining failures](VIETNAMESE_NLU_EVIDENCE_FIRST_FAILURE_ANALYSIS_V1.md), [artifact hashes](evidence-extraction-v1/artifact-hashes-v1.json).
''')

model_table='\n'.join(f"|{name}|{m['planned']}|{m['attempted']}|{m['primaryHttp200']}|{m['rawExact']}|{m['exactBusinessSemantics']}|{m['backendConfusion']['correctAccepted']}|{m['notRun']}|" for name,m in models.items())
write('GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md',f'''
# Gemini Vietnamese NLU model comparison v1

24/11/2026. **PARTIAL; NO MODEL WINNER**. Frozen candidate: `vietnamese-evidence-v1`, `candidate-r2-reference-phrase`. Ordinary runtime unchanged.

## Shared design and serving differences

Predeclared identities: VNE1-002/003/004/007/011/013/029/041. Coverage: strict reference price, full stay, missing destination, explicit occupancy, unsupported pool, selected room, removal, natural discovery. Original business oracle and clock `2026-11-24T10:18:48.242851600Z` were shared. No fresh20 model-selection data or responses were used.

Same semantic contract, prompt meaning, output budget1024, compiler, corroboration and scoring; one call per fixed-model sample, retries0, **fallback disabled**. Exact profiles: gemini-3.5-flash-lite, gemini-3.1-flash-lite, gemini-2.5-flash-lite, gemini-2.5-flash.3.5/3.1 retain the original SDK serving schema;2.5 uses the existing bounded serving adaptation (including equivalent const/enums and removal of serving hints), while strict local validation remains unchanged. No model received a larger output/thinking allowance. No explicit thinking or sampling configuration was sent; provider defaults may differ. Model-specific wire digests are frozen in the plan; this is semantic-contract control, not identical-wire equality across all model families.

## Actual outcomes

|Requested exact model|Planned|Attempted|HTTP200|Raw exact|Canonical exact|Correct admitted|NOT_RUN|
|---|---:|---:|---:|---:|---:|---:|---:|
{model_table}

Actual reported identity for the6 successful comparison envelopes: `gemini-3.5-flash-lite`. The3.1 attempts returned no response/modelVersion, so reported identity is NOT_REPORTED; requested identity is recorded, not substituted for provider observation. Both2.5 serving paths remain live NOT_RUN in this batch. Prior-batch compatibility evidence is historical, not a new comparison result.

Primary3.5 evidence precision13/13; observed retention13/19 expected witness units over **all8 attempted** samples. Its503/timeout supply no evidence and remain in the overall denominator. Conditional on the6 received valid proposals, explicit evidence/request/canonical outcomes were correct; no hallucinated/duplicate/contradictory evidence or incorrect acceptance was observed in those6. This conditional100% is not overall model accuracy. Canonical action/intent/criteria/missing-slot correctness:6/8 overall, with2 provider NOT_EVALUATED. Backend matrix:6 correct accepted,0 correct rejected,0 incorrect accepted,0 incorrect rejected,2 NOT_EVALUATED. Overall correct admission6/8; admission precision6/6. False acceptance among incorrect typed proposals has denominator0 and is NOT_APPLICABLE.

3.1 has0/2 observed correct admissions and2/2 technical failures; **semantic/model quality NOT_EVALUATED**, not proof it misunderstands Vietnamese. Its remaining6 and all16 planned2.5 observations are NOT_RUN. Retention by date/occupancy/reference/removal and every metric denominator are in [model metrics](evidence-extraction-v1/model-specific-metrics-v1.json). No failed observation was replaced by another model.

## Performance, tokens and availability

Comparison3.5 attempt latency mean5286.375ms/median4885.5ms/p95=max10007ms; logical mean6155.375ms/median6621ms/p95=max12387ms. Known tokens2597 input +417 candidate output =3014;2 UNKNOWN usage attempts.3.1 attempt mean/median10007ms, p95=max10008ms; logical mean11327.5ms, max12645ms; both usages UNKNOWN. No actual token total can be claimed for them. Thinking NOT_REPORTED; provider-only/SDK/cleanup latency NOT_SEPARATELY_RECORDED. No2.5 latency/usage observations.

ATT-013 returned locally terminated503 High Demand; fixed-model design intentionally prevented fallback. ATT-017/020/021 were locally terminated timeouts; no timeout fallback. ATT-020/021 were consecutive and sealed the campaign. All4 actual technical failures count, even though later distinct3.5 samples succeeded. The runner's capacity-selector counter0 describes no selector transition; actual HTTP evidence contains1 High Demand503. [Attempts](evidence-extraction-v1/actual-attempts-v1.json), [fallback/technical failures](evidence-extraction-v1/fallback-metrics-v1.json).

Quota limits were developer snapshots, not guarantees or account-wide proof. Retained same-day prior counts106/1/5/1; new whole-batch counts19/2/0/0. No quota/billing/model probe or key change. Stop was the transient failure threshold, not observed429. Planned8/model is already small; actual imbalance and missing responses prevent comparative superiority or a four-model quality conclusion.

## Decision

Retain the predeclared `gemini-3.5-flash-lite` development default; no new evaluation cohort could dispatch after the mandatory stop. Do not select a winner, promote C, change ordinary runtime primary or interpret a model name as quality evidence. Architecture recommendation **MORE_EVIDENCE_REQUIRED**. Plans, raw/schema/canonical/backend/latency/token dimensions and exact actual identities remain separately available in [comparison observations](evidence-extraction-v1/model-comparison-observations-v1.json).
''')

write('VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md','''
# Vietnamese NLU evidence-contract comparison v1

24/11/2026. **EVIDENCE_FIRST_EVALUATION_PARTIAL**. A/B figures below are frozen historical evidence or offline replay, never new model calls in this batch.

|Metric / planned40|A v3 historical|B v1 historical|B v1 improved backend replay|C r2 frozen40|C r2 fresh20|
|---|---:|---:|---:|---|---|
|HTTP200|37|40|40|NOT_RUN|NOT_RUN|
|Raw schema valid|32|40|40|NOT_RUN|NOT_RUN|
|Canonical typed valid|32|26|26|NOT_RUN|NOT_RUN|
|Exact business semantics|12|22|22|NOT_RUN|NOT_RUN|
|Correct accepted|9|18|22|NOT_RUN|NOT_RUN|
|Correct rejected (false rejection)|3|4|0|NOT_RUN|NOT_RUN|
|Incorrect accepted|0|0|0|NOT_RUN|NOT_RUN|
|Incorrect rejected|20|4|4|NOT_RUN|NOT_RUN|
|Compiler rejections|N/A|14|14|NOT_RUN|NOT_RUN|
|Backend NOT_EVALUATED|8|14|14|NOT_RUN|NOT_RUN|

The four B1 admission repairs016/041/042/048 are preserved. Raw22 and typed26 did not improve in replay; only backend acceptance changed. No replay is called Gemini improvement. [Current actual-response replay](evidence-extraction-v1/b1-actual-response-replay-v1.json).

## Diagnostic revisions are not primary cohorts

Historical B2 revision1:10 actual attempts,8 exact, originally5 correct admissions,8 after backend replay. Historical B2 revision2:5 attempts,2 exact/2 admitted,2 compiler rejections,1 timeout; remaining5 NOT_RUN. They are not merged into one frozen cohort and do not establish80% overall accuracy.

C revision1 diagnostics:8 actual attempts,8 raw-schema valid,8 request meanings correct,7 complete/exact/correct admissions; one evidence-type/completeness rejection. Precision19/21, recall19/20; missing witness1, wrong-type fragments2, nonmember/invented0, duplicates/overlap0. Date/occupancy/unsupported/removal retention are recorded separately by type. Backend matrix7 correct accepted/0 correct rejected/0 incorrect accepted/0 incorrect rejected/1 upstream NOT_EVALUATED; correct admission7/8 overall, precision7/7. Rejecting the incomplete selected expression was safe and does not make its model proposal correct.

C revision2 reference checks:2/2 raw/complete/exact/correct admissions, precision2/2 and recall2/2. One is a labeled retest, one a predeclared generalized control. They do not replace revision1's failure or supply a frozen40 denominator. The prompt changed at that explicit boundary; the validator did not loosen. Frozen comparison/frozen40/fresh20 candidate thereafter stayed unchanged.

The fixed-model comparison received6 correct proposals from10 total attempts;4 technical failures have semantic NOT_EVALUATED. No result from another model substituted for them. [All phase metrics](evidence-extraction-v1/phase-metrics-v1.json).

## Raw, canonical and admission metrics

Raw membership/type/precision/recall/completeness and request meaning are model dimensions. Normalized dates/amounts/destination, canonical action/intent, exclusivity, missing slots and mutation operations are Java/compiler dimensions. No model credit is awarded for derived values. Canonical validity, action, intent, criteria, missing-slot accuracy, exact semantics, compiler/evidence/completeness rejections and backend confusion matrices are separate fields in the observation/metric artifacts.

Overall rates retain every attempted provider/evidence/compiler failure. Backend classification uses typed-valid proposals and explicitly retains upstream NOT_EVALUATED. False-accept/false-reject rates with denominator0 are NOT_APPLICABLE, not evidence of measured0% risk. Admission precision uses all admitted typed proposals; end-to-end correct admission uses all attempted primary logical samples. Full matching and denominator definitions were frozen in [scoring plan](evidence-extraction-v1/scoring-and-campaign-plan-v1.json).

## Paired outcomes

FAIL→PASS0, PASS→FAIL0, PASS→PASS0, FAIL→FAIL0, **NOT_COMPARABLE40**. All original40 identities are listed in [paired outcomes](evidence-extraction-v1/paired-comparisons-v1.json); C primary observations are absent. No new same-model primary B1/C improvement can be estimated. Diagnostic retention changes are a representation/prompt signal, not a causal model capability improvement. Historical compiler/corroboration fixes, current evidence representation, model selection and provider availability effects remain separate.

## Parser-only comparison and targets

Offline deterministic comparator: **40/40 frozen and20/20 fresh canonical exact**,0 HTTP/key reads. It reuses supported parser/corroboration and local business derivation; this is not model accuracy or live full-stack acceptance. These development cases demonstrate no incremental case coverage from an LLM. [Per-case comparator](evidence-extraction-v1/parser-only-comparator-v1.json).

Frozen40 required≥38 exact, raw schema40/40; fresh20 required≥19 exact. Evidence completeness/request/canonical dimensions≥95%, reference-price100%, incorrect/unsafe/invented-evidence acceptance0 were development targets. Primary/fresh targets are **NOT_ASSESSED** because0/40 and0/20 dispatched. No production readiness or broad generalization claim follows from diagnostics or offline tests. Architecture decision **MORE_EVIDENCE_REQUIRED**; deterministic-first/hybrid responsibility review is a bounded future proposal, with incremental LLM value still to demonstrate. Do not start context-aware runtime migration here.
''')

write('GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md','''
# Gemini local timeout and continuation verification v1

24/11/2026. **LOCAL_TIMEOUT_PATH_OBSERVED_LIVE; CAMPAIGN_SEALED_AT_TRANSIENT_THRESHOLD**.

Previous ATT-015 remains immutable: status0/TIMEOUT, unknown usage, no response/termination proof, sealed UNKNOWN_IN_FLIGHT. Its original hash is still `83a6c72fcb704af74297b7ad62e8de43530d8a6b2184f4491caf76d99dd70043`. It was never reopened/reclassified as proven termination retroactively.

Actual source already had a post-stop wrapper that returned observed transport termination after an exception. Remaining defect: it discarded the safe ProviderException category; the runner then synthesized UNAVAILABLE when Reply had no response. Reply now retains the typed category, and the runner propagates it. No category, timeout exception, Future.cancel or interrupt alone can prove termination.

Local evidence: classic HTTP execution is synchronous; its response callback completes before return. Response streams, HTTP client and connection pool close before transport finally marks termination. The evaluation worker is the calling thread; no queued asynchronous response delivery/state callback exists on this local path. SDK maxRetries0 and Apache automatic retries disabled. A pre-dispatch expired control has no fabricated transport-termination proof. An unknown exchange/worker lifecycle stops and cannot dispatch/lookup credentials again. The existing ordinary runtime worker pool is not migrated by this batch.

Offline: GeminiEvidenceTimeoutTest covers pre-dispatch expiry, real loopback timeout/cancellation with server-observed EOF/reset, completed synchronous call, unknown termination stopping/reopen/key lookup, known timeout continuing only to another sample after≥15s, full16384+1024 UNKNOWN reservations and transient threshold. Existing GeminiTransportTest/GeminiLocalFailurePolicyTest/GeminiLocalModelFallbackTest cover bounded bodies, socket aborts, no retries, rate/model caps, fallback exclusion and sealed campaigns. All pass in final1235-test package; no real key/provider use in Maven.

Technical smoke phase used1/4 allowed attempts: successful HTTP200/known usage. It alone did not verify timeout behavior. Later **natural**, not deliberately induced, live observations:

|Attempt|Model|HTTP/category|Local termination|Usage|Continuation|
|---|---|---|---|---|---|
|ATT-017|3.5 Lite|0 / TIMEOUT|true|UNKNOWN|15s cooldown, distinct next sample|
|ATT-020|3.1 Lite|0 / TIMEOUT|true|UNKNOWN|15s cooldown, distinct next sample|
|ATT-021|3.1 Lite|0 / TIMEOUT|true|UNKNOWN|mandatory stop; no next dispatch|

These verify delivery of actual termination/category metadata and bounded local continuation. No successful response replaced these attempts, no same-sample retry and no timeout fallback occurred. The earlier503 also retained UNKNOWN usage and a full reservation. Total4 unknown-usage attempts retain69632 reserved tokens. All21 reservations365568 remain charged conservatively.

Policy: terminated timeout can advance only when local resources/worker/delivery are ended; cooldown≥15s.503 capacity fallback requires actual terminated allowlisted capacity evidence and eligibility/reserve/deadline, and is disabled in fixed-model comparison.400/401/403/429, unreconciled successful usage, critical safety/authority, unknown local termination and exhausted budget/deadline stop. Two consecutive logical transient failures or five cumulative actual transient failures stop; recovered capacity attempts still count. Actual stop: two consecutive timeouts, cumulative4; no new campaign/directory/process bypass or credential lookup followed.

Configured deadline10s; observed timeout wall latencies10007/10006/10008ms include unseparated SDK/cleanup overhead. The excess above configured time is7/6/8ms, not separately measured provider processing. Local closure does **not** prove remote inference stopped, nor remote inference concurrency1. Late remote responses cannot enter the closed local exchange; no remote termination API/probe was used. [Classifications](evidence-extraction-v1/timeout-classifications-v1.json), [all failures/reservations](evidence-extraction-v1/budget-accounting-v1.json), [sealed segments](evidence-extraction-v1/segments-v1.json).
''')

sections=['''# Vietnamese evidence-first remaining failure analysis v1

24/11/2026. One model-evidence failure and four provider/transport failures were observed. No frozen40/fresh20 semantic failures can be inferred from NOT_RUN. Full expected canonical objects, source identities, raw evidence, pipeline locations and decisions: [machine-readable failures](evidence-extraction-v1/remaining-failures-v1.json).
''']
for f in failures:
    provider=f['semanticEvaluation']=='NOT_EVALUATED'
    if not provider:
        detail='''Expected: ASK_SELECTED with a complete target expression; canonical SELECTED/CLARIFY, no fields, whole original reference, no missing/inferred slots. Actual request meaning was correct, but REFERENCE “này” omitted its noun and ROOMS “phòng” had no quantity. Evidence membership was valid; meaning/completeness were not. One missing reference witness, two wrong-type/incomplete fragments; invented0, duplicates0. Canonical result: none; evidence verification rejected before compilation/admission. Compatibility category EXTRACTION_SCHEMA_INVALID does not mean the raw JSON schema failed.

Root H1/H2: model evidence-type/target-expression misunderstanding and insufficiently specific reference instruction. No confirmed H4 compiler defect. Backend accepted=false; no state/tool/confirmation authority. Revision2 added a noun+qualifier instruction without weakening validation; its two labeled checks passed. Original failure remains in revision1; general single-utterance quality is unproven.'''
    else:
        meaning='Complete Đà Lạt stay01/12/2026–03/12/2026,2 adults,0 children,1 room' if f['sourceId']=='VNE1-003' else 'Nha Trang reference price strictly below2000000 VND' if f['sourceId']=='VNE1-002' else 'Selected-room availability recognition with no target resolution'
        detail=f'''Expected meaning: {meaning}. Actual raw evidence: **NOT_AVAILABLE**. Missing/invented evidence: **NOT_EVALUATED**, not zero semantic errors. Canonical result: NOT_EVALUATED; backend admission not reached. Failure stage: PROVIDER/TRANSPORT, category {f['category']}; all local exchanges terminated, usage UNKNOWN, full17408 reservation retained.

Root H8. {'Actual HTTP503 provider body reported High Demand; fixed-model comparison correctly disabled fallback.' if f['category']=='UNAVAILABLE' else 'No response arrived before the local10s deadline. Provider queue/inference/network contributions cannot be separated.'} No H1/H2 model-semantic conclusion is justified. No state/tool authority was granted. {'ATT-021 completed the second consecutive timeout and sealed the campaign.' if f['sampleId']=='CMP-FLASH_31_LITE-VNE1-003' else 'The failed attempt remains even where a later distinct sample succeeded.'} Next action: retain the evidence and review provider availability under a separately valid future scope; never retry/resume this sealed campaign or shop for an answer.'''
    sections.append(f"\n## {f['sampleId']} ({f['phase']})\n\nUtterance: {f['utterance']}\n\n{detail}\n\nActual evidence:\n```json\n{json.dumps(f['actualRawEvidence'],ensure_ascii=False,indent=2)}\n```\n")
sections.append('''
## Not-run limitations and architecture

Comparison22 planned observations, frozen40 all40 and fresh20 all20 remain NOT_RUN, with every identity/utterance/expected outcome retained in the plan/observation artifacts. They are not semantic FAILs, successful recoveries or exclusions. No confirmed implementation defect caused the campaign stop; timeout metadata continued safely until the existing technical threshold required closure.

Recommendation MORE_EVIDENCE_REQUIRED. Parser-only40/40+20/20 shows substantial deterministic coverage, not incremental LLM value. Do not respond to this provider-limited result by indefinitely expanding prompt/parser rules or migrating runtime. D07/D08 unaccepted; E30/FULL_AGENT_E2E/context resolution/tools NOT_RUN. No production acceptance.
''')
write('VIETNAMESE_NLU_EVIDENCE_FIRST_FAILURE_ANALYSIS_V1.md','\n'.join(sections))
print('Wrote five outcome reports; contract specification preserved separately')
