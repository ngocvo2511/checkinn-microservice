# Batch 10B.4B-DECISIONS — D1 / D2 / D3

Ngày 23/11/2026, Asia/Ho_Chi_Minh. **DONE — implementation nội bộ/offline; D3 INTERNAL_ONLY.** Không production-ready, không bắt đầu 10B.4C. Maven cuối **592 PASS / 0 failures / 0 errors / 0 skipped**. Historical gốc **23 PASS / 16 FAIL / 0 NOT_APPLICABLE / 1 NOT_RUN**. Supplemental approved-policy **79 PASS** được đếm riêng, không đổi thành historical PASS.

## 1. Git baseline và phạm vi

Backend branch `feat/ai-agent-read-service`, HEAD `fa388a2b2fa5787bafed7fa85324cb543a22acc7`; không đổi. Root workspace không phải Git repo. Đã đọc root AGENTS, tìm nested AGENTS trong backend (kể cả hidden, loại build/Git/dependencies): không có. Frontend ngoài scope, không sửa. Không commit/push/stage/stash/reset/clean/checkout/xóa untracked.

Baseline tracked modifications: `.idea/compiler.xml`, `.idea/encodings.xml`; Agent README; GeminiBudget, GeminiHttpTransport, GeminiLlmProviderAdapter, GeminiProperties, GeminiSyntheticSmoke; AgentOrchestrator, ConversationState, EvidenceLedger, GroundingGate, InMemoryConversationStore, PolicyExecutor; application.yml; AgentFoundationTest, GeminiProviderTest, GeminiTransportTest, OrchestrationTest.

Baseline untracked: LIVE_DIAG_AUTO/EXECUTION/FIX/PREFLIGHT reports; CONTRACT/FIX/VIETNAMESE_NLU_INTEGRATION reports; GeminiDebugSession/SmokeApproval/SmokeDiagnostics/SmokeWire và GeminiDebugSessionTest/GeminiFirstCallTest; NLU source/resources/tests/fixtures; SelectedTarget, ReadComparison, ResponseSemantics; HistoricalEvaluationTest, ReadContractTest, ReadFailureContractTest, VietnameseConversationFixTest. Tất cả giữ lại. Git diff so HEAD bao gồm công việc trước batch; **không** gán toàn bộ diff cho batch này. EvidenceLedger/SelectedTarget/ReadComparison và toàn bộ Gemini/provider/config/IDE files không được sửa trong batch này.

## 2. Initial verification và source inspection

Trước edit chạy đúng `mvn -o -B -ntp -pl ai-agent-service package` từ backend: **513 tests PASS, 0/0/0, BUILD SUCCESS**; historical **22/17/0/1**, đúng kỳ vọng. Đã đối chiếu CONTRACT/FIX/NLU reports, 10A architecture/review/decision/evaluation, 10B.2 READ contract/implementation, 10B.3 orchestration, 10B.4A provider/synthetic diagnostics và README với source hiện hành.

Các boundary đã kiểm: VietnameseParser/ReadIntentRouting/ExtractionProposal; criteria/provenance; state/store/lease/TTL; SelectedTarget, EvidenceLedger, ReadComparison; PolicyExecutor/TurnBudget; READ A01–A05 schemas/validation/projection; GroundingGate/ResponseSemantics; historical runner và synthetic tests. Không dùng dated reports làm runtime guarantee.

## 3. D1 — automatic safe browse

Sau deterministic parse và authoritative criteria validation, chỉ discovery request dạng tìm/có/cho tôi xem khách sạn có destination hợp lệ mới là automatic BROWSE candidate. Phải không có ngày nào, không có occupancy được cung cấp và không có hotel-name target; không có clarification/unsupported clause. Ngày một đầu vẫn hỏi đầu còn thiếu. Ngày lỗi/quá khứ/contradictory criteria bị reject; không downgrade để né validation. Availability/detail/selected-room/WRITE/input mơ hồ/unknown destination/unsupported amenities không thành BROWSE.

Không thêm ngày hoặc occupancy mặc định vào state. Backend-default quantity trong undated A01 chỉ là browse basis, không USER_CONFIRMED. Full stay + confirmed occupancy vẫn SEARCH. Typed SEARCH seam cũ vẫn yêu cầu dates, D1 chỉ áp dụng Vietnamese discovery routing.

Inclusive base/reference price filters được giữ trong A01 arguments: contract hiện tại đã hỗ trợ minPrice/maxPrice, chỉ bỏ rào Agent cũ cấm giá undated; không đổi API domain. “Giá tham khảo dưới X” có local `referencePriceExclusive` predicate trong chính SearchCriteriaState: gửi inclusive maxPrice=X như superset, rồi renderer lọc fresh cards với BigDecimal <X. Không trừ một đồng, không đổi rounding/unit. Metadata không gửi như field mới sang domain. Filter scope **RETURNED_PAGE_ONLY**, bỏ totalElements của superset khỏi rendered facts. Không page walk và không tuyên bố toàn catalog không có kết quả khi trang hiện tại không có card phù hợp. Câu “giá dưới X” thông thường chưa rõ reference/per-night vẫn hỏi theo quy tắc cũ; không mở rộng mọi strict money phrase.

## 4. D1 — grounded response

BROWSE message nêu giá cơ bản/cơ sở/tham khảo, không phải final price bảo đảm, chưa xác nhận dates/group, chưa kiểm tra phòng theo ngày, chưa giữ phòng/tạo booking; mời cung cấp ngày và số khách/phòng. Chỉ nói về trang mới đọc. availableRooms/checkIn/checkOut/checkedAt undated vẫn null theo validated projection, không tạo checkedAt. retrievedAt vẫn là thời gian READ, không giả làm availability checkedAt.

A01 valid EMPTY giữ EMPTY. 503/504/timeout không thành EMPTY. Giá từ projection, không fake price; không dùng inventory từ lượt trước.

## 5. D2 — identity tách khỏi freshness

`ResultSetReferences` private constructor; mint chỉ từ A01 current projection **đã render trong completed grounded answer**. Chứa session ID, criteria revision, expiry và tối đa 5 hotel identity/name/display ordinal; không price, availability, raw response, cached offer hay global registry. Ordinal theo cards thực sự hiển thị sau strict-price filtering.

Identity TTL tối đa **5 phút**, không gia hạn khi so sánh; kiểm future-clock, session expiry và criteria revision. Inventory/fact freshness vẫn **30 giây** và A01/A05 prior-turn vẫn không CURRENT, kể cả dưới 30 giây. Không dùng cùng một TTL để hợp thức hóa identity lẫn facts. Criteria/provenance edit invalidates refs. Fresh 404/approval denial khi comparison bỏ pair binding; transient 503/504 không biến thành identity/facts thành công.

State nằm trực tiếp trong ConversationState và InMemoryConversationStore cũ; recent8/evidence24/session cap1000/idle TTL30m giữ nguyên. Không second store/durable ownership.

## 6. D2 — conversational comparison

Grammar bounded cho first-two, thứ nhất/thứ hai, hai khách sạn vừa tìm/đó, price+review, chi tiết, “phù hợp hơn”, exact quoted/unquoted names. “Hai ... đó/vừa tìm/phù hợp hơn” chỉ resolve khi result set có đúng hai entries; first-two rõ ordinal có thể dùng trong set lớn hơn. Names chỉ resolve exact unique normalized match. Không guess A/B khi không đúng tên evidence hoặc trùng tên; arbitrary UUID/ordinal999/suffix injection/unsupported fields hỏi lại hoặc refusal, zero HTTP.

Resolve cần issued trusted result-set ref. Historical/raw A01 nằm trong ledger không được tự mint selection khi bắt đầu comparison. Existing selected room/hotel binding vẫn dùng source A02/A03/A04 và lifetime tối đa30s như trước; A01 không được biến thành SelectedTarget qua event cũ.

Comparison dùng A01 mới với cùng criteria/stay/quantity, match đúng trusted hotel IDs trong bounded page; A02 mới cho từng hotel trong cặp. **Không** gọi A02 lowestBasePrice là giá matched-offer A01 mới: renderer bỏ lowestBasePrice khỏi comparison detail facts. Chỉ giá VND/phòng/đêm từ A01 mới mới tham gia price ordering. Reviews từ fresh A01 giữ null/zero-review distinction. Nếu selected hotel không còn trong fresh page thì không có matched-offer price để so sánh. “Phù hợp hơn” trả facts và nói chưa đủ preference/evidence để kết luận chung.

Không enumerate trang tiếp theo; không invent rating/amenity/final payable/hold. Room-pair comparison và fuzzy hotel-name matching chưa có contract, tiếp tục clarify; không quảng bá hỗ trợ tổng quát mọi hotel/room pair.

## 7. D2 — partial/unavailable

Một A02 lỗi503/504: trả PARTIAL với fresh detail/offer của bên đã xác minh; không render giá cũ của bên lỗi và không kết luận bên nào rẻ hơn. Fresh A01 không có cả hai offers: detail facts có thể PARTIAL, không suy price từ A02. Cả ba refresh fail: UNAVAILABLE, zero facts. Error metadata chỉ enum an toàn; raw downstream SQL/token/URL/body không đi vào answer.

Budget stop/cancellation giữ safe policy-stop/unavailable theo policy hiện hữu, không fallback old facts. Evidence mới của incomplete failed turn không commit. Existing previous-record invalidations vẫn được giữ.

## 8. D3 — trusted internal confirmation

`TrustedCriteriaConfirmation` là typed capability event có private constructor, package-private factory `explicitInternalAction`, package-private orchestrator admission `confirmCriteria`; không public factory/JSON creator/controller/endpoint hoặc trusted boolean. Event binding tới đúng immutable state snapshot trong store, conversation và expected state/criteria revisions, bounded event ID/action ID, explicit internal-application source, occurredAt và confirmed field group.

Đây là **INTERNAL_ONLY server/application seam**. Chỉ code adapter cùng trust boundary mới được mint event sau khi kiểm chứng action thực tế. Synthetic tests mint capability trong cùng package; không tuyên bố chúng là authenticated UI events end-to-end. Model JSON chỉ MODEL_PROPOSED; không thể tạo event hoặc promote USER_CONFIRMED qua extraction.

## 9. D3 — validation, ordering, replay

Admission yêu cầu existing session, exact session snapshot capability (copied/cross-session/recreated state không được chấp nhận), expected state revision qua store.begin, criteria revision, session còn hạn, source/action provenance, event/action IDs 1..100 ký tự allowlist, action không tương lai/không trước state.lastActivity và tuổi <30s.

Allowlist fields chỉ adults/children/rooms và optional **cả cặp** checkIn/checkOut. Complete occupancy group bắt buộc; domain validator kiểm rooms1..20, adults1..40, children0..40, ISO civil dates, past/stay/date consistency. Null/model-proposed/unknown field/trusted flag bị reject trước commit. Event patch thay nhóm bằng existing SearchCriteriaState.update, không carry omitted companions.

Receipt tối đa32 event IDs/session, fail closed khi đầy (không evict ID rồi cho replay); duplicate/replay và non-increasing occurredAt bị reject. Commit đúng một state revision, criteria revision tăng khi value/provenance thay đổi. Criteria thay đổi bỏ selected target/result refs, cần fresh context/reselection; không silently rebind. Receipt và lastConfirmationAt nằm trong existing ConversationState, được giữ qua turn/selection/store commit.

Existing exclusive lease bảo vệ concurrent confirmation/turn. Hai confirmation cùng revision chỉ một commit; stale/busy còn lại fail. Không superseding accepted turn, không bypass lease. Chat explicit full counts tiếp tục USER_CONFIRMED; ambiguous “2 người”, increment, range, thiếu group/contradictory/forged instruction không tự xác nhận trọn nhóm.

## 10. D3 — availability và ownership limits

A05 vẫn cần selected room/public relationship hợp lệ, đủ valid dates và **USER_CONFIRMED** adults/children/rooms, current revision/evidence scope/policy. Selected-target identity không chứng minh occupancy. Thiếu member hỏi đúng fields (selected context không hỏi lại destination search). Literal combined price+availability route đã nhận diện; historical SELECTED thiếu provenance vẫn NEED_OCCUPANCY, không HTTP.

Supplemental xác minh internal confirmation → fresh A04 → selection event → A05, not-held; exact partial missing-occupancy clarification; forged event/client boolean/model confirmation không admitted.

**Không có authentication/ownership infrastructure hay frontend adapter được triển khai.** Local conversation ID, Java snapshot identity và session scope không phải authenticated user ownership. Future adapter cần chứng minh application source, session ownership và explicit UI action trước mint event; không nhận JSON rồi tin confirmed=true. Restart/multi-replica/durable replay protection chưa có.

## 11. READ tool budget audit

Không đổi enum quyền, registry10 fixed anonymous GETs, method/path/headers, retries hoặc dependencies. One TurnBudget trước parsing/resolve/refresh/compare; không reset counters/deadline. Successful pair refresh: model0, proposals3, rounds2, actual HTTP3 (A01 + A02 + A02), tuần tự. Không fanout/unbounded page walk.

Hard caps giữ ≤30s/turn, ≤3 model, ≤2 rounds, ≤6 proposals, ≤6 actual HTTP, ≤10s/tool và remaining turn, retry0. Reserve toàn bounded pair trước detail round; actual attempts vẫn lấy ToolExecutionContext ledger. Reduced HTTP budget test dừng sau đúng1 attempt, không thêm response model call. Cancellation và tool deadline dùng guards cũ.

## 12. Security regression

79 supplemental invocations gồm D1 valid/invalid routing, undated/dates/occupancy/price bounds, EMPTY vs outages; D2 expired/cross-session/criteria/withdrawn/ambiguous-name/ordinal/arbitrary-ID/injection/unsupported field, fresh price đổi800000→900000, fresh dated availability đổi2→1, partial503/504, absent fresh offers, all-fail, reduced shared budget; D3 client trusted flag/JSON forgery/model proposed/full-group/bounds/dates/action/session/replay/revision/lease/expiry/ordering/capacity và actual concurrent commits.

Existing513 invocations vẫn chạy: grounded citation/target/room relationships, prompt injection, WRITE, secret exfiltration, downstream errors, deadlines, zero retries, SDK/transport network guards và feature OFF. Không thêm payload logger/raw message/completion/domain response/secret trace. Scoped source/diff checks không phải comprehensive penetration test.

## 13. Test suites và thay đổi test hợp lệ

New `ApprovedDecisionsTest`: **79 PASS**; fake provider, injected clock, loopback backend-shaped synthetic fixtures. Không sửa original historical fixtures/manifest/hard assertions. Supplement tests độc lập, không đặt tên chúng thành E## PASS.

Existing test input updates theo approved behavior: ba conversation openings có một ngày rõ ràng để tiếp tục kiểm đúng missing-date-pair flow và giữ nguyên assertions về state/zero HTTP/occupancy; undated minPrice=1 nay hợp lệ nên negative schema case chuyển sang minPrice=-1; schema field count assertion đổi10→11 đúng một bounded local predicate mới. Không xóa/skip assertion hay hạ tiêu chuẩn để che lỗi. Original16 Vietnamese fixtures không đổi.

Historical runner chỉ thêm semantic automation cho E03 theo original browse/defaults/not-availability rubric đã có; không thay expected values, original JSON/SHA, hard assertion checker hoặc SELECTED provenance. Các failed historical case không được thay bằng supplemental seam tests.

## 14. Commands và kết quả

Tất cả Maven từ backend, `-o -B -ntp -pl ai-agent-service`, không skip tests hoặc live key:

|Lần chạy|Kết quả|
|---|---|
|Initial package trước edit|513 PASS, historical22/17/0/1|
|ReadContractTest sau core implementation|31 PASS|
|ApprovedDecisionsTest + HistoricalEvaluationTest + OrchestrationTest + VietnameseConversationFixTest + ReadToolsTest checkpoint|247 tests,4 failures do old D1 expectations; supplemental59 PASS|
|Cùng targeted suites sau policy-aligned test input updates và strict bound|248 PASS, historical23/16/0/1|
|Intermediate package|585 tests,2 failures: schema count10→11 và supplemental dùng tên fixture AVAILABILITY không tồn tại; sửa count và dùng fixture AVAILABLE, không nới output assertions|
|ApprovedDecisionsTest,VietnameseNluTest|139 PASS (75+64)|
|Final package|**592 PASS,0 failures/errors/skipped; BUILD SUCCESS**,22:43:03 +07:00|

Standard SecurityManager/Byte Buddy warnings tồn tại trước batch. Không xóa chúng bằng đổi JVM/dependency. Counts checkpoint không cộng vào final.

|Suite cuối|Tests PASS|
|---|---:|
|AgentFoundationTest|32|
|GeminiDebugSessionTest|3|
|GeminiFirstCallTest|56|
|GeminiProviderTest|75|
|GeminiTransportTest|17|
|VietnameseGrammarFixTest|41|
|VietnameseNluTest|64|
|ApprovedDecisionsTest|79|
|HistoricalEvaluationTest (accounting audit)|1|
|OrchestrationTest|70|
|ReadContractTest|31|
|ReadFailureContractTest|6|
|VietnameseConversationFixTest|9|
|ReadToolsTest|108|
|Total|592|

## 15. Historical E01–E40 before/after

Pinned manifest SHA-256 `5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f`, không đổi. Full runner được chạy trong initial/final package. **39 cases executed, E30 explicitly NOT_RUN**; không gọi “40 executed” hoặc một JUnit accounting PASS là40 acceptance PASS. Generated artifact `ai-agent-service/target/historical-evaluation-results.json`, không chỉnh tay.

|Case|Before|After|Actual category|Turn HTTP|
|---|---|---|---|---:|
|E01|PASS|PASS|GROUNDED|1|
|E02|PASS|PASS|GROUNDED|1|
|E03|FAIL|PASS|GROUNDED|1|
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

Improved **E03 FAIL→PASS**; **0 baseline PASS regressions**. E21 tiến từ NLU_CLARIFICATION tới đúng NEED_OCCUPANCY nhưng vẫn FAIL theo oracle gốc.

E17/E19/E21/E23/E24/E26 còn FAIL vì original SELECTED chỉ USER_PROVIDED; D3 không tự bổ sung confirmation vào fixture. Vì chưa admitted nên downstream availability fault fixtures **không** được coi exercised bởi các historical cases này; adapter/supplement coverage là evidence khác.

E27 đòi zero READ calls/use old context, xung đột D2 fresh-only; original COMPARE không có result-set reference đã issued và không có confirmed occupancy. E28 đòi context price/source timestamp với detail-only fixtures, không cung cấp fresh A01. Không tự mint refs từ ledger cũ, sửa context/fixture hoặc dùng A02 để giả refresh A01. Hai case giữ FAIL; approved behavior có supplemental separate.

E33 từ chối fabrication và giữ zero HTTP, nhưng original oracle đòi ANSWER/context price; current safe UNAVAILABLE/UNVERIFIED_CLAIM không bị đổi thành historical PASS. E34 REFUSED vs original UNAVAILABLE chưa reconcile. E04/E05/E06/E15/E39/E40 là grammar/semantic/size-contract gaps đã được ghi ở prior report.

## 16. Supplemental approved-policy results

**79/79 PASS** trong ApprovedDecisionsTest; không phải79 historical cases. D1 browse/search/non-downgrade/reference filtering; D2 two-turn identities surviving inventory TTL, fresh-only comparison, missing/partial/error/budget/security; D3 full chat/internal capability admission và negative provenance/state/concurrency.

Direct original READ failure suites tiếp tục PASS (6 invocations), original contract suite31 PASS. Không substituting supplemental positives để nâng SELECTED historical provenance.

## 17. E30 và quyết định còn mở

E30 **NOT_RUN**: admission timestamp/replay sequencing trong D3 không tạo latest-event cancellation/delivery semantics. Existing busy turn không được supersede. Cần authenticated ordered ingress, event sequencing, cancellation ownership và latest-result delivery contract được duyệt rồi harness tái hiện đúng original sequence. Lease-conflict/concurrent-confirmation test không được gán E30 PASS.

D1/D2/D3 đã operator-approved trong scope này; không mở lại approval đó. Những phần chưa duyệt/thiếu infrastructure: public ingress/session authentication, source/action attestation từ frontend, durable/multi-replica state/replay, supersession/delivery, broader language/catalog semantics, D07/D08/live/data/privacy/release gates. Không suy những quyền đó từ ba quyết định.

## 18. Files changed và remaining blockers

New main: `nlu/ComparisonRouting.java`; `orchestration/ResultSetReferences.java`, `TrustedCriteriaConfirmation.java`.
Modified main: `nlu/VietnameseParser.java`, `ReadIntentRouting.java`, `ExtractionProposal.java`; `orchestration/AgentOrchestrator.java`, `ConversationState.java`, `InMemoryConversationStore.java`, `SearchCriteriaState.java`, `PolicyExecutor.java`, `GroundingGate.java`, `ResponseSemantics.java`; `tools/ToolArguments.java`.
Paths dưới `ai-agent-service/src/main/java/com/example/aiagent/`.

New test: `src/test/java/com/example/aiagent/orchestration/ApprovedDecisionsTest.java`.
Modified tests: HistoricalEvaluationTest, OrchestrationTest, VietnameseConversationFixTest; `nlu/VietnameseNluTest.java`; `tools/ReadToolsTest.java`.
Modified resource: `src/main/resources/nlu/extraction-proposal.schema.json`.
Updated README additively; new report này. Không sửa historical reports/evaluation plan/fixture files.

Blockers cho wider acceptance/rollout:16 FAIL/1 NOT_RUN historical; authenticated UI adapter/ownership absent; room-pair/general recommendation contract absent; fixed finite grammar/catalog filter limitations; local ephemeral single-process state; không live provider quality/latency/cost/domain integration evidence.

## 19. Readiness và safety boundary cuối

**Implementation D1/D2/D3 DONE trong phạm vi internal/offline; UI confirmation INTERNAL_ONLY. Batch10B.4C NOT_STARTED, NOT_READY cho acceptance/rollout.** Không production-ready hoặc D07/D08 acceptance. Feature `AGENT_ENABLED=false`, provider `FAKE`, Gemini live `false` giữ nguyên; không POM/dependency/model/config change.

Gemini live attempts **0**. Real domain/Gateway/Supabase calls **0**. Chỉ synthetic loopback HTTP từ tests. Không đọc private dotenv/CA/journal/key, không real customer/hotel data, không Docker/stack, không migrations, không public ingress, không frontend/Place/Gateway/domain edit, không WRITE capability. Final source diff/static bounds/flags, tracked git diff --check và direct UTF-8/whitespace/schema/report links được kiểm tra khi bàn giao; generated Maven artifacts chỉ do build/test tạo.

