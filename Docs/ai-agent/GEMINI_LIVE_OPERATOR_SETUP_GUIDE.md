# Gemini synthetic evaluation — operator setup guide

24/11/2026. **Operator setup/preflight only; no live authorization or execution.** No real keys, trust root, account evidence or approval were provisioned here. Defaults remain OFF; ordinary Agent extraction remains FAKE-only.

Read the [completion report](BATCH10B4C_LIVE_AUTHORIZATION_OFFLINE_COMPLETION_REPORT.md), [candidate review](VIETNAMESE_NLU_CANDIDATE_REVIEW.md) and [nonexecutable oracle draft](VIETNAMESE_NLU_ORACLE_REVIEW_DRAFT.json). INITIAL_SMOKE remains VEX1-01/02/03, run≤3/process≤9. Expanded evaluation uses a distinct signed campaign backed by durable allocation; it does not raise legacy or ordinary Agent caps.

## Review before provisioning

|Item|Required operator action|
|---|---|
|Oracle|Independent reviewer checks expected action/intent/criteria operations/values, missing slots and references. Decide expected literal admission separately from model semantics.|
|Scope|Select exact sampleId/sessionId/fixtureId list. Independent repeated samples require distinct reviewed IDs and a coverage/repeatability rationale; no automatic retries.|
|Account|Verify project/account/model entitlement/region/tier, current pricing including thinking/other classes, RPM/TPM/RPD, terms/retention, synthetic-only approval, shared consumers and finite coordinated allocation/window.|
|Grant|Review exact model/protocol/dataset/oracle/request hashes, UTC times, budgets, continuation/revocation policy and trusted path. Sign outside Agent.|
|Storage|Provision one persistent private directory on the same machine, shared by every campaign/process using that allocation.|

TRUST_ROOT_PROVISIONING_REQUIRED, BILLING_QUOTA_UNVERIFIED and INDEPENDENT_ORACLE_REVIEW_REQUIRED remain until genuine evidence exists. Placeholders/config booleans/offline PASS cannot substitute for authority or account verification.

## Trust root outside Agent

Use approved operator signing infrastructure. If none exists, operator may create Ed25519 keys on a separate signing host. These illustrative OpenSSL commands were **not run with real keys here**; operator must verify their installed tool supports Ed25519/raw EdDSA and protect private-key access/backup.

```text
openssl genpkey -algorithm ED25519 -out operator-private.pem
openssl pkey -in operator-private.pem -pubout -outform DER -out operator-public.der
```

Only X.509 SubjectPublicKeyInfo **public DER** reaches Agent/preflight. Never provide private PEM/PKCS8, API keys, `.env` or `.local` credentials. Private signing key never enters Agent runtime/source/config. Provision issuer/keyId/public key independently of artifact JSON. A valid signature under an arbitrary embedded key is not trusted.

Maintain a separate operator-controlled JSON revocation list of key IDs, approval references, campaign/session IDs or allocation IDs. `[]` means an empty revocation snapshot, not account verification. Rotate with a new keyId/public key and revoke old IDs/references. Launches load a fresh authenticated snapshot; active internal Control.cancel/revoke/kill cancels CallControl and stops later requests. Without an active driver control channel, stopping the process leaves the unfinished allocation nonresumable. No distributed revocation service is claimed.

Provision an absolute persistent directory with operator-owned ACLs, no untrusted writers, symlink/junction redirection, cleanup or backup rollback. Runtime checks path equality, symbolic-link ancestry and NOFOLLOW opens; it does not attest ACLs or defend hostile filesystem TOCTOU/junction attacks. Do not use different roots for campaigns sharing an allocation.

## Offline preparation commands

From backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
$evalJar = '.\ai-agent-service\target\ai-agent-service-0.0.1-SNAPSHOT.jar'
$evalTool = '-Dloader.main=com.example.aiagent.model.GeminiEvaluationPreflight'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher template initial-smoke 'C:\operator-review\smoke-UNSIGNED.json'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher template nlu 'C:\operator-review\nlu-UNSIGNED.json'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher template extended 'C:\operator-review\extended-UNSIGNED.json'
```

The tool runs instead of Spring application startup. It supports template/bind-scope/canonicalize/envelope/verify only: **no execute command, environment credential lookup or external transport**. All outputs use CREATE_NEW; choose fresh filenames, never overwrite prior approval evidence. Templates are UNSIGNED / NOT APPROVED / NOT EXECUTABLE: DRAFT state, invalid signature/timestamps, unverified evidence and zero financial fields. They do not select campaign samples.

Smoke template binds the three pinned fixtures/request digests and ordered STOP_ON_ANY_FAILURE policy. Fill/review nested approval/review and shared allocation only after real operator approval.

For campaigns, independently review a copy of the oracle draft. It has DRAFT_NOT_INDEPENDENTLY_REVIEWED and every remoteApproved=false; runtime rejects it. Reviewer must correct targets/backend expectations/categories, document genuine independent review, supply reviewerReference/new version and approve selected eligible rows. Runtime requires reviewStatus=INDEPENDENTLY_REVIEWED **and** exact oracle hash signed by trusted authority. That field alone grants nothing. DENY/premodel-blocked rows remain local even if mistakenly approved in an oracle.

Scope file format only, not an approved scope or count:

```json
[{"sampleId":"REVIEWED_SAMPLE_ID","sessionId":"REVIEWED_SESSION_ID","fixtureId":"VNE1-001"}]
```

After independent oracle review, compute exact bindings from a chosen scope:

```powershell
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher bind-scope nlu 'C:\operator-review\scope.json' 'C:\operator-review\reviewed-oracle.json' REVIEWED_CAMPAIGN_ID 'C:\operator-review\campaign-payload-DRAFT.json'
```

Use extended for the separately reviewed extended mode. This computes exact utterance/context/instruction/schema/request digests and oracle byte hash, retaining DRAFT/unverified financial fields. It does not sign, approve, generate sample/session IDs or read credentials. Current protocol sends **one utterance only**; synthetic context is bound for integrity but is not transmitted. Reference/action extraction is not context-aware model reasoning or full Agent E2E.

## Complete the payload

Smoke version is operator-extraction-sequence-v2; campaign version is operator-evaluation-campaign-v1. Fill trusted issuer/keyId, operator/approval reference, unique campaign/session/sample identities, approved lifecycle state only after actual approval, absolute journal directory and precise UTC timestamps. Smoke≤5min; allocation/campaign window≤2h; each call≤10s. Future/expired approvals fail closed. If utterance dates become stale, prepare a separately reviewed dataset version instead of editing pinned packs.

Campaign limits: requests≤200, sessions≤200, input/call≤16384/output1024, concurrency1/retries0, explicit duration/expiry, per-session requests/total tokens, campaign input/output/total tokens and exposure. Samples must fit every bound; sessions are contiguous. CONTINUE_SEMANTIC_ONLY records ordinary linguistic/grammar failures and continues; STOP_ON_ANY_FAILURE is also supported. Provider/schema failures conservatively stop in both. Unknown usage/quota/budget/auth/unsafe output/journal uncertainty/cancel/revoke always stop. No retry/fallback.

Allocation descriptor binds allocation ID, project/account, window, request/input/output/total/money ceilings, concurrency1 and approved campaign IDs. All campaigns sharing one allocation carry the **identical descriptor**. Initial smoke uses session ID as its allocation campaign identity. Do not change a descriptor/campaign ID or restart JVM to evade consumed totals. New allocations/windows require genuine operator allocation, not automatic minting. Session/sample identities cannot be reused across one allocation.

Rates are verified upper bounds per million input and non-input tokens. Campaign pricingCoverage=ALL_BILLABLE_CLASSES_UPPER_BOUND is permitted only if evidence proves it covers thinking and every other billable class; total-minus-input is bounded by output cap. If that envelope cannot bound actual billing, do not sign/dispatch. Never assume Free Tier or zero thinking cost.

`reservationCost = (inputCap*verifiedInputUpperRate + outputCap*verifiedNonInputUpperRate)/1_000_000`.

Whole signed campaign envelope is consumed durably up front, including never-run samples. No refund on unused budget/error/cancel/unknown billing. Per-attempt campaign/session/process reservation still occurs before each key lookup. Restart cannot create a new balance. Financial examples in tests are fictional, not current Gemini prices.

## Canonical bytes and external signature

Review the completed **payload object**, not the full unsigned envelope. Oracle SHA covers its exact bytes, including whitespace. Canonicalize using the shipped implementation:

```powershell
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher canonicalize 'C:\operator-review\reviewed-payload.json' 'C:\operator-review\canonical.bin'
```

Profile v1: recursively sorted object keys, retained array order, normalized exact decimal numbers and compact Jackson UTF-8. Not an RFC8785 claim. Do not round decimal rates or reorder samples through another serializer.

Operator signs canonical bytes on the signing host, outside Agent. Illustrative procedure, not executed with real keys here:

```text
openssl pkeyutl -sign -rawin -inkey operator-private.pem -in canonical.bin -out approval.sig
```

Return only the64-byte detached signature and envelope it without loading any private key:

```powershell
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher envelope 'C:\operator-review\reviewed-payload.json' 'C:\operator-review\approval.sig' 'C:\operator-review\approval.json'
java $evalTool -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher verify 'C:\operator-review\operator-public.der' REVIEWED_ISSUER REVIEWED_KEY_ID 'C:\operator-review\revocations.json' 'C:\operator-review\approval.json' 'C:\operator-review\reviewed-oracle.json' 'C:\operator-journals' OFFLINE_ONLY
```

Enveloping is not verification or approval. For smoke, use `-` for the unused oracle argument. Verify checks closed signature/authority/evidence/window/exact scope/oracle/model/schema/request/budgets/directory without journals, credentials, Spring or external transport. It cannot guarantee later lock/budget/window availability: runtime reserves atomically. PASS is **not LIVE_READY** or live execution permission.

## Separate future live batch and recovery

After genuine operator setup and fresh offline preflight, obtain explicit live authorization for the exact signed artifact/scope/account/budget/window. A separately reviewed operator host calls existing internal GeminiAuthorizedExtraction.sequence or GeminiEvaluationRunner.run real-transport overload with provisioned verifier/directory/clock, explicit feature/intent controls and a credential supplier from existing secure configuration. Their full reservation→credential→fixed transport→validation→ingestion path is implemented; no new prompt/model/endpoint/budget logic is needed. This offline tool intentionally has no automatic live command/bean/public endpoint. Old allocation-less v1 real entry now denies.

Review immutable observations and planned/eligible/attempted/schema-valid/premodel/NOT_RUN counts, separate model/backend results, known/unknown usage and descriptive latency. READ/grounding/booking/full multi-turn remain NOT_RUN in extraction-only evaluation. Preserve journals. COMPLETE reconciles accounting and never refunds exposure. UNCERTAIN/missing terminal/corrupt/partial/existing campaign tombstone means stop. Never delete/truncate/reset to recover budget; process-loss campaigns never resume automatically. Single-machine locking does not control unrelated account consumers or defend malicious deletion/rollback/storage loss.

Roadmap:3-request API/schema/usage smoke → independently reviewed30–60 Vietnamese samples (current49 candidates remain unreviewed) → architecture improvements from actual observed errors →100–200 broader/fresh-holdout or justified independent samples → separately approved synthetic E2E. No real domain calls. D07/D08 and production readiness remain unaccepted.

## 24/11/2026 operator invocation correction

The offline GeminiEvaluationPreflight tool still has no execute command. The subsequent operator-setup recheck discovered that the package-private sequence alone was not a practical invocation path and added a separate `GeminiInitialSmokeOperator` main, disabled unless both explicit live intent flags are supplied. It reuses the existing signed v2/allocation/session/process/credential/wire gates and permits only ordered VEX1-01/02/03. No ordinary Agent feature is enabled. Earlier instruction to arrange an internal Java host is superseded for initial smoke only.

Use the exact [operator action package](GEMINI_INITIAL_SMOKE_OPERATOR_ACTIONS.md) for independently provisioned trust/config/control paths and the future command. Do not execute it in setup: account evidence, independent fixture review, actual trust root/journal directory, fresh signature/read-only preflight and separate live permission are still required. [Recheck report](BATCH10B4C_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK_REPORT.md). No production key or journal directory was provisioned.
