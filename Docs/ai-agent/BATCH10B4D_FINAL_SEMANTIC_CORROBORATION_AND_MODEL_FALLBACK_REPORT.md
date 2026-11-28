# Batch10B4D-FINAL: semantic corroboration, fallback and live A/B

24/11/2026, Asia/Ho_Chi_Minh. **CORROBORATION_FIXED_AB_EVALUATION_PARTIAL**.

**98 actual new HTTP attempts /120 cap**,95 planned logical samples attempted and terminated,94 with a usable HTTP200 technical response. Original A40 identity coverage is complete; B40 is complete. There are37 comparable same-model primary A/B pairs,11 improvements/4 regressions/8 unchanged successes/14 unchanged failures; three original A provider-failure pairs remain NOT_COMPARABLE. Architecture decision: **SIMPLIFIED_CONTRACT_PROMISING_BUT_INCONCLUSIVE**. No production readiness, D07/D08 acceptance, context-aware implementation or FULL_AGENT_E2E claim.

## Baseline, preflight and preservation

Backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Read root AGENTS.md; no nested instructions were found. Existing dirty/untracked implementation, tests, reports, IDE changes and previous synthetic responses were preserved. No commit/stage/push/stash/checkout/reset/clean. Frontend/domain services/database/configuration/dependencies were not changed or started.

Read previous10B4D reports/contracts/A-B evidence, actual response artifacts, original dataset/oracle, Agent README/practical architecture, relevant local evaluation/failure/transport/SDK/protocol/scoring/compiler/parser/state/routing/orchestration/policy/grounding source and regression tests. Did not read unrelated private files or dotenv. Used only the specifically authorized GEMINI_API_KEY environment lookup; no key was printed or persisted.

Required baseline command: `mvn -o -B -ntp -pl ai-agent-service package`. Actual baseline **1042 tests PASS, failures0/errors0/skipped0**, BUILD SUCCESS,1:33. Final same command: **1080 tests PASS, failures0/errors0/skipped0**, BUILD SUCCESS,1:03. New tests:17 corroboration cases and21 fallback/serving/segment cases, plus retained23 actual synthetic responses in a test resource. Existing assertions/oracles were not weakened. One old fake orchestration proposal was supplied its newly required missing-slot metadata while retaining all original state/budget/NEED_DESTINATION assertions.

Focused tests and full-package runs were executed while developing. Intermediate failures included missing-slot fake metadata, a test substring assertion matching the word “tools” in prompt text, a parser delimiter offset and test compilation mistakes; these were corrected before live/continued phases. [Verification record](semantic-corroboration-final-v1/verification-v1.json) distinguishes failed intermediate builds from passing full builds1072/1074/1078/1079/1080. Automated tests remained offline, using actual SDK capture, injected fake/loopback transport and controlled clocks. The packaging-only `-DskipTests package` used to build the replay CLI is not counted as test evidence.

Historical E01–E40 remains **23 PASS/16 FAIL/0 NOT_APPLICABLE/1 NOT_RUN** in the final full suite; E30 still lacks authenticated ordered ingress. Original hashes are unchanged: VEX1 `7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3`, VNE1 `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`, historical `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f`, oracle `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`. [Fresh hash checks](semantic-corroboration-final-v1/preserved-hashes-v1.json). No previous25 A observation, provider failure, prompt/schema, sealed campaign, report or expected outcome was rewritten.

## Actual offline replay and backend fixes

Replayed all20 historical typed-valid responses and three typed-invalid responses with the original utterances/oracle/classification and original evaluation clock. The original two provider failures have no response to replay. The before run reproduced the historical matrix exactly; the final-backend replay reconfirmed the improved matrix. This is backend improvement on identical model output, **zero new model calls**, not model quality improvement.

|Historical20 typed-valid|Before|After|
|---|---:|---:|
|Correct accepted|5|7|
|Correct rejected|2|0|
|Incorrect accepted|3|0|
|Incorrect rejected|10|13|
|False acceptance / incorrect|3/13|0/13|
|False rejection / correct|2/7|0/7|
|Backend classification accuracy|15/20|20/20|

005 incomplete dates now require the correct clarification action/opposite missing endpoint;006 missing business slots are independently derived from literal dates/occupancy/destination;015 selected-room meaning cannot become unsupported/generic clarification.027 keep/filter stars and028 explicit destination replacement now parse without unrelated mutations. Complete operation/value/field-set, unsupported-constraint, reference binding, inferred-field and reviewed-destination validation remain intact. No model field supplies its own evidence; no date, occupancy, ID or trusted context is invented.

All5 old correct admissions and10 old incorrect rejections are preserved, including generic people→adults008, invalid-date repair009, reversed-range swapping010, pool dropping011 and omitted adults/invalid flag025. Generalized tests cover both partial date endpoints, keep/filter synonyms, bounded replacement/prior-city syntax, wrong stars/destination, unknown suffixes, selected/ordinal references and legitimate clarification. Production corroboration rules contain no fixture IDs/oracle lookup. [Detailed rules and five changed decisions](VIETNAMESE_SEMANTIC_CORROBORATION_FIX_V1.md), [matrix](semantic-corroboration-final-v1/backend-before-after-matrix-v1.json).

## Lightweight fallback and actual availability

Implemented only the standalone local evaluation path. Typed allowlist/order: **gemini-3.5-flash-lite→gemini-3.1-flash-lite→gemini-2.5-flash-lite→gemini-2.5-flash**. Exact IDs, structured-output support and public text Free Tier were verified against official model/pricing documentation; project-specific numeric limits came from the developer's active quota snapshot, not copied between models. Limits:15/15/10/5 RPM;250000 TPM each;500/500/20/20 RPD. Models2.5 have existing-user access restrictions;2.5 Flash is documented for automatic routing from01/12/2026, so this24/11 snapshot must not be treated as perpetual eligibility. [Sources, profiles and configurations](GEMINI_MODEL_FALLBACK_DESIGN_V1.md).

Only terminated503 with actual provider JSON503/UNAVAILABLE and an allowlisted high-demand/overload capacity message can trigger fallback. No semantic/schema/compiler/backend failure, safety refusal,400/401/403/429 or uncertain in-flight request can trigger it. No same-model retry, answer shopping, quota bypass, hidden countTokens/health probe or automatic failback. Failed models remain unavailable in the current availability segment; selected model, predecessor, trigger, status/body, reservation, usage and latency are recorded per actual attempt.

Offline tests cover primary success, one/two/full-chain fallbacks, all unavailable, excluded models, critical HTTP stops, schema/semantic/safety/usage/in-flight non-fallback, no same-model retry, four-attempt bound, global120/rolling/model limits, retained reservations, identity/endpoint/SDK configuration, immutable prior records and shared segment accounting. No network call is made by Maven tests.

Actual events, in order:

1. Technical3 and first14 remaining A requests ran on primary. Original A051 primary attempt returned503 High Demand (ATT-018,2094ms). After15s,3.1 Lite returned the same classified503 (ATT-019,6582ms). After15s,2.5 Lite returned400 INVALID_ARGUMENT (ATT-020,1027ms): schema constraint has too many serving states. The segment stopped immediately; no2.5 Flash fallback was used to hide400. All three bodies/reservations remain.
2. Inspected400 and implemented a versioned **2.5 serving-wire adaptation** removing costly bound/format/description hints while preserving property/type/enum/required/alternative semantics. Original strict local schema/compiler remained unchanged. Offline1074 passed. This is not a silent semantic repair.
3. A separately planned B technical/model-verification reserve probe returned primary200, creating a new availability segment. All previous counters/deadline/failures were imported. This is a separate observation, not recovery of A051 or reopening its sealed record. B diagnostics8, frozen B40 and most supplemental calls then used primary.
4. GEN-10 primary returned503 (ATT-093,895ms).3.1 remained run-locally unavailable, so the chain used2.5 Lite with the adapted B wire. It returned200 (ATT-094,973ms), valid provider/usage/raw schema. Its SEARCH+UNSUPPORTED_FILTER proposal violated the semantic compiler contract and was rejected. **Technical availability recovery succeeded; NLU semantic recovery failed.** No further model was tried for that wrong answer.
5. Two separately planned fixed-model reserve probes verified adapted B structured output on2.5 Lite and2.5 Flash: both200/schema/semantics/backend PASS. These single synthetic browse probes are not primary NLU accuracy. Two additional fixed2.5 Lite A reserve probes then verified serving compatibility: adapted v1 returned200 but proposed invalid operation EQUAL; equivalent const→single-valued enum in serving v2 returned200/REPLACE but still an invalid nonempty reference on CRITERIA. Both strict A validation failures remain recorded. No answer was repaired, selected as best, substituted into A40 or followed by semantic fallback. Thus A serving400 is resolved on the probe, while A/2.5 extraction correctness is not verified.3.1 live semantic compatibility remains NOT_RUN after its503. The full four-model chain is offline-tested; it was not forced live.

|Actual model|New attempts|HTTP200|Capacity503|Other failure|Known tokens / unknown usage|Median attempt latency|
|---|---:|---:|---:|---|---|---:|
|3.5 Flash Lite|91|89|2|None|43927 /2 UNKNOWN|1303ms|
|3.1 Flash Lite|1|0|1|None|UNKNOWN, no known response|6582ms, failure only|
|2.5 Flash Lite|5|4|0|1 schema400|1919 /1 UNKNOWN|1683ms|
|2.5 Flash|1|1|0|None|475 /0 UNKNOWN|2250ms|

Quality scopes: primary A40/B40 below;3.1 no semantic observation;2.5 Lite two B observations are raw schema2/2, canonical1/2 and backend1/2 (one capacity recovery, one simple probe);2.5 Flash is1/1 simple B probe. The two extra2.5 Lite A probes are schema0/2, semantics0/2 and backend0/2; they remain a separate model/wire/protocol scope. Do not pool those scopes/contracts or advertise a fallback-model corpus accuracy.

Availability: **2 logical samples required fallback;3 actual fallback attempts;1 technical recovery/1 failed recovery (50%)**,0 all-models-unavailable outcomes. Transitions:3.5→3.1→2.5 Lite, then3.5→2.5 Lite with recorded3.1 unavailability. Full logical latencies42.853s and20.377s; overhead beyond the failed first attempt40.759s and19.482s (60.241s total, including cooldown/serialization/accounting/transport). Extra fallback reservations52224 tokens. Actual98/95=1.03158 attempts/logical sample. `LIVE_FALLBACK_VERIFIED` describes technical transition evidence, not successful meaning extraction. [Availability evidence](semantic-corroboration-final-v1/availability-metrics-v1.json).

Recommendation: retain the developer order for local availability experiments; do not reorder on tiny evidence.3.1 capacity failed once;2.5 Lite can recover technical availability after serving adaptation, but quality evidence is insufficient and RPD20 is restrictive.2.5 Flash's one successful probe and documented upcoming routing change do not justify preferring/promoting it. Runtime fallback remains unimplemented.

## Remaining A, diagnostics, B40 and paired comparison

Original A first-attempt coverage is **40/40 on the primary**, across historical and new segments; its051 has additional mixed-model availability attempts kept separate. The remaining15 identities were all attempted,14 primary200/1 primary503. Of those15,12 typed-valid/5 exact/2 correct accepted/3 correct rejected/0 incorrect accepted/7 incorrect rejected;3 not evaluated (2 schema,1 provider). Original A40 totals:37 primaryHTTP200,32 typed-valid,12 exact and9 correct accepted. Final backend replay reconfirms the new A matrix2/3/0/7 without rewriting live evidence.

B diagnostics:8 actual primaryHTTP200, raw schema8/8, canonical typed7/8, exact7/8, admitted5/8. The bad complete-stay proposal emitted SEARCH+destination+reference while omitting dates/occupancy, correctly rejected. Correct removal proposals029/051 exposed parser wording gaps. Added a bounded whole-clause `bỏ/xóa/không cần giới hạn giá/số sao` equivalence with positive/negative operation tests; no arbitrary suffix is stripped. Offline1078 passed before B40. Original diagnostics remained unchanged. Prompt/schema/compiler were not tuned; final B backend freeze is `independent-semantic-v3-removal`.

|Primary metric, actual3.5 model|A40|B40|
|---|---:|---:|
|HTTP200|37/40|40/40|
|Raw schema valid|32/40 typed A|40/40 raw B|
|Canonical typed valid|32/40|26/40|
|Canonical action / intent|20/40 /16/40|24/40 /24/40|
|Canonical criteria / missing slots|20/40 /27/40|24/40 /26/40|
|Exact business semantics|12/40 (30%)|22/40 (55%)|
|Correct accepted / correct rejected|9 /3|18 /4|
|Incorrect accepted / incorrect rejected|0 /20|0 /4|
|NOT_EVALUATED|8|14 compiler rejections|
|Reference-price exact|0/3|3/3|
|Spurious CLEAR|6|0|
|Median latency|1809.5ms|1175.5ms|
|Known mean tokens/request|516.30,37 known|478.35,40 known|

B raw action/intent each32/40, raw meaning criteria25/40, exact raw meaning22/40. Raw missing slots NOT_APPLICABLE; compiled business missing slots26/40. Fourteen compiler FAIL results are rejected contradictory/unsupported model proposals, not14 established compiler code defects. Canonical schema/action metrics exclude failed compilation in the numerator and retain them in the40 denominator. No model PASS is inferred solely from Java derivation. [B pipeline evidence](semantic-corroboration-final-v1/b40-v1.json).

Same-model comparable37 primary pairs:11 FAIL→PASS,4 PASS→FAIL,8 PASS→PASS,14 FAIL→FAIL;029/030/051 provider-failure pairs NOT_COMPARABLE. Conditional paired A12/37→B19/37 (+7/37,18.92pp); full40 descriptive30%→55% also includes provider differences. Supplemental first12 A identities versus the same B40 give4 improvements/2 regressions/6 unchanged failures on overlapping identities, not12 additional unique primary pairs.029/030 deliberate retests are1/2 exact, both200. Generalized B paraphrases6/10 exact: primary6/9,2.5 Lite0/1. [Detailed A/B report and IDs](VIETNAMESE_NLU_CONTRACT_AB_COMPARISON_V2.md).

## Budget, usage and safety

|Phase|Actual / ceiling|
|---|---:|
|Technical|3/3|
|Remaining A first attempts|15/15|
|B diagnostics|8/8|
|B40|40/40|
|Supplemental|24/24|
|Extra fallback/model verification reserve|8/30:3 fallback+5 planned probes|
|Aggregate|98/120|

The remaining22 allowance is unused, not permission for unrestricted diagnostics. First-to-last dispatch span~31.09min, within original60min. No counter/deadline reset across segments. Global minimum observed dispatch spacing5.000426s; max11 requests/rolling60s,191488 reserved tokens/rolling60s. Model rolling maxima11/1/2/1 versus RPM15/15/10/5. New per-model counts91/1/5/1 versus RPD500/500/20/20. No observed429/credential failure. Concurrency1, call≤10s, automatic same-model retries0; no tool/domain/Supabase/countTokens calls.

New-campaign known usage **40932 input +5366 candidate output +23 separately reported thinking =46321 total**,94 known attempts;4 UNKNOWN retain full reservation and are not treated as zero. Thinking was reported on the single2.5 Flash probe; absence elsewhere means NOT_REPORTED, not zero. Reservations:1605632 input+100352 output=1705984 tokens, no refunds/double counting. A40 comparison usage reuses old known usage and is deliberately separate from new98-attempt accounting. [Budget](semantic-corroboration-final-v1/budget-accounting-v1.json), [per-model performance](semantic-corroboration-final-v1/model-specific-metrics-v1.json).

No observed unsafe/provenance/tool/confirmation flags or incorrect typed semantic admissions in the new scoped evidence. This does not establish general safety or zero false rejection. Automated tests were offline; provider data was synthetic only. Successful/error response retention is bounded and sanitized, no request headers persisted. Scoped active-key/provider-key/JWT scanning and unchanged original hashes are recorded in verification evidence; this is not an assertion about unobserved provider internals.

## Source scope, acceptance and next work

Changed/added main sources: `GeminiHistoricalReplay`, `GeminiLocalFallbackEvaluation`, `GeminiLocalModelFallback`, `GeminiLocalModelProfile`, `GeminiLocalExtractionContract`, `GeminiExtractionProtocol`, `GeminiExtractionSmoke`, `VietnameseParser`, `VietnameseExtraction`, `ReadIntentRouting`. Tests: new `VietnameseCorroborationFixTest`, `GeminiLocalModelFallbackTest`, actual-response fixture, narrowly updated `LlmVietnameseOfflineTest`. README and four requested reports plus versioned JSON/CSV/analysis are delivered. Pre-existing changes in transport/session/IDE/other evidence were preserved. Compiler source, policy/grounding/state, all domain services and frontend remain unchanged. Final metadata-only correction distinguishes model eligibility from fixed-probe dispatch selection; original segment records are retained with that interpretation documented.

|Acceptance|Actual status|
|---|---|
|SEMANTIC_CORROBORATION_FIX_DONE|DONE for observed historical defects, actual replay and offline tests|
|MODEL_FALLBACK_IMPLEMENTED|DONE, local only|
|LIVE_FALLBACK_VERIFIED|VERIFIED technical transitions;1 recovery,0 correct-semantic recoveries|
|EXISTING_CONTRACT_BASELINE_COMPLETED|DONE identity40, fixed primary first attempts; availability attempts separate|
|SIMPLIFIED_CONTRACT_EVALUATION_COMPLETED|DONE40 primary identities|
|AB_COMPARISON_COMPLETED|PARTIAL37/40 comparable primary pairs|
|Architecture|SIMPLIFIED_CONTRACT_PROMISING_BUT_INCONCLUSIVE|
|D07/D08 / E30 / FULL_AGENT_E2E|Unaccepted /NOT_RUN /NOT_RUN|

The known005/006/015 admissions and027/028 rejections are fixed, but B still misclassifies selected-room013/033; loses/conflicts explicit stay facts; violates clarification/reference combinations; and fails canonical reviewed-label casing/spelling. B's correct016/041/042/048 proposals are still rejected by natural discovery grammar. Four paired regressions003/004/007/049 and55% exact preclude promotion despite price/CLEAR improvements.

Next: a targeted semantic-contradiction, full date/occupancy retention, finite reviewed-label normalization and observed literal-coverage fix, preserving all new negative cases and oracles. Defer `BATCH10B4E-CONTEXT-AWARE-VIETNAMESE-NLU` until single-utterance quality is adequate. Future relative expressions must use trusted conversation revisions/selected references/confirmed occupancy and existing budgets; no absolute values or targets can be invented now. No additional authorization/infrastructure batch is proposed.
