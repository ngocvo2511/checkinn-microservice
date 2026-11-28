# BATCH 10B.4C-LIVE-AUTHORIZATION-OFFLINE-COMPLETION

24/11/2026, Asia/Ho_Chi_Minh. **AUTHORIZATION_OFFLINE_COMPLETION_DONE — READY_FOR_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK**. This is implementation/offline evidence only; no live execution permission.

## Baseline and preservation

Actual branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`; no checkout/reset to historical values. Root AGENTS read; recursive backend scan found no nested AGENTS. Baseline tracked dirty: `.idea/compiler.xml`, `.idea/encodings.xml`, Agent README. Prior untracked work: previous authorization report/evaluation plan; GeminiOperatorApproval/AuthorizedExtraction/EvaluationCampaign/EvaluationMetrics; their three test files; VNE1 JSON/hash. These are previous batch work, not all newly created here. Index empty/unchanged. No commit/push/stage/stash/reset/clean/checkout/delete-untracked.

Before edits, captured SHA-256 of scoped main/test/resources/POM/README/reports plus IDE files in temporary storage outside repo. Read current authorization report, evaluation plan and prior 10B4C unblock/preflight/offline evidence, 10B4B contracts/decisions, 10A/READ/orchestration context and Agent README/source/tests. Source remains authoritative; dated checkpoints are not current guarantees. No `.env`, private `.local`, actual API key, old sealed session or console read.

Baseline command from backend root: `mvn -o -B -ntp -pl ai-agent-service package`: **854 PASS /0 failures /0 errors /0 skipped**, BUILD SUCCESS, elapsed **24.457s**. Historical **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. No discrepancy.

## Blocker matrix

|Blocker|Before|After implementation|
|---|---|---|
|B1 multi-fixture smoke|Wrapper sealed after one request|Versioned signed sequence, ordered three-fixture session, durable allocation + existing session/process caps, exact mocked dispatches/stop/seal tests|
|B2 campaign signature|Unsigned in-memory policy/sample list|Closed Ed25519 campaign-v1 artifact, exact samples/sessions/context/request/oracle binding, no unsigned overrides|
|B3 durable shared allocation|In-memory Process only|Persistent allocation ledger, exclusive OS lock, whole-envelope reservation, replay counters and conservative crash/uncertainty seal; actual child JVM tests|
|B4 execution|Reservation-only component|Signed grant→dataset/oracle→controls/path→allocation→campaign/session/process→private permit→credential→fixed SDK/wire/HTTP→validation→scoring/corroboration→journal|
|B5 ingestion|Disconnected scorer|Immutable per-sample observations/report connected to actual mocked executions; planned/NOT_RUN preserved and semantics/backend separated|
|B6 independent review/provisioning|Absent|Review matrix, nonexecutable oracle draft, unsigned templates, exact-scope preparation/canonical/envelope/read-only preflight tooling and guide; actual operator/reviewer evidence still absent|

No required execution/accounting boundary remains design-only in the implemented single-machine extraction scope. Distributed/cloud coordination, enterprise IAM, automatic resume and context-aware/E2E protocols are deliberately outside scope.

## Multi-fixture initial smoke

`GeminiAuthorizedExtraction.sequence` verifies a versioned `operator-extraction-sequence-v2` artifact **once**, using existing GeminiOperatorApproval canonicalization/trust roots. Nested v1 review retains every old restriction. Signed fixtureOrder must contain unique approved VEX1-01/02/03 only, size1…3 and equal the reviewed fixture map/request count; continuation must be STOP_ON_ANY_FAILURE. No fourth/unapproved fixture. One existing GeminiExtractionSession, private one-shot capability per fixture, old shared GeminiBudget.PROCESS, same strict wire/schema/literal validator. Shared allocation reserves the entire reviewed envelope first. Returns immutable ordered outcomes; existing per-attempt journal retains fixture identity/usage/result and final SUCCESS_SEALED when appropriate. Fault at fixture2 blocks fixture3; cancellation/revocation/kill checks prevent later dispatch.

Old real v1 wrapper now denies because that artifact has no shared allocation. Its injected mock compatibility seam remains for existing tests; it cannot be confused with the new real sequence path. No old smoke grant/journal is reused or modified. Existing legacy run≤3/process≤9 and existing financial/call caps unchanged. Signed sequence may not use the previously discussed3–5 planning range to raise caps.

## Signed campaign contract and exact allowlist

`GeminiCampaignApproval` consumes the same independently provisioned GeminiOperatorApproval issuer/key map and Ed25519 canonical profile, with distinct `operator-evaluation-campaign-v1` closed payload. It binds version/issuer/keyId/operator/approval/state/campaign/mode/purpose/model/protocol/dataset version/hash/reviewed oracle version/hash/classification/not-before/expiry/duration, exact ordered sample IDs/session IDs/fixture IDs/utterance-context-instruction-schema-request digests, input/output/total/per-call/per-session/concurrency/retry limits, financial envelope/project/allocation/journal/revocation/continuation and external evidence references.

Only VIETNAMESE_NLU_EVALUATION and EXTENDED_NLU_EVALUATION extraction purposes are admitted. INITIAL_SMOKE has its own verifier/limits; SYNTHETIC_E2E/extractor-only adversarial purposes are not enabled. State must APPROVED_FOR_SEPARATE_LIVE_BATCH; key/approval/campaign/allocation revocations deny. Future/expired/malformed/missing/duplicate/unknown/coerced fields, invalid signature/untrusted key/issuer/root injection, wrong model/protocol/manifest/oracle/sample/context/request/schema/financial envelope fail closed before credentials. No caller-selected endpoint/prompt/model or arbitrary ChatRequest argument.

Pinned runtime copy of original VNE1 pack is byte-identical, SHA **9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950**. Runtime resolves only its fixtures. An external reviewer oracle has closed schema, precise file-byte hash and reviewer reference/status. Signature authenticates that exact oracle; reviewStatus/remoteApproved alone are never authorization. Original candidate status stays draft. DENY/premodel cases stay local; REVIEW_REQUIRED needs both reviewed oracle and signed sample scope. Repeated sampling uses distinct sample IDs explicitly listed in the signature, never runtime retries or automatically minted sessions. Context digest is bound but context/history is **not sent** by the single-utterance protocol.

### Actual artifact sizes

Test-only signed compact artifacts, short test identities, same complete per-sample bindings:

|Samples|Signed envelope UTF-8 bytes|Canonical payload bytes|
|---:|---:|---:|
|30|17442|17334|
|60|32804|32696|
|100|53290|53174|
|200|104590|104474|

Generated by test in `target/campaign-artifact-measurements.json`. 200 samples exceed old64KiB. Campaign cap is separately **256KiB**, including bounded parser depth/string/strict duplicate/field/coercion checks; smoke remains64KiB. Exact bindings are retained, not dropped to fit. Longer operator identities still have finite bounds and oversized artifacts reject. This is capacity evidence, not an issued live grant.

## Durable shared allocation and restart policy

`GeminiSharedAllocation` uses `allocation-v1-<SHA256(allocationId)>.journal`, separate from evaluation-v1 campaign and vex/old smoke journals. Limits bind project/account reference, verified window, request/input/output/total/money ceilings, concurrency1 and approved campaign identities. Descriptor must match the original ledger binding exactly; no caller path/ID override. All campaigns sharing it must use one trusted absolute persistent directory. Same allocation cannot become a fresh balance by changing campaign ID, session/sample identity or restarting JVM.

The **whole signed campaign envelope is reserved/consumed up front** under one exclusive FileLock held until terminal accounting. No refund of unused samples/tokens/exposure, errors, timeouts or unknown usage. Per-attempt campaign/session/process reservations still occur afterward before key lookup. New purpose-specific Process instance is bounded by the already durably consumed allocation envelope; it does not replace/reset legacy GeminiBudget or create unbacked spend. Campaign/session/sample duplication and aggregate exhaustion deny; two signed campaign executions sharing one descriptor are integration-tested.

CREATE_NEW bootstrap; existing ledger opens without reset/truncation. Records have descriptor binding + previous/checksum chain, strict fields and bounded read≤512KiB/record≤64KiB, durable force(true). Existing zero/partial/corrupt/valid-header-only/unfinished RESERVED/UNCERTAIN state denies. COMPLETE allows a separately signed approved next campaign only with remaining allocation; it never refunds prior consumption. Unknown in-flight/process loss seals the allocation, not merely the prior campaign. Campaign tombstones remain CREATE_NEW/nonresumable, even for unattempted fixtures. No automatic reconciliation/reset/resume engine.

Evaluation campaign journal now permits≤4KiB allowlisted metadata records/≤512KiB total, separately from unchanged vex64KiB. This accommodates200 observations plus reservations/terminal metadata without raw response retention. Failed disk/lock/journal operations deny and preserve conservative allocation consumption.

### Cross-JVM evidence

`GeminiSharedAllocationTest`:12 invocations using **actual controlled child JVMs**, not an in-memory substitute. One child acquires/holds the allocation; concurrently launched second child returns DENIED. After first reconciles/releases, an explicitly allowed remaining campaign can consume. Separate JVMs prove restart consumption, new campaign denial at exhaustion, duplicate campaign/session/sample rejection, descriptor substitution, empty/partial/corrupt/header-only denial, and Runtime.halt process loss leaving a nonresumable reservation. Directory substitution also rejects. Children install network/environment-denying guards and have no provider/domain clients. No cross-JVM test skipped.

Assumptions: one machine, operator-controlled persistent directory/ACLs and trusted monotonic storage. No defense claimed against malicious deletion/rollback/junction substitution/TOCTOU, storage/power-loss anomalies, unrelated hosts/account consumers or operator-issued conflicting allocations in different roots. force(true) is not a claim of hostile-storage rollback or universal power-failure guarantees. External shared-project consumer coordination remains an operator prerequisite.

## Integrated execution and continuation

`GeminiEvaluationRunner` verifies signed campaign/evidence/oracle and exact dataset bindings, checks explicit intent/feature/kill/cancel/revoke, validates directory, durably consumes shared envelope, opens campaign, reserves campaign+session+purpose-specific process per sample, issues thread/owner/sample-bound private one-shot permit, then resolves credential and lazily constructs fixed GeminiHttpTransport. Real and mocked overloads use the same path. There is no public evaluation endpoint, bean, ordinary Agent entry or generic model routing.

GeminiExtractionProtocol reuses pinned SDK serialization; common GeminiExtractionSmoke Wire handles exact fixed HTTPS endpoint/body/header admission, no retries/tools/search/maps/code/url context, request/response bounds, cancellation/deadline, safe errors and strict usage/envelope/extraction parsing. Existing GeminiHttpTransport is reused unchanged; no second HTTP stack, endpoint override or connectivity probe. Literal corroboration helper is shared, without loosening existing VietnameseExtraction/confirmation/PolicyExecutor/GroundingGate behavior. A literal DateTimeException is captured as backend rejection in evaluation; original grammar/oracle are untouched.

Policy CONTINUE_SEMANTIC_ONLY records wrong intent/criteria/missing slots/clarification and model-correct/backend-rejected results, then proceeds to next independently signed sample. No retries. STOP_ON_ANY_FAILURE is available and mandatory for smoke. Provider/schema/protocol failures conservatively stop in both policies; there is no hidden provider-failure continuation. Unknown usage/quota/budget/auth/wire identity/unsafe inferred fields/tool/confirmation exposure/journal/cancel/revoke/kill stop and retain remaining NOT_RUN. Optional thinking/cached counters must be consistent with bounded total/input usage; unknown/inconsistent exposure is not zero.

Unsafe/confirmation checks are conservative stop classifiers plus unchanged strict proposal validators, not a general secret detector certification. No tool execution path exists in this runner. Actual READ/WRITE dispatch and full Agent E2E remain NOT_RUN; output tool-attempt flags are distinct from actual tool execution.

### Fully mocked configurations

Signed30/60/100/200 sample scopes each execute complete signature→admission→durable envelope→campaign/session/process→sentinel→exact actual SDK wire→mocked response→strict parser→semantic/backend scoring→immutable ingestion→journal pipeline. Largest: **200 requests,10 explicitly signed sessions×20 samples, concurrency1, retries0, input/call16384/output1024; reserved input3276800/output204800/total3481600; explicit30min test window**. Test exposure10USD inside a100USD allocation and fictional1/2 upper rates are **test arithmetic only, not Gemini prices or live proposals**.

These integration tests reuse three reviewed **test-only** VNE fixtures with distinct pre-signed independent sample IDs. They prove infrastructure scaling, not200 distinct linguistic cases or real model quality. Previous accounting-only3/5/30/60/100/200 tests remain, but30/60/100/200 now also have full mocked dispatch/ingestion coverage;5 remains accounting-only. No expanded budget activated in real runtime.

## Dataset review and SDK measurements

Original VEX1 pack/hash **7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3**, VNE1 pack/hash and original E01–E40/runner/assertions untouched.60 fixtures remain20 DEVELOPMENT/20 REGRESSION/20 HOLDOUT,49 REVIEW_REQUIRED/11 DENY, all PENDING_INDEPENDENT_REVIEW. The visible holdout is not blinded.

[Technical review](VIETNAMESE_NLU_CANDIDATE_REVIEW.md) covers all60: closed expected protocol parsing, fields/operations/date/count/price/reference/missing slots, premodel/classification/privacy/severity/split/context and current grammar. All60 draft response shapes parse, which does not independently validate expected business semantics.45 literal accepted results include safe clarification/reference extraction;13 candidate/grammar mismatches,2 invalid-date literal exceptions. VNE1-018 missing children hint requires review;8 context-dependent candidates are not context-aware model tests;7 references are not actual state-resolution proof. Three DENY cases are not regex-blocked but remain local due the pinned dataset fence. No expectations silently revised to match grammar.

[Oracle review draft](VIETNAMESE_NLU_ORACLE_REVIEW_DRAFT.json) translates response representation only; every remoteApproved=false and reviewStatus DRAFT_NOT_INDEPENDENTLY_REVIEWED. It is not executable, independently reviewed or signed. Its backend/grammar targets are explicit proposals for reviewer correction.

Pinned SDK1.17.0 serialized all60 utterances in in-memory Capture: normalized bodies **5733–5817 bytes**, conservative input **12490–12658**, output1024, no tools. Hashes in generated `target/vietnamese-candidate-technical-review.json`; unchanged instruction/schema/request bindings for original smoke. Synthetic context is not transmitted. No countTokens or actual schema/API acceptance measurement.

## Result ingestion and metrics

Immutable Observation metadata binds campaign/session/sample/fixture/dataset/oracle/category/status/provider/schema/semantics/backend/safety/usage/latency/stop reason/diagnostic codes. Completed rows cannot be removed/overwritten; duplicate results reject. Unattempted planned rows remain NOT_RUN, including stop reason. Per-sample raw protocol values remain only in bounded transient scoring memory, not journals/report. Safe diagnostics identify mismatch axis/missing or unexpected field/operation/grammar rejection, never raw values or response text.

Runner connects every outcome to existing GeminiEvaluationMetrics. Reports planned/eligible/attempted/schema-valid/local-blocked/NOT_RUN counts; API/schema/action/intent/exact criteria/per-field/missing slots/clarification; unsafe output/tool attempts/fabricated confirmation, backend false acceptance/rejection and separate model-correct/backend-accepted or rejected; budget-violation observations; known/unknown usage; p50/p95/counts. Numeric representation and unordered missing-slot hints normalize for semantics; original protocol/security validation is unchanged. Failed provider/schema attempts remain in attempted denominator, NOT_RUN is never success, premodel-blocked never remote. Policy-denied pre-dispatch attempts cannot be counted as successful requests. Unknown usage stays unknown, not fabricated zero. No verified observed model cost exists.

Extraction-only E2E status is explicitly NOT_RUN_EXTRACTION_ONLY. Correct model/grammar-rejected test retains semantic PASS/backend REJECTED and continuation; unsafe output safely rejected remains model failure, not model PASS. Descriptive latency percentiles at small n are not SLO certification. No aggregate percentage overrides safety failures. Proposed98% schema/95% semantic targets remain proposals with sample-size limitations in existing plan; D07/D08 unaccepted.

## Operator tooling and provisioning status

[Operator setup guide](GEMINI_LIVE_OPERATOR_SETUP_GUIDE.md) gives external Ed25519 key/public-DER provisioning, issuer/key ID/revocation/rotation/expiry/directory procedures, account checklist, unsigned smoke/NLU/extended templates, exact scope binding, canonical byte preparation, external signing/enveloping and read-only preflight commands. Public tool supports only offline preparation/verification; no execute command, credential source or external transport. CREATE_NEW outputs avoid overwriting evidence. No production private key/trust root/real approval created. Test keys/signatures/oracle status are explicitly test-owned and cannot represent actual operator approval.

Actual packaged JAR dry-run: invoked PropertiesLauncher + GeminiEvaluationPreflight template for initial-smoke/nlu/extended, all three exit0 and print UNSIGNED_NOT_APPROVED_NOT_EXECUTABLE. Outputs were created in a new temporary review directory outside repo; no signing/key/account/transport operation. Canonical/envelope/bind-scope/public-key/preflight paths also exercised by guarded test-owned fixtures. No OpenSSL production key/signing command was run.

TRUST_ROOT_PROVISIONING_REQUIRED; BILLING_QUOTA_UNVERIFIED; INDEPENDENT_ORACLE_REVIEW_REQUIRED. Signed evidence references bind operator attestations; this batch never accessed account consoles or authenticated actual prices/quota/terms. Genuine project/model/region/tier/entitlement, thinking/other billing classes, quotas/shared consumers, allocation/window and privacy terms must be reviewed before signing. No Free Tier/price assumption. No real artifact issued.

## Tests, final verification and exact scope

Baseline854 retained unchanged. New tests: GeminiCampaignExecutionTest57; GeminiSmokeSequenceTest9; GeminiSharedAllocationTest12; GeminiEvaluationReviewTest3; GeminiEvaluationOperatorToolTest8 =89. Helpers GeminiEvaluationTestSupport/GeminiAllocationChild are test-only. Network/key-environment guards and sentinel/factory/dispatch counters demonstrate no external calls/actual credential lookup; children are also guarded. Existing loopback HTTP tests still use synthetic local sockets, not real domain services.

Positive/negative coverage: valid signatures and complete30/60/100/200 mock runs; correct ordered three-call smoke; no fourth/duplicate/unapproved fixture; fixture2 stop/cancel/revoke; signature/root/issuer/key/mode/model/protocol/dataset/oracle/context/utterance/request/schema/state/expiry/future/financial/unknown/missing/duplicate/oversize/evidence/template rejection before key; shared allocation/journal/path/budget exhaustion/replay; multi-campaign accounting; actual cross-JVM exclusion/restart/crash/corruption/header-only; semantic continuation/strict stop; unsafe/schema/unknown/quota/budget/tool/provider/cancel/kill stops; remaining NOT_RUN; immutable observations, oracle substitution and offline tooling.

Intermediate review test failed once on existing invalid-date DateTimeException; corrected the evaluation classification to backend rejection and documented the parser behavior, without editing original grammar/oracle or weakening historical assertions. Earlier full939/941/942 runs passed while additional operator/scorer/header-only boundary checks were completed. Final source is verified by the final command below, not by those earlier totals.

Final command `mvn -o -B -ntp -pl ai-agent-service package`: **BUILD SUCCESS;943 tests PASS /0 failures /0 errors /0 skipped**, elapsed **1min31s**. Existing854 + new89 retained. No source changes after this final run; subsequent changes are report/README only.

Historical final **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**; E30 stays NOT_RUN. New tests are not historical/model-language PASS. No excluded/skipped tests. Existing Jackson/SecurityManager deprecation warnings remain; no dependency/runtime version change. Whitespace/UTF-8/links/hash/diff audit recorded separately; Maven alone generated target outputs.

Modified existing scoped source: GeminiAuthorizedExtraction, GeminiOperatorApproval, GeminiEvaluationCampaign, GeminiExtractionProtocol, GeminiExtractionSession, GeminiExtractionSmoke. Append README only. New main: GeminiSharedAllocation, GeminiCampaignApproval, GeminiEvaluationDataset, GeminiEvaluationRunner, GeminiEvaluationPreflight. New main resource: byte-identical VNE1 pinned copy. New test files: five Test classes above + two helpers. New documents: this report, setup guide, candidate review and oracle draft. Previous reports, original packs, existing test sources, POM/dependencies/model/config, ordinary budgets, old smoke journals/protocol restrictions and unrelated IDE work are preserved. No frontend/Place/Gateway/domain/DB/Config Server modifications.

## Final gates and next step

**AUTHORIZATION_OFFLINE_COMPLETION_DONE** within the documented single-machine, trusted-filesystem, synthetic extraction scope. **READY_FOR_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK**, not LIVE_READY. Required technical B1–B5 are implemented/integrated/tested, including true cross-JVM allocation behavior. No further technical blocker is reported within that scope; B6 genuine review/provisioning/evidence/live permission remains operator-only. This does not certify actual API/schema/model quality, account-wide coordination, hostile-storage recovery, full Agent E2E or production readiness.

Gemini live attempts **0**; actual credential reads **0**; real domain/Gateway/Supabase calls **0**; external network **0**. Feature defaults OFF/FAKE/liveOFF; no public ingress/WRITE tools/dependency/model upgrade. Actual model schema acceptance/Vietnamese accuracy/real latency/cost remain NOT_RUN. D07/D08 and production readiness unaccepted.

Recommended next batch: **OPERATOR_SETUP_AND_PREFLIGHT_RECHECK**, with real independent oracle review, independently provisioned public key/trusted directory, actual billing/quota/privacy/coordinated allocation evidence and fresh exact signed artifacts. After that, request separate explicit bounded live authorization for initial VEX1-01/02/03 smoke, then30–60 quality samples selected by reviewed coverage, architecture improvements based on results,100–200 broader/fresh-holdout or justified independent samples, and separately approved synthetic E2E. No automatic live transition or real-domain calls.
