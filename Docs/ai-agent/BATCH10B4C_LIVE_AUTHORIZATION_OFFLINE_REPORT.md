# BATCH 10B.4C-LIVE-AUTHORIZATION-OFFLINE

Ngày 24/11/2026, Asia/Ho_Chi_Minh. **AUTHORIZATION_OFFLINE_PARTIAL — INTERNAL_ONLY**.

Đã triển khai verifier Ed25519, internal initial-smoke execution gate, isolated offline campaign reservation/journal và scorer, kèm 60 candidate fixtures. Chưa hoàn thành signed campaign authorization và live dispatch cho campaign lớn. Không tuyên bố DONE hoặc quyền chạy live.

**TRUST_ROOT_PROVISIONING_REQUIRED; BILLING_QUOTA_UNVERIFIED; LIVE_TRANSPORT_WIRING_INCOMPLETE (expanded campaign integration); REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN; REAL_VIETNAMESE_NLU_ACCURACY_NOT_RUN.** D07/D08 chưa accepted. Không production readiness.

## 1. Git baseline và preflight

Backend branch `feat/ai-agent-vietnamese-nlu`; HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Root không phải Git repo. Trước edit chỉ `.idea/compiler.xml`, `.idea/encodings.xml` tracked-dirty; không existing untracked entries; index không thay đổi. Không stage/commit/push/stash/reset/clean/checkout. Không sửa frontend/Place/Gateway/domain/database/Config Server/dependency/model/config.

Đọc root AGENTS.md; recursive backend scan không thấy nested AGENTS. Đối chiếu previous 10B4C UNBLOCK/PREFLIGHT/OFFLINE, 10B4B DECISIONS, Gemini synthetic/live reports, 10A architecture/review/evaluation, READ contracts, 10B3 orchestration và Agent README/POM. Kiểm tra protocol/session/journal/smoke, Gemini budget/properties/transport/adapter/approval/debug/wire/provider factory, Vietnamese extraction/security preflight, orchestration/policy/grounding/TurnBudget và tests liên quan. Dated report branch/HEAD khác hiện tại được giữ nguyên như historical evidence.

Baseline trước edit: `mvn -o -B -ntp -pl ai-agent-service package`, **BUILD SUCCESS, 783 tests /0 failures /0 errors /0 skipped**, 27.997s. Historical runner: **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Không discrepancy. Snapshot SHA-256 Agent src/README/POM và existing Docs/ai-agent lưu ngoài repo trong Temp; audit không quy existing IDE changes cho batch này. Không đọc `.local`, `.env`, API key hoặc old live journals.

## 2. Implemented trust model

`GeminiOperatorApproval` thực hiện JCA **Ed25519 signature verification**; main source không có signing/key generation. Public-key trust map được cung cấp độc lập qua constructor, gồm key ID → issuer/public key. Map immutable; artifact không được cung cấp trust root. Không tự tạo/provision production key, không default issuer, không public endpoint/bean/CLI.

Artifact envelope có đúng `algorithm`, `payload`, `signature`. Algorithm chỉ Ed25519; signature Base64 chuẩn, decoded length64. Payload version `operator-extraction-approval-v1`, issuer/keyId, evaluationMode `INITIAL_SMOKE`, revocation policy `DENY_ON_REVOCATION_OR_UNCERTAINTY`, external evidence references và toàn bộ `GeminiExtractionSession.Reviewed`.

Signed review binds operator/reference, session, purpose/model/protocol/manifest, approved fixture map (utterance/instruction/schema/normalized request digests), synthetic classification, lifecycle state, not-before/expiry, journal directory, request/input/output/total token caps, concurrency/retry/call deadline, process limit, monetary allocation and input/output upper rates. Session maximum duration is expiry minus not-before, checked by existing session≤5min validation. Evidence binds project/account reference, pricing/quota/privacy/allocation/coordinated window references, verifiedAt và expiry. Trust authority must authenticate the referenced evidence before signing; presence of a signed reference is not a console verification performed by this batch.

Canonical profile v1: recursively sorted object keys, retained array order, normalized exact decimal numbers (strip trailing zeros), compact Jackson UTF-8. Same bytes are signed and verified; no RFC8785/interoperability certification claim. No Unicode normalization is applied. Duplicate keys/trailing JSON, unknown/missing/null creator fields, scalar coercions, fractional integer caps, wrong algorithm/signature/key/issuer/version/purpose/model/protocol/manifest/digests/window/state/revocation/evidence fail closed; artifact cap64KiB; nested parser limits inherited. Existing session validation is still required after signature verification: a returned review itself is not a capability.

Verifier accepts only initial-smoke artifacts. The old fake verifier seam remains solely for existing internal tests, and is **not accepted by `GeminiAuthorizedExtraction`**, whose parameter type requires the concrete signed verifier. Lifecycle DRAFT/REVIEWED/AWAITING/APPROVED/EXPIRED/REVOKED/SEALED remains unchanged. No actual operator approval issued. Revocations are an immutable provisioned snapshot; a continuously authenticated revocation feed is not implemented. Runtime session revoke/cancel remains available in existing session API.

**TRUST_ROOT_PROVISIONING_REQUIRED**: future operator must provision issuer/keyId/public key and trusted directory using independently reviewed operational infrastructure. Private signing key stays outside Agent. Test keypairs are generated only in test code and never persisted.

## 3. Credential and transport gate

`GeminiAuthorizedExtraction` is a package-private initial-smoke entry, not ordinary Agent routing. Sequence:

1. Explicit execution intent, feature flag, kill switch, live/synthetic flags and external verification status gates.
2. Concrete trusted artifact verifier, signed evidence references/window, exact fixture/request/model/schema/protocol identity.
3. Existing session review validation and exact absolute journal directory binding; CREATE_NEW session reservation.
4. Private one-shot fixture capability; durable request/token/financial reservation via existing session.
5. Shared existing Gemini process budget reservation.
6. Sentinel/operator-supplied credential lookup; lazy fixed transport construction.
7. Exact pinned SDK wire admission; single dispatch, bounded deadline/cancellation; strict response/usage/extraction parsing and literal corroboration.
8. Allowlisted journal outcome and conservative session sealing.

The real-transport overload always uses **GeminiBudget.PROCESS**; it cannot select a fresh process ledger. Package-private mock overload injects test ledger/transport. No System.getenv call added; future secure credential source remains independently provisioned. This batch never invokes the real overload or constructs a real evaluation transport.

Reuse is possible without modifying `GeminiHttpTransport`: dedicated extraction wire fixes HTTPS endpoint/model, reconstructs headers and enforces body/input caps; existing transport disables redirects/cookies/auth forwarding/retries and bounds body/deadline/cancellation. The transport itself is generic package infrastructure; the new gate fixes its source request through existing exact wire admission. Old smoke admission restrictions are untouched. No arbitrary endpoint/model/header/utterance/ChatRequest argument added. Mock positive test verifies exact endpoint/body/headers and one dispatch, with durable file nonempty before sentinel lookup. Errors omit raw credential/provider bodies.

Important scope: wrapper dispatches **one fixture and closes/seals its session**. A grant containing more than one fixture is not a campaign continuation implementation. Existing low-level session tests cover a three-call session; new wrapper does not automatically resume it. Full campaign wiring and continuous revocation/account controls remain incomplete, therefore **LIVE_TRANSPORT_WIRING_INCOMPLETE** for expanded evaluation. ProviderConfiguration and ordinary FAKE-only extraction entry remain unchanged.

## 4. Legacy budget preservation

No existing source changed in GeminiBudget/GeminiProperties/old smoke/TurnBudget/AgentProperties or feature configuration. Legacy run≤3/process≤9 remain; ordinary turn≤30s/model≤3/rounds≤2/proposals≤6/HTTP≤6/tool≤10s/retry0 remain. Old sealed smoke identity and original manifests unchanged.

`GeminiEvaluationCampaign` is a distinct **OFFLINE accounting component**; it has no credential/transport/model method and cannot dispatch through an alternate safety path. It does not silently raise legacy caps. EVALUATION_CAMPAIGN_BUDGET_NOT_IMPLEMENTED is not the state: working bounded reservation code and tests exist. **SIGNED_CAMPAIGN_GRANT_AND_DISPATCH_NOT_IMPLEMENTED** is the remaining boundary.

Policy fields: campaign ID, mode, exact manifest hash, max sessions/requests/tokens/exposure/concurrency, session requests/tokens, per-call input/output, retries, duration, expiry, verified-rate placeholders and shared allocation reference. Modes INITIAL_SMOKE, VIETNAMESE_NLU_EVALUATION, EXTENDED_NLU_EVALUATION, SYNTHETIC_E2E_EVALUATION are represented; mode names do not authorize execution. Actual repeated samples must receive distinct reviewed sample digests, never retry counters.

Supported technical ceiling **offline tested**: 200 requests, 3,481,600 reserved total tokens (200×(16,384+1,024)), concurrency1, retries0; largest executed reservation test uses one session200, duration30min, synthetic100USD ceiling and fictional1/2 rates only as test parameters. These are **not prices, recommended financial allocations, defaults or live approval**. Validator upper bounds: sessions≤200, requests≤200, wall duration≤2h, input/call≤16384/output/call≤1024; all actual limits explicitly provided. Tests also execute3/5/30/60/100-request reservations. No actual transport involved.

Campaign→session→fixture/sample→attempt accounting reserves full per-call ceilings before recording outcomes; never refunds unknown/error exposure. Counts across every session contribute to campaign totals. One shared Process object enforces process request/token/money allocation across campaign instances; process consumption survives campaign close within JVM. In-memory Process can be reconstructed by package code/restart, so it is **not** durable account-wide enforcement. No campaign dispatch is exposed until signed campaign policy, exact sample allowlist, durable shared allocation ownership and authenticated project consumer coordination are integrated. The component takes a policy supplied by trusted test/control code; it does not cryptographically authenticate that policy. This limitation is a reason for PARTIAL, not an authorization exception.

## 5. Durable journal/replay

Evaluation uses separate `evaluation-v1-<SHA256(campaignId)>.journal` namespace, distinct from vex/old-smoke journals. Exact trusted/requested directory match, no symlink ancestry, CREATE_NEW, exclusive FileLock, force(true), bounded≤1024-byte records and≤128KiB file. Record data contains hashes/counts/reserved cost/fixed outcome categories, never utterance/key/raw response.

Campaign lock serializes session/request reservations; active request limit1. Durable consumed sample digest rejects same/cross-session duplicates within campaign. Existing empty/partial/corrupt/reserved/closed journal rejects **every reopen**, without repair/reset/truncation. No parser/resume is claimed: all existing campaign state, including unattempted fixtures, is conservatively unavailable after restart. Unknown in-flight request is consumed; no automatic retry; usageUnknown/failure/cancel seals. Lock/disk failure fails closed; process budget may remain consumed if disk write fails.

Assumptions: operator controls persistent directory and file ACL/ownership; Java path/symlink checks are not a complete hostile-filesystem TOCTOU defense. No protection claimed against malicious deletion/rollback/storage loss/power-loss filesystem anomalies, duplicate campaign IDs in different trusted roots, new campaign identity abuse, unrelated JVM/host consumers or account-wide concurrent spend. Verified continuation/reconciliation, campaign-level revocation integration and durable shared allocation journal are remaining work before expanded live dispatch. No JVM restarts or minted sessions were used to dispatch/bypass any provider cap.

## 6. Dataset and review status

New test-only `vietnamese-nlu-evaluation-candidates-v1.json` + `.sha256`: **60 fixtures VNE1-001…060**; SHA-256 **9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950**. Proposed splits20 DEVELOPMENT/20 REGRESSION/20 HOLDOUT; stable unique IDs and utterances, pinned hash tested. Original E01–E40 and VEX1-01…20 (SHA7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3) byte-unchanged.

Coverage includes browse/destination/dates/ranges/Vietnamese numbers/reference-price/star/occupancy; missing or ambiguous slots; refine/replace/clear; selected hotel/room/comparison; price/availability; unsupported amenities/nearby; invalid dates/prices/counts; paraphrases/informal/no accents/typos/mixed language; injection/fabrication/WRITE/extractor-only adversarial probes. Candidate rows bind synthetic context, expected action/intent/criteria/missing/reference, model semantics, backend admission and tool policy target, forbidden behaviors/privacy/severity/category/split. Multi-turn context proposes synthetic identities/criteria/reference times; it cannot mint runtime trusted reference capabilities. No real records.

**49 REVIEW_REQUIRED /11 DENY** remote-default labels. DENY covers security/unsupported/adversarial cases; REVIEW_REQUIRED is not eligibility or permission. Existing premodel fences must always run; actual blocked inputs cannot be sent by default. Extractor-only adversarial purpose requires separate authorization. No fixture from this file is wired into dispatch.

All rows **PENDING_INDEPENDENT_REVIEW**. First15 expectations are copied draft candidates, not newly independently reviewed truth. New semantic targets and backend limitation labels are proposals; they do not claim grammar support. CLARIFY criteria remain empty, with separately noted literal semantic slots where useful. Current field names checkIn/checkOut/starRatings retained. Holdout partition is visible to developers and not blinded; freeze/review before experiments and do not repeatedly tune against it or claim unbiased accuracy. Independent oracle review remains a real blocker; no review provenance synthesized.

## 7. Metrics implemented and remaining

`GeminiEvaluationMetrics` keeps unique immutable observation snapshots. No removal/overwrite API. Remote failures stay in denominator; premodel-blocked cases are excluded from remote denominator; eligible NOT_RUN is separately visible. Implemented numerator/denominator: API success/schema valid, action/intent/exact criteria/missing slots, exact semantic object, per-expected-field extraction, correct clarification, backend accepted/false acceptance/false rejection, unsafe inference/unauthorized tool/fabricated confirmation/injection resistance. Correct model semantics with backend grammar rejection retains semantic success and separately increments false rejection. Unsafe semantic output safely rejected does not become model PASS. Schema-valid wrong intent/criteria remains semantic failure.

Usage aggregates known input/output/total plus explicit unknown attempt count, never represents unknown billing as zero. Nearest-rank p50/p95 helper returns empty with no attempts; descriptive only. No cost is reported without verified prices. No automatic comprehensive live result ingestion or full Agent E2E scorer exists. Budget-compliance, secret leakage and WRITE-vs-READ breakdowns need campaign/Agent audit observations in the future runner; current scorer is not their proof. No real model accuracy/latency/cost result produced.

See separate evaluation plan for proposed semantic thresholds, safety gates, sample-size caveats, denominator discipline and roadmap. Thresholds are proposals only; no D07/D08 acceptance.

## 8. Tests and verification

New tests: `GeminiOperatorAuthorizationTest`37, `GeminiEvaluationCampaignTest`23, `GeminiEvaluationMetricsTest`11 = **71 PASS**. Existing783 retained.

Authorization/gate tests exercise real test-only Ed25519 signature, wrong key/keyId/issuer/root injection, altered/unknown/missing/duplicate/trailing structure, invalid/missing/fractional/coerced caps, invalid signature/algorithm/oversize, tampered project, wrong purpose/model/protocol/manifest/fixture/request/schema, future/expired/revoked/sealed, evidence expiry, wrong mode/version, financial substitution, missing approval, billing/intent/feature/kill gates, process exhaustion, directory mismatch, replay, exact single mocked dispatch, journal-before-key, no raw key persistence. Existing86 unblock tests preserve response/usage/cancel/revoke/timeout/one-shot/premodel protections and old9/10 process ceiling.

Campaign tests execute finite3/5/30/60/100/200 requests; request/session/token/money/concurrency exhaustion; duplicate/cross-session sample; unknown usage/failure/cancel sealing; empty/partial/corrupt journal, restart and in-flight tombstone rejection; shared in-JVM process cap across campaign instances; expiry/directory mismatch. They do not prove missing durable shared project allocation or signed campaign grants.

Metrics tests distinguish correct/incorrect intent/criteria/missing slots, grammar false rejection, unsafe output safe rejection, schema/API failures retained in denominator, premodel exclusions, NOT_RUN, immutable/no-duplicate samples, unknown usage and latency percentiles, dataset hashes/split separation. They do not execute60 candidate linguistic cases against a real model.

Commands actually run: baseline offline package; scoped authorization/campaign/metrics tests; final offline package. Intermediate failures: one test compile error from Jackson fluent generic return type; two positive tests initially rejected numeric canonical mismatch. Fixed implementation/test helper; no weakened historical assertions. Scoped66-test run PASS before additional strict-type/metric tests. Subsequent full run **854 PASS /0 failures /0 errors /0 skipped**, then final rerun after gate/pack/budget finishing changes (result below). No tests excluded. Deprecation warnings remain for existing test SecurityManager and Jackson mapper configuration, not build failures.

Final verification command: `mvn -o -B -ntp -pl ai-agent-service package` — **854 tests PASS /0 failures /0 errors /0 skipped; BUILD SUCCESS**. Historical **23/16/0/1**, E30 NOT_RUN. New tests are not historical/model-quality PASS. Whitespace/new-file UTF-8/hash audit performed; existing source/report/fixture hashes unchanged except intentional README append. Existing IDE work preserved; index untouched.

## 9. Files changed

New main source: GeminiOperatorApproval.java; GeminiAuthorizedExtraction.java; GeminiEvaluationCampaign.java; GeminiEvaluationMetrics.java under `ai-agent-service/src/main/java/com/example/aiagent/model/`.

New tests: GeminiOperatorAuthorizationTest.java; GeminiEvaluationCampaignTest.java; GeminiEvaluationMetricsTest.java in corresponding test package. New candidate JSON/hash in test resources/gemini. Added this report and VIETNAMESE_NLU_LIVE_EVALUATION_PLAN.md; appended README checkpoint. No previous report/source/manifest/oracle modified. Build outputs generated only by Maven, not manually edited/staged.

## 10. Billing/privacy/operator checklist and blockers

**BILLING_QUOTA_UNVERIFIED** remains. Operator must independently verify exact account/project/model entitlement/region, free-or-paid tier, input/output pricing, thinking and other billable classes, RPM/TPM/RPD, shared consumers, finite financial allocation, terms/retention/use, synthetic-only approval, campaign duration and exclusive/coordinated window. Signature binds references and upper rates; this is not external account verification. No console/web/account access, key read or fictional rates presented as actual.

Before any live batch: independently provision trust root/trusted persistent directory, review revocation source/rotation/reconciliation, issue a fresh exact scope-bound signed artifact, validate pricing/evidence expiry and project window, review selected manifest/oracle/per-request digests and financial envelope. No operator signing artifact generated here. Expanded campaign additionally needs signed campaign/sample allowlist binding and dispatch integration, durable shared account allocation reservation and tested cross-JVM/restart enforcement, cancellation/revocation integration, real result ingestion and independently reviewed fixtures. These are technical incompletions, not merely absent signatures; do not claim expanded readiness.

Privacy audit: all test data synthetic; network guard around authorization tests reports zero socket/key-environment attempts; transport mocked. Existing regression suite includes synthetic loopback servers, so “external network0” does not mean “no loopback sockets anywhere”. Gemini live attempts **0**, actual credentials read **0**, real domain/Gateway/Supabase calls **0**, external network **0**. Feature defaults OFF/FAKE/liveOFF; no public ingress/WRITE tools/dependency/model/schema/business changes. Never automatically execute next batch.

## 11. Readiness and next batch

**AUTHORIZATION_OFFLINE_PARTIAL**. Real signed initial-smoke verifier/gate and useful finite200-request offline budget infrastructure are implemented/tested, but full trusted scalable live campaign gate is not implemented. Trust root provisioning absent; external verification absent; dataset oracle not independently approved; expanded campaign dispatch/account replay boundary incomplete.

Recommended next: **LIVE-AUTHORIZATION-OFFLINE-COMPLETION** addressing the explicit technical gaps, then a separately approved initial smoke and quality evaluation roadmap. Not immediate live execution. Quality roadmap is not restricted to three requests: after API/schema/usage smoke, review a balanced30–60 semantic campaign, then100–200 extended sampling with independent sample IDs and refreshed holdout where justified; separately approved synthetic E2E only. No real domain calls or production acceptance implied.
