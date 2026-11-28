# Batch 10B.4C-LIVE-UNBLOCK-OFFLINE

Ngày 24/11/2026, Asia/Ho_Chi_Minh. **OFFLINE_UNBLOCK_DONE — INTERNAL_TEST_ONLY** trong phạm vi implementation/offline được kiểm thử. **TRUSTED_ISSUER_NOT_INTEGRATED**, **BILLING_QUOTA_UNVERIFIED**, **REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN**. Không cấp quyền/chạy LIVE; không production readiness, D07 hoặc D08 acceptance.

## 1. Baseline và bảo toàn workspace

Root AGENTS đã đọc; recursive backend scan không tìm thấy nested AGENTS. Backend branch `feat/ai-agent-read-service`, HEAD `fa388a2b2fa5787bafed7fa85324cb543a22acc7`; workspace root không phải Git repository. Index trống; existing work không sạch.

Existing tracked unstaged: `.idea/compiler.xml`, `.idea/encodings.xml`; Agent README; GeminiBudget/HttpTransport/LlmProviderAdapter/Properties/SyntheticSmoke; AgentOrchestrator/ConversationState/EvidenceLedger/GroundingGate/InMemoryConversationStore/PolicyExecutor/SearchCriteriaState; ToolArguments; application.yml; AgentFoundationTest/GeminiProviderTest/GeminiTransportTest/OrchestrationTest/ReadToolsTest.

Existing untracked: 10B4A LIVE DIAG_AUTO/EXECUTION/FIX/PREFLIGHT reports; 10B4B CONTRACT/DECISIONS/FIX/VIETNAMESE_NLU reports; 10B4C OFFLINE/PREFLIGHT reports; GeminiDebugSession/SmokeApproval/SmokeDiagnostics/SmokeWire; NLU sources/resources/tests; ReadComparison/ResponseSemantics/ResultSetReferences/SelectedTarget/TrustedCriteriaConfirmation; GeminiDebugSessionTest/GeminiFirstCallTest/VietnameseExtractionPreflightTest; supplemental orchestration/historical tests; nlu and gemini candidate test resources.

Trước edit đã lưu SHA-256 toàn bộ Agent src, README/POM và Docs/ai-agent vào snapshot temporary ngoài repo. Audit cuối so sánh bytes, không quy toàn bộ dirty diff so với HEAD cho batch này. Chỉ README được append; source/test/schema/report có sẵn giữ byte-equivalent. Không stage/commit/push/stash/reset/clean/checkout/xóa untracked. Frontend/Place/Gateway/domain/database/Config Server ngoài scope, không sửa.

## 2. Initial verification và source review

Baseline command từ backend root: `mvn -o -B -ntp -pl ai-agent-service package`: **BUILD SUCCESS; 697 tests PASS /0 failures /0 errors /0 skipped**, elapsed52.188s. Historical **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Không baseline discrepancy.

Đọc/đối chiếu 10B4C PREFLIGHT/OFFLINE, 10B4B DECISIONS, 10B4A Gemini integration/live reports, 10A architecture/review/decision register/evaluation, READ_TOOL_CONTRACT, 10B.3 policy/grounding, Agent README/POM. Source authority: VietnameseExtraction/SecurityPreflight/schema; LlmProviderAdapter, Gemini adapter/protocol/wire/transport/budget/properties/approval/smoke/debug source; AgentOrchestrator, TurnBudget, PolicyExecutor, GroundingGate và existing tests. Không mở private .env/key/old live journal; không chạy GeminiSyntheticSmoke.main hoặc truy cập web/account console.

## 3. Exact request measurements

Actual pinned LangChain4j1.17.0 serializer + in-memory HTTP SPI, không countTokens/network. Before là request cũ qua SDK; after là exact compact outbound body sau dedicated extraction normalization. Mỗi request chỉ fixed SystemMessage, một fixed synthetic UserMessage, JSON response schema, output1024; no tools.

System instruction giữ nguyên **1019 UTF-8 bytes**. Runtime resource schema trước **11168 bytes** (bao gồm CRLF/formatting); compact schema **4417 bytes**. Source đọc bằng text newline normalization có thể cho10724; bảng này dùng **actual resource UTF-8 bytes**, không dùng giá trị normalized đó làm runtime measurement. Full SDK body trước có serialization/framing riêng, không phải tổng thủ công của ba thành phần.

Conservative input admission vẫn `2 * fullOutboundUtf8Bytes + 1024`; input maximum16384, output maximum1024, request byte cap16384 giữ nguyên. Đây là conservative local estimate, không phải actual billed tokens. Table generated bằng test tại `ai-agent-service/target/extraction-request-measurements.json`, bao gồm component sizes và binding digests; bảng audit chép bên dưới.

|Fixture|User bytes|Body before|Estimate before|Body after|Estimate after|
|---|---:|---:|---:|---:|---:|
|VEX1-01|32|12070|25164|5743|12510|
|VEX1-02|66|12104|25232|5777|12578|
|VEX1-03|106|12144|25312|5817|12658|
|VEX1-04|91|12129|25282|5802|12628|
|VEX1-05|57|12095|25214|5768|12560|
|VEX1-06|66|12104|25232|5777|12578|
|VEX1-07|60|12098|25220|5771|12566|
|VEX1-08|32|12070|25164|5743|12510|
|VEX1-09|66|12104|25232|5777|12578|
|VEX1-10|98|12136|25296|5809|12642|
|VEX1-11|46|12084|25192|5757|12538|
|VEX1-12|36|12074|25172|5747|12518|
|VEX1-13|41|12079|25182|5752|12528|
|VEX1-14|49|12087|25198|5760|12544|
|VEX1-15|51|12089|25202|5762|12548|
|VEX1-16|49|12087|25198|5760|12544|
|VEX1-17|39|12077|25178|5750|12524|
|VEX1-18|44|12082|25188|5755|12534|
|VEX1-19|40|12078|25180|5751|12526|
|VEX1-20|48|12086|25196|5759|12542|

Cả20 request compact vừa cap từng call. Không có nghĩa20 requests được duyệt hoặc được chạy trong cùng session. Không nâng bất kỳ token/request/process cap nào.

## 4. Compact protocol và authoritative backend schema

New package-private `GeminiExtractionProtocol`, purpose **VIETNAMESE_EXTRACTION_SYNTHETIC**, version **vietnamese-extraction-compact-v1**, exact existing model identity **gemini-3.5-flash-lite**. Không public API/bean/CLI/provider factory registration hoặc caller-selected utterance/model/endpoint.

Optimization chỉ canonical compact JSON: minify raw-root schema và full SDK body; giữ nguyên instruction, six output keys, every schema keyword/constraint/type/enum/bound/required/closed properties. Không thay đổi representation của output, không converter/JSON repair. Wire schema và backend schema là hai serialization của **cùng semantic JSON tree**; equality test cho cả20 fixtures. Không cần bỏ `$schema`/oneOf/const/additionalProperties/uniqueItems hoặc rút instruction để vừa budget.

Original VietnameseExtraction.parse và ExtractionProposal/SearchCriteriaState validators không sửa: duplicate/unknown/trailing/type/bounds/date/provenance/ID guards vẫn authoritative. MODEL_PROPOSED không thành USER_CONFIRMED; corroboration so với independent literal parser, exact references và no inferred fields giữ nguyên. D1/D2/D3/READ/grounding/original entry FAKE gate không sửa. New harness không commit conversation state hoặc gọi domain.

## 5. SDK/wire/schema compatibility

|Boundary|Offline result|Không được suy diễn|
|---|---|---|
|Java request/response/control|PASS existing interface/pinned SDK|Không chứng minh API acceptance|
|Exact SDK serialization|PASS all20, fixed instruction/user, raw-root JSON schema, MIME, output cap, zero tools|Không tokenizer/model accuracy evidence|
|Dedicated extraction wire|Implemented/tested exact normalized body, endpoint/method/header checks|Không đi qua hoặc nới old GeminiSmokeWire|
|Backend strict validation|Unchanged, invoked after SDK parse|Wire schema không phải security boundary|
|Gemini schema dialect|Serialized unchanged, offline only|Keywords vẫn cần actual API acceptance; không tự xóa để giả compatibility|
|Real model|REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN|Không real Vietnamese accuracy/latency/cost/D07/D08 claim|

Dedicated wire chỉ bỏ SDK `candidateCount=1` sau kiểm exact pinned config keys/value và canonical serialize. Đây là explicitly versioned extraction path, không old smoke grant/bridge reuse. `maxRetries(0)`, built-in code/search/maps/url tools OFF, returnThinking(false), logs OFF. `returnThinking(false)` không chứng minh zero billed thinking; total-minus-input vẫn bị output cap ràng buộc.

`admit` kiểm fixed HTTPS POST URL, root/config fields, exact reserved normalized body, no unexpected headers. Outbound headers được dựng lại chỉ Content-Type/Accept/x-goog-api-key. No generic ChatRequest ingress: run chỉ nhận private capability, resolve utterance từ pinned classpath pack. Source/body substitution hoặc tools/schema/prompt/URL/method/header changes bị từ chối, không có arbitrary route.

## 6. Approval implementation và trusted issuer boundary

`GeminiExtractionSession` có immutable Reviewed/Caps/Binding contract và ApprovalVerifier interface. `unavailableIssuer()` luôn deny; **không có production verifier implementation/integration**. Class/constructors/methods package-private, không JSON approved parser, endpoint, config binding, environment approval, default key source hoặc HTTP transport. Operator identity/reference chỉ hợp lệ khi verifier đáng tin xác minh; code không coi chuỗi tham chiếu do caller đưa là proof.

Review binds: unique stable session ID, purpose, manifest hash, approved fixture IDs + utterance digest, instruction/schema/full normalized request digests, model, protocol, SYNTHETIC_ONLY, operator identity/reference, notBefore/expiry≤5m, request/input/output/total/concurrency/retry/time caps, provider process cap and exact financial envelope, absolute journal directory. Artifact opaque≤64KiB, verifier receives clone; bound record/maps immutable. Key/transport không được truy cập trong verification/serialization/admission.

Private constructor Capability giữ owner session identity, thread, fixture, exact request body; registry dùng identity set, not equals/JSON. One issue/fixture, one consume, attempt index0 only, wrong thread/session/replay/copy cannot create fresh authority. Complete bytes resolved from pinned pack, no caller prompt argument.

Supported state contract: DRAFT, REVIEWED, AWAITING_OPERATOR_APPROVAL, APPROVED_FOR_SEPARATE_LIVE_BATCH, EXPIRED, REVOKED, SEALED. Only approved state from verifier can open; all other initial states reject. Administration/review transitions belong to **not-integrated trusted issuer**, không có self-approval transition method. revoke/seal cancel active CallControl and deny new dispatch; expired window/monotonic deadline rechecked. Batch chỉ test-owned fake approved records trong temporary directories; không real grant/approval persist.

**TRUSTED_ISSUER_NOT_INTEGRATED / INTERNAL_TEST_ONLY**: phải integrate/authenticate operator-side artifact provenance và external gates trong separately scoped work trước bất kỳ real key/live transport wiring nào. Không tái sử dụng smoke approval/debug permit/token/journal cũ. Old factory/CLI vẫn không phải extraction approval boundary; không gọi chúng trong batch.

## 7. Budget and durable replay implementation

Giữ orchestration hard caps30s/3model/2rounds/6proposals/6HTTP/10s-tool/retry0. New extraction-only session maximum3 requests/≤3 fixture IDs, exactly one attempt per ID, concurrency1/retry0; provider process max≤9. Per call input≤16384/output=1024, input/output/total session reservations explicit, expiry≤5m/call timeout≤10s. Reserve **full configured ceilings**, not cheap observed test usage; no refund on error/timeout/cancel or missing usage. Exact financial rate/exposure/process/run/input/output envelope must match verified review before key lookup; fictional test rates are not billing verification.

`GeminiExtractionJournal` uses separate `vex-<SHA256 verified session ID>.journal` namespace inside review-bound trusted absolute directory, no old path. CREATE_NEW + exclusive FileLock; force(true) on creation and every bounded JSON-line reservation before key/dispatch. Same verified session/file can **never be resumed**, even empty/partial/corrupt/fully completed. On restart re-open fails rather than parsing/truncating/resetting/retrying uncertain state. Changing caller journal directory fails against verified binding. No automatic refund/reset/resume, symlink ancestor checks, bounded64KiB file/4096-byte record.

This is conservative durable local **session tombstone** protection: abrupt process loss consumes whole session, including unused IDs. Tests simulate restart by closing/reopening same verified session and inject partial/corrupt bytes in test-owned directory; original bytes remain unchanged after denial. No recoverable append-history parser is claimed. Assumes trusted non-deleted local journal directory and normal OS filesystem persistence; not proof against malicious filesystem deletion, machine rollback, storage loss or arbitrary power-failure behavior. No database/hosted store involved.

Existing GeminiBudget is reused by injection for process reservations/exposure, preserving caps/counters. Test demonstrates9 requests across distinct sessions accepted and10th denied **before key**, using the same ledger. Future runtime wiring must use the shared PROCESS ledger, retain journal directory/session IDs and external project controls; no auto-create-new approval/session/JVM reset to evade process caps. Local tombstone is not a durable account-wide quota counter across unrelated projects/hosts/processes; such external controls remain unverified, not claimed solved.

Session/call elapsed deadlines use monotonic time as well as reviewed UTC expiry; transport timeout is remaining call/session/control time. Late-response guard rejects even a mocked transport ignoring its deadline. No auto continuation after transport/schema/semantic/backend failure; session seals. Final successful quota consumption records SUCCESS_SEALED; errors record fixed category and seal. No background execution, no domain tools.

## 8. Proposed feasible subset, exact identities and budget

Propose **VEX1-01, VEX1-02, VEX1-03**, one three-request session, **not operator approved**. Happy-path automatic browse/reference-price/complete dated SEARCH extraction covers one request each, actual SDK/strict parser/corroboration/semantic oracle tested. No domain dispatch/grounded READ answer is part of this smoke.

Manifest unchanged: **20 fixtures**, SHA-256 `7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3`. Main resource `gemini/extraction-candidates-v1.json` is a byte-identical pinned copy of original test candidate pack, checked at class initialization. Original candidate/schema/historical resources untouched.

Instruction digest: `48b6b4eb7d57cbf1d13ce6a561bc0f82ff72a94de8c38a99ad9670754f64f8eb`.

Compact wire schema digest: `d8617d8e373927d539535d5d4f3ac826b92f28bed14ae34d02cfa8350c554aa9`.

|Proposed ID|Full normalized outbound request SHA-256|
|---|---|
|VEX1-01|660335d24ae98c2bdd6280e8d8c34e10f8c523bb1b0fea2e5cf6bd61500c66b4|
|VEX1-02|f6e56958121e8721ab7f5edf959133e176c501901e16b42f8f1dedd23cb83b96|
|VEX1-03|9819a108e93dd7395d76c0f1408908bd6ab5eba368e1a314e4011c6bdcb80898|

Proposed ceilings derived from unchanged adapter: input/call16384, output/call1024, session input49152/output3072/total52224; requests3, concurrency1, retry0; short expiry≤5min/call≤10s. Test window60s is synthetic only; operator must choose exact notBefore/expires. Monetary rates/allocation **UNVERIFIED**, no price/free-tier assumption; verifier must bind independently verified conservative rates including billable thinking and finite exposure before any real execution. Test objects use fictional1/2 upper rates and1USD solely to exercise checks, not a proposed quote/allocation.

Not selected: VEX1-04…15 fit byte/input cap but omitted from proposed initial three-call scope due run cap and semantic/context review, not removed from pack; no automatic follow-up sessions. VEX1-16…20 explicitly excluded by pinned PREMODEL classification and/or existing security/unsupported fence. Found VEX1-17 exact fabrication wording is not recognized by current finite parser; pinned candidate exclusion now denies it in new session admission without changing original parser/historical oracle. Do not claim general fabrication classifier solved. Adversarial remote probe would require another independently reviewed purpose/scope, not an exception in this path.

## 9. Full mocked extraction pipeline

Test-only opaque artifact → fake trusted verifier → immutable bindings/window/caps/fixture eligibility → private capability → atomic durable session reservation → existing process budget → sentinel key supplier → fixed SDK request → exact dedicated wire → mocked HTTP SPI → strict Gemini envelope/usage checks → actual SDK parser → original strict extraction parser → independent literal corroboration/routing checks → typed schema/semantic/backend outcome → allowlisted metadata journal/seal.

No ToolRegistry/domain adapter/conversation store is instantiated here. A safe corroborated proposal means extraction accepted, not completed grounded hotel answer. Semantic oracle compares action/intent/criteria/reference/missing slots/inferred absence separately; syntactically valid but uncorroborated city proposal gives **schema PASS /semantic FAIL /BACKEND_REJECTED**, not model PASS. Backend acceptance with wrong semantic metadata also fails model semantic gate and seals. Raw model text never becomes factual output/journal content.

Response rejects empty/multiple candidates, non-STOP finishes, safety prompt/finish blocks, tool/function calls, malformed/duplicate/unknown fields, invalid output types, body>64KiB or proposal>8192bytes, missing/nonintegral/negative usage, inconsistent/overcap totals, unexpected envelope, cancellation/deadline/revocation/late response. Optional thought metadata is bounded and never evidence/authority; total-minus-input includes billed thinking exposure. HTTP429→QUOTA_EXCEEDED, other errors→safe typed failure, raw SDK causes discarded. Schema/parser and backend corroboration remain distinct outcomes.

## 10. Positive/negative test matrix

New `GeminiExtractionUnblockTest`: **86 invocations**, actual SDK + fake HTTP, fake issuer, temporary journals. Socket-denying SecurityManager and key environment permission guard assert0 attempts. Supplier/transport counters independently assert denials occur before key/dispatch.

|Coverage|Executed assertions|
|---|---|
|All20 measurements|Full actual SDK before/after sizes, preserved schema tree/roles/instruction/output cap, no tools, pinned original hash|
|VEX1-01/02/03 positive, individually and shared session|Exact request/digests, usage12/8/20 from synthetic envelope, strict parse/corroboration/semantic PASS, one shot, full reservations, final seal|
|Review/admission mismatch|Purpose/manifest/model/protocol/classification; mutated fixture/instruction/schema/request digests; unknown ID; expired/future/>5m window; all nonapproved lifecycle states; invalid caps/concurrency/retry|
|No issuer/missing/forged JSON|Unavailable verifier/null result/missing artifact; no approval JSON parser; zero key/transport|
|Capability isolation|Private constructor, owner/session/thread, issued identity replay, duplicate issue, attempt>0, unauthorized ID, terminal revocation/seal|
|Budgets|Input/output/total/request exhaustion, in-flight concurrency, exact financial envelope substitution, shared process9/10 ceiling; all before key|
|Persistence|Empty/partial/corrupt/reserved/completed existing journal denies restart; file unchanged; directory substitution denied; reservation file nonempty before sentinel lookup|
|Wire substitution|URL/method/tools/schema/prompt/header changes rejected by actual admission function|
|Response/security|Malformed/empty/multiple/non-STOP/safety/function/duplicate/unknown/type/forged confirmation/oversize; unknown/invalid/overcap usage; quota429 zero retries; no raw payload leakage|
|Time/cancellation|Cancelled/expired before call; cancellation/revocation during transport; actual elapsed per-call deadline with ignored mocked timeout; session expiry before key; late response never success|
|Premodel exclusion|All VEX1-16…20 reject at review before key/transport|
|Backend vs model|Schema-valid wrong literal city: backend rejects, model semantic FAIL, session sealed|

No test-only approval state is written as a real live grant. Existing697 tests/original evaluation retained. Intermediate new-test failures were corrected: Windows exclusive-lock reader needed close before audit; intentionally reduced session totals needed internally consistent caps; empty response mapped to MALFORMED_RESPONSE rather than admission error; missing-usage fixture now actually removes usage instead of creating invalid responseId; VEX1-17 finite-parser/classification discrepancy handled by additional pinned admission exclusion. No historical assertions weakened or fixtures/oracle altered.

## 11. Privacy/journal audit

Only fixed synthetic utterances and unchanged instruction/schema can reach injected exchange. No real customer/hotel/booking/payment records, real history, raw READ evidence, tokens/internal headers/production URLs/DB errors/stack traces. Endpoint is fixed provider URL, not model-selected. No environment/.env/API key read, no old journal access. SDK test key is a literal noncredential; not logged or persisted in body/journal.

Journal record keys exactly: fixtureId, protocolVersion, requestDigest, modelIdentity, startTimestamp/endTimestamp, latencyMillis, httpStatusCategory, inputTokens/outputTokens/totalTokens (null when unknown), schemaValidationResult, semanticResult, stopReason. Reservation uses NOT_RUN/null; complete uses verified usage or UNKNOWN/null, never fabricated zero. No raw request/response/header/auth/key/evidence/SDK cause. Tests assert exact allowlist and no utterance/sentinel/tool payload leakage. No raw-response debug retention enabled. Local journal stores only synthetic metadata, not approval identity or raw artifact. Bindings remain in trusted control memory; no real approval artifact persisted.

## 12. Final verification

Final command: `mvn -o -B -ntp -pl ai-agent-service package`, no test skip: **BUILD SUCCESS; 783 tests PASS /0 failures /0 errors /0 skipped**, elapsed23.796s. Existing697 + new86 retained, including ApprovedDecisionsTest79 and LlmVietnameseOfflineTest78. Earlier intermediate full run777 had1 new-test failure due the malformed missing-usage fixture; corrected fixture and final regression passed. No exclusions or skipped suites.

Git diff --check and explicit new-file whitespace/UTF-8 checks; original source/report/schema/test/candidate hashes compared to pre-edit snapshot. No model/dependency/config/feature flag change, no public ingress/WRITE. Existing source versions Java21/Boot3.3.4/LangChain4j1.17.0 retained.

## 13. Historical evaluation

Initial **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**, original pinned manifest/hash/hard assertions/fixtures/runner untouched. E30 remains NOT_RUN, no latest-event sequence introduced. New86 tests are not historical/model-language accuracy PASS. Final runner also **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**, no historical regression.

## 14. Changed files

New main source (all package-private internal components):

- `ai-agent-service/src/main/java/com/example/aiagent/model/GeminiExtractionProtocol.java`.
- `ai-agent-service/src/main/java/com/example/aiagent/model/GeminiExtractionSession.java`.
- `ai-agent-service/src/main/java/com/example/aiagent/model/GeminiExtractionJournal.java`.
- `ai-agent-service/src/main/java/com/example/aiagent/model/GeminiExtractionSmoke.java`.

New resource/test/report: pinned main resource `ai-agent-service/src/main/resources/gemini/extraction-candidates-v1.json`; `ai-agent-service/src/test/java/com/example/aiagent/model/GeminiExtractionUnblockTest.java`; this report. Append Agent README with current checkpoint. Target measurements/surefire/JAR outputs generated by Maven only, not manually edited or staged. Previous reports remain historical and byte-unchanged.

## 15. Remaining blockers/operator decisions

**TRUSTED_ISSUER_NOT_INTEGRATED**: authenticated operator artifact verification, production configuration/key/transport wiring and durable trusted directory provisioning are deliberately absent. Default verifier denies; no real approved artifact/session exists. Operator identity/reference in fake tests is not approval. Current standalone harness is INTERNAL_TEST_ONLY; ordinary Agent extraction still FAKE-only and generic Gemini adapter still denies arbitrary extraction.

**BILLING_QUOTA_UNVERIFIED / OPERATOR_VERIFICATION_REQUIRED**: exact project/model/region/key entitlement, data terms, current billable token classes/pricing/quotas/shared consumers/exclusive window and finite spend allocation require operator evidence. No inferred Free Tier or model availability from identity. No external lookup performed.

**REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN**: actual Gemini dialect/metadata acceptance, Vietnamese extraction correctness, usage/latency/timeout behavior and paid exposure require separately authorized bounded live measurement. Compact schema retains full JSON Schema keywords; real API rejection is still possible. No report declares real schema compatibility.

Operator must review exact proposed three IDs/manifest/request/schema/instruction/model/protocol hashes, semantic oracle/missing slots, precise times/caps/verified financial bounds, trusted issuer integration and journal path ownership/retention. D07 criteria and D08 release remain separate. No blanket approval of remaining17 fixtures or another process/session. No automatic continuation after offline PASS or first failure. No real-domain E2E/production/public ingress authorization.

## 16. Final verdict

**OFFLINE_UNBLOCK_DONE — INTERNAL_TEST_ONLY**, technical request-budget blocker solved without cap increase, dedicated wire + fail-closed verifier seam/private capability + local durable session tombstone + complete mocked pipeline implemented and tested. Trusted real issuer/external verification/actual API acceptance remain explicit unresolved live prerequisites. Gemini live attempts **0**; real domain/Gateway/Supabase calls **0**; external network **0** (existing tests synthetic loopback only); feature defaults OFF/FAKE/liveOFF. Không tự chuyển sang LIVE, không real grant, không production-ready claim.
