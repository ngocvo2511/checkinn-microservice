# Batch 10B.4A — Gemini provider integration

23/11/2026, Asia/Ho_Chi_Minh. **DONE for the authorized offline, synthetic-only provider integration scope.** Live Gemini **NOT_RUN**, manual live smoke **NOT_READY**, real Gateway **NOT_RUN**, Batch10B.4B **NOT_STARTED**. Completion does not authorize live activation or production. No customer data or domain WRITE.

## 1. Git baseline

Recorded before implementation:

| Repository | Branch | HEAD | Initial status |
|---|---|---|---|
| Backend | `ci/github-actions` | `5f7d22b16f8a7ff4e3d8d68e45f84dd5b64851cd` | Modified `.idea/compiler.xml`, `.idea/encodings.xml`, root `pom.xml`; untracked `ai-agent-service/` and eight AI documents: architecture, tool design, evaluation,10A review,10B plan,10B1/2/3 reports |
| Frontend | `fix/frontend-ci-remediation` | `1c972c009a1439c85b7673895d31f932ec833b06` | Clean |

Root workspace is not a Git repository. Root AGENTS applies; recursive discovery found no more specific AGENTS. Read the nine requested AI documents and current provider/config/orchestrator/policy/budget/state/criteria/evidence/grounding/registry/tests. Older descriptions of fake-only/no orchestration and READ contract retry1 do not override current source or this task's retry0/synthetic-only scope.

Preserved pre-existing work. No stage, commit, push, stash, reset, clean or checkout. Root POM and IDE files were not edited. All changes in this batch are under Agent and Docs/ai-agent. Untracked work requires direct file inspection in addition to Git tracked diffs. Branches/HEAD remain unchanged; frontend stays clean.

## 2. Scope and files

New model files: `GeminiLlmProviderAdapter`, `GeminiHttpTransport`, `GeminiRequestProtocol`, `GeminiProperties`, `GeminiBudget`, `SyntheticGeminiFixtures`, `GeminiSyntheticSmoke`. Updated `LlmProviderAdapter.CallControl`, `ProviderException`, `ProviderConfiguration`, `AgentProperties`, `AgentOrchestrator`, application YAML and Agent README. Agent POM sets explicit existing application main class so adding the separate CLI does not make Boot packaging ambiguous; no dependency/version change.

New `GeminiProviderTest` and `GeminiTransportTest`; four additive `OrchestrationTest` cases. Existing202 assertions/cases retained. Updated architecture/evaluation/implementation plan/10A decision register, added this report. No Hotel/Booking/Gateway business source, Place, frontend, schema/migration/proto/event, hosted Supabase, Config Server or Docker changes. Maven-generated ignored build output was generated only through normal lifecycle.

## 3. SDK compatibility

Java21.0.3, Maven3.9.9, Boot3.3.4, LangChain4j core/google-ai-gemini/open-ai **1.17.0 unchanged**. Apache client5.3.1 and Boot-managed Jackson2.17.2 retained. No starter/AI Services/new SDK/stack upgrade.

Inspected actual pinned sources from [Gemini1.17.0 source artifact](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-google-ai-gemini/1.17.0/langchain4j-google-ai-gemini-1.17.0-sources.jar), including BaseGeminiChatModel, GeminiService, SchemaMapper and FinishReasonMapper; [official integration reference](https://docs.langchain4j.dev/integrations/language-models/google-ai-gemini/) is rolling documentation, not the version authority. SDK supports injected HTTP SPI, passive function declarations, native messages/results, raw-root responseJsonSchema and usage metadata. Default retries2 are explicitly overridden to0. SDK uses x-goog-api-key header, not a key query parameter.

Pinned parser can dereference missing usageMetadata/candidates; raw boundary checks occur before it. Nested JsonRawSchema children are unsupported by its Gemini SchemaMapper. A local dialect bridge converts all10 registry declarations to typed schema hints; authoritative server validation remains untouched. Grounding schema uses raw root JSON, retaining maxItems/additionalProperties instead of silently losing them. Real SDK serialization/parsing, including Boot's managed Jackson, is tested offline.

## 4. Model compatibility and official research

No operational model selected or automatically defaulted. `GEMINI_MODEL_ID` must match trusted reviewed-model-id; missing/unreviewed IDs fail selected GEMINI initialization. Offline name `gemini-offline-fixture` is deliberately synthetic and is NOT a deployable model recommendation.

Official sources checked23/11/2026 (pages may redirect to Google's AI Studio documentation):

| Source | Finding and limit |
|---|---|
| [Gemini2.5 Flash-Lite model](https://ai.google.dev/gemini-api/docs/models/gemini-2.5-flash-lite) | Documented model ID `gemini-2.5-flash-lite`, input1,048,576/output65,536, function calling/structured output. Current page restricts2.5 access to prior active users; new-account access is not established. Documentation verification only, not account availability or live SDK acceptance |
| [Pricing](https://ai.google.dev/gemini-api/docs/pricing#gemini-2.5-flash-lite) | Standard2.5 Flash-Lite table lists free input/output quota. This does not prove this project's/account's entitlement. No price values/model ID installed into live configuration |
| [Regions](https://ai.google.dev/gemini-api/docs/available-regions), [terms](https://ai.google.dev/gemini-api/terms) | Account/region restrictions require operator review. Unpaid inputs/outputs can be used for improvement and human review; do not submit sensitive/personal/confidential data. No zero-retention promise inferred. Project/account-specific terms/retention NOT_VERIFIED |
| [Rate limits](https://ai.google.dev/gemini-api/docs/rate-limits) | RPM/TPM/RPD constraints are project-scoped and model/tier dependent; actual capacity not guaranteed. Operator must verify actual project quotas/billing, not assume key-local counters or generic advertised limits suffice |
| [Structured output](https://ai.google.dev/gemini-api/docs/structured-output), [generateContent](https://ai.google.dev/api/generate-content) | Tool/schema combinations and dialect support vary by model/API. No assumption that2.5 accepts simultaneous tools+JSON schema; application rejects that combination and uses explicit phases |
| [Token counting](https://ai.google.dev/gemini-api/docs/tokens) | countTokens is a separate provider exchange; usage metadata is actual usage when supplied. No hidden countTokens request is made in this one-request smoke protocol |

**Exact deployed model/account/region Free Tier: NOT_VERIFIED.** Documentation lookup is not a Gemini inference call. No account login, key inspection, model list request, billing activation or quota change performed. Future models require their own exact API/lifecycle/schema/context/cost review; no automatic replacement or provider fallback.

## 5. Provider configuration

Default YAML: AGENT_ENABLED=false, AGENT_PROVIDER=FAKE, AGENT_GEMINI_LIVE_ENABLED=false. Missing GEMINI_MODEL_ID uses fake-v1 for FAKE only; GEMINI with that value fails validation. OPENAI/unknown selection fails closed; no fallback.

`checkinn.agent` retains existing constructors/hard-turn limits and strict binding. Separate strictly bound `checkinn.gemini` holds activation, synthetic mode, reviewed model, verification statuses and finite caps. All Gemini verification statuses default NOT_VERIFIED; budget fields default0, intentionally invalid for live. YAML/environment only, no Config Server. See [Agent README](../../ai-agent-service/README.md) and application.yml for exact property/environment names.

Key source is only System.getenv of the trusted configured environment-variable name, resolved after all nonsecret gates. Missing/blank/control/whitespace/oversized values rejected. No key value is bound into properties, preflight, exceptions, metrics, prompts, tool arguments, source, docs or logs. Tests use conspicuous synthetic noncredentials. Inbound JWT/cookies/user credentials are never accepted or forwarded.

## 6. Adapter implementation

Implements existing `LlmProviderAdapter.generate(ChatRequest, CallControl)` and returns native ChatResponse text/tool proposals/usage/finish metadata. Real SDK handles serialization/parsing. No executable tools/framework callbacks, streaming, built-in Google Search/Maps/URL context/code, domain dependencies or model-chosen HTTP destinations.

Guarded SPI requires exact configured Google HTTPS generateContent URL and POST and at most one transport execute. Apache client disables retries/redirects/cookies/auth negotiation/compression and rebuilds allowed headers. TLS validation unchanged; no system proxy/credential inheritance. Successful response capped64KiB, application/json and identity encoding required; byte count enforced before parse, including streamed bodies. Strict duplicate/trailing/depth checks reject malformed JSON. Raw parts/finish/usage checked before SDK conversion.

Empty/malformed/unsupported finish/MAX_TOKENS responses are errors, never successful empty answers. Safety/prompt blocking is SAFETY_REFUSAL;429 and bounded RESOURCE_EXHAUSTED/QUOTA_EXCEEDED error bodies become QUOTA_EXCEEDED. Other HTTP errors are safe UNAVAILABLE. Missing/invalid usage becomes UNKNOWN_USAGE; no fabricated zeros. Exceptions retain fixed categories/messages, no raw SDK causes/bodies. Fake scripted/default adapters remain functional.

## 7. Activation gates

GEMINI selection requires feature enabled, valid reviewed exact model, explicit live opt-in, key available, synthetic-only enabled, valid finite caps and VERIFIED model/account-region-Free-Tier/data-terms/quota-billing/transport statuses. Any missing condition fails initialization. Runtime calls cannot override those immutable trusted settings. Key presence alone cannot activate Gemini.

Verification fields are **operator attestations of external evidence**, not an automated account/quota verifier. This implementation created no readiness/approval record and set none of the live gates VERIFIED. Offline test-owned attestations exercise guards without representing reviewed business/provider facts.

## 8. Synthetic-only boundary

Only package-internal compile-time fixture registry/harness can create an exact request-identity/thread-bound single-use grant, removed in finally. CLI accepts no prompt, state, backend result, arbitrary fixture ID or synthetic boolean. The public adapter method denies generic requests, copied fixtures, arbitrary user prompts, real-backend-shaped records and cross-thread/session inputs before HTTP.

Fixed production smoke fixture `evidence-empty-v1` contains synthetic instructions with no hotels/customers/evidence and asks for empty evidence selection. Additional text/tool/tool-result fixtures support offline SDK testing. No domain tool execution from harness. No anonymization/PII-regex trust shortcut. Normal AgentOrchestrator requests, including real Gateway projections, cannot obtain a grant and fail SYNTHETIC_BOUNDARY even if GEMINI initialization succeeds. This is intentional authorization separation, not a generic live chat facility.

## 9. Token/request/spend budgets

| Layer | Enforcement |
|---|---|
| Existing turn |30s,3 model requests,6 proposals,6 tool HTTP attempts,2 tool rounds,10s/tool, retry0 unchanged; lower caps supported |
| Provider request | Required input cap1024..16384; output cap1..1024 passed to SDK. Input admission conservative estimate2×serialized UTF-8 bytes+1024; actual usage required afterward |
| Smoke run | Exactly1 request by construction; configured max-run-requests1..3 must admit it. No internal loop/retry, arbitrary run count or scheduling |
| Process/session | JVM-wide production ledger; max-process-requests>=run cap and<=9; max-concurrent-requests exactly1; failed attempts/reservations never refunded |
| Exposure | Required positive max-exposure-usd<=1 and positive bounded input/output USD-per-million upper rates. Reserve `(inputCap×inputRate + outputCap×outputRate)/1,000,000` before SDK execution. No free-price assumption or default pricing |
| Provider account | NOT_VERIFIED; external project quotas/billing controls mandatory. No account upgrade, billing enablement or paid tier configuration |

Input estimate includes full serialized schemas/system/messages/tool contents and generous framing allowance; it is **estimated**, not exact Gemini tokenizer usage or a formal bound for arbitrary multimodal inputs (which cannot enter the fixture registry). Extra countTokens RPC would violate fixed one-request protocol, so none is hidden. Reported input/candidate/total counts must be nonnegative integers; total-input includes thinking/other output and must fit output cap. Missing/over-limit usage or quota stops future process calls; reservation remains charged locally even on timeout/error.

Counters do not survive JVM restart or coordinate other processes/clients sharing a project/key. Configured rate upper bounds need independent verification. **Only local exposure is bounded; absolute cost/zero cost is not guaranteed.** Missing financial verification keeps live disabled. No invented usage is used for budget accounting.

## 10. Retry, timeout and cancellation

SDK maxRetries(0), Apache disableAutomaticRetries and disableRedirectHandling; one actual HTTP attempt per admitted request.429/500/503 no retry/sleep/fallback/hidden second call. Quota exhausts the process ledger immediately.

CallControl exposes remaining monotonic deadline; provider timeout<=min(configured model timeout, remaining caller deadline). Cancellation registration calls HttpPost.cancel; a daemon watchdog cancels at provider deadline including headers/body. No extra provider worker/unbounded queue; process concurrency1. Caller deadline TIMEOUT, transport PROVIDER_TIMEOUT, CANCELLED, SAFETY_REFUSAL, quota and malformed responses are distinct typed outcomes.

Tests observe actual loopback socket EOF/reset after cancellation/timeout during stalled headers and body. Actual SDK→guarded SPI→Apache→synthetic loopback integration also passes. This proves local transport abort, **not live Google TLS cancellation or cancellation of work already accepted server-side**. Bounded JSON CPU parsing may complete after cancellation; control checks and orchestration deadline prevent late success/state evidence/tool dispatch. No hard realtime OS scheduling guarantee. Live transport acceptance remains NOT_VERIFIED and blocks manual execution.

## 11. Structured responses and tool compatibility

Gemini-specific protocol: first up to2 requests may propose tools; the final available request is JSON-only evidence selection with no declarations. Proposal prose triggers explicit selection phase and is never rendered. Shorter budgets reserve the final request for selection; no fourth request, no final-phase tool execution. PolicyExecutor admission/registry validation, revision/freshness/evidence IDs and GroundingGate remain mandatory. Fake provider's prior combined request contract remains intact for existing regressions.

All10 existing registry declarations, native tool call arguments and tool-result messages tested through actual SDK fake transport. Grounding schema is converted to raw-root responseJsonSchema retaining array/maxItems/required/additionalProperties. Gemini tool schema hints use supported types/enum/required; SDK cannot carry every original bound/format/pattern/root predicate. Full authoritative validator still enforces them before HTTP. Unknown unsupported schema types fail UNSUPPORTED_PROTOCOL, not relaxed server validation. Combined tools+JSON-schema requests explicitly rejected.

Phased orchestration tests use scripted fake provider + synthetic mock Gateway; they do not authorize sending real backend data to Gemini. Live schema adherence/multi-turn model behavior is NOT_RUN. Server-side grounding continues to decide correctness regardless of provider claims.

## 12. Test evidence and commands

All Maven checks offline (`-o`), no live key/Internet/database/Docker prerequisites. Surefire reports under Agent target are generated evidence, not manually edited.

| Command/check | Result |
|---|---|
| Agent unit/provider/SDK/synthetic/orchestration `mvn -o -B -ntp -pl ai-agent-service test` | Intermediate293 tests PASS after schema/phasing additions |
| `mvn -o -B -ntp -pl ai-agent-service package` | **296 tests,0 failures,0 errors,0 skipped; BUILD SUCCESS** at00:53:18 local |
| Foundation / provider SDK / transport / orchestration / READ adapters |32 /74 /16 /66 /108 tests respectively; existing202 retained plus94 new cases |
| Dependency tree filtered to LangChain/Jackson/Apache | BUILD SUCCESS; versions unchanged1.17.0/2.17.2/5.3.1; no dependency addition |
| Selected reactor compile: Agent, Gateway, Hotel, Booking, `-DskipTests` | **BUILD SUCCESS4/4**,00:55:54 local; Place excluded; compile is not sibling behavioral-test evidence |
| Standalone GeminiSyntheticSmoke with **no arguments** via Boot PropertiesLauncher | PASS **preflight-only**: FAKE, disabled, caps0, all statuses NOT_VERIFIED; exited0; no provider constructed/call/key lookup |
| Git diff/check, direct untracked whitespace/link/secret/outbound inspection | PASS;51 text files/121 decoded relative links; details below |

Coverage includes defaults/unknown provider and invalid config; key alone/no model/no key/no budgets; typed faults; every error single attempt; no redirect/credential forwarding; malformed/empty/unknown finish/safety/usage; reservation/input/output/process/concurrency denial; approved fixture versus arbitrary/flag/backend/PII/copied/cross-thread input; no Gateway under real adapter; phased hard budgets/policy/grounding; late response rejection; actual stalled-socket abort and SDK→loopback integration.

SDK tests install deny-all outbound network guard and assert0 attempts; transport tests permit127.0.0.1 only and assert0 external attempts. Existing loopback domain tests remain synthetic. Java21 SecurityManager/ByteBuddy warnings and existing Hotel Lombok/MapStruct warnings are not new test failures or a reason to change sibling source.

Intermediate failures were fixed, not hidden: MeterRegistry is not AutoCloseable (explicit close); checked schema conversion inside stream lambda replaced with checked loop; invalid fake SuccessfulHttpResponse status fixtures corrected to the actual HTTP SPI HttpException contract and adapter maps sanitized status. Initial73-case/86-case runs had7 failures for that fixture/status boundary; final assertions remain and pass. No assertions weakened/deleted/skipped.

**NOT_RUN:** live Gemini smoke/inference, model account/region/quota/billing inspection, Google TLS acceptance, real Gateway/domain integration, hosted Supabase, payment/refund providers, Docker/services startup, frontend tests, full40-case behavioral evaluation/manual acceptance. D07/D08 not approved. No unrun work labelled PASS.

## 13. Security findings and observability

No raw prompt/completion/tool-output/provider-payload logger or trace attribute added. SDK logs explicitly false and package/Apache logging OFF in YAML. No inbound HTTP credential object; transport reconstructs Content-Type/Accept/Accept-Encoding/key header only. Config exceptions retain no raw cause/key/body. Synthetic capture tests confirm no secret/payload sentinels in logs and no credentials in model body; source scan checks high-confidence credential patterns without exposing private environment data.

Metrics `agent.gemini.requests`, `duration`, `errors{category}`, `tokens{kind}`, `usage.unknown`, `budget.denials`, `synthetic.denials` are payload-free/low-cardinality. TIMEOUT/PROVIDER_TIMEOUT/CANCELLED/QUOTA_EXCEEDED/SAFETY_REFUSAL counts are distinguished by bounded category. No model ID/session/user/prompt as metric tag. No hidden SDK listeners or payload-bearing tracing callbacks.

No out-of-scope security workaround, TLS bypass, live data processing, privileged API, WRITE tool or production claim. Operator attestation mistakes, model/API evolution, external quota/account billing and live TLS remain real limitations; they are guarded by default NOT_VERIFIED gates, not declared solved by mocks.

## 14. Live smoke readiness

Harness **implemented**; preflight-only invocation verified. CLI accepts only optional literal `--execute-fixed-synthetic`; no arbitrary text/fixture/HTTP ingress. Execution path is fixed1 request, fixed synthetic empty-evidence fixture, strict token/deadline/exposure/config guards, no domain tools/retry/fallback/scheduler. Preflight always prints selected safe model ID, fixture, fixed/run/process caps, token/exposure caps, feature/provider/live/synthetic flags and verification statuses before execution; never key/completion. Strict GroundingGate validates response.

**Manual live execution is NOT_READY and was NOT_RUN.** Operator must obtain reviewed evidence for exact model availability/lifecycle/API/schema/token limits, specific account/region free entitlement and applicable data/retention terms, project quota/billing financial controls and upper price rates, plus live transport acceptance procedure. Do not set VERIFIED merely to bypass a gate. No generated approval/provenance file, real key lookup or unapproved network validation performed. A future controlled live acceptance procedure needs explicit opt-in/authorization; this batch stops offline.

## 15. Remaining approvals

D02 APPROVED only Gemini Free Tier+synthetic-only local pilot. Exact model/account/region/terms and external financial controls unresolved. D03 approved hard limits unchanged; local provider caps are implementation safeguards, not approval for unlimited/paid usage. D04 remains10 internal READ tools/minimal projection. D05 permits only trusted synthetic outbound fixtures plus prior ephemeral state scope; customer data/real backend payloads/public ingress/ownership/retention/durable restoration remain unapproved. D06 local pilot/OFF only. **D07/D08 NOT_APPROVED.** No approvals fabricated or inferred from passing tests.

## 16. Readiness for Batch10B.4B

Offline technical provider foundation is available for review: real SDK adapter, fake preserved, default OFF, strict synthetic boundary/key/guards, retry0/no fallback, local transport cancellation, typed errors, explicit schema/tool phases, all296 tests/package PASS. **Not ready/authorized for automatic10B.4B transition, live dialogue, customer data, Gateway/frontend ingress or production rollout.** Future scope needs separate instruction and unresolved model/financial/privacy/live-acceptance gates. Stop after10B.4A.

## Final validation addendum

Selected4-module compile SUCCESS (Hotel/Booking/Gateway/Agent); Place excluded; sibling tests NOT_RUN. Final direct UTF-8/trailing-whitespace/high-confidence credential-pattern scan:51 Agent/design/report text files,0 issues;121 decoded relative link targets resolve. Initial link checker flagged two existing percent-encoded frontend links; decoding them resolved both, no source/doc link edit needed. Both repositories' `git diff --check` PASS. Static model-source inspection confirms one environment key lookup after gates, one fixed production Google HTTPS endpoint, SDK retry0/transport retry+redirect disabled, no model controller/scheduler/framework executor/domain client. No private env/secrets inspected. These are scoped static checks, not a comprehensive security audit.

Final frontend remains clean; backend branch/HEAD and initial tracked IDE/root-POM changes remain unchanged. New report is untracked along with pre-existing Agent/docs; nothing staged or committed. Stop after10B.4A; shutdown requested by user is performed only after all work/report checks finish.
