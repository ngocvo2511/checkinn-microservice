# Vietnamese NLU quality comparison v1

24/11/2026. **LIVE_TECHNICAL_BLOCKED**. Full primary baseline0/40, improved40 NOT_RUN. Không có full40 before/after comparison, independent evaluation hoặc verified NLU improvement.

## What can be compared

V1 previous batch có4 targeted reference-price observations thiếu exclusive; không có40 baseline trước sửa. V2/V3 batch hiện tại có6 diagnostic attempts mỗi version. Hai sets có5 câu chung, khác negative control còn lại; percentages6/6 chỉ mô tả từng set. Không gộp versions thành một model accuracy hoặc so40 với smoke.

|Metric|V2 diagnostics|V3 diagnostics|Interpretation|
|---|---:|---:|---|
|HTTP/envelope/SDK/schema|6/6 (100%)|6/6 (100%)|No diagnostic technical failure|
|Action|6/6 (100%)|6/6 (100%)|Targeted coverage only|
|Intent|6/6 (100%)|6/6 (100%)|Targeted coverage only|
|Exact criteria|3/6 (50%)|2/6 (33.33%)|Different6-case sets; not unbiased delta|
|Missing slots|5/6 (83.33%)|6/6 (100%)|Nha Trang extra prerequisites removed|
|Exact semantics|3/6 (50%)|2/6 (33.33%)|No quality gain established|
|Backend accepted|3/6 (50%)|2/6 (33.33%)|Incorrect proposals rejected|
|Correct model/backend rejected|0|0|No observed parser false rejection|
|Incorrect model/backend accepted|0|0|No observed semantic false acceptance|
|Parser exceptions|0|0|Limited price/browse coverage|
|Median latency|1882ms|1937.5ms|Small n, not causal prompt attribution|
|Known tokens/request|2875/6=479.17|3164/6=527.33|All known for diagnostics|

Paired5 common inputs: FAIL→PASS0, PASS→FAIL1, PASS→PASS1, FAIL→FAIL3. Regression: Huế dưới một triệu returned exclusive in v2 and omitted it with unsolicited starRatings CLEAR in v3. V3 fixed unnecessary missingSlots in Nha Trang but did not fix its exact criteria. See [paired diagnostic evidence](nlu-quality-v1/diagnostic-paired-comparison-v1.json).

## Full40 comparison deliberately unavailable

|Metric|Primary baseline40|Improved40|Delta|
|---|---|---|---|
|HTTP/schema/action/intent/criteria/missing slots/exact semantics|N/A,0 attempted|NOT_RUN|NOT_COMPARABLE|
|Backend acceptance/false acceptance/false rejection/parser exceptions|NOT_RUN|NOT_RUN|NOT_COMPARABLE|
|Latency/tokens|N/A|N/A|NOT_COMPARABLE|

Forty sample identities/expected outcomes remain in [baseline40 plan](nlu-quality-v1/baseline40-observations-v1.json). All40 pairs NOT_COMPARABLE; none silently excluded. No offline replay campaign because backend/parser unchanged; recorded compatibility tests remain OFFLINE_REGRESSION, not new live Gemini or replay improvement evidence.

## Model versus backend

Across13 schema-valid outputs, raw counts are6 model-correct/backend-accepted,7 model-incorrect/backend-rejected,0 correct/rejected,0 incorrect/accepted. These counts describe outputs from separate diagnostic versions and smoke, not pooled quality accuracy. One additional provider failure has no model/backend extraction to assess.

No evidence here shows deterministic parser restricting a correct LLM proposal. It correctly rejects missing strict-bound semantics and invented CLEAR. H2/H3/model conditioning is the observed quality bottleneck; attribution to a single component is unproven. Provider non-200/unknown usage is the campaign-completion bottleneck. Do not weaken admission or claim broad Vietnamese date/occupancy/reference competence from price probes.

## Generalization and next step

Targeted new decimal/inclusive/non-price probes were specified before dispatch; they are diagnostic controls, not an untouched holdout. No generalization score, multi-turn application, selected-target resolution or FULL_AGENT_E2E. With7 wrong business representations still observed and primary unattempted, next work remains reference-price representation investigation and full40 execution when technical conditions permit; context-aware10B4E is premature.

[Failure analysis](VIETNAMESE_NLU_LIVE_FAILURE_ANALYSIS_V2.md), [main report](BATCH10B4D_VIETNAMESE_NLU_QUALITY_IMPROVEMENT_REPORT.md). No production readiness, D07/D08 acceptance or billing cost claim.
