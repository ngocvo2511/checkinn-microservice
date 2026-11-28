# Batch 10B.4A-LIVE — preflight review

23/11/2026, Asia/Ho_Chi_Minh. **PARTIAL — PRECHECK_REQUIRED; live readiness BLOCKED.** Offline audit and preflight preparation only. Gemini inference **NOT_RUN**; no authenticated Google model/list/countTokens request, account mutation, Gateway, Docker, Supabase, frontend or Batch10B.4B action. No approval or VERIFIED state fabricated. This report does not authorize execution.

## 1. Git baseline and scope

Backend `feat/ai-agent-read-service`, HEAD `fa388a2b2fa5787bafed7fa85324cb543a22acc7`. Initial status: modified `.idea/compiler.xml`, `.idea/encodings.xml`; preserved. Workspace root AGENTS read; no narrower AGENTS found under Agent/Docs. Frontend outside this task, untouched. No stage/commit/push/stash/reset/clean/checkout.

Read the 10B.4A integration report, 10B.3 orchestration report, architecture, implementation plan, Agent README/POM/YAML, provider/config/transport/protocol/budget/fixtures/harness and provider/transport/orchestration tests. Historical fake-only and missing-orchestration paragraphs are superseded by current source. Old reports' branches, untracked status and test totals are historical, not current evidence.

Changes restricted to `GeminiSyntheticSmoke.java`, `GeminiProviderTest.java`, Agent README and this report. Summary now includes sanitized reviewed model, concurrency cap and local PASS/FAIL checks; operator verification values explicitly do not establish external evidence. No dependency, activation default or business logic change.

## 2. Exact model and documentation evidence

Official pages retrieved on 23/11/2026; ai.google.dev documentation redirects to aistudio.google.com. Public web reads are not authenticated Gemini API calls. Model availability in the catalog is distinct from project entitlement.

| Candidate | Documentation findings | Decision |
|---|---|---|
| `gemini-2.5-flash-lite` | Stable, not deprecated, no shutdown date announced; access restricted to users who actively used 2.5 previously. Function calling and structured outputs supported; input 1,048,576/output 65,536 tokens | Prior candidate retained for review only; actual account availability NOT_VERIFIED. Do not assume a new project can use it |
| `gemini-3.5-flash-lite` | Stable, July2026; no shutdown date announced; function calling and structured outputs; same input/output limits | Proposed preferred alternative for a new project, pending explicit model approval and pinned SDK compatibility review. No configuration switch or inference trial |

Sources: [2.5 model](https://aistudio.google.com/docs/models/gemini-2.5-flash-lite), [3.5 model](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite), [lifecycle](https://aistudio.google.com/docs/deprecations), [current catalog](https://aistudio.google.com/docs/models). Current catalog includes Gemini4 models and Gemini3 Flash variants; newest model alone is not the Free Tier choice. Neither Vietnamese response quality nor live schema acceptance has been evaluated; the fixed smoke prompt is English and cannot prove Vietnamese dialogue quality.

[Standard pricing](https://aistudio.google.com/docs/pricing): both candidates list free input/output quota. Published paid text rates per million tokens: 2.5 input $0.10/output $0.40; 3.5 input $0.30/output $2.50, including output thinking. These public rates are not this project's entitlement or an approved allocation. No built-in grounding/caching/batch features used. Recheck prices immediately before any approved run.

[generateContent reference](https://aistudio.google.com/api-reference/generate-content) documents usageMetadata prompt/candidate/total token counts and finish reasons. Actual returned metadata remains NOT_VERIFIED. [Structured outputs](https://aistudio.google.com/docs/structured-output) are advisory to the application: strict GroundingGate remains authoritative. No simultaneous tools+JSON assumption or automatic model fallback.

## 3. Account, Free Tier, billing, quota and key

| Required evidence | Status | Observation |
|---|---|---|
| Exact project/account/model entitlement | NOT_VERIFIED | No authenticated project state supplied or connected Google Cloud/account verifier available |
| Account and execution region eligibility | NOT_VERIFIED | [Supported regions](https://aistudio.google.com/docs/available-regions) includes Vietnam; timezone does not establish actual account/execution location |
| Project Free Tier for selected model | NOT_VERIFIED | Public free price table is insufficient |
| No unintended active paid billing | NOT_VERIFIED | No billing console/account evidence |
| Project RPM/TPM/RPD and remaining allowance | NOT_VERIFIED | No project-specific active limits/usage evidence |
| Default `GEMINI_API_KEY` process environment presence | FAIL / absent | Checked only nonblank presence and local accepted shape; no value printed, no private env file read |
| Key validity, ownership and model permission | NOT_VERIFIED | No provider call made; absent process variable says nothing about other shells or secret stores |

[Rate limits](https://aistudio.google.com/docs/rate-limits): RPM, input TPM and RPD apply per project across keys; any exceeded dimension errors, RPD resets midnight Pacific. Active limits are account/model/tier dependent and not guaranteed. Do not reuse historical generic numeric quotas. A 429/RESOURCE_EXHAUSTED stops this local ledger without retry.

[Billing](https://aistudio.google.com/docs/billing): keys inherit project billing/tier; a key is not a financial control. No billing setup, upgrade, unlink, new key or quota change performed. No account state can be inferred from published eligibility rules.

## 4. Data terms review

Public documentation review **PASS**; account-specific applicability and operator consent **NOT_VERIFIED**. [Additional Terms](https://aistudio.google.com/docs/terms), effective23/03/2026: API Terms also apply; developer/business use, age18+, supported-region requirements. EEA/UK/Swiss rules differ, including paid-service restrictions for API clients made available to users there. This internal smoke publishes no API client.

Unpaid inputs/outputs can support product/model improvement and human review; sensitive, confidential and personal information must not be sent. Paid treatment differs and does not establish zero retention. [Abuse monitoring](https://aistudio.google.com/docs/usage-policies) describes55-day retention of prompts/context/output for abuse prevention and possible authorized review. This is not a blanket maximum for every data-use purpose or a project-specific retention guarantee. Search/Maps-specific30-day wording is not the retention policy for this smoke; both features are disabled.

Only compile-time synthetic instructions, synthetic prompt and empty evidence are eligible. No chat history, real records, names/contact details, credentials, payments, dumps or Gateway responses. Operator must acknowledge applicable data terms and retention before any future run; runtime data-terms attestation stays NOT_VERIFIED.

## 5. Transport audit

| Control | Source/offline result | Limit |
|---|---|---|
| HTTPS, fixed endpoint/model | Guarded SDK SPI requires exact POST `https://generativelanguage.googleapis.com/v1beta/models/{reviewed-model}:generateContent`; model identifier constrained, equality checked | Google TLS/model acceptance NOT_RUN |
| Retry/redirect | SDK maxRetries0; Apache automatic retries/redirects disabled; SPI one execute | No automatic second attempt or fallback |
| Ambient credentials/proxy | Dedicated Apache client does not use system proxy configuration; auth negotiation/cookies/auth cache disabled; headers rebuilt | Key only x-goog-api-key; no inbound JWT/cookie/trace forwarded |
| Deadline/cancel | Minimum provider/caller monotonic deadline; cancellation and daemon watchdog cancel HttpPost | Loopback socket abort proves local behavior, not remote server work cancellation |
| Response cap | 64KiB actual byte cap, JSON MIME, identity encoding, strict duplicate/trailing/depth checks | No unbounded raw body output |
| Errors | Fixed typed categories, no retained raw cause/body; quota stops ledger | No raw provider response printed |

`GeminiHttpTransport` has a package-only127.0.0.1 test seam; production adapter constructor uses fixed transport, no externally supplied endpoint. No controller/scheduler invokes the CLI. Current transport-acceptance attestation remains NOT_VERIFIED: prior report refers to live acceptance, creating a first-run prerequisite ambiguity. A separately reviewed acceptance procedure must define what evidence admits the first controlled call; do not mark prior live success VERIFIED when no call occurred. No gate changed in this batch.

## 6. Synthetic boundary audit

**PASS within offline tested scope.** CLI accepts no prompt/fixture input; default accepts no arguments and does not construct a provider or resolve a key. Execute mode permits only literal `--execute-fixed-synthetic`; never invoked here. Production smoke selects `evidence-empty-v1`, structured `{evidenceIds:[]}`, no tool declarations/executors/domain clients.

Adapter grant is package-owned, request-identity/thread-bound and single-use, removed in finally. Public generate rejects arbitrary/copy/backend-shaped/user-flag requests before HTTP. Additional registry text/tool/result fixtures are compile-time offline support, not CLI-selectable. Normal orchestrator cannot obtain grant; runtime Gemini does not accept real Gateway context. Regression covers cross-thread/reentrant/copy/PII/flag denials and zero Gateway dispatch.

## 7. Token, request and exposure configuration

Current effective settings are intentionally disabled (see dry-run). Defaults have zero provider caps, invalid for live. No runtime configuration or verification value changed.

Proposed future request envelope, **not approved or installed**: exact approved model; fixed fixture; input8192/output128 tokens; run1/process1/concurrency1; exposure$0.01; conservative text rates input$1/output$3 per million. Reservation `(8192*1 + 128*3)/1,000,000 = $0.008576`, within$0.01. Public candidate rates currently fit these proposed upper rates; operator must verify again. This is a local estimate/reservation, not a promise of zero provider cost or guaranteed model completion within128 tokens.

Provider admission estimates `2*serialized UTF-8 bytes+1024` including schema/instructions, no hidden countTokens call. Existing actual SDK fixed-fixture offline test uses input8192/output128 successfully; lower input1024 is rejected before HTTP. Actual usage is mandatory and total-minus-input must fit output cap, including thinking. Unknown/over-cap usage and quota stop future local calls. Failed attempts retain reservation. Ledger is JVM-wide, not durable or shared with other processes/project clients; external controls remain mandatory.

## 8. Dry-run and offline verification

| Executed check | Result |
|---|---|
| `mvn -o -B -ntp -pl ai-agent-service package` | **PASS**, BUILD SUCCESS12:03:08+07:00;297 tests,0 failures/errors/skipped |
| Foundation/provider/transport/orchestration/READ tests |32 /75 /16 /66 /108 PASS; fake SDK or synthetic loopback only, outbound guards retained |
| `java '-Dloader.main=com.example.aiagent.model.GeminiSyntheticSmoke' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher` (no arguments) | **PASS**, exit0, preflight-only; no provider/key resolution/domain/inference exchange |
| `git diff --check` and scoped diff review | PASS; expected Git LF/CRLF notices only; original IDE changes preserved |
| Changed-file high-confidence credential pattern inspection |0 Google-key/private-key/JWT-pattern matches; no secret values printed. Scoped check, not a comprehensive scanner |
| Dependency changes/checks | No dependency changes; additional dependency analysis NOT_RUN |
| Frontend/hosted DB/real Gateway/live provider | NOT_RUN; outside authorized preflight |

Initial sandbox Maven attempt compiled but stalled at Mockito/ByteBuddy attachment: native named-pipe access denied in Surefire dumpstream. Interrupted that run (exit1), then reran the same offline command with explicitly approved execution outside sandbox. The successful run above is fresh evidence; assertions/network guards unchanged. Existing Java21 SecurityManager/dynamic-agent warnings remain.

Effective dry-run output:

```text
model=fake-v1; reviewedModel=UNSET; provider=FAKE
fixture=evidence-empty-v1; fixedRequests=1
agentEnabled=false; liveEnabled=false; syntheticOnly=false
runCap=0; processCap=0; concurrencyCap=0
inputTokenCap=0; outputTokenCap=0; localExposureUsd=0
modelVerification=NOT_VERIFIED; accountRegionFreeTier=NOT_VERIFIED
dataTerms=NOT_VERIFIED; quotaBilling=NOT_VERIFIED; transportAcceptance=NOT_VERIFIED
feature=FAIL; provider=FAIL; live=FAIL; synthetic=FAIL; reviewedModelMatch=FAIL
agentLimits=PASS; budgets=FAIL; oneRequestExposure=FAIL
keyValidity=NOT_VERIFIED; accountEvidence=NOT_VERIFIED; liveInference=NOT_RUN
verificationValues=OPERATOR_ATTESTATIONS_ONLY
```

Preflight-only exit0 is successful summary execution, not approval or live readiness. FAIL on disabled local gates is expected safe default; external checks remain NOT_VERIFIED. Proposed candidate/envelope in sections2/7 was not installed to make this dry-run appear ready. The added regression confirms locally admissible simulated budgets still report external evidence NOT_VERIFIED, rejects an unaffordable single reservation and sanitizes reviewed-model line injection.

## 9. Remaining blockers and operator checklist

1. In [AI Studio Projects](https://aistudio.google.com/projects), identify the intended project locally; provide redacted dated evidence of Free Tier and selected-model entitlement. For2.5 confirm historical access; otherwise approve the proposed3.5 model and review pinned SDK compatibility. Do not invoke inference to probe model names.
2. Check [AI Studio Billing](https://aistudio.google.com/billing) and that exact project's Cloud Billing linkage. Confirm no unintended paid billing. Do not click setup/upgrade/add credits/change billing for this task.
3. In [active rate limits](https://aistudio.google.com/rate-limit), record exact selected-model RPM/TPM/RPD, current usage and enough remaining allowance for one request; account for other clients sharing project quota. No quota upgrade requested.
4. Confirm actual account/execution region, eligibility and applicable terms; acknowledge unpaid improvement/human review and retention with synthetic-only data.
5. Supply key to the future execution process through existing local environment/secret mechanism after verifying the key belongs to the reviewed project. **Never paste a key in chat, source, command arguments or report.** No new key creation requested.
6. Review finite caps/rates and the first-call transport acceptance procedure. Keep all attestations NOT_VERIFIED until supported by appropriate evidence.
7. After blockers resolve, approve a concrete request plan separately: exactly one generateContent request, approved exact model, evidence-empty-v1, above reviewed caps, timeout<=10s/remaining deadline, no tools/real data/retry/fallback/second attempt/background run. Approval is not supplied by this task.

## 10. Future acceptance evidence and stopping point

Future explicitly approved run must record sanitized HTTP/model acceptance or safe failure category, structured schema/GroundingGate outcome, usage input/candidate/total, finish reason, latency and local reservation/request count, without raw response or secrets. Current CLI reports only generic successful validation, so complete evidence collection needs a reviewed bounded instrumentation plan before execution; no need to open chat or enable general orchestration.

Live inference **NOT_RUN**. Account/Free Tier/billing/quota **NOT_VERIFIED**. Public data terms reviewed; operator applicability/consent unresolved. **Stop at PRECHECK_REQUIRED**, no Phase8,10B.4B or production transition.
