# Batch 10B.4C-OFFLINE — Vietnamese LLM extraction integration

Ngày 23/11/2026, Asia/Ho_Chi_Minh. **DONE trong phạm vi implementation internal/offline, fake provider only.** Đây là executable extraction orchestration, không chỉ prompt/schema. Không tuyên bố real-model Vietnamese accuracy, D07/D08 acceptance hoặc production readiness. 10B.4C-LIVE **NOT_STARTED / NOT_READY để execute**; cần phê duyệt riêng và giải quyết các prerequisites cuối báo cáo.

## 1. Git baseline và bảo toàn workspace

Backend branch `feat/ai-agent-read-service`, HEAD `fa388a2b2fa5787bafed7fa85324cb543a22acc7`. Workspace root không phải Git repository. Root AGENTS đã đọc; recursive backend scan không tìm thấy nested AGENTS. Chỉ backend Agent và Docs/ai-agent nằm trong scope.

Preflight `git status --short` có existing dirty work, không phải thay đổi của batch này:

|Loại|Nội dung có sẵn|
|---|---|
|Tracked, unstaged|`.idea/compiler.xml`, `.idea/encodings.xml`; Agent README; GeminiBudget, GeminiHttpTransport, GeminiLlmProviderAdapter, GeminiProperties, GeminiSyntheticSmoke; AgentOrchestrator, ConversationState, EvidenceLedger, GroundingGate, InMemoryConversationStore, PolicyExecutor, SearchCriteriaState; ToolArguments; application.yml; AgentFoundationTest, GeminiProviderTest, GeminiTransportTest, OrchestrationTest, ReadToolsTest|
|Untracked reports|BATCH10B4A_LIVE_DIAG_AUTO/EXECUTION/FIX/PREFLIGHT; BATCH10B4B_CONTRACT/DECISIONS/FIX/VIETNAMESE_NLU_INTEGRATION|
|Untracked implementation|GeminiDebugSession/SmokeApproval/SmokeDiagnostics/SmokeWire; existing nlu package/schema; ReadComparison, ResponseSemantics, ResultSetReferences, SelectedTarget, TrustedCriteriaConfirmation|
|Untracked tests/resources|GeminiDebugSessionTest/GeminiFirstCallTest; existing nlu tests/resources; ApprovedDecisionsTest, HistoricalEvaluationTest, ReadContractTest, ReadFailureContractTest, VietnameseConversationFixTest|
|Index|Không có staged diff|

Tất cả work có sẵn được giữ. Batch chỉ patch thêm phần liên quan trong AgentOrchestrator/README và tạo năm file mới được liệt kê cuối báo cáo. Không stage/commit/push/stash/reset/clean/checkout; không xóa untracked. `git diff` so với HEAD bao gồm các batch trước, không gán toàn bộ diff đó cho 10B.4C.

## 2. Preflight, source và baseline đã thực thi

Đối chiếu BATCH10B4B_DECISIONS/CONTRACT/FIX/VIETNAMESE_NLU reports, 10A architecture/review/decision register/evaluation, 10B.2 READ tool contracts/report, 10B.3 orchestration/grounding, 10B.4A adapter/synthetic diagnostics và Agent README. Source authoritative đã kiểm tra: LlmProviderAdapter, Gemini adapter/protocol/configuration, FakeLlmProviderAdapter, ExtractionProposal/schema, VietnameseParser/Numbers, DestinationLabels, ReadIntentRouting, ComparisonRouting; AgentOrchestrator, SearchCriteriaState, ConversationState/Store/InMemoryStore, PolicyExecutor, SelectedTarget, ResultSetReferences, TrustedCriteriaConfirmation, ReadComparison, EvidenceLedger, GroundingGate, ResponseSemantics, TurnBudget, ToolArguments/ToolId và historical runner/tests.

Trước edit chạy từ backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
```

**BUILD SUCCESS, 592 tests, 0 failures, 0 errors, 0 skipped**; historical **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Không discrepancy so với yêu cầu. Java21/Boot3.3.4/LangChain4j1.17.0 giữ nguyên; không POM/dependency/model/config change.

Finding: strict `ExtractionProposal.parseModelJson` đã có nhưng chỉ là seam, chưa gọi provider trong Vietnamese turn. D1/D2/D3 và typed READ/grounding/state đều đã tồn tại. Gemini adapter chỉ admit identity-bound synthetic grants, không phải arbitrary business extraction. Không mở grant hoặc sửa HTTP transport để nối offline path.

## 3. Executable integration

Entry point Java-only:

```java
AgentOrchestrator.turnVietnameseWithLlm(conversationId, expectedRevision, message, cancelled)
```

Luồng: feature/FAKE gate → bounded input → existing exclusive lease/expected revision → deterministic security preflight → extraction `ChatRequest` qua existing `LlmProviderAdapter.generate` và existing cancellable worker → strict JSON → provenance/literal corroboration → existing criteria update/routing → PolicyExecutor → fixed READ registry → GroundingGate → existing structured Result/state commit.

`turnVietnamese(...)` giữ deterministic compatibility path; `turn(Request)` giữ typed internal path. Không public endpoint, tool executor callback, new provider stack, second conversation store hoặc shadow state. Extraction request không có tool declarations và không có conversation evidence/identity/credentials. User utterance là UserMessage data dưới bounded fixed SystemMessage. Tool requests trong extraction response bị từ chối trước registry.

Entry mới yêu cầu enabled=true **và configured provider=FAKE**. Khi feature OFF hoặc provider khác FAKE, DISABLED trước provider. Tests inject FakeLlmProviderAdapter multi-step scripts qua cùng ChatRequest/ChatResponse/CallControl interface mà Gemini adapter implements. Không hidden fallback/retry/key lookup. Gemini transport/grants/model/properties không sửa; arbitrary extraction vẫn không được live adapter admit. Đây là shared provider contract verification, **không phải exact extraction schema qua live Gemini SDK/HTTP acceptance**.

Sau extraction, existing orchestration vẫn dùng scripted model tool proposals và evidence-ID-only selection. Backend quyết định exposed tools, arguments/criteria/targets, budget admission và rendered facts. Không cho model tự thực thi tool hoặc viết factual answer. Một browse/search thành công thường model3 = extraction + proposal + evidence selection. Comparison dùng backend fresh-read flow nên chỉ cần extraction model1. Không tăng cap để accommodate extra calls; flow cần thêm model round phải stop.

## 4. Prompt và strict schema

Prompt cố định tại `VietnameseExtraction.INSTRUCTION`; schema riêng `nlu/vietnamese-extraction.schema.json`. Giữ schema/extraction protocol cũ để compatibility. Raw-root JSON schema được gắn vào responseFormat của ChatRequest hiện có; không tool+schema combination.

Root yêu cầu đúng sáu keys: `action`, `intent`, `fields`, `reference`, `missingSlots`, `inferredFields`; additionalProperties=false. Actions: CRITERIA, SELECTED, COMPARISON, CLARIFY. Intents giữ SEARCH/BROWSE/REFINE/CLARIFY/UNSUPPORTED/UNVERIFIABLE. Không WRITE enum. `fields` chỉ 11 keys hiện hữu: cityName/hotelName/checkIn/checkOut/adults/children/rooms/minPrice/maxPrice/referencePriceExclusive/starRatings. Mỗi change là CLEAR hoặc REPLACE/value; referencePriceExclusive chỉ REPLACE true, không CLEAR.

Giới hạn: input4096 UTF-8 bytes; proposal envelope8192 UTF-8 bytes, nested legacy criteria JSON4096 chars; JSON depth10 (nested legacy criteria depth8), string200, numeric token24; cityName120/hotelName200; reference200 chars; missing/inferred arrays≤11, unique allowlisted names; stars≤5 unique integers1..5. Counts/stay/money tiếp tục validator cũ: rooms1..20, adults1..40, children0..40, ISO civil date/past/max30nights, nonnegative ordered price. Numeric corroboration so sánh exact decimal value, không dùng floating-point hoặc phụ thuộc IntNode/DecimalNode representation.

SELECTED/COMPARISON yêu cầu intent CLARIFY, fields rỗng, reference đúng toàn bộ utterance; backend diễn giải expression bằng finite grammar. CRITERIA/CLARIFY reference phải rỗng. Missing/inferred hints là untrusted metadata; backend tự tính criteria còn thiếu. Không output free-form clarification, status, tool name, URL/header/secret/system instructions, IDs hoặc confirmed/provenance authority fields.

Runtime dùng strict duplicate detection, trailing-token rejection, required/unknown-field/type/enum/size validation, legacy proposal validator và authoritative criteria validator, không JSON repair. Unsafe controls/format/surrogate hoặc privileged URL/command strings bị chặn. Model-forged USER_CONFIRMED, hotelId/roomId, tool calls, hallucinated price/availability không đi vào facts.

## 5. Provenance: model proposes, backend disposes

Parsed model changes luôn MODEL_PROPOSED; không gọi `patch(true)` trên model proposal. `corroborate` yêu cầu intent, full changed-key set, operations và exact values khớp một **independent deterministic literal extraction** của câu gốc (chỉ finite, server-owned equivalences). Không partial apply để bỏ constraint chưa hiểu. Nếu grammar trả clarification, model không được thêm criteria. Inferred fields không được admit; không tự đổi guess thành confirmed. CLARIFY không được mutate criteria.

Chỉ proposal literal do backend parser tạo mới đi qua `patch(true)` và existing state validation. Đây là xác lập bằng chứng chat độc lập, không relabel model output: explicit dates/destination/name/price thành USER_PROVIDED; rõ adults/children/rooms theo grammar D3 thành USER_CONFIRMED. “2 người” không thành hai adults/zero children. Model tự thêm dates khi utterance không có ngày, đổi destination, thêm stars/price hoặc omit explicit constraint đều bị reject. Trusted internal UI capability vẫn private/package-owned contract cũ, không nhận model JSON.

Destination label chỉ admit canonical match trong reviewed label snapshot hiện có, không tạo city ID hoặc chứng minh backend city catalog. Quoted label ngoài snapshot cũng không được admit trong entry LLM mới. HotelName vẫn bounded literal search query, không phải trusted hotel identity. SelectedTarget và ResultSetReferences chỉ resolve qua state/evidence cũ, không từ model.

## 6. Security preflight

Trước generate: bounded byte count; reject control/format/surrogate; chặn literal WRITE/booking/hold/payment/refund/cancel/update/delete, privileged API/URL, system/developer/prompt override, tool/policy override và common secret-exfil vocabulary. Mixed READ+WRITE bị reject cả câu, không âm thầm tìm kiếm. Existing parser UNSUPPORTED/UNVERIFIABLE fence cũng chạy trước provider, bao gồm bịa dữ liệu/weather/promotion/distance đã nhận biết.

Keyword fence có tính bảo thủ, **không phải general semantic jailbreak classifier**. Nếu vocabulary chưa nhận biết, schema/literal corroboration/policy/grounding vẫn là authorization/factual boundary: unknown constraints không được bỏ để thực thi safe-looking READ. Không tool cho arbitrary HTTP, không model-selected route/header, không WRITE capability.

## 7. Vietnamese NLU improvements và giới hạn

|Nhóm|Behavior offline mới|Giới hạn giữ nguyên|
|---|---|---|
|Missing slots/E04-like|Finite chỗ nghỉ→khách sạn, cho đoàn, vào ngày; interval “15 đến17/12/2026” dùng đúng năm được viết. Giữ cả cặp ngày; hỏi destination và occupancy, không hỏi lại ngày đủ.|Không đoán năm từ current date; yearless/ambiguous câu vẫn clarify. Không general paraphrase acceptance.|
|Price/E05-like|Literal reference-price proposal qua exact decimal corroboration; ambiguous currency/unit/range có fixed clarification; negative/reversed proposals fail closed.|Không swap range hoặc gán currency cho bare values. Final payable price không được extract làm fact.|
|Filters/E06-like|Thông báo không thể áp dụng filter chưa có verified mapping, zero search; không silently drop pool/nearby/sort constraints.|Không thêm amenity/type/sort mapping hoặc gọi Place.|
|Destination/E15-like|Sai label/guess/city ID bị reject; label query ngoài reviewed snapshot không admit.|Không biến câu vague thành EMPTY/search ở destination cũ.|
|Combined price+availability/E21-like|“Phòng này giá bao nhiêu và còn phòng không?” route AVAILABILITY qua trusted room selection; D3 chưa đủ thì NEED_OCCUPANCY/zero A05.|Không thêm price-only partial contract khi thiếu confirmation; không dùng old room price để fill answer.|
|Comparison/E27/E28-like|Model đề xuất exact utterance reference; existing first-two/ordinal/names/vừa tìm grammar resolve trusted refs; thêm bounded price-only/review-only first-two expressions.|Không fuzzy names/arbitrary IDs/room-pair; không mint refs từ ledger setup.|
|Unsupported/fabrication/E33/E34-like|Giữ deterministic refusal/unverified response trước model cho recognized requests.|Không đổi REFUSED thành ANSWER để thỏa historical oracle.|
|Loop/body/E39/E40-like|Shared caps vẫn stop; oversized loopback READ response typed RESPONSE_TOO_LARGE, no facts.|Không mở all-data search hoặc bật gzip; không coi non-admitted historical fault exercised.|

NLU vẫn conservative: model là proposal gate có corroboration bằng finite grammar; những cách nói ngoài grammar sẽ hỏi lại. Không có bằng chứng real-model recall/precision/latency hoặc Vietnamese general understanding. Các cải thiện trên là synthetic supplemental cases, không tự đổi historical status.

## 8. D1/D2/D3 và multi-turn

D1 automatic browse chạy sau validated criteria/state merge như trước. Không dates/confirmed group thì browse label-only/reference-price theo policy đã duyệt; có partial stay/group không downgrade. Browse chỉ reference/base price, unavailable dates/availability null, not held/not final price.

D2 chỉ issued result-set identities từ rendered grounded A01. Existing identity TTL5m và fact freshness30s giữ riêng; A01/A05 prior-turn không CURRENT. LLM comparison resolve exact session/revision/ref rồi fresh A01 + A02 + A02, cùng budget; partial detail failure chỉ render fresh verified subset, không old price/lowestBasePrice masquerade. Missing refs trả SELECTION_REQUIRED/zero domain HTTP. Không refresh identity TTL bằng model.

D3 chat confirmation và internal capability giữ nguyên. Positive new test: internal confirmation → fresh A04 → server selection → LLM extraction SELECTED → A05 grounded not-held. Negative selected original USER_PROVIDED counts → NEED_OCCUPANCY, zero A05. Không có authenticated frontend/UI integration mới; INTERNAL_ONLY vẫn là giới hạn.

Một TurnBudget tạo trước lease/parsing, không tạo lại sau extraction. Existing lease giữ xuyên provider call và commit; stale expectedRevision/busy session chặn trước model. Provider late/timeout/cancel không commit proposal; current immutable state không thể bị concurrent accepted update vượt lease. New offline path tái kiểm session expiry sau extraction và trước commit; expiry discard với existing conflict category. Criteria material changes vẫn invalidate selection/refs/evidence theo revision. Không direct model state edit, session owner, evidence relabel hoặc TTL mutation. Đây không phải latest-event-wins delivery/E30; exclusive lease conflict test không được gọi E30 PASS.

## 9. Budgets, grounding và responses

Hard caps không đổi: turn≤30s, model≤3, rounds≤2, proposals≤6, actual HTTP≤6, each tool≤10s và remaining turn; retries0. Reserve extraction model call trước generate, dùng existing model timeout/deadline/cancellation poll/future. Không retry malformed JSON/provider error hoặc model self-repair. Failed reservations không tạo budget mới. Existing ToolExecutionContext attempt ledger vẫn là nguồn đếm HTTP, PolicyExecutor vẫn là sole dispatch.

GroundingGate không sửa: model cuối chỉ chọn existing evidenceIds; whitelist renderer quyết facts/identity/price/availability/reviews. Fabricated raw factual JSON bị GROUNDING_REJECTED; no facts/no result refs. Timeout/dependency failure không EMPTY; partial chỉ có verified facts và typed errors. Giữ ANSWER/EMPTY/CLARIFICATION/REFUSED/UNAVAILABLE/PARTIAL/POLICY_STOP và existing safe categories, không invent response error từ lỗi chưa xảy ra. Extraction provider failures dùng MODEL_* safe category hiện hữu, map typed UNAVAILABLE; invalid extraction dùng INVALID_NLU/CLARIFICATION.

## 10. Synthetic LLM-assisted evaluation

New `LlmVietnameseOfflineTest`: **78 invocations**. Scripted extraction responses là synthetic test-owned proposals, không tự tạo bằng live model; loopback fixtures là backend-shaped synthetic data. Đây là integration/negative verification, không real-model language evaluation.

|Nhóm case thực thi|Kết quả/guard được assert|
|---|---|
|Automatic/explicit browse, chỗ nghỉ alias, complete SEARCH|Grounded A01, shared model3/HTTP1; no inferred occupancy; explicit group independently confirmed|
|Reference-price bound|Exact2000000 VND value, exclusive local page filter; no floating-point tolerance|
|Missing destination/group with explicit shared-year dates|Retain dates, ask missing group/destination; model1/HTTP0|
|Uncorroborated dates/counts/destination/name/price/stars; generic2people|INVALID_NLU, no state commit/HTTP; parsed model slots remain MODEL_PROPOSED|
|Unknown filters/destination/currency/unit; forged reference/unmapped quoted destination|Clarification/rejection, no tool execution or omitted constraint|
|WRITE/mixed READ+WRITE, URL/API/key/prompt/tool override, weather/fabrication|Before model, zero model/HTTP/facts|
|Malformed/fenced/trailing/oversized JSON, duplicate/unknown/missing keys, wrong enum/numeric type|Strict fail closed, no repair/retry or domain HTTP|
|Forged confirmation/provenance/hotelId/roomId; invalid bounds/inference/duplicate hints|Strict rejection, no model authority/state commit|
|Provider TIMEOUT/FAILURE/MALFORMED, extraction tool request|Safe typed failure, one counted model call, no HTTP/state commit|
|Model/proposal/shared HTTP budgets; oversized READ body|Existing caps stop, no unbounded search; typed RESPONSE_TOO_LARGE/no facts|
|Grounding fabricated price/availability output|GROUNDING_REJECTED/no rendered facts/no issued refs|
|Two-turn comparison, inventory TTL elapsed, fresh A01/A02, partial detail503|ANSWER or PARTIAL, one extraction call/three HTTP, facts restricted to verified refreshed subset|
|Missing trusted comparison refs|SELECTION_REQUIRED, no minted refs or HTTP|
|Selected missing confirmation vs internal D3 confirmation→A05|Negative zero A05; positive current availability/checkedAt/reservationHold=false|
|Feature OFF, stale revision/busy lease, controls/input size|No provider calls on nonadmitted inputs|
|Late provider, cancellation after extraction, session expiry during extraction|No proposal commit/no domain HTTP; lease cannot be superseded|

Existing592 tests retained, including ApprovedDecisionsTest79 separately. Those79, new78 and original historical outcomes are three different accounting sets.

## 11. Historical E01–E40 before/after

Original manifest/fixtures/hardAssertions/expected outcomes/forbidden behaviors không sửa; runner không sửa. SHA-256 normalized pinned JSON `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f` được runner assert. Historical runner tiếp tục deterministic `turnVietnamese`, không thay scripts để gọi new LLM entry hoặc thêm confirmation/ref setup. 39 executed; E30 NOT_RUN. Generated artifact: `ai-agent-service/target/historical-evaluation-results.json`, chỉ do runner tạo.

**Before23/16/0/1 → after23/16/0/1. Zero baseline historical PASS regressions. Zero historical cases newly claimed improved.**

|Case|Before|After|Actual category|Turn HTTP|
|---|---|---|---|---:|
|E01|PASS|PASS|GROUNDED|1|
|E02|PASS|PASS|GROUNDED|1|
|E03|PASS|PASS|GROUNDED|1|
|E04|FAIL|FAIL|NLU_CLARIFICATION|0|
|E05|FAIL|FAIL|NLU_CLARIFICATION|0|
|E06|FAIL|FAIL|NLU_CLARIFICATION|0|
|E07|PASS|PASS|INVALID_CRITERIA|0|
|E08|PASS|PASS|INVALID_CRITERIA|0|
|E09|PASS|PASS|INVALID_CRITERIA|0|
|E10|PASS|PASS|GROUNDED|1|
|E11|PASS|PASS|INVALID_CRITERIA|0|
|E12|PASS|PASS|GROUNDED|1|
|E13|PASS|PASS|INVALID_CRITERIA|0|
|E14|PASS|PASS|INVALID_CRITERIA|0|
|E15|FAIL|FAIL|NLU_CLARIFICATION|0|
|E16|PASS|PASS|READ_UNAVAILABLE|1|
|E17|FAIL|FAIL|NEED_OCCUPANCY|0|
|E18|PASS|PASS|GROUNDED|1|
|E19|FAIL|FAIL|NEED_OCCUPANCY|0|
|E20|PASS|PASS|READ_UNAVAILABLE|1|
|E21|FAIL|FAIL|NEED_OCCUPANCY|0|
|E22|PASS|PASS|GROUNDED|1|
|E23|FAIL|FAIL|NEED_OCCUPANCY|0|
|E24|FAIL|FAIL|NEED_OCCUPANCY|0|
|E25|PASS|PASS|READ_UNAVAILABLE|1|
|E26|FAIL|FAIL|NEED_OCCUPANCY|0|
|E27|FAIL|FAIL|SELECTION_REQUIRED|0|
|E28|FAIL|FAIL|SELECTION_REQUIRED|0|
|E29|PASS|PASS|GROUNDED|1|
|E30|NOT_RUN|NOT_RUN|—|—|
|E31|PASS|PASS|UNVERIFIED_CLAIM|0|
|E32|PASS|PASS|UNVERIFIED_CLAIM|0|
|E33|FAIL|FAIL|UNVERIFIED_CLAIM|0|
|E34|FAIL|FAIL|UNSUPPORTED|0|
|E35|PASS|PASS|UNSUPPORTED|0|
|E36|PASS|PASS|GROUNDED|1|
|E37|PASS|PASS|UNSUPPORTED|0|
|E38|PASS|PASS|UNSUPPORTED|0|
|E39|FAIL|FAIL|NLU_CLARIFICATION|0|
|E40|FAIL|FAIL|NLU_CLARIFICATION|0|

E04/E05/E06/E15/E39/E40 deterministic grammar/semantic gaps giữ nguyên; analogous new LLM-entry tests không được tính historical PASS. E17/E19/E21/E23/E24/E26 không admitted vì original SELECTED không có USER_CONFIRMED group; fault fixtures downstream vẫn không coi đã exercised bởi historical cases. E27/E28 thiếu issued references/fresh evidence; oracle reuse old context/zero reads mâu thuẫn approved D2. E33 oracle ANSWER/context-price khác current safe UNVERIFIED_CLAIM; E34 expected UNAVAILABLE khác deterministic REFUSED. Không đổi security semantics để green oracle. D1 E03 PASS được giữ; D3 không tự sửa fixtures. E30 latest-event delivery remains NOT_RUN.

## 12. Verification commands và totals

Tất cả Maven commands offline, từ backend, không skip tests:

|Run|Kết quả|
|---|---|
|Initial `mvn -o -B -ntp -pl ai-agent-service package`|592 PASS,0 failures/errors/skipped; historical23/16/0/1|
|Targeted `-Dtest=LlmVietnameseOfflineTest test` checkpoint1|59 PASS|
|Targeted same suite checkpoint2|67 PASS|
|Full package checkpoint1|668 PASS (592+76), historical unchanged|
|Full package checkpoint2|670 PASS (592+78), historical unchanged|
|Final full package, after final source audit|**BUILD SUCCESS, 670 PASS (592 retained +78 new),0 failures/errors/skipped**,23:41:57 +07:00; historical23/16/0/1|

Các checkpoint không cộng dồn. Không test thất bại hoặc skipped trong các runs nêu trên. New price corroboration audit phân biệt numeric value với Jackson node representation; positive reference-price test giữ exact money assertion. Final schema audit bổ sung rejecting CLEAR cho referencePriceExclusive và hidden Unicode trong proposal; không nới assertions. Standard Java21 SecurityManager/ByteBuddy warnings và intentionally-invalid configuration-test warnings có sẵn, không upgrade JVM/dependencies để xóa chúng.

## 13. Security/scope audit và authored files

Authored files (paths tương đối backend root):

|File|Thay đổi batch này|
|---|---|
|`ai-agent-service/src/main/java/com/example/aiagent/orchestration/AgentOrchestrator.java`|Add LLM_OFFLINE mode/entry, preflight/extraction/corroboration under existing lease/budget, expiry guards; compatibility modes retained|
|`ai-agent-service/src/main/java/com/example/aiagent/nlu/VietnameseExtraction.java`|New prompt/request/strict parse/provenance corroboration/finite equivalences|
|`ai-agent-service/src/main/java/com/example/aiagent/nlu/VietnameseSecurityPreflight.java`|New bounded deterministic pre-model fences|
|`ai-agent-service/src/main/resources/nlu/vietnamese-extraction.schema.json`|New closed extraction schema|
|`ai-agent-service/src/test/java/com/example/aiagent/orchestration/LlmVietnameseOfflineTest.java`|New synthetic fake-provider integration/negative suite|
|`ai-agent-service/README.md`|Add current checkpoint pointer and offline entry/limits documentation|
|`Docs/ai-agent/BATCH10B4C_OFFLINE_REPORT.md`|This report|

No authored changes outside ai-agent-service/Docs-ai-agent; existing IDE/Gemini/config/domain dirty work left intact. No previous report/evaluation plan/original fixture modifications. No frontend/Place/Gateway/Hotel/Booking/Payment/Refund/database/migration/Supabase/Config Server edits. No dependency/model upgrades, WRITE tools or public ingress. Feature defaults AGENT_ENABLED=false, providerFAKE, Gemini livefalse remain as found. No payload logger, credential file, `.env`, CA, management secret or live journal read/write. Synthetic terms in tests are not real credentials/customer/hotel data.

Final suite totals: AgentFoundation32, GeminiDebugSession3, GeminiFirstCall56, GeminiProvider75, GeminiTransport17, VietnameseGrammarFix41, VietnameseNlu64, ApprovedDecisions79, HistoricalEvaluation audit1, LlmVietnameseOffline78, Orchestration70, ReadContract31, ReadFailureContract6, VietnameseConversationFix9, ReadTools108 = **670**, all0 failures/errors/skipped. Historical audit1 is accounting integrity, not40 acceptance PASS.

Final schema/UTF-8/newline/whitespace checks passed for authored files; pinned historical SHA matches. `git diff --check` identified one added README EOF blank line during final documentation audit; that added blank was removed and check rerun. Existing LF→CRLF Git warnings remain. Index remains empty; HEAD unchanged. Source/file-scope review found only the seven authored files listed above; existing unrelated dirty files remain. Maven build outputs under ignored target are generated only by lifecycle/tests, not hand-edited.

Gemini live attempts during this batch: **0**. Real domain/Gateway/Supabase calls: **0**. Only synthetic loopback HTTP; no Docker/service startup or production rollout. Source review and fake tests are not a comprehensive penetration test or real-service acceptance.

## 14. Remaining blockers và separately approved LIVE prerequisites

Offline implementation is reviewable; LIVE execution is not authorized or enabled. Before a separately approved 10B.4C-LIVE:

1. Explicit synthetic-only live extraction scope/protocol/fixture approval; existing Gemini grants allow fixed prior synthetic fixtures only. Do not pass generic user/business extraction through the old permit or reopen sealed diagnostics session. Any new live request admission requires reviewed identity-bound fixtures and adapter boundary review in that separate batch.
2. Verify exact existing model/account/region/Free Tier/data/privacy terms, quota/billing/exclusive budget, finite token/request/concurrency/spend caps, stop conditions and transport/schema compatibility. No model switch/dependency upgrade assumed. Operator evidence is distinct from tests' synthetic attestations.
3. Live schema dialect/latency/cost/usage/timeout acceptance and Vietnamese semantic evaluation remain NOT_RUN. Define approved evaluation criteria/D07 before calling any synthetic pass production acceptance; D08 release/rollout remains separate.
4. Resolve finite grammar/catalog mapping and price-only partial policy limits as needed. Historical16 FAIL/1 NOT_RUN remain visible; do not amend original oracle or inject confirmation/ref provenance.
5. Authenticated ingress/session ownership, UI-action attestation, durable/multi-replica state/replay and ordered latest-event cancellation/delivery are absent. INTERNAL_ONLY capability/in-memory snapshot identity is not authentication. Broader real-data/retention/public ingress approval is not implied by offline completion.

No live request, data provisioning, key lookup, flag enabling or next-batch execution was undertaken. No claim of general Vietnamese real-model accuracy, full E01–E40 acceptance, D07/D08 or production readiness.
