# BATCH10B.4C OPERATOR SETUP AND PREFLIGHT RECHECK

24/11/2026, Asia/Ho_Chi_Minh. Primary status: **OPERATOR_SETUP_PREFLIGHT_AWAITING_OPERATOR**. Automated setup complete; no real trust root, account evidence, journal provisioning or signature supplied. This is a preparation checkpoint, not execution authorization. **No Gemini generateContent/countTokens/connectivity probe was executed.**

## Baseline, preservation and evidence

Actual branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Root AGENTS read; backend recursive hidden scan found no nested AGENTS. Existing tracked dirty: `.idea/compiler.xml`, `.idea/encodings.xml`, Agent README, GeminiExtractionProtocol/Session/Smoke. Existing untracked: authorization/completion/setup/candidate/oracle/evaluation documents; AuthorizedExtraction, OperatorApproval, CampaignApproval, EvaluationCampaign/Dataset/Metrics/Preflight/Runner, SharedAllocation; runtime VNE1 copy; ten prior test/helper files and VNE1 test pack/hash. These are prior developer work, preserved; full Git diff must not be attributed to this batch. No staged changes; no stage/commit/push/stash/reset/clean/checkout or untracked deletion.

Scoped source/test/resource/POM/README/report SHA256 snapshot saved outside Git before edits, temp `checkinn-setup-baseline-cf0154fe-6122-428d-8742-16e7db10096d.json`. Existing source/config/manifests/reports were not overwritten. Read current setup/completion/candidate/oracle/evaluation material and previous 10B4C/10B4B/10A evidence alongside actual source/tests. Dated checkpoint statements are historical. No private .env/.local/secrets/old sealed journals read. No authenticated console login performed; public API-keys URL redirected to sign-in and supplied no account evidence.

Initial `mvn -o -B -ntp -pl ai-agent-service package`: **BUILD SUCCESS, 943 PASS, failures0/errors0/skipped0**, 1:21. Historical **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**, E30 NOT_RUN. No discrepancy. Baseline includes12 actual controlled cross-JVM tests and previous89 integration tests; no new model-quality evidence.

Pinned byte hashes verified on current source files:

|Resource|SHA256|
|---|---|
|Original VEX1 test manifest + runtime extraction-candidates-v1 copy|7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3|
|Original VNE1 test pack + runtime copy|9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950|
|Original historical E01–E40|5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f|

## Exact initial-smoke review — operator confirmation still required

Utterances copied from original fixture source, not reconstructed. All three SYNTHETIC_ONLY; action=CRITERIA, extraction intent=SEARCH; every expected field uses REPLACE/value; missingSlots=[], inferredFields=[], reference="". Extraction SEARCH does not override deterministic automatic BROWSE routing.

|ID|Exact Vietnamese utterance|Expected fields|Backend expectation|
|---|---|---|---|
|VEX1-01|Tìm khách sạn ở Đà Lạt|cityName="Đà Lạt"|ADMIT_LITERAL; automatic BROWSE|
|VEX1-02|Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu|cityName="Nha Trang", maxPrice=2000000, referencePriceExclusive=true|ADMIT_LITERAL; filter returned page only|
|VEX1-03|Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng|cityName="Đà Lạt", checkIn="2026-12-01", checkOut="2026-12-03", adults=2, children=0, rooms=1|ADMIT_LITERAL; independent explicit occupancy only|

Exact forbidden fields for every row: hotelId, roomId, bookingId, USER_CONFIRMED, provenance, price, availability, toolCalls, headers, url. No invented dates/counts, live/final price, availability, hold/booking/WRITE authority. Fixture tool target A01_ONLY_AFTER_POLICY is a future policy expectation, **not a domain call in extraction-only smoke**.

Technical consistency review by Codex: D1 permits undated destination browse without inventing stay/occupancy; VEX1-02 uses inclusive maxPrice upstream superset and exclusive local predicate only on fresh returned page, not catalog-wide/no-result proof. D2 trusted selections/fresh READ cannot be minted by these utterances; all references empty. D3 VEX1-03 contains explicit complete occupancy, including explicit children=0; model proposals remain MODEL_PROPOSED. VietnameseExtraction.corroborate obtains confirmed literal values independently; no model label promotes provenance. SEARCH/availability still requires valid state/policy and fresh evidence; GroundingGate has no availability evidence in this batch. Existing parser/schema tests and new mocked operator path accept all three exact expected outputs. No dates/fixture bytes changed. If stay dates become past, create separately reviewed version, never patch pinned manifest silently.

**INDEPENDENT_ORACLE_REVIEW_REQUIRED**: this technical review is neither independent product approval nor signed execution authorization. Operator must confirm these exact rows, not all60 VNE1 rows, for initial smoke.

## Current SDK request preflight and exact identities

Measurements regenerated by current `GeminiExtractionUnblockTest.measureEveryFixtureWithActualSdkAndKeepAllBackendConstraints`, using pinned LangChain4j1.17.0 in-memory Capture and sentinel credential. No real HTTP transport. `target/extraction-request-measurements.json` contains generated current values; compared all three request digests with earlier unblock baseline, identical. System instruction UTF8=1019 bytes; compact schema=4417 bytes. Exact schema sent in responseJsonSchema equals the compact unchanged backend schema tree; no constraint dropped. SDK candidateCount=1 is the only removed field. This does not prove provider schema acceptance.

|Fixture|Normalized UTF8 bytes|Conservative input bound (2×bytes+1024)|Input cap|Output cap|Exact normalized request SHA256|
|---|---:|---:|---:|---:|---|
|VEX1-01|5743|12510|16384|1024|660335d24ae98c2bdd6280e8d8c34e10f8c523bb1b0fea2e5cf6bd61500c66b4|
|VEX1-02|5777|12578|16384|1024|f6e56958121e8721ab7f5edf959133e176c501901e16b42f8f1dedd23cb83b96|
|VEX1-03|5817|12658|16384|1024|9819a108e93dd7395d76c0f1408908bd6ab5eba368e1a314e4011c6bdcb80898|

|Fixture|Exact utterance SHA256|
|---|---|
|VEX1-01|5279d4cdca5b8e83934b4c3801774c4b6f2d745169fe2e7b10676bc2f5fd6847|
|VEX1-02|49abf319fd49800e089d0140154a6283568108baa5a5971c2893cfdc690ffea9|
|VEX1-03|1daf2b48609ab18f7031a2a50fed4bd6bd7227c9d4da18e0e5f6d42435ac3a81|

Shared instruction SHA256 `48b6b4eb7d57cbf1d13ce6a561bc0f82ff72a94de8c38a99ad9670754f64f8eb`; compact protocol schema SHA256 `d8617d8e373927d539535d5d4f3ac826b92f28bed14ae34d02cfa8350c554aa9`. Schema digest uses compact Jackson protocol schema JSON, not whitespace-rich source-file bytes; wire responseJsonSchema structural equality is tested, and full wire byte identity is covered by requestDigest. Full normalized body hash covers actual SDK wire representation. No request identity changed or silently rebound.

Fixed model `gemini-3.5-flash-lite`; POST `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent`; MIME application/json. One system instruction + one exact user utterance; no history/context, tools/search/maps/code/URL context, caller-defined prompt/endpoint/model or real business data. Bound is a conservative local estimate, not provider token count or billed usage. **REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN**.

## Tooling and minimal invocation correction

Existing GeminiEvaluationPreflight supports exactly template/bind-scope/canonicalize/envelope/verify. Template initial-smoke binds exact request hashes/order and v2 schema but state DRAFT, invalid signature/window, unverified evidence and zero financial fields. `bind-scope` validates independently reviewed oracle and eligible exact samples; not needed for smoke. Canonicalize sorts keys/normalizes decimals/preserves arrays, envelope accepts64 detached signature bytes, loadVerifier requires independently provisioned X509 SPKI Ed25519 public DER/issuer/keyId/revocations. `verify ... - <journal> OFFLINE_ONLY` is the smoke read-only path: signature/evidence/version/purpose/model/protocol/request/window/revocations/financial constraints/directory; no credential, journal write/reservation/capability/transport. It does not attest account evidence provenance, filesystem ACLs, later budget-lock availability or real schema acceptance.

Concrete operational blocker discovered: only package-private `GeminiAuthorizedExtraction.sequence` was available; no practical executable operator invocation. Added **GeminiInitialSmokeOperator** as a separate PropertiesLauncher main. No Spring/controller/bean/public endpoint or general scheduling framework. Requires `--execute-initial-smoke --enable-synthetic-live` and independently provisioned paths/issuer/key ID. Default/missing arguments deny before file/env/transport access. Verified sequence must be exact ordered VEX1-01/02/03. Limits derive from signed caps, not CLI budget overrides or fictional defaults. Fixed model/transport unchanged. Existing sequence performs durable whole-allocation reservation then session/process reservation and private one-shot permit before lazy GEMINI_API_KEY lookup and real transport construction.

Operator-owned control file must contain exactly ASCII RUN; no newline. Missing/unreadable/changed control or fresh public-root/revocation verification failure stops. Revocations reread before boundaries, no dynamic trust from approval JSON. No live cancellation service: control change during HTTP is observed at next boundary; call deadline≤10s. Process termination leaves unfinished allocation sealed/nonresumable. No promise of immediate remote cancellation.

Immutable outcomes reused; printed scalar categories/schema/semantics/backend result. Existing journals retain safe request/usage/latency metadata. No raw model text/key/provider response output. Non-success/incomplete CLI exits nonzero with fixed redacted error; no retry/model fallback. Exact future command and independent provisioning steps: [operator actions](GEMINI_INITIAL_SMOKE_OPERATOR_ACTIONS.md). It was **not invoked with execution flags and real credentials** in this batch.

10 focused tests exercise success full3 mocked SDK-wire calls, missing intent, unsigned draft, invalid signature, missing evidence, directory mismatch, invalid allocation, control STOP, revoked key and default main. Denials prove zero supplier lookups/transport factories/dispatch. All under network/environment-denying guard; sentinel keys/test-owned Ed25519 keys only. Existing guarded operator tests cover templates/canonical/envelope/public-key/positive read-only verify and unchanged empty journal directory. No production private key or executable operator approval created.

## Real operator prerequisites and current approval state

|Gate|Current status|
|---|---|
|Account/project/key binding/entitlement/region/restrictions|Operator evidence absent; public model listing is not account entitlement|
|Billing/tier/input/output/thinking/other billable classes|BILLING_QUOTA_UNVERIFIED|
|RPM/TPM/RPD/remaining/shared consumers/window|BILLING_QUOTA_UNVERIFIED; three sequential feasibility not yet attested|
|Privacy/retention/training/applicable account tier|Operator confirmation required; synthetic-only remains mandatory|
|Credential environment|CREDENTIAL_CONFIGURATION_OPERATOR_ATTESTATION_REQUIRED; exact variable GEMINI_API_KEY; no value inspected, no claim it works|
|Independent fixture review|INDEPENDENT_ORACLE_REVIEW_REQUIRED|
|Public trust root/issuer/keyId/revocation source|TRUST_ROOT_PROVISIONING_REQUIRED|
|Persistent absolute operator journal path/ACL|TRUSTED_JOURNAL_DIRECTORY_REQUIRED; proposal only, no actual directory provisioned|
|Approval|UNSIGNED / NOT APPROVED / NOT EXECUTABLE; generated draft only, final canonical payload deferred|
|Actual signed read-only preflight|NOT_RUN_MISSING_OPERATOR_ARTIFACT; test-owned valid verify PASS only|
|Practical invocation|Implemented and mocked; no unresolved technical invocation blocker|
|Live permission|LIVE_EXECUTION_APPROVAL_REQUIRED, separate future batch|

Original template intentionally retains missing evidence/rates/window/path rather than inserting invented values. The [unsigned review artifact](GEMINI_INITIAL_SMOKE_UNSIGNED.json) contains freshly proposed unique session/allocation/approval identities, still DRAFT with invalid signature/window and unverified evidence. Identity format: `smoke-<freshUUID>` session, `allocation-<freshUUID>` allocation and `approval_<freshUUID>` reference (within validators); final identity bindings must be reviewed with actual operator values, not reused from tests. Signature must authenticate exact nested v1 review inside operator-extraction-sequence-v2. Allocation campaign identity is that exact session ID. Revalidate short validity immediately before signing/verification; never auto-extend expiry. No real signed artifact stored in Git.

## Public references, account verification and financial exposure

Read-only official references checked24/11/2026: [pricing](https://aistudio.google.com/docs/pricing#gemini-3.5-flash-lite), [rate limits](https://aistudio.google.com/docs/rate-limits), [terms](https://aistudio.google.com/docs/terms). Public standard paid listing: input USD0.30/M and output including thinking USD2.50/M. No account-specific verification. No Free Tier assumption. Rates for Batch/Flex/Priority are not substituted for this standard endpoint. Tools/caching are absent; operator must still verify all applicable token classes and that output-cap accounting bounds thinking/non-input charges. Paid-tier data usage statement alone is not privacy approval. Account restrictions/tier and retention remain operator-only.

Retained conservative signed proposal caps: requests3/concurrency1/retries0, input/call16384, output/call1024, deadline≤10s, run≤3/process≤9, STOP_ON_ANY_FAILURE; reservation input49152/output3072/total52224. These ceilings are not expected usage. With verified account-specific upper rates I and O covering **all** billed classes, `maxExposureUSD=(49152*I+3072*O)/1000000`. Public standard-rate illustration only: `(49152*0.30+3072*2.50)/1000000 = USD0.0224256`; conditional operator allocation proposal USD0.03 only if account confirms those upper rates and complete billing coverage. **Not a real financial approval or final executable exposure.** Signed legacy smoke exposure must be positive≤USD1; no cap raised. If full billing cannot be bounded within existing envelope, do not sign/dispatch. Billing alerts are not guaranteed hard caps. No real billed cost exists.

Quota future plan: after obtaining remaining quotas, conservative interval≥max(60/RPM_available, 60*16384/TPM_available) seconds with appropriate provider-window margin; verify RPD and consumers separately. Three calls must fit fresh≤5min smoke window and allocation window≤2h. If not feasible, stop and resolve genuine quota/window rather than retry after429. Later30–60/100–200 need separate quotas, financial approval and signed campaign; not activated here. No fixed pacing assumption before real quotas.

## Trusted storage semantics

Proposed new absolute outside-Git root `C:\Users\ACER\CheckInnGeminiOperator\journals`, **not provisioned or signed**. Operator ownership/ACL and no cleanup/junction/symlink/rollback are required. SharedAllocation validates exact normalized absolute trusted/requested equality, existing directory and no symbolic-link ancestry; no ACL attestation by Java. Same allocation must use one root across processes. `allocation-v1-<SHA256(allocationId)>.journal`, `evaluation-v1-<SHA256(campaignId)>.journal` for future campaigns, `vex-<SHA256(sessionId)>.journal` for smoke/session tombstone. CREATE_NEW session/campaign prohibits all replay, including previously closed sessions. Allocation ledger holds whole-envelope reservation under OS lock, checksum chain and fsync; COMPLETE may admit separately approved remaining campaigns, never refund prior ceilings. Pending/partial/corrupt/uncertain/crash states deny and never resume automatically. Read-only verify neither opens these journals nor mints permits/reserves allocation.

No claims of malicious deletion/rollback resistance, universal power-loss durability, unrelated account consumers or distributed coordination. Do not reuse/read/modify old sealed smoke directories. Never delete/truncate journals to recover budget. No raw credentials/model response storage.

## Future initial-smoke execution plan

BATCH10B4C-LIVE-INITIAL-SMOKE, only after genuine provisioning + independent review + signed fresh v2 preflight + separate explicit authorization. Invoke exact operator command in action package once. No ordinary Agent ingress/domain calls. Maximum3 actual dispatches, sequential ordered VEX1-01/02/03, retries0, STOP_ON_ANY_FAILURE; no fallback model.

For each future call record actual API/HTTP outcome, strict envelope/schema acceptance, action/intent/fields/missing semantics and literal backend corroboration, known/unknown usage, latency, budget reservation and terminal state. Journals record safe usage/latency; immutable outcomes provide schema/semantic/backend status. Actual READ/grounding/full E2E are NOT_RUN, not inferred from literal acceptance. Allocation reconciliation COMPLETE means accounting terminal, not product quality approval.

All3 successful: protocol/provider readiness evidence only; proceed toward independently reviewed30–60 meaningful NLU samples. Schema rejection: retain exact identity, investigate dialect, no retry. Semantic mismatch: stop and investigate, no automatic retry. Unknown usage/quota/safety/auth/budget/journal uncertainty/cancel/revoke: stop/seal; preserve NOT_RUN remaining rows and consumed grants. Expired artifact requires fresh external signing. No automatic next-batch execution.

## Future Vietnamese NLU review package (does not delay smoke)

Reuse [candidate review](VIETNAMESE_NLU_CANDIDATE_REVIEW.md), [oracle draft](VIETNAMESE_NLU_ORACLE_REVIEW_DRAFT.json), [campaign plan](VIETNAMESE_NLU_LIVE_EVALUATION_PLAN.md); no dataset/oracle modified.60 rows,20 DEVELOPMENT/20 REGRESSION/20 HOLDOUT;49 REVIEW_REQUIRED/11 DENY, all PENDING_INDEPENDENT_REVIEW. Proposed review prioritizes natural/informal/no-accents/typo/mixed-language, prices/numbers/dates, missing criteria/ambiguous occupancy, refine/CLEAR/REPLACE and reference/action/known grammar gaps. Keep hard cases and semantics separate from backend admission. Select30–49 genuinely reviewed eligible distinct cases from current pack;60 distinct needs versioned expansion because11 DENY must remain local.100–200 future expansion/repeated independent samples requires distinct reviewed identities/rationale; never retries or easy-case selection to inflate PASS.

Retain13 mismatches VNE1-016/027/028/029/030/031/033/041/042/045/048/049/051;009/038 invalid-date exceptions;018 missing children hint;044 typo interpretation;8 context-dependent and7 reference-without-resolution cases.035/036/057 remain DENY even without regex block. Do not silently match oracle to finite grammar. Review principles pending operator confirmation: no inferred children0; missing occupancy clarifies dated SEARCH; D1 browse remains; valid paraphrase can be semantically correct/backend-rejected; unambiguous typo normalization may be desirable, ambiguous destination clarifies; unsupported features remain unsupported. Single-utterance extraction/reference-action vs typed state vs actual context-aware reasoning vs full Agent E2E stay separately scored; no context protocol implemented. Visible holdout not blinded. D07/D08 remain unaccepted.

## Final verification and verdict

Final `mvn -o -B -ntp -pl ai-agent-service package`: **BUILD SUCCESS,953 PASS /0 failures /0 errors /0 skipped**,1:14. Baseline943 +10 focused entry-point tests. Scoped `-Dtest=GeminiInitialSmokeOperatorTest test`:10 PASS. Historical remains23/16/0/1, E30 NOT_RUN; no historical assertions/runner edited. Existing Jackson/SecurityManager/ByteBuddy deprecation warnings remain. No source changes after final package. Packaged PropertiesLauncher dry-run: initial-smoke/nlu/extended templates exit0; draft canonicalization and invalid-test-signature enveloping exit0 (not approval); new operator main with no args rejects nonzero before credential/transport. Temporary outputs outside Git: `checkinn-operator-dry-5cc0106f-a655-413c-ae33-c7cfaa749921`. No real trust/key/account/transport initialized. Read-only valid signed preflight exercised with test-owned keys in guarded8-test operator suite; genuine signed preflight remains NOT_RUN. Baseline hash audit before document updates found every previous scoped file unchanged; only additive new main/test/docs at that point. Final expected existing-file edits are setup guide correction and README additive checkpoint; previous reports/resources/source remain unchanged. `git diff --check` passes (existing line-ending notices only); index remains empty. Remaining operator-only actions consolidated in [operator actions](GEMINI_INITIAL_SMOKE_OPERATOR_ACTIONS.md). No further general infrastructure required. Defaults OFF/FAKE/liveOFF; ordinary extraction FAKE-only. Gemini live attempts0, actual Gemini credential reads0, real domain/Gateway/Supabase calls0. Public documentation HTTP reads are separate from model/domain dispatch.

Retain REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN, REAL_VIETNAMESE_NLU_ACCURACY_NOT_RUN, FULL_AGENT_E2E_NOT_RUN; production readiness unproven. Do not report READY_FOR_EXPLICIT_LIVE_INITIAL_SMOKE_AUTHORIZATION until actual signed preflight and operator prerequisites complete.



