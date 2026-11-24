# AI Agent Service — Batch 10B.1

Current offline checkpoint: [10B.4C executable extraction and limitations](../Docs/ai-agent/BATCH10B4C_OFFLINE_REPORT.md), with the additive implementation section at the end of this README. Previous checkpoint sections below retain their historical counts.

## Batch 10B.4B-DECISIONS — current internal checkpoint

**D1/D2/D3 implementation DONE offline; trusted UI confirmation INTERNAL_ONLY.** Final package: **592 tests PASS**, including **79 approved-policy supplemental tests**. Original historical evaluation: **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**; E03 improved, no baseline PASS regressions. See [decisions report, security limits and full before/after matrix](../Docs/ai-agent/BATCH10B4B_DECISIONS_REPORT.md). No production readiness or 10B.4C rollout.

Vietnamese hotel discovery with a valid destination and no supplied dates/occupancy can automatically BROWSE after parsing/criteria validation. It explains reference/base price, unknown dated availability and no hold/booking. Partial/invalid dates, availability, selected/detail/WRITE requests and unsupported constraints never downgrade to browse. Undated inclusive base-price filters are retained. Explicit “giá tham khảo dưới X” uses an internal exclusive predicate over a freshly read bounded page, with inclusive maxPrice=X sent to A01; no rounding/subtract-one conversion, inferred stay or pagination sweep. Results describe only that page; an empty filtered page is not proof the whole catalog has no match.

`ResultSetReferences` keeps at most5 hotel identities/display ordinals from actually rendered validated A01 cards for at most5minutes in the existing session state. Criteria changes invalidate them; comparisons do not extend their lifetime. This identity lifetime does not change the30s fact freshness or prohibition on prior-turn A01/A05 inventory. Conversational comparisons resolve exact trusted pairs, refresh A01 with the same criteria and both A02 details through the existing PolicyExecutor/shared budget (3proposals/2rounds/3HTTP, no model calls), then render only fresh matched evidence. A02 detail prices never impersonate A01 offer prices. Partial outages retain verified data only; absent prices/reviews remain unknown, no final-price/hold claim. Expired/ambiguous/unissued bindings ask for a new search/selection. Room-pair/fuzzy-name/general recommendation contracts remain unsupported.

`TrustedCriteriaConfirmation` has no public constructor/factory/JSON ingress. Its package-private explicit internal action factory and `AgentOrchestrator.confirmCriteria` form a server-adapter seam, bound to an existing session snapshot and expected state/criteria revisions. Complete occupancy, allowed fields/domain bounds, explicit source/action identity, <30s action freshness, monotonic action time, bounded replay receipts (max32, fail closed), session expiry and the existing exclusive lease are enforced before one state commit. Model-proposed fields and client `trusted/confirmed` booleans cannot mint authority. No authenticated UI sender/ownership integration is implemented; local session binding is not authenticated user ownership. Material criteria changes still require fresh evidence/reselection. Chat full counts retain USER_CONFIRMED; missing/ambiguous counts never default.

Run from backend root: `mvn -o -B -ntp -pl ai-agent-service package`. Historical original fixtures/SHA/hard assertions are unchanged; the accounting test is not40/40 acceptance. SELECTED fixtures without occupancy provenance still fail; E27/E28 original old-context expectations conflict with fresh-only comparison. E30 remains NOT_RUN, with no superseding turn/delivery ordering implemented. Feature OFF /FAKE /Gemini live OFF, all budgets and retry0 remain unchanged. Gemini live attempts0; real Gateway/domain/Supabase calls0; only synthetic loopback tests, no public ingress or WRITE tools. Earlier sections below describe prior checkpoints.

## Batch 10B.4B-CONTRACT — internal READ contracts

**PARTIAL; no 10B.4C/live rollout.** See [contract reconciliation and per-case evidence](../Docs/ai-agent/BATCH10B4B_CONTRACT_REPORT.md). Earlier checkpoint totals below remain historical.

`AgentOrchestrator.selectTarget(conversationId, expectedRevision, evidenceId, type, ordinal)` is an internal trusted-application event, with the existing exclusive lease and optimistic revision. It resolves identities only from current session A02/A03/A04 projections, never model/text IDs. Selection expires with the source evidence (at most30s), is invalidated by criteria changes, and rechecks session/evidence scope. A01/A05 prior-turn inventory remains ineligible. There is no selection endpoint or implemented frontend sender, authenticated ingress, second state store or durable ownership guarantee.

Deterministic complete READ clauses route selected hotel detail, room listing/detail, availability and reviews through existing tools. Missing/expired selection asks without model/HTTP. Availability still needs explicit confirmed occupancy and valid dates. Explicit reference-price browse is supported without asserting availability; ordinary undated SEARCH still asks for dates. Confirmed-stay wording reuses only the existing criteria/provenance.

`Result.semantics()` adds internal typed ANSWER/EMPTY/CLARIFICATION/REFUSED/UNAVAILABLE/PARTIAL/POLICY_STOP metadata and bounded error enums. HTTP504 and local tool deadline differ; errors never become hotel evidence. Existing Result constructor/accessors remain available. Fresh failed reads invalidate older same-tool/argument successes; no implicit success fallback. `ReadComparison` compares exactly two offers in one current A01 record, same turn/revision/stay/quantity, retaining null ratings for zero reviews. It is a Java contract seam, not an implemented conversational multi-hotel fanout or permission to reuse prior-turn inventory. PARTIAL metadata is implemented; end-to-end partial multi-hotel comparison remains deferred.

Offline checks from backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service '-Dtest=ReadContractTest,ReadFailureContractTest,HistoricalEvaluationTest' test
mvn -o -B -ntp -pl ai-agent-service package
```

Tests use fake providers and synthetic loopback fixtures only. Original historical JSON/SHA/expectations/hard assertions are preserved; newly automated semantic checks follow original answer/forbidden-behavior rubrics. The accounting JUnit result is separate from40-case acceptance. Gzip remains prohibited and maps to invalid response before decompression; an identity response over the existing cap maps to response too large. Feature OFF /FAKE /live OFF defaults and all budgets remain unchanged.


## Batch 10B.4B-FIX — current offline checkpoint

**PARTIAL**: conversation clarification and deterministic grammar expanded; all40 historical cases accounted for with their original expectations. Full Maven regression **476 passed**, but historical evaluation **14 PASS /25 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Maven success is not40-case acceptance. See [FIX report and complete matrix](../Docs/ai-agent/BATCH10B4B_FIX_REPORT.md).

Questions now identify missing date/occupancy members and explain why the related group must be resent. Date/occupancy replacement validation, USER_PROVIDED vs USER_CONFIRMED, optimistic revision, exclusive lease and TTL are unchanged. Increment requests ask for explicit totals; mixed ambiguous input commits no criteria changes and says so. No pending store was introduced.

Grammar adds bounded Vietnamese number words, compound money (`một triệu rưỡi`, `một triệu năm trăm nghìn`), full word dates with explicit year, optional `ngày`, check-in/check-out labels and shared-year interval notation. Weekend/month ambiguity still asks. `DestinationLabels` supports an immutable trusted label snapshot (max200, no IDs); defaults retain the original8 labels. The internal orchestrator uses those defaults. An arbitrary explicit destination may be supplied as a quoted query (`ở "Cần Thơ"`), never as a fabricated catalog identity. Live catalog fetching/fuzzy matching is absent. Amenities/near-sea/sort without an exact supported evidence mapping still clarify; no guessed code or discarded constraint.

Run from backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service '-Dtest=VietnameseGrammarFixTest,VietnameseConversationFixTest' test
mvn -o -B -ntp -pl ai-agent-service '-Dtest=HistoricalEvaluationTest' test
mvn -o -B -ntp -pl ai-agent-service package
```

The historical audit checks a pinned byte-equivalent copy of the10A JSON block (line endings normalized) and writes `ai-agent-service/target/historical-evaluation-results.json`. It executes original inputs/contexts/fixtures through internal NLU, records unmet assertions as FAIL, and labels unsupported scenarios NOT_RUN. Its one JUnit accounting/safety test passing does **not** mean all historical cases passed. No original manifest expectation is changed; no typed-request shortcut substitutes for a missing NLU intent. E30's concurrent delivery event scenario remains NOT_RUN. Historical selected-target/compare/error-response gaps remain visible.

Feature OFF /providerFAKE /Gemini liveOFF defaults and all budgets are unchanged. No key, Internet, real Gateway/domain/Supabase, Config Server, smoke journal, public ingress or WRITE is used. These changes do not authorize Batch10B.4C or a live rollout. Older sections below describe their respective checkpoints.

## Batch 10B.4B internal Vietnamese NLU

`AgentOrchestrator.turnVietnamese(conversationId, expectedRevision, message, cancelled)` is a Java-only testing entry point. It runs a conservative deterministic parser inside the existing exclusive session lease and shared turn deadline, validates through `SearchCriteriaState`, then uses the unchanged PolicyExecutor/READ registry/GroundingGate path. Existing `turn(Request)` remains compatible. No separate session state, extraction model call, controller, authenticated ingress or frontend was added.

Supported grammar: accent/case/whitespace normalization; eight literal city-name queries; quoted hotel names; full civil dates `dd/MM/yyyy` or `ngày d tháng M năm yyyy`; server-clock `ngày mai`; explicit adults/children/rooms (digits and một/hai/ba); inclusive VND price ranges with explicit per-night units; stars; destination replacement and explicit location/name/price/star clears. Unsupported or ambiguous constraints ask in Vietnamese before model/tools. Amenities/near-sea/sort require catalog/semantic evidence and are not converted into invented codes. Strict “dưới” prices ask for an inclusive maximum because domain maxPrice is inclusive. Increments ask for total occupancy. Unknown words are not silently dropped. This finite grammar is not general Vietnamese accuracy evidence.

Typed proposal distinguishes absent fields from CLEAR and REPLACE, and EXPLICIT_USER from MODEL_PROPOSED. The strict offline JSON protocol is documented by `src/main/resources/nlu/extraction-proposal.schema.json`; `ExtractionProposal.parseModelJson` rejects malformed/duplicate/unknown fields, invalid values and IDs. It is a fake-protocol test seam, not an executable LLM extraction path. Only deterministic explicit output can enter the NLU execution patch. Raw NLU text is not sent to the downstream provider or persisted in history.

Feature flags remain `AGENT_ENABLED=false`, `AGENT_PROVIDER=FAKE`, `AGENT_GEMINI_LIVE_ENABLED=false`. There is no new enable flag or startup task. For internal synthetic tests only, fixtures construct enabled FAKE properties and loopback tools. Do not use the sealed Gemini debug session.

From backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service '-Dtest=VietnameseNluTest,OrchestrationTest' test
mvn -o -B -ntp -pl ai-agent-service package
```

No API key, Internet, real Gateway, Supabase, Docker or domain service is needed. [Batch report and evaluation matrix](../Docs/ai-agent/BATCH10B4B_VIETNAMESE_NLU_INTEGRATION_REPORT.md) describes exact evidence and limitations. Earlier checkpoints below remain historical.

> Current checkpoint **10B.4A: Gemini provider integration DONE offline**, feature OFF, provider FAKE, Gemini live OFF by default. [Report](../Docs/ai-agent/BATCH10B4A_GEMINI_PROVIDER_INTEGRATION_REPORT.md) is current; descriptions below of absent adapters/fake-only startup are historical checkpoints. No live smoke/real Gateway/10B.4B/ingress/FE rollout.

## Gemini synthetic-only pilot

Java21/Boot3.3.4/LangChain4j1.17.0 retained, no dependency upgrade. `GeminiLlmProviderAdapter` uses real SDK with injectable internal HTTP SPI and explicit retries0. Apache transport has fixed Google HTTPS destination, no redirects/cookies/auth forwarding/compression, bounded body/deadline and request cancellation. Only trusted compile-time fixtures can mint a single-use identity/thread-bound grant. Normal `generate(ChatRequest, CallControl)` denies all generic user/state/backend requests, including copied fixture content or `synthetic=true`. No public endpoint and no real domain tools in the smoke harness.

Environment defaults: `AGENT_ENABLED=false`, `AGENT_PROVIDER=FAKE`, `AGENT_GEMINI_LIVE_ENABLED=false`; `GEMINI_MODEL_ID` has no Gemini default (`fake-v1` only supports FAKE). API key comes solely from environment variable named by `checkinn.agent.api-key-environment-variable` (default `GEMINI_API_KEY`). Key presence never enables live. No key values in YAML/arguments/preflight/logs.

Separate `checkinn.gemini` properties (strict binding; corresponding `AGENT_GEMINI_*` variables are listed in application.yml): live-enabled, synthetic-only; reviewed-model-id equal to exact selected model. The normal provider factory still requires model/account/data/quota/transport VERIFIED and never consumes first-call permission. Defaults are false/blank/NOT_VERIFIED. Do not set VERIFIED merely to make startup succeed. The CLI-only first-call path below distinguishes operator confirmations from offline review and live observations. FAKE needs no key or Gemini setup.

Required provider caps default0 (invalid for live): max-input-tokens1024..16384, max-output-tokens1..1024, max-run-requests1..3, max-process-requests>=run cap and<=9, max-concurrent-requests exactly1, max-exposure-usd positive<=1. Required positive input/output-usd-per-million-upper-bound<=1000 must conservatively cover the selected model including billed thinking. No pricing values are supplied by default. These bounds are safeguards, not a provider quote or approved financial allocation. Fixed smoke run uses exactly1 request regardless of a higher allowed cap. Process requests/exposure are shared across production adapter instances and never refunded/reset within a JVM; restarts/other processes/clients still require external controls.

Input admission estimates `2 × full serialized request UTF-8 bytes + 1024`; it is not tokenizer usage. No implicit countTokens call. Output maxTokens sent to SDK; actual prompt/candidate/total usage required and checked, including total-minus-input against output cap. Unknown usage/quota/over-cap stops the process ledger. Never claim guaranteed zero cost from local counters or generic Free Tier documentation.

Gemini tool proposals and strict evidence-selection requests are separate phases: up to2 proposal calls, final available call JSON-only; no fourth call, no tool execution in final phase. Server validators/PolicyExecutor/GroundingGate remain authoritative. All10 registry specs serialize through a pinned SDK dialect bridge; tool bounds/format/pattern are enforced server-side. Grounding raw root schema retains maxItems/additionalProperties. Combined tools+JSON schema is rejected rather than guessed supported. Fake provider protocol/regressions remain available.

Build/test from backend root: `mvn -o -B -ntp -pl ai-agent-service package`. Tests use fake SPI or synthetic loopback sockets only, no API key/Internet/DB/Docker. Google TLS/account/quota verification is NOT_RUN. Default production main remains AiAgentApplication.

For a **preflight-only** summary after packaging (no provider construction/call):

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiSyntheticSmoke' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

The separate CLI accepts only the optional literal `--execute-fixed-synthetic`, which is a future manual live action and was NOT run in10B.4A. It always prints model, fixed fixture `evidence-empty-v1`, fixed1/run/process counts, token/exposure caps, flags and verification statuses before attempting execution, never the key or completion. Live execution refuses unresolved gates. Do not mark account/region/free-tier/privacy/billing/transport VERIFIED without reviewed evidence. No scheduler, arbitrary prompt/fixture CLI parameter, domain tool or retry/fallback. Generic application orchestration remains unable to authorize live requests even when the provider initializes.

Preflight also prints the sanitized reviewed model, concurrency cap and PASS/FAIL local gates, including whether one reservation fits the exposure cap. It now resolves the configured environment variable only to print `GEMINI_API_KEY_PRESENT=true/false`; it never imports `.env` or tests the key with Google. Exit0 in preflight-only mode means the summary ran, not live readiness. External confirmation values are attestations, not authenticated account verification. See [live fix review](../Docs/ai-agent/BATCH10B4A_LIVE_FIX_REPORT.md).

## Fixed first-call acceptance (10B.4A-LIVE-FIX)

`GeminiSyntheticSmoke` is a standalone non-web CLI, not the ordinary `AiAgentApplication` Spring Boot main. No startup runner, controller or scheduler was added. A key set in one IntelliJ Run Configuration is not evidence that another configuration/terminal/Maven JVM receives it. Private IDE settings are not inspected or edited by this workflow.

Create a separate **Application** Run Configuration locally with main `com.example.aiagent.model.GeminiSyntheticSmoke`, Java21, classpath module `ai-agent-service`, working directory the backend root. Supply the existing secret locally using the operator's environment/secret injection mechanism; configurations do not automatically share each other's environment. Do not store the key in shared project configuration, VM/program arguments, source or reports. Keep program arguments **empty** for preflight. Confirm the boolean presence in this exact JVM. Never run the normal application to trigger a smoke call.

The following nonsecret VM properties describe the reviewed envelope and evidence categories, not automatic verification. Configure only after reviewing supporting evidence; defaults remain off. No project configuration is changed to install these values:

```text
-Dcheckinn.agent.enabled=true
-Dcheckinn.agent.provider=GEMINI
-Dcheckinn.agent.model-id=gemini-3.5-flash-lite
-Dcheckinn.gemini.live-enabled=true
-Dcheckinn.gemini.synthetic-only=true
-Dcheckinn.gemini.reviewed-model-id=gemini-3.5-flash-lite
-Dcheckinn.gemini.model-verification=VERIFIED
-Dcheckinn.gemini.account-region-free-tier-verification=OPERATOR_CONFIRMED
-Dcheckinn.gemini.data-terms-verification=OPERATOR_CONFIRMED
-Dcheckinn.gemini.quota-billing-verification=OPERATOR_CONFIRMED
-Dcheckinn.gemini.transport-acceptance=NOT_VERIFIED
-Dcheckinn.gemini.max-input-tokens=8192
-Dcheckinn.gemini.max-output-tokens=128
-Dcheckinn.gemini.max-run-requests=1
-Dcheckinn.gemini.max-process-requests=1
-Dcheckinn.gemini.max-concurrent-requests=1
-Dcheckinn.gemini.max-exposure-usd=0.01
-Dcheckinn.gemini.input-usd-per-million-upper-bound=1
-Dcheckinn.gemini.output-usd-per-million-upper-bound=3
-Dcheckinn.gemini-smoke.offline-transport-safety=OFFLINE_TRANSPORT_SAFETY_VERIFIED
-Dcheckinn.gemini-smoke.first-call=FIRST_CALL_AUTHORIZED
-Dcheckinn.gemini-smoke.no-paid-billing=OPERATOR_CONFIRMED
-Dcheckinn.gemini-smoke.exclusive-quota=OPERATOR_CONFIRMED
```

Model-verification means reviewed public model/wire compatibility here, not authenticated project entitlement. Verify applicable account/execution region before configuring the combined account-region-Free-Tier confirmation. `First-call local gates=PASS` checks local nonsecret configuration only. Missing/invalid key is rejected before transport; key presence is not key validity. Provider timeout remains default10s or lower caller deadline, turn<=30s; no retry/fallback/cap increase. Reservation$0.008576 fits$0.01 at reviewed upper rates; recheck pricing before any future run. Local ledgers do not coordinate other processes or survive restart.

Only a later authorized execution uses the literal program argument `--execute-fixed-synthetic`. This fix batch runs preflight only. The harness mints a private-constructor permit bound to exact model, fixed `evidence-empty-v1` request identity and thread. It cannot select another fixture; repeated/copy/cross-thread/generic calls fail. Production CLI has an atomic one-execution latch plus shared JVM budget capped1. First-call configuration does not enable the ordinary provider factory/orchestrator. Transport remains NOT_VERIFIED before the attempt; only received HTTP status produces the diagnostic `LIVE_TRANSPORT_OBSERVED`, which is an observation, not automatically persisted verification.

Pinned SDK1.17.0 emits candidateCount1. For this exact model only, the guarded HTTP boundary accepts the exact fixed SDK request shape/content/schema/cap, removes only candidateCount and reconstructs the request with unchanged URL/method/headers. Other models pass unchanged. Added/unknown parameters, tools, altered content/schema/caps, duplicate JSON or wrong endpoint fail closed. No SDK fork/upgrade and no arbitrary JSON rewrite. Output128 may still truncate; MAX_TOKENS remains failure, never a reason to retry/increase the cap.

Execution prints bounded scalar diagnostics: exact model/fixture, transport attempt count (not proof bytes reached Google), retries0, typed category, HTTP status class when observed, HTTP acceptance (200 only, not entitlement proof), grounding PASS/FAIL/NOT_RUN, usage or null when unknown, allowlisted finish reason, monotonic run latency and retained budget reservation. No raw prompts/completions/bodies/headers/keys. Unknown usage and errors fail closed; CLI exits2 for a reported failed execution. Stop after the first outcome, including timeout/400/403/404/429/500/malformed. Never rerun automatically.

## Authorized debug session (10B.4A-LIVE-DIAG-AUTO)

The debug CLI remains separate from production. Only `--execute-debug-synthetic` uses the session journal, holding an exclusive file lock for the complete invocation. Fixed session state is `ai-agent-service/.local/gemini-diag-auto-20261123/session.journal` relative to the backend working directory; `.local` is already Git-ignored. A one-time `--init-debug-session` creates the directory/file exclusively; it never overwrites/resets existing state. Do not delete, relocate or restore older state to recover budget. No configurable session ID or automatic initialization on execution.

Each invocation still executes at most one fixed evidence-empty-v1 request through the existing first-call permit, exact8192/128 envelope and10s timeout. It appends/fsyncs a nonrefundableUSD0.008576 reservation before transport and appends/fsyncs only bounded scalar outcomes afterward. At most5 new reservations (maximumUSD0.042880, belowUSD0.06), concurrency1 across JVMs. The prior operator-reported attempt is retained separately in the header and is never an unused credit. Missing/truncated/oversized/pending/crash state or another owner fails closed. SUCCESS, quota, safety, synthetic-boundary, unknown-usage and budget stop outcomes seal further reservation. The successful session recorded in the report is closed; do not initialize another session to continue it.

Diagnostics now identify first failing pipeline stage, sanitized fixed error code, envelope root type, candidate part count/content presence and signature presence boolean; signature/text/headers are never printed or persisted. The exact reviewed model's documented thoughtSignature (bounded opaque string) and false thought metadata are handled separately from one required content field. True thought content, unknown metadata, malformed signatures and unsupported parts still fail closed. Final selection/schema/GroundingGate are unchanged. Other models retain prior raw-part validation.

This journal protects cooperative local runs and process crashes; it is not a provider billing cap or protection against an operator deliberately deleting/tampering with local state. No automatic retry/loop/model fallback/provider key probe. Review offline evidence before any later authorized attempt; stop on429. [Current debug evidence](../Docs/ai-agent/BATCH10B4A_LIVE_DIAG_AUTO_REPORT.md).

> Current checkpoint: **10B.3 internal fake orchestration implemented**. Earlier skeleton/adapter-only descriptions below are historical. See [10B.3 evidence](../Docs/ai-agent/BATCH10B3_ORCHESTRATION_POLICY_REPORT.md).

## Internal orchestration 10B.3

`AgentOrchestrator.turn(Request)` is a Java-only local-testing boundary, not a public chat API. Caller supplies explicit intent/targets and typed criteria patch with provenance; natural-language extraction and authenticated ingress are deferred. `SEARCH` clarifies missing destination/dates/confirmed occupancy. `BROWSE` admits undated catalog requests only without stay/price/occupancy criteria. Model proposals pass only through `PolicyExecutor.executeRound`; tools run sequentially, no retry/fallback/framework callbacks. Final model JSON selects existing evidence IDs only; deterministic code renders base price/availability observations/general cancellation facts, rejecting arbitrary prose and fabricated claims.

In-memory `ConversationStore` has optimistic expected revision + exclusive session leases, idle TTL30m, cap1000; stores recent8 intent/answer summaries, structured criteria and bounded24 projected evidence. More than8 user turns work; no durable full history or restart recovery. Criteria updates invalidate old-revision evidence; occupancy/date/location group replacement avoids carrying unstated old slots. Occupancy requires USER_CONFIRMED; model proposals cannot confirm it. Invalid criteria do not commit; failed/cancelled turns commit valid user updates + safe summary, discarding newly obtained evidence. No raw user prose/model responses/tool transcripts are stored in recent history; structured destination/name/filter values remain state data for local synthetic testing.

Turn hard caps:30s,3model calls,6proposals,6actual HTTP attempts including failure,2tool rounds; each tool≤10s and remaining turn, zero retries. Counts never reset within a turn. Proposal-batch rejection is all-or-nothing before HTTP. Adapter attempt ledger remains the counter source. Evidence must match revision and local30s freshness; availability/search is never reused across turns. Timeout polling cancels requests/futures; a noncooperative injected provider may finish CPU/blocking work later, bounded by two daemon workers/no queue. No live transport guarantee is implied.

Run `mvn -o -B -ntp -pl ai-agent-service package` from backend root: foundation/offline SDK + fake orchestration + loopback adapter tests, no API key/DB/stack. Metrics and payload-free spans carry only bounded categories/counts; server traceparent header is mock verified. Real Gateway trace/live LLM integration remain NOT_RUN. Feature OFF and FAKE-only configuration remain unchanged; no frontend, DB, migrations, Redis, persistence or replica support.

Java21, Spring Boot3.3.4, LangChain4j core/provider libraries1.17.0. Independent Maven module; no Spring Cloud/discovery/config client, DB, migrations, AMQP or domain RPC. Parent/root dependency management of other services is unchanged.

Run scoped checks from backend root:

```powershell
mvn -B -ntp -pl ai-agent-service test
mvn -o -B -ntp -pl ai-agent-service test
mvn -B -ntp -pl ai-agent-service dependency:tree -Dverbose
```

Offline Maven requires artifacts previously resolved. Tests install a Java21-only SecurityManager network guard using a test JVM flag; it denies outbound connections and asserts zero attempts. This is not runtime security configuration and must be replaced when upgrading beyond Java21. No stack, DB, API key or provider account is needed.

Defaults: bind 127.0.0.1:8092 (8091 is regulations-service), feature OFF, FAKE only; health/readiness remains UP without model/DB probes. Only health is exposed over Actuator. No chat controller or domain tools exist, including when enabled=true. The injected provider gate rejects calls while disabled. GEMINI/OPENAI fail startup validation in this batch; there is no live adapter bean or fallback.

Configuration prefix `checkinn.agent`: enabled=false, provider=FAKE, model-id=fake-v1, api-key-environment-variable=GEMINI_API_KEY (a name only; never resolved), model-timeout=10s, tool-timeout=10s, turn-deadline=30s, http-attempts=6, tool-proposals=6, model-requests=3, tool-rounds=2, automatic-retries=0, log-payloads=false. Durations positive, tool≤10s, turn≤30s, model/tool≤turn; count ranges1..cap, retries exactly0; payload logging rejected. Unknown Agent properties fail binding. Do not supply an API key value through these properties.

The provider contract retains LangChain4j request/response metadata without executing tools. Never log/serialize these objects as diagnostics: they contain messages/proposals and may hold raw provider metadata. CallControl is cooperative fake cancellation/deadline, not proven live transport cancellation. Model/tool/turn budgets are configuration foundations; no full turn executor is claimed.

Metrics agent.model.calls/tokens/duration/errors use bounded tags and do not contain payloads. LangChain4j logs are OFF by default. W3C tracing bridge and correlation filter serve health; outbound propagation/Gateway integration remain unverified. HTTP error/chat contract is deferred until an actual API is implemented.

[Evidence report](../Docs/ai-agent/BATCH10B1_LANGCHAIN4J_SKELETON_REPORT.md). No live integration or production readiness claim.

## Batch 10B.2 internal READ adapters

Feature remains OFF. Internal `tools.ToolRegistry` has exactly A01–A10, fixed anonymous Gateway GETs, passive LangChain4j declarations, strict input validation and whitelist projection. No chat endpoint, automatic model tools, PolicyExecutor or AgentOrchestrator. Trusted `AGENT_GATEWAY_ORIGIN` defaults to `http://127.0.0.1:8080`, `AGENT_GATEWAY_CONNECT_TIMEOUT` to `PT3S` (positive≤3s); Agent tool timeout remains≤10s. No credential forwarding, redirect, compression or retry. Never point tests at hosted data.

`ToolExecutionContext` is server-owned: populate catalog sets and public room/hotel relationships from validated same-turn observations and confirmed rooms/adults/children, never from model-supplied context. Missing evidence fails before HTTP; registry does not fetch catalogs implicitly. Future PolicyExecutor owns turn budget/dedup/revision admission. `attemptLedger()` counts every admitted execute attempt, including failures, with generated request ID.

Run `mvn -o -B -ntp -pl ai-agent-service test` from backend root. ReadToolsTest uses only a synthetic loopback HTTP server; AgentFoundationTest retains deny-all network guard. These tests do not prove real Gateway/provider integration. See [implementation report](../Docs/ai-agent/BATCH10B2_READ_TOOLS_IMPLEMENTATION_REPORT.md).
# Batch 10B.4C-OFFLINE: Vietnamese extraction

Internal `AgentOrchestrator.turnVietnameseWithLlm(conversationId, expectedRevision, message, cancelled)` executes a bounded extraction request through the existing `LlmProviderAdapter`, then strict JSON/provenance corroboration, existing criteria/state routing, PolicyExecutor and GroundingGate. It uses one session lease and one TurnBudget for extraction and READ orchestration. `turnVietnamese(...)` remains the deterministic compatibility path.

This entry point is **FAKE-only** and returns DISABLED without a provider call when the feature is OFF or the configured provider is not FAKE. It does not grant generic requests access to Gemini's synthetic-only transport. No public chat endpoint or new tool exists. Repository feature/live flags remain OFF.

The closed protocol is [`vietnamese-extraction.schema.json`](src/main/resources/nlu/vietnamese-extraction.schema.json): intent/criteria, bounded literal target/comparison expression, missing slots and inferred-field hints. Model fields retain MODEL_PROPOSED provenance. Only an independent literal parser can establish explicit chat values; model labels cannot confirm occupancy. Unsupported or unconsumed constraints clarify atomically; invalid/forged proposals fail without domain HTTP. Finite equivalences cover chỗ nghỉ, explicit shared-year intervals, selected room questions and price/review comparison wording. This is conservative proposal corroboration, not unrestricted Vietnamese semantic understanding.

Offline tests: `mvn -o -B -ntp -pl ai-agent-service package`; supplemental suite: `-Dtest=LlmVietnameseOfflineTest test`. Synthetic fake proposals and loopback backend-shaped fixtures only; historical E01–E40 remains a separate pinned evaluation. See [offline implementation evidence and live blockers](../Docs/ai-agent/BATCH10B4C_OFFLINE_REPORT.md). No real-model accuracy, D07/D08 acceptance or production readiness is claimed. Earlier sections describe previous checkpoints.


## Batch 10B.4C-LIVE-UNBLOCK-OFFLINE

Dedicated package-private Gemini extraction protocol/session/journal/smoke components now support an entirely mocked synthetic extraction pipeline. See [implementation, measurements and remaining live gates](../Docs/ai-agent/BATCH10B4C_LIVE_UNBLOCK_OFFLINE_REPORT.md). Canonical compact JSON keeps the original instruction and every backend schema/validation/provenance constraint: all20 pinned candidates fit the existing16384 input estimate cap and1024 output ceiling. Proposed initial subset is VEX1-01/02/03, at most3 requests; no operator approval implied.

Status is **OFFLINE_UNBLOCK_DONE / INTERNAL_TEST_ONLY; TRUSTED_ISSUER_NOT_INTEGRATED**. Default verifier denies; no bean, public endpoint, CLI, environment key lookup, default external transport or live activation is added. Capabilities are fixture/request/schema/model/session/thread bound and one-shot. Separate review-bound CREATE_NEW journals reserve before dispatch and never resume any existing/uncertain session. Journal files require a trusted persistent operator directory; local session tombstones do not establish account-wide quota controls. Existing Agent entry remains FAKE-only, old smoke grants/journals remain separate, defaults OFF/FAKE/liveOFF. BILLING_QUOTA_UNVERIFIED and REAL_MODEL_SCHEMA_ACCEPTANCE_NOT_RUN remain live prerequisites. Do not wire a real key/transport or execute a live batch without separate reviewed issuer integration and explicit operator authorization.

Offline regression: `mvn -o -B -ntp -pl ai-agent-service package`. New `GeminiExtractionUnblockTest` uses fake verifier/HTTP, a literal noncredential sentinel and temporary journals under socket/key-lookup guards. Generated `target/extraction-request-measurements.json` contains exact per-fixture sizes and hashes; no real payload or provider response journal. Historical E01-E40 remains a separate unchanged evaluation.
