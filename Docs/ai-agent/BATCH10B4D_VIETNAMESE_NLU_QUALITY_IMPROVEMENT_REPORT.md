# Batch 10B.4D — Vietnamese NLU quality improvement

24/11/2026, Asia/Ho_Chi_Minh. **LIVE_TECHNICAL_BLOCKED**. Không hoàn thành batch và không claim production readiness.

Đã thực hiện14/120 actual Gemini HTTP attempts:12 prompt/schema diagnostics và2/3 smoke. Primary **0/40 attempted,40 NOT_RUN**; improved40 NOT_RUN. Live dừng vì attempt VEX1-02 đi vào nhánh provider non-200, category UNAVAILABLE, không giữ được exact HTTP status hoặc usage. Yêu cầu batch bắt buộc dừng khi usage không xác định; không retry/fallback hoặc khởi động campaign khác để vượt stop. Semantic failures trước đó vẫn được tiếp tục; chúng không phải nguyên nhân chặn primary.

## Baseline, phạm vi và thay đổi

Branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`, unchanged. Root không phải Git repo. Đã kiểm tra instructions; không có nested AGENTS.md trong backend. Dirty/untracked baseline gồm IDE, README, Protocol/Session/Smoke và các lớp/evidence batch10B4C; giữ nguyên, không stage/commit/push/stash/reset/clean. Không sửa frontend/domain services/database/config/dependencies hoặc khởi động stack.

Baseline thực chạy `mvn -o -B -ntp -pl ai-agent-service package`: **987 tests, failures0/errors0/skips0, BUILD SUCCESS**,46.982s. Final cùng command: **991 tests, failures0/errors0/skips0, BUILD SUCCESS**,55.976s. Một package trung gian991 PASS,54.394s. Historical evaluation vừa chạy lại: **23 PASS/16 FAIL/0 NOT_APPLICABLE/1 NOT_RUN**, E30 NOT_RUN. Các thử nghiệm focused ban đầu phát hiện request v2 quá lớn và đã được sửa trước dispatch; không che kết quả lỗi này. Một build phát triển `-DskipTests` chỉ dùng kiểm tra compilation/package, không được tính là test evidence.

Đã đọc contract/parser, criteria state/routing, orchestrator/policy/grounding, provider/SDK/wire, local evaluator/dataset/metrics và regression liên quan, Agent README, practical architecture, reports/evidence10B4C. Không cần nguồn web để xác định hành vi: dùng code và synthetic responses thật; không nâng model/SDK hoặc tìm quota qua API khác.

Thay đổi của batch:

- `GeminiLocalExtractionContract.java` mới: version local v2/v3, explicit semantic instruction và descriptions; v1 giữ nguyên. Không đổi authoritative schema resource, allowed fields, validation semantics hoặc ordinary runtime prompt.
- `GeminiExtractionProtocol.java`: thêm overload serialization cho local version; đường v1 trả cùng instruction/schema và request. `GeminiExtractionSmoke.java`: overload shared evaluator nhận request đã đóng băng; admission vẫn so exact body, cùng envelope/SDK/parser/corroboration. Không tạo implementation NLU song song.
- Existing untracked `GeminiLocalEvaluation.java`: `--protocol`, fixed diagnostics, request byte/bound/expected extraction trong plan; local cap120/deadline60min; semantic/schema failure được cô lập có usage tiếp tục ở mọi phase. Critical stops giữ nguyên; signed smoke và ordinary Agent không đổi.
- `GeminiHttpTransport.java`: sau khi quan sát lỗi thật, giữ thêm scalar observed HTTP status; local runner đọc kể cả transport throw. Không đổi transport routing/TLS/retry/SDK. Đây là sửa observability sau lần thất bại; không thể phục hồi status lịch sử và không có live verification mới.
- `nlu-quality-diagnostics-v1.json`,4 synthetic boundary probes, expected outcomes định trước dispatch; oracle40 giữ nguyên.
- `GeminiNluQualityTest.java` mới4 focused tests: bounded/versioned plan và pinned instruction hash; semantic+schema smoke failures vẫn chạy hết40; descriptions không đổi constraints; unsafe output dừng sau1. Existing LocalEvaluationTest giữ cả schema/semantic cases và chuyển assertion thành continuation, không bỏ coverage. CompatibilityTest kiểm tra120 cap; TransportTest thêm observed-status assertions cho200/redirect/429/500/503. Automated tests chỉ fake/loopback, không gọi Gemini/domain services.
- README và ba report batch mới, machine evidence trong `nlu-quality-v1/`. Không nhận các implementation/evidence có sẵn của10B4C là thay đổi mới.

## Reference-price contract và versions

`VietnameseParser`: maxPrice/minPrice inclusive; riêng undated `giá tham khảo dưới X` thêm `referencePriceExclusive REPLACE true`, price vẫn VND. Dated strict dưới không bị đổi thành inclusive; cần clarification. Inclusive `không quá/tối đa` cần explicit per-night units. CLEAR giá xóa min/max, không sinh exclusive false/CLEAR. `SearchCriteriaState` xóa exclusive cũ khi maxPrice thay đổi; `GroundingGate` áp dụng strict predicate trên fresh returned cards, không claim toàn catalog/availability. Không thay đổi các logic đó.

V1 instruction không giải thích semantic cờ; schema có const true nhưng không có description. V2 thêm rule BOTH maxPrice+exclusive, amount/unit/date/occupancy/CLEAR semantics và descriptions. V3 thêm omit absent fields/CLEAR only explicit removal và missingSlots cho reference browsing. Descriptions không thay constraints; versions chỉ local prompt/schema-description variants.

[Exact instruction/schema snapshots và SHA256](nlu-quality-v1/contract-versions-v1.json):

|Version|Instruction SHA256|Schema SHA256|
|---|---|---|
|v1|48b6b4eb7d57cbf1d13ce6a561bc0f82ff72a94de8c38a99ad9670754f64f8eb|d8617d8e373927d539535d5d4f3ac826b92f28bed14ae34d02cfa8350c554aa9|
|v2|0a5f3cbb08fb79652c8f1852bf267770f86ff5c718d43fd101524a0b30f4d63f|0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9|
|v3|458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46|0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9|

Wire serialization vẫn existing SDK1.17.0 + normalize bỏ pinned candidateCount1; response validation `gemini-envelope-usage-extensions-v2`. Không sửa metadata compatibility. Model `gemini-3.5-flash-lite`; evaluation clock `Clock.systemUTC()` với date rules Asia/Ho_Chi_Minh; các stay dates01/12/2026→03/12/2026 không đổi. V3 frozen trước smoke/main, không tune trong primary plan. Schema descriptions/prompt v1 và historical signed mode không bị thay thế.

Actual requests có byte size/conservative bound `2*UTF8bytes+1024` trong từng observation, đều dưới16384. VEX1-01 v3:7523 bytes/bound16070; VEX1-02:7557/16138. All40 planned rows giữ expected extraction/request digest, ngay cả NOT_RUN. Các snapshot prompt được đối chiếu với digest của cả ba run, trùng khớp.

## Diagnostics thật, không phải primary accuracy

V2 chạy6 completed diagnostics, sau đó dừng có chủ đích trước smoke để cải thiện prompt. Original file tên `baseline-v2.json` thực chất là interrupted diagnostic plan; interim stopReason COMPLETED không có nghĩa hoàn thành50 rows. Không có pending/in-flight reservation khi dừng; mọi6 attempt đều có response/usage. Giữ original bytes và ghi annotation trong budget accounting. V3 chạy6 diagnostics riêng, tổng12/12 diagnostic cap. Hai process sau cách nhau hơn60s; không reset aggregate count.

|Metric|V2 diagnostic n6|V3 diagnostic n6|
|---|---:|---:|
|HTTP/envelope/SDK/schema|6/6 (100%)|6/6 (100%)|
|Action/intent|6/6 (100%)|6/6 (100%)|
|Exact criteria|3/6 (50%)|2/6 (33.33%)|
|Missing slots|5/6 (83.33%)|6/6 (100%)|
|Exact semantics|3/6 (50%)|2/6 (33.33%)|
|Backend accepted|3/6 (50%)|2/6 (33.33%)|
|Exclusive on applicable below probes|1/4 (25%)|0/4 (0%)|
|Semantic false acceptance/rejection|0/0 observed|0/0 observed|

Các denominator6 là diagnostic plan, không phải bộ40. Hai sets khác một negative control: v2 có tối đa1triệu, v3 có non-price; không dùng overall delta như paired score. Five common inputs: **0 FAIL→PASS,1 PASS→FAIL,1 PASS→PASS,3 FAIL→FAIL**. V3 regression: `Khách sạn ở Huế giá tham khảo dưới một triệu`, v2 đúng exclusive, v3 thiếu exclusive và thêm starRatings CLEAR. MissingSlots của câu Nha Trang được sửa từ thiếu dates/adults không cần thiết sang[], nhưng exact semantics vẫn FAIL.

Live chứng minh ít nhất một proposal hoàn toàn đúng:

```json
{"action":"CRITERIA","intent":"SEARCH","fields":{"cityName":{"operation":"REPLACE","value":"Huế"},"maxPrice":{"operation":"REPLACE","value":1000000},"referencePriceExclusive":{"operation":"REPLACE","value":true}},"reference":"","missingSlots":[],"inferredFields":[]}
```

Đó là DIAG4D-02 v2, backend ACCEPTED. Chưa đủ để đánh dấu REFERENCE_PRICE_FIX_VERIFIED: omission vẫn lặp lại. Inclusive explicit per-night2triệu đúng cả hai versions; inclusive tối đa1triệu v2 đúng, không thêm exclusive. Non-price Huế v3 đúng, không price/flag. Decimal dưới1,5triệu hiểu đúng1500000 nhưng thiếu exclusive/thêm starRatings CLEAR ở cả hai. Missing-unit,800nghìn,`nhỏ hơn`,ranges/invalid dates/occupancy chưa có live coverage mới; không tự tạo oracle hoặc product semantics cho chúng.

## Smoke và primary40

|Fixture|Technical|Exact semantic|Backend|Outcome|
|---|---|---|---|---|
|VEX1-01|HTTP200/envelope/SDK/schema PASS|PASS|ACCEPTED|Đà Lạt, không invent dates/occupancy|
|VEX1-02|UNAVAILABLE, usage UNKNOWN|FAIL in attempted scoring; linguistic NOT_EVALUATED|NOT_EVALUATED|Transport non-200 throw path; exact status UNKNOWN|
|VEX1-03|NOT_RUN|NOT_RUN|NOT_EVALUATED|Stopped on technical unknown usage, dates unchanged|

**TECHNICAL_SMOKE_FAIL**; **SEMANTIC_SMOKE_FAIL/INCOMPLETE**,1/3 exact fulfilled,1/2 attempted (50%),1 NOT_RUN. VEX1-02 không bị gán lỗi hiểu tiếng Việt vì không có extraction. Automatic BROWSE của01 là route mong đợi theo code, không phải domain execution đã thực hiện.

Nguồn lỗi là `GeminiHttpTransport.lambda$execute$5:85`: nhánh đọc non-200 JSON rồi throw UNAVAILABLE. Không biết exact status/billing/quota/auth cause; không suy diễn500/403 hoặc credential invalid. Runner cũ chỉ ghi response.statusCode sau transport return, nên mất status khi throw. Không có raw body/usage cho attempt đó. Bổ sung observed status chỉ giúp lần sau; không “phục hồi” status0 thành HTTP0 thật. Unknown usage không bằng zero tokens.

Primary oracle giữ40 selected từ60 candidates;9 NEEDS_PRODUCT_REVIEW và11 DENY không gửi. All40 NOT_RUN; action/intent/criteria/field/missing-slot/clarification/reference/exact-semantic accuracy **N/A,0 attempted**, không0% NLU. Positive reference/context dimensions, selected resolution/state application, generalization set, improved40 và FULL_AGENT_E2E NOT_RUN. Không có baseline40 nên không có before/after40; [paired40 artifact](nlu-quality-v1/paired-comparison-v1.json) có40 NOT_COMPARABLE. Offline improvement replay NOT_RUN vì backend/parser không đổi. Existing recorded compatibility replay trong Maven được ghi nhãn offline regression, không phải live campaign.

## Model/backend bottlenecks và H1–H7

Trên13 schema-valid outputs:6 correct+accepted,7 incorrect+rejected,0 correct+rejected,0 incorrect+accepted; thêm1 technical output NOT_EVALUATED. Đây là raw evidence counts qua nhiều targeted versions, không pooled model accuracy. Không thấy parser mismatch/exception hay semantic false acceptance/rejection. Những spurious CLEAR là criteria sai, đã bị field-set equality chặn; không tính là WRITE tool execution.

H1: không có city/amount/action/intent hiểu sai trên các schema-valid observations;7 outputs sai về business representation, không suy ra7 lỗi ngôn ngữ độc lập. H2/H3 là hypotheses có căn cứ do exclusive omission và absent-field CLEAR bất chấp instruction; không có ablation để phân bổ chính xác số lỗi do prompt/schema/model conditioning. Schema biểu diễn được vì1 response đúng; chưa chứng minh schema incompatibility. H4:0 model-correct/backend-rejected quan sát; không có lý do nới deterministic parser. H5/H6 không có defect đã chứng minh, oracle không đổi. H7:1 provider non-200/unknown-usage blocker. Bottleneck chất lượng đã quan sát là representation/conditioning của cặp reference price; bottleneck hoàn thành campaign là provider failure.

[Failure analysis V2](VIETNAMESE_NLU_LIVE_FAILURE_ANALYSIS_V2.md) và [per-observation matrix](nlu-quality-v1/failure-matrix-v1.json) giữ expected/actual, root-cause hypothesis, severity, đề xuất cho từng failure. Không bỏ regressions hoặc thay oracle để tăng PASS.

## Usage, latency, accounting và safety

13/14 attempts HTTP200 (92.86%);13 envelope/SDK/schema valid,0 observed extraction-schema-invalid,1 provider failure/unknown usage. Required denominator attempted bao gồm failure; schema-valid-only là secondary. Đã validate isolated-schema continuation và critical stops bằng offline tests.

Known13: input4983/output1540/total6523 tokens;1 UNKNOWN usage, không tổng thực tế hoặc cost claim. Thinking counters NOT_REPORTED, không coi zero hoặc cộng token detail hai lần. Mean known total501.77 tokens/response. Latency14 attempts:mean2343.93ms,median1902.5ms,p95/max6229ms (nearest-rank,small n). V2 median1882ms,total2875; v3 median1937.5ms,total3164; smoke median2520ms,known484. Tổng từ first dispatch đến last failed response191665.48ms (~3m11.7s), limiter wait26441ms. Không tính thời gian viết code/report thành model latency.

[Aggregate ledger](nlu-quality-v1/budget-accounting-v1.json):14/120 attempts,diagnostics12/12,smoke2/3,primary0/40,improved0/40,supplemental0/25;concurrency1;min spacing5s;max6 requests/rolling60s;max104448 reserved tokens/rolling60s;≤200000;input16384/output1024;call≤10s;aggregate deadline≤60min;automatic retries0. Counter giữ tổng qua ba process; không countTokens/request splitting/fallback model. Campaign sealed do unknown usage, không dùng remaining106 requests sau stop.

Observed critical unsafe inference/provenance fabrication/unsupported confirmations/unauthorized tool proposals0 trên responses hợp lệ; unauthorized READ/WRITE/domain/Supabase executions0 theo standalone execution path. Chỉ synthetic utterances, không data khách/hotel/booking/payment thật. Injection resistance NOT_TESTED vì không gửi DENY. Real leakage observed0 theo synthetic-only inputs và scan; không claim kiểm chứng độc lập toàn provider. Feature defaults OFF/FAKE/liveOFF; ordinary turn30s/model3/rounds2/proposals6/HTTP6/tool10s/retries0, A01–A10/PolicyExecutor/GroundingGate/trusted provenance không đổi. D07/D08 unaccepted.

## Artifacts và bước tiếp theo

Machine outputs: [metrics](nlu-quality-v1/aggregate-metrics-v1.json), [40 unchanged planned rows](nlu-quality-v1/baseline40-observations-v1.json), [diagnostics](nlu-quality-v1/diagnostic-observations-v1.json), [smoke](nlu-quality-v1/smoke-observations-v1.json), [CSV](nlu-quality-v1/observations-v1.csv), [versions](nlu-quality-v1/contract-versions-v1.json), [budget](nlu-quality-v1/budget-accounting-v1.json), [verification](nlu-quality-v1/verification-v1.json). Improved40/replay/supplemental statuses là file riêng NOT_RUN; không giả output.

[Original hashes](nlu-quality-v1/preserved-hashes-v1.json) đều unchanged: VEX1 `7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3`; VNE1 `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`; historical `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f`; oracle `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`. Previous reports/observations giữ nguyên. Local raw13 responses nằm ignored `.local/batch10b4d/*.responses/`, explicit debug only; không headers/credentials. Không có response raw của provider failure.

Đề xuất tiếp theo vẫn **targeted reference-price NLU improvement + hoàn tất40**, chưa chuyển sang context-aware10B4E: điều tra provider/schema conditioning bằng scalar status và bounded synthetic requests trong một campaign mới được xác định rõ; dùng các failures hiện có để nghiên cứu vì sao flag bị thay bằng starRatings CLEAR. Giữ validation strict; chưa có bằng chứng cần thay parser. Sau khi pipeline/provider có thể tiếp tục đáng tin cậy mới chạy đủ40 với frozen version, rồi làm paired regression comparison; không yêu cầu authorization infrastructure mới.
