# Batch 10B.4C — live compatibility fix and evaluation

24/11/2026, Asia/Ho_Chi_Minh. **COMPATIBILITY_FIXED_SMOKE_FAILED**.

**COMPATIBILITY_FIX_DONE**: tái hiện lỗi trên HTTP response thật, sửa validator metadata, replay offline và xác nhận Gemini live thành công. **LIVE_INITIAL_SMOKE_PASS chưa đạt**: VEX1-01 PASS; VEX1-02 thiếu `referencePriceExclusive=true`, backend từ chối; STOP_ON_ANY_FAILURE giữ VEX1-03 NOT_RUN. **LIVE_NLU_EVALUATION_DONE chưa đạt**: cả 40 mẫu chính NOT_RUN vì smoke gate thất bại. Không dùng diagnostic thay thế smoke, không sửa oracle hoặc chạy lại để che lần thất bại.

Đã thực hiện **7/60 actual Gemini HTTP attempts**, concurrency1, automatic retries0: compatibility2, final smoke2, main0, supplemental3. Không còn tiến trình live. Supplemental chỉ khảo sát lỗi giá đã quan sát, không phải bộ 40 mẫu chính. Không có production readiness, D07/D08 acceptance hoặc FULL_AGENT_E2E.

## Baseline và phạm vi

Đọc root AGENTS.md; quét workspace không thấy nested AGENTS.md. Backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Không checkout/stage/commit/push/stash/reset/clean. Frontend và các domain services ngoài phạm vi.

Working tree trước tác vụ đã có thay đổi IDE compiler/encodings, Agent README, GeminiExtractionProtocol/Session/Smoke và nhiều lớp/test/report local/signed evaluation chưa tracked. Giữ nguyên các thay đổi đó. Batch này bổ sung trên runner/Smoke hiện hữu; không nhận các phần từ batch trước là mã mới của batch này. Không sửa POM, Java21, Boot3.3.4 hoặc LangChain4j1.17.0.

Baseline thực chạy `mvn -o -B -ntp -pl ai-agent-service package`: **978 tests, failures0/errors0/skips0, BUILD SUCCESS**, 1:09. Historical evaluation vẫn **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**, E30 NOT_RUN. Đã đọc source pipeline, dataset/oracle, metrics, parser/extraction/orchestrator, transport/adapter, README, practical architecture và các evidence V1.

## Raw Gemini response và nguyên nhân chính xác

Compatibility diagnostic01, run `f846d968-aba1-4725-b3bd-27902fc7ba35`, gửi nguyên VEX1-01/prompt/schema/request cũ; HTTP200, latency2727ms. Request digest vẫn `660335d24ae98c2bdd6280e8d8c34e10f8c523bb1b0fea2e5cf6bd61500c66b4`.

Response có một candidate, `content.role="model"`, một text part kèm `thoughtSignature`, `finishReason="STOP"`, index0. Nội dung text là JSON hợp lệ:

```json
{"intent":"SEARCH","fields":{"cityName":{"operation":"REPLACE","value":"Đà Lạt"}},"action":"CRITERIA","reference":"","missingSlots":[],"inferredFields":[]}
```

Metadata thực tế:

```json
{"promptTokenCount":214,"candidatesTokenCount":79,"totalTokenCount":293,"promptTokensDetails":[{"modality":"TEXT","tokenCount":214}],"serviceTier":"standard"}
```

Điểm từ chối là `GeminiExtractionSmoke.Wire.validate(String)`, nhánh kiểm tra `fields(usage)`: allowlist cũ chỉ có prompt/candidate/total/thoughts/cached counters. `promptTokensDetails` và `serviceTier` làm `containsAll(fields(usage))` trả false và ném `ProviderException(Category.MALFORMED_RESPONSE)` **trước SDK deserialization và trước VietnameseExtraction.parse**. Vì gán input/output/total nằm sau assertion, lần quan sát gốc ghi UNKNOWN dù raw body có counters. Đây không phải lỗi hiểu “Đà Lạt”, JSON extraction hay thoughtSignature.

Diagnostic01 không thu stack exception đầy đủ ở thời điểm dispatch. Class/category và vị trí throw được xác định từ nhánh code và tái hiện chính xác predicate bằng recorded-response regression; không bịa một stack trace đã thu. Pipeline mới ghi stage/class/first stack location cho lỗi tương lai, không ghi exception message/body tùy ý.

[Gemini API reference](https://ai.google.dev/api/generate-content#UsageMetadata) xác nhận đây là metadata hợp lệ; detail breakdown không cộng thêm vào counters tổng. Bytecode thực tế của SDK1.17.0 (`javap -p -c ...Json`) cho thấy mapper disable FAIL_ON_UNKNOWN_PROPERTIES; UsageMetadata record chỉ nhận năm counters cũ. Replay toàn bộ raw body qua SDK đã PASS. Vì thế không cần nâng SDK hoặc thay HTTP stack. `serviceTier=standard` là metadata request, không phải bằng chứng billing/Free Tier.

**Giới hạn lịch sử:** raw response của lần thất bại trong batch trước không được lưu. Lỗi hiện tại được tái hiện trực tiếp với request giống nhau nhưng không thể khẳng định hai response có cùng bytes/nguyên nhân. Original first-run JSON/analysis không bị sửa.

## Bản sửa và bằng chứng kiểm chứng

- `GeminiExtractionSmoke`: bỏ closed allowlist chỉ ở usage metadata; tiếp tục bắt buộc ba integer counters không âm, kiểm tra optional thinking/cache counters, quan hệ counters và caps. Không cộng cached/detail counts lần nữa. Metadata mở rộng không được dùng làm authority. Required envelope/candidate/content/parts, STOP, giới hạn response và strict extraction giữ nguyên.
- Detailed outcome phân biệt provider envelope, usage, SDK, extraction JSON và backend corroboration; JSON extraction sai có category `EXTRACTION_SCHEMA_INVALID`. Main chỉ có thể tiếp tục loại lỗi contained này nếu usage đã được validate và không có safety flags. SDK/envelope/usage/quota/budget/safety failures vẫn dừng. Signed admission và ordinary Agent không được mở rộng quyền.
- `GeminiLocalEvaluation`: opt-in `--debug-response`, fixed compatibility diagnostic VEX1-01, fixed ba supplemental probes; không arbitrary input/model/endpoint. Hard cap local giảm70→60. Result v2 ghi prompt/schema digest, utterance synthetic và pipeline; response-validation version `gemini-envelope-usage-extensions-v2`.
- Debug writer chỉ nhận response body; không nhận headers. Redact active key và credential patterns/fields thông dụng. Không bật logging raw trong ordinary runtime. Raw local nằm trong `.local/`, ignored bởi Git; artifacts CREATE_NEW.
- README cập nhật commands, artifact paths, continuation/stop semantics và aggregate limits giữa tiến trình. Không tạo authorization/financial/debug framework mới.

Prompt/schema/wire protocol **không đổi**, vẫn `vietnamese-extraction-compact-v1`. Instruction digest cũ/mới đều `48b6b4eb7d57cbf1d13ce6a561bc0f82ff72a94de8c38a99ad9670754f64f8eb`; compact schema digest cũ/mới đều `d8617d8e373927d539535d5d4f3ac826b92f28bed14ae34d02cfa8350c554aa9`. Không normalization/sửa JSON model để tính PASS.

Compatibility diagnostic02, run `1c9ef0cb-61e6-4a83-91d2-1b264f6676cd`: xem [record đầy đủ](live-compatibility-v1/diagnostic-02.json). HTTP200, **envelope PASS / SDK PASS / extraction PASS / semantic PASS / backend ACCEPTED**, input214/candidate79/total293, latency2787ms. Đây là manual diagnostic sau concrete fix, không automatic retry và không được tính là final smoke.

## Final initial smoke

Run `99aea0ce-3307-41bf-b735-166d56eceb30`; frozen protocol/model/oracle, không chỉnh ngày. Combined plan3+40 được duyệt offline trước credentials. Actual initial smoke dừng ở mẫu thứ hai:

|Fixture|HTTP / envelope / SDK / schema|Semantic / backend|Tokens input/candidate/total|Latency|
|---|---|---|---|---:|
|VEX1-01|200 / PASS / PASS / PASS|PASS / ACCEPTED|214 /79 /293|3270ms|
|VEX1-02|200 / PASS / PASS / PASS|FAIL / REJECTED|221 /111 /332|1816ms|
|VEX1-03|NOT_RUN|NOT_RUN|N/A|N/A|

VEX1-01 trả đúng CRITERIA/SEARCH, cityName Đà Lạt; không dates/occupancy/availability claims. Corroboration là pure backend literal admission. Automatic BROWSE là route dự kiến của code hiện có, không phải một domain/tool execution đã thực hiện.

VEX1-02 trả đúng action, intent, Nha Trang, maxPrice2000000, missingSlots[], inferredFields[], reference rỗng; **thiếu** `fields.referencePriceExclusive={operation:REPLACE,value:true}`. Schema cho phép omission nhưng oracle và parser yêu cầu cờ này. Backend `VietnameseExtraction.corroborate` từ chối tại field-set equality: `Uncorroborated extraction`. Không có actual price/availability confirmation. Không nới backend để chấp nhận inclusive thay exclusive.

VEX1-03 vẫn giữ 01/12/2026→03/12/2026, adults2/children0/rooms1; ngày còn hợp lệ tại24/11/2026 nhưng **chưa gửi** vì STOP_ON_ANY_FAILURE. Không chạy lại final smoke; diagnostic sau đó không thay thế thất bại.

## Vietnamese NLU chính và denominator

|Phase|Planned|Attempts|HTTP success|Envelope / SDK / schema valid|Exact semantic|Backend accepted|NOT_RUN|
|---|---:|---:|---:|---:|---:|---:|---:|
|Compatibility diagnostics|2|2|2/2|1/2 /1/2 /1/2|1/2|1/2|0|
|Final initial smoke|3|2|2/2|2/2 /2/2 /2/2|1/2 (50%)|1/2 (50%)|1|
|Primary Vietnamese NLU|40|0|N/A|N/A|N/A|N/A|40|
|Supplemental reference price|3|3|3/3|3/3 /3/3 /3/3|0/3 (0%)|0/3 (0%)|0|

Primary selection/oracle40 identities và outcomes giữ nguyên. Review label **TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION**, không independent human review/blinded holdout. Dataset60 = selected40 +9 NEEDS_PRODUCT_REVIEW +11 DENY; không gửi20 excluded fixtures. Không chuyển3 supplemental thành3 primary successes/failures. Main action/intent/criteria/field/missing-slot/clarification/reference/exact-semantic accuracy đều **N/A (0 attempted)**, không 0% hiểu tiếng Việt.

Riêng smoke n2: action2/2, intent2/2, exact criteria1/2, missing slots2/2, exact semantics1/2; field cityName2/2, maxPrice1/1, referencePriceExclusive0/1. Không có positive reference/clarification case nên accuracy hai nhóm đó N/A. Supplemental n3: action/intent/missing slots3/3; city3/3, maxPrice3/3, exclusive0/3. Các số nhỏ/chủ đích này không đại diện chất lượng NLU toàn bộ.

City và price là coverage thực tế của các requests đã gửi. Dates/occupancy/natural paraphrase/informal/no-accents/refinement/CLEAR/REPLACE/reference/comparison/unsupported-constraint categories của main đều NOT_RUN. Context-dependent dimensions, conversation state application, selected-target resolution và FULL_AGENT_E2E NOT_RUN.

## Supplemental và phân tích model/backend

Ba probes có expected behavior định trước trong fixed plan, từ cùng frozen oracle; không đổi prompt giữa các requests:

|Sample / source|Exact utterance|Expected price|Actual / backend|
|---|---|---:|---|
|SUP1-REF-01 /VNE1-002|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|2000000, exclusive=true|2000000, thiếu exclusive /REJECTED|
|SUP1-REF-02 /VNE1-020|Khách sạn ở Huế giá tham khảo dưới một triệu|1000000, exclusive=true|1000000, thiếu exclusive /REJECTED|
|SUP1-REF-03 /VNE1-050|Tìm khách sạn Huế giá tham khảo dưới năm trăm nghìn|500000, exclusive=true|500000, thiếu exclusive /REJECTED|

Schema3/3 PASS, latency3560/1889/1782ms, tokens332/329/329. SUP1-REF-01 là explicit repeated-utterance diagnostic để xem omission có lặp lại, không hidden retry hay thay observation VEX1-02. Cả bốn giá reference attempts (smoke1+supplemental3) thiếu cùng cờ; số tiền và city đều đúng. Điều này ủng hộ **H2 prompt/instruction limitation**: prompt không nói khi nào phải bật field nghiệp vụ `referencePriceExclusive`, schema chỉ const true nếu field xuất hiện. Đây là attribution có căn cứ, chưa phải causal proof từ A/B prompt đã sửa. Có biểu hiện H3 schema documentation gap; schema có khả năng biểu diễn đúng nên không phải structural incompatibility. Không có bằng chứng Gemini không hiểu số tiền tiếng Việt hoặc deterministic parser từ chối một proposal đúng.

Smoke model/backend matrix: correct+accepted1, correct+rejected0, incorrect+accepted0, incorrect+rejected1; backend NOT_EVALUATED0. Supplemental:0/0/0/3 tương ứng. Diagnostic01 backend NOT_EVALUATED1; diagnostic02 correct+accepted1. Semantic false acceptance observed0; semantic false rejection observed0. Generic runner `falseRejection` theo expectedBackend gồm model proposal sai, không được dùng để đổ lỗi parser. Parser mismatch/exception của các extraction hợp lệ đã đánh giá:0; policy/corroboration rejection4 do thiếu field là safety success.

Xem [failure analysis](VIETNAMESE_NLU_LIVE_FAILURE_ANALYSIS_V1.md) cho từng expected/actual, severity và khuyến nghị. Không chạy thêm thay đổi prompt rồi gộp với campaign này; final smoke đã fail và acceptance yêu cầu3/3 cùng protocol.

## Usage, hiệu năng, an toàn và giới hạn

7 HTTP200, HTTP failures0; với validator cũ1 envelope rejection, validator sửa6/6 envelope/SDK/schema PASS. Không có extraction-schema-invalid live; nhánh này chỉ kiểm thử offline. Provider failure/invalid schema vẫn nằm trong attempted denominator; NOT_RUN41 tách riêng.

Usage ghi nguyên bản:6 known attempts = input1309/candidate599/total1908; diagnostic01 UNKNOWN1. Raw diagnostic01 sau đó replay offline thành công với counters214/79/293: tổng **bao gồm recovery có ghi nhãn** input1523/candidate678/total2201. Không sửa dòng UNKNOWN trong observation gốc và không coi unknown là0. Cả7 raw bodies không có thoughtsTokenCount; thinking **NOT_REPORTED**, không suy ra zero. promptTokensDetails trùng prompt total, không cộng thêm; cache nếu có đã nằm trong prompt. Không có cost/billing guarantee.

Latency n7: mean2547.29ms, median2727ms, nearest-rank p95/max3560ms. Total run-loop durations24422ms; first dispatch14:17:41.724, last response khoảng14:27:48.655, wall606931ms (~10m6.9s), bao gồm build/chuyển phase. Rate limiter wait5701ms. Small-n p95 chỉ mô tả.

Actual aggregate limits từ dispatch records: minimum spacing5.008s; maximum3 requests/rolling60s, reserved52224 tokens/rolling60s, dưới12/200000; concurrency1, timeout10s, retries0. Các tiến trình cách nhau trên60s; run records không reset aggregate counter. Phase caps2/5 diagnostics,2/3 smoke,0/40 main,3/12 supplemental;7/60 total; toàn bộ live nằm trong30min. `.local/batch10b4c-compat/batch-accounting.json` cộng số thực tế giữa các tiến trình. Không dùng allocation/signed grants; không có claim phối hợp quota trên toàn account.

Observed unsafe flags/provenance fabrication/unsupported confirmation0 trên responses đã đánh giá; không tool proposal/functionCall hoặc unauthorized execution được quan sát. Domain/Supabase/READ/WRITE/tool executions0 theo fixed standalone path; chỉ synthetic utterances, không customer/hotel/booking/payment data. Đây không phải live injection-resistance test: DENY không gửi, không adversarial model coverage. PolicyExecutor/GroundingGate/typed state/trusted references/A01–A10 không đổi. Ordinary source defaults OFF/FAKE/liveOFF, ordinary budgets30s/model3/rounds2/proposals6/HTTP6/tool10s/retries0; không khởi động Spring/stack hoặc database.

## Artifacts và kiểm chứng

- [Per-run records và phase metrics](live-compatibility-v1/analysis-v1.json), [all48 planned observations JSON](live-compatibility-v1/observations-v1.json), [CSV](live-compatibility-v1/observations-v1.csv). Hai diagnostic +43 primary plan rows +3 supplemental;7 attempted,41 NOT_RUN. Mỗi row có expected extraction, synthetic actual/raw extraction nếu có, pipeline/model/protocol/status/usage/latency/failure category. Dữ liệu diagnostic không trộn main accuracy.
- Raw sanitized responses: `.local/batch10b4c-compat/{diagnostic-01,diagnostic-02,primary-01,supplemental-01}.json.responses/*.json`. Chỉ7 response files, không headers. Hai recorded synthetic regression fixtures ở `ai-agent-service/src/test/resources/gemini/compatibility/` đủ tái hiện metadata defect và exclusive omission; các raw còn lại ignored.
- [Hashes](live-compatibility-v1/artifact-hashes-v1.json): manifest `7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3`; VNE dataset `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`; historical `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f`; oracle `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`. Original run SHA `0b8fa752e8a0c0f8f59711710fee2fb4e77eb12948f9b6038a35f99268531a46`; original analysis `a939aca2815e32f86f97a9d2cf8eeb9efa4b213f0920f8cda2b329829f271d02`; unchanged.

Executed focused Maven116 PASS,0/0/0. Pre-final-smoke package985 PASS,0/0/0. Supplemental build986 PASS,0/0/0. Final required `mvn -o -B -ntp -pl ai-agent-service package`: **987 PASS, failures0/errors0/skips0, BUILD SUCCESS**,1:30. Credential/link verification is recorded in [verification](live-compatibility-v1/verification-v1.json). Automated tests remain offline; no Maven test calls Gemini. New GeminiCompatibilityTest has9 focused tests: actual recorded metadata rejection predicate/full pipeline acceptance; metadata extensions/invalid extraction/missing usage; thinking/cache handling; actual omitted exclusive safe rejection; contained main-schema continuation/no retry;60-cap; opt-in debug/single pinned diagnostic; credential redaction; fixed separate supplemental plan. Existing limiter regression adjusted70→60 to enforce the task cap, not weaken assertions.

Actual batch file changes: GeminiExtractionSmoke.java, existing untracked GeminiLocalEvaluation.java, README.md, existing untracked GeminiLocalEvaluationTest.java, new GeminiCompatibilityTest.java, two recorded synthetic test response JSONs, this report, failure analysis and new `live-compatibility-v1/` evidence. Existing IDE/Protocol/Session and other untracked batch files remain outside this batch's edits. No unrelated services/config/migrations or lockfiles changed.

## Đề xuất tiếp theo

**BATCH10B4D-VIETNAMESE-NLU-QUALITY-IMPROVEMENT**: version prompt/schema descriptions để ánh xạ rõ “giá tham khảo dưới X” → maxPrice=X và referencePriceExclusive=true; giữ quy tắc không bịa đơn vị/dates/occupancy và strict backend comparison. Thêm targeted paraphrase/boundary cases, kiểm tra offline, rồi một final smoke mới được ghi riêng và frozen40 campaign khi smoke đạt. Không rewrite oracle/giảm expectation, không sửa hậu kỳ JSON model, không nới policy hay xây thêm infrastructure/authorization. Chất lượng dates/occupancy/refinement/reference chưa có live evidence để ưu tiên thay vì giả định.
