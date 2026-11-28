# Batch 10B.4D-FIX — semantic extraction simplification

24/11/2026, Asia/Ho_Chi_Minh. **SEMANTIC_EXTRACTION_IMPLEMENTED_LIVE_PARTIAL**.

**28 actual Gemini HTTP attempts**: technical3, existing baseline25, semantic diagnostics0, simplified evaluation0, generalization0. Baseline40 therefore has **15 NOT_RUN**; B40 has **40 NOT_RUN**. Two consecutive actual HTTP503 provider failures sealed this new campaign. No retry, restart, replacement sample, account/model switch or use of the remaining92 allowance followed. Previous batch14 attempts remain separate historical evidence, not charged against this batch or overwritten.

The local semantic compiler and strict local protocol are implemented and offline verified. **Existing40 completion, simplified40 completion and live A/B comparison are not done.** Architecture decision: **MORE_TARGETED_EVIDENCE_REQUIRED**. No production readiness, D07/D08 acceptance or FULL_AGENT_E2E is claimed.

## Preflight and scope

Read the root AGENTS.md; workspace search found no applicable nested AGENTS.md. Backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Preserved existing dirty/untracked implementation, tests, README, reports and IDE changes. No checkout, commit, stage, push, stash, reset or clean. Frontend and domain services were not modified; no application stack/database was started.

Read the previous10B4D report, failure analysis v2, quality comparison v1, nlu-quality-v1 evidence, previous compatibility evidence, practical architecture, Agent README and actual local runner/protocol/smoke/transport/scorer/dataset/parser/state/routing/orchestration/policy/grounding source. Reused existing extraction, SDK wire validation, limiter and independent corroboration.

Actual baseline command, from backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
```

Baseline **991 tests PASS**, failures0/errors0/skipped0, BUILD SUCCESS,1:23. Java21, Boot3.3.4 and LangChain4j1.17.0 unchanged. Historical evaluation remains **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**; E30 still NOT_RUN because authenticated ordered ingress is not implemented.

## Evaluation resilience actually implemented

The dedicated local path now records received HTTP status even when transport throws, bounded sanitized error body, exception class, timeout category/termination, latency, known/UNKNOWN usage, full reservation and continuation reason. Optional successful raw-response debugging is reused. Local diagnostics opt-in is set only by the standalone CLI; ordinary transport error-body retention remains off. Error capture reads at most64KiB plus one overflow byte, redacts credentials and never forwards/persists arbitrary headers. Exact provider code/status/message remain available in the sanitized error JSON.

The local policy stops on400/401/403/429, unknown in-flight state, untrustworthy successful usage, protocol/envelope/SDK failures and critical authority/safety failures. A terminated timeout/connection error or isolated500/502/503/504 may continue to the **next distinct sample**, never retry the same sample. Cooldown≥15s, longer valid Retry-After within the original deadline; stop at two consecutive or five cumulative transient failures. Known technical success, including contained semantic/schema failure, resets the consecutive count only. Schema/semantic accuracy is not a stop gate.

Every actual dispatch retains16384 input+1024 output reservation. Failed calls do not refund it. Non-200 without trustworthy usage stays UNKNOWN, not zero; known actual usage is reported separately and never added a second time to rolling reservations. Actual attempt counters, rather than SDK entry, drive local attempted denominators. Rolling waits precede the per-call clock, with another actual-dispatch admission check.

The existing local limiter is extended with an exclusive sibling lock and persisted sibling reports to carry aggregate attempts, rolling reservations, phase ceilings and earliest60min deadline across phases/processes. Caps are5 technical/40 existing/15 diagnostic/40 simplified/20 supplemental, aggregate120, concurrency1, retry0, spacing≥5s, ≤12 requests and≤200000 reserved tokens/rolling60s.17408 reserved per request makes the effective rolling maximum11. A stopped/incomplete/uncertain sibling report blocks further dispatch. No signing, operator ceremony, financial/distributed framework or ordinary-budget expansion was added. Cooperative local reports do not coordinate unrelated account consumers or prove remaining quota/Free Tier billing.

Offline tests exercise critical status stops, isolated transient continuation, terminated versus unknown in-flight timeout, retained unknown-usage reservations,200 unknown-usage stop, thresholds/reset behavior, semantic/schema continuation, critical safety stop, Retry-After, redaction, actual single-attempt loopback requests, aggregate120 across prior-process reports and sealed-run rejection before key lookup.

## Actual technical verification and baseline

Comparator A was predeclared as the latest frozen **v3**, not selected after new scores. Prompt digest `458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46`; schema digest `0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9`. Model `gemini-3.5-flash-lite`; successful envelope/SDK validation and usage checks remained strict. Plans recorded all sample identities, expected outcomes, request digests, clock and oracle identity before dispatch. No parser/prompt/schema/oracle tuning occurred inside A.

Technical smoke used original VEX1-01/02/03 and their original dates/expected outcomes. **TECHNICAL_SMOKE PASS**:3/3 HTTP200/envelope/SDK/schema, trustworthy usage and functioning limits. **SEMANTIC_SMOKE FAIL**,2/3 exact accepted: VEX1-02 again omitted exclusive meaning and added star CLEAR, correctly rejected. This did not block baseline40 and no extra attempts were spent trying to perfect smoke.

Oracle SHA256 `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`, TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION. Original60 candidates,40 selected,9 unresolved product cases,11 DENY retained; only selected eligible synthetic inputs were dispatched. The40-sample plan is intact, including difficult/unsupported/reference cases. No actual conversation context or trusted target resolution is claimed.

|Primary A metric|Actual result|
|---|---:|
|Planned /attempted /NOT_RUN|40 /25 /15|
|HTTP200 /503|23 /2|
|Envelope /SDK valid|23/25 /23/25|
|Strict extraction schema valid|20/25 (80%)|
|Action /intent accuracy|12/25 (48%) /10/25 (40%)|
|Canonical criteria accuracy|13/25 (52%)|
|Business missing-slot accuracy|15/25 (60%)|
|Reference extraction accuracy|18/25 (72%)|
|Exact business semantics|7/25 (28%)|
|Secondary exact/schema-valid|7/20 (35%)|
|Backend accepted|8/25 (32%)|
|Correct accepted /correct rejected|5 /2|
|Incorrect accepted /incorrect rejected|3 /10 schema-valid proposals|
|Backend NOT_EVALUATED|5:3 schema failures+2 provider failures|
|Reference-price exact applicable|0/2|
|Spurious CLEAR|3/25|
|Parser exceptions|0/20 schema-valid backend evaluations|

Provider/schema failures remain in attempted denominators. NOT_RUN is not a model failure or a success. Legacy admission-expectation scorer fields are preserved but are **not** semantic false acceptance/rejection; the new analysis computes3/13 incorrect schema-valid admitted and2/7 correct rejected. This distinction matters because every selected oracle row expected admission.

### Provider failures and exact stop

VNE1-029 (`Bỏ giới hạn giá đi`) returned **503**, provider error code503/status UNAVAILABLE, message `This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.` No trustworthy usageMetadata was available. Full17408 reservation was kept, decision TRANSIENT_NEXT_DISTINCT_SAMPLE, cooldown15s. The previous synchronous call had terminated.

The next distinct sample VNE1-030 (`Xem khách sạn thứ hai`) also returned **503** with that provider error. Decision TRANSIENT_FAILURE_THRESHOLD stopped the campaign after two consecutive transient failures. No sample was retried or replaced. There was no observed400/401/403/429, timeout or unsupported successful-usage failure in this live batch; those paths were tested offline.

Remaining **VNE1-031,032,033,037,039,040,041,042,043,046,047,048,049,050,051** are NOT_RUN. The sealed baseline report preserves their expected outcomes. No inference is made about the unknown exact status in the previous batch; these new503 observations cannot recover historical error evidence.

## What the partial baseline actually shows

The bottleneck is broader than reference-price flags. Observed representation defects occur in002/010/020/025 (overlapping other errors): missing exclusive, unrelated CLEAR and invalid false exclusive. Spurious CLEAR specifically appears in002/010/020. Five observed explicit fact/constraint errors occur in008/009/010/011/025: generic people promoted to adults, invalid date changed to28/02, reversed range swapped, unsupported pool requirement dropped, explicit adult count omitted. These are observable multi-label error axes; they do not identify a unique H1/H2/H3 causal mechanism or count all Vietnamese misunderstandings.

There is now real **H4 corroboration limitation** evidence: exact-correct027 (`Chỉ giữ khách sạn 4 sao`) and028 (`Đổi điểm đến sang Huế`) are rejected as ambiguous/unconsumed by the existing parser. Unlike previous price-only diagnostics, correct/backend-rejected is nonzero. The parser was not loosened or tuned during the frozen baseline, and remains unchanged in B's local experiment.

Incorrect/backend-admitted005/006/015 reveal incomplete semantic corroboration:005 should clarify incomplete dates,006 lacks oracle business missing slots,015 misclassifies selected-room intent as unsupported clarification. No tool executed; admission is an extraction-boundary observation, not proof of policy bypass or actual READ. Ordinary orchestration still validates state and tool scope, but these observations preclude claiming zero semantic false acceptance at this boundary.

The invalid date modification009 and generic-adult assumption008 are **two observed inferred-fact proposals**, both rejected before domain/state application. Empty model `inferredFields` and zero critical authority flags do not mean zero semantic inference. Three outputs failed typed extraction validation (009/014/025); their actual synthetic text is retained. No parser exception, oracle defect or state/policy execution defect was established; actual state application is NOT_RUN.

Main quality bottleneck: intent/ambiguity/reference extraction and business representation, with confirmed conservative-parser coverage and semantic-admission gaps. Campaign-completion bottleneck: provider high-demand503 threshold. Prompt/schema simplification is a reasonable hypothesis, not a demonstrated fix. Full40 quality and category generalization remain unknown.

## Semantic contract, compiler and offline verification

After the live stop, implemented local `vietnamese-semantic-v1`, documented in [semantic contract](VIETNAMESE_SEMANTIC_EXTRACTION_CONTRACT_V1.md). Gemini proposes meaning: intent, literal destination/hotel, dates, occupancy, price kind/operator/unit/amount, explicit removals, references and semantic ambiguity issues. It no longer emits REPLACE/CLEAR wrappers, backend field names, exclusive flag or missingSlots arrays.

Java maps a validated proposal into the existing typed extraction, derives exclusive/mutation/missing-slot structure, then reuses the unchanged independent literal parser and existing typed validators. Absent means no-change; removal is explicit and allowlisted; unsupported strict price meaning fails rather than becoming inclusive. No fixture IDs/oracle reads occur in the compiler, no model output is repaired using expected answers, and no trusted provenance or tool/state capability is minted.

Example semantic proposal:

```json
{"intent":"SEARCH","destination":"Nha Trang","price":{"kind":"REFERENCE","operator":"LT","amount":2000000,"unit":"VND"}}
```

Java produces cityName/maxPrice REPLACE and `referencePriceExclusive REPLACE true`. The same amount with LTE compiles without exclusive, and is independently rejected when the original utterance says strict `dưới`. Wrong amount/destination/removal/date/occupancy is never overwritten by parser values. Raw schema, compiler result, compiled extraction and backend result are separately recorded by the local pipeline.

The semantic schema is strict for duplicate/trailing/unknown keys, exact types/enums/numbers, bounded strings/nesting/body, dates/occupancy/price and removal lists. Final existing typed validation and MODEL_PROPOSED provenance remain. Missing-slot derivation is backend responsibility; model semantic-issue prediction remains model responsibility. Raw B missing-slot accuracy is NOT_APPLICABLE, compiled business accuracy applicable. The design does not fix existing corroboration gaps by declaring its own proposal trustworthy.

Offline verification constructs equivalent test proposals for all40 original outcomes and verifies canonical equality without rewriting the oracle. Those40 results are **OFFLINE_EQUIVALENT_PROPOSAL_NOT_MODEL_OUTPUT**, not live model responses or raw semantic accuracy. Positive/negative tests cover price LT/LTE/range/refinement, dates/occupancy, destination-only/no-change, explicit star/price removal, clarification, selected/reference and unsupported requests; malformed/oversized/deep/unknown/coerced data, wrong operators/amounts/units, invented facts and fabricated IDs/provenance/tools fail. Fake SDK integration confirms raw/compiled/backend layer recording, with zero external calls. See [compiler verification](semantic-extraction-fix-v1/compiler-verification-v1.json).

B diagnostics have a fixed eight-case offline plan (price, browse, dates/occupancy, removals, comparison, ambiguity). B40 uses the exact selected40 business oracle. Both **live NOT_RUN** after the sealed stop. Generalization is also NOT_RUN. No B prompt iteration, live compiler-error count, performance estimate or inferred model improvement is presented.

## A/B decision, usage and safety

[A/B report](VIETNAMESE_NLU_CONTRACT_AB_COMPARISON_V1.md): **0 comparable pairs,40 NOT_COMPARABLE**, improvements/regressions unknown. A raw schema and B raw semantic schema are not compared as field-set equality. Recommendation **MORE_TARGETED_EVIDENCE_REQUIRED**; retain existing ordinary default and keep B as a local experiment. Small-sample offline coverage/maintainability alone cannot justify promotion.

A primary known usage: input9533/output2560/total12093 over23 known requests, mean525.78 total tokens;2 UNKNOWN. A latency mean1857.36ms, median1803ms, p95 nearest-rank2372ms, max3053ms, n25 including failures. A limiter wait75135ms, cooldown15s. B token/latency comparison is NOT_RUN.

Across all28 attempts, **performance/accounting only, not pooled quality**:26 known, input10793/output2995/total13788;2 UNKNOWN. Thinking tokens NOT_REPORTED, not zero. Full reservations input458752/output28672 (487424 total); no refund or double counting. Aggregate latency mean1902ms, median1807ms, p95 nearest-rank2695ms, max3053ms. First recorded dispatch to last response approximately173.951s. Observed minimum dispatch spacing5.000s, max11 requests/rolling60s, max191488 reserved tokens/rolling60s, under limits. No Retry-After was observed on the two failures. No actual timeout/retry/fallback/countTokens/domain/Supabase call occurred.

Critical authority/provenance/unsupported-confirmation/tool flags observed0; actual unauthorized READ/WRITE/domain/Supabase executions0. Two safely rejected inferred-fact proposals are separately acknowledged above. Only reviewed synthetic inputs were sent; no real customer/hotel/booking/payment data. Injection resistance is NOT_TESTED because DENY samples were not sent. Real leakage observed0 in scoped artifact/active-key/JWT scans is not an independent guarantee about all provider internals.

Feature defaults remain OFF/FAKE/liveOFF; ordinary turn30s/model3/tool rounds2/proposals6/HTTP6/tool10s/retries0 unchanged. A01–A10, PolicyExecutor, GroundingGate, SearchCriteriaState, trusted selected references and occupancy confirmation are unchanged. No frontend, gateway, hotel, booking, payment, place, database/migration, hosted Supabase or Config Server changes.

## Final verification, files and acceptance

Final required command is the same offline Maven package. Latest completed result and exact source/status evidence are in [verification](semantic-extraction-fix-v1/verification-v1.json). Final suite: **1042 executed tests PASS**, failures0/errors0/skipped0, BUILD SUCCESS,1:06 (latest full invocation). Added parameterized cases: local policy13, semantic compiler26, transport8; local runner4. Existing assertions/oracles were not weakened. Full reruns were made after new compiler, integration, budget and denominator changes; intermediate development failures were corrected, not suppressed. Earlier focused runs found a429 classification regression and a test incorrectly constructing a non-2xx SuccessfulHttpResponse; those were fixed and the latter replaced with a real loopback failure test. Compiler development initially had bounded-schema parsing and test inverse-CLEAR mistakes, corrected before final green verification. None triggered extra Gemini attempts.

Batch code scope: `GeminiLocalEvaluation`, `GeminiLocalExtractionContract`, `GeminiHttpTransport`, `GeminiExtractionSmoke`, new `GeminiLocalFailurePolicy`, new `VietnameseSemanticCompiler`; four related test files and Agent README. Remaining changes are these three new reports and versioned evidence/analysis script. Pre-existing IDE/protocol/session changes remain untouched by this batch. No generated stubs/build output was manually edited.

Preserved original hashes: VEX1 `7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3`; VNE1 `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`; historical `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f`; oracle `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`. Previous responses, sealed records, reports and prompt/schema v1/v2/v3 snapshots were not overwritten. [Hash evidence](semantic-extraction-fix-v1/preserved-hashes-v1.json).

|Acceptance item|Status|
|---|---|
|EVALUATION_RESILIENCE_DONE|DONE, offline and actual isolated503 continuation/threshold evidence|
|EXISTING_CONTRACT_BASELINE_DONE|PARTIAL,25/40 attempted|
|SIMPLIFIED_CONTRACT_IMPLEMENTED|DONE, local/offline only|
|SIMPLIFIED_CONTRACT_EVALUATION_DONE|NOT_RUN,0/40|
|AB_COMPARISON_DONE|PARTIAL,0 comparable pairs|
|Generalization|NOT_RUN|

Machine evidence includes [technical](semantic-extraction-fix-v1/technical-verification-v1.json), [baseline40](semantic-extraction-fix-v1/baseline40-observations-v1.json), [failure matrix](semantic-extraction-fix-v1/failure-matrix-v1.json), [metrics](semantic-extraction-fix-v1/aggregate-metrics-v1.json), [budget](semantic-extraction-fix-v1/budget-accounting-v1.json), [performance](semantic-extraction-fix-v1/all-attempted-performance-v1.json), [B40](semantic-extraction-fix-v1/simplified40-observations-v1.json), [pairs](semantic-extraction-fix-v1/paired-comparison-v1.json), [snapshots](semantic-extraction-fix-v1/contract-snapshots-v1.json), compiler evidence and CSV. Successful raw synthetic envelopes remain in ignored `.local/batch10b4d-fix/*.responses/`; provider failures are retained in sanitized result JSON. [README](../../ai-agent-service/README.md) gives simple offline/live-future/debug/error-inspection commands. No additional operator setup is required.

Next batch should target observed ambiguity, intent/reference and refinement corroboration defects, then obtain the missing live paired evidence in a separately authorized bounded campaign. Do not restart this sealed campaign, promote B from offline tests, or prioritize context-aware10B4E before single-utterance quality has sufficient evidence. No new authorization/infrastructure batch is proposed.
