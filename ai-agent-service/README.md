# AI Agent Service — Batch 10B.1

> Current checkpoint **10B.4A: Gemini provider integration DONE offline**, feature OFF, provider FAKE, Gemini live OFF by default. [Report](../Docs/ai-agent/BATCH10B4A_GEMINI_PROVIDER_INTEGRATION_REPORT.md) is current; descriptions below of absent adapters/fake-only startup are historical checkpoints. No live smoke/real Gateway/10B.4B/ingress/FE rollout.

## Gemini synthetic-only pilot

Java21/Boot3.3.4/LangChain4j1.17.0 retained, no dependency upgrade. `GeminiLlmProviderAdapter` uses real SDK with injectable internal HTTP SPI and explicit retries0. Apache transport has fixed Google HTTPS destination, no redirects/cookies/auth forwarding/compression, bounded body/deadline and request cancellation. Only trusted compile-time fixtures can mint a single-use identity/thread-bound grant. Normal `generate(ChatRequest, CallControl)` denies all generic user/state/backend requests, including copied fixture content or `synthetic=true`. No public endpoint and no real domain tools in the smoke harness.

Environment defaults: `AGENT_ENABLED=false`, `AGENT_PROVIDER=FAKE`, `AGENT_GEMINI_LIVE_ENABLED=false`; `GEMINI_MODEL_ID` has no Gemini default (`fake-v1` only supports FAKE). API key comes solely from environment variable named by `checkinn.agent.api-key-environment-variable` (default `GEMINI_API_KEY`). Key presence never enables live. No key values in YAML/arguments/preflight/logs.

Separate `checkinn.gemini` properties (strict binding; corresponding `AGENT_GEMINI_*` variables are listed in application.yml): live-enabled, synthetic-only; reviewed-model-id equal to exact selected model; model-verification, account-region-free-tier-verification, data-terms-verification, quota-billing-verification and transport-acceptance all VERIFIED. Defaults are false/blank/NOT_VERIFIED. These operator attestations require external review; do not set them just to make startup succeed. Selected GEMINI fails initialization when any gate/key/budget is missing; FAKE needs no key or Gemini setup.

Required provider caps default0 (invalid for live): max-input-tokens1024..16384, max-output-tokens1..1024, max-run-requests1..3, max-process-requests>=run cap and<=9, max-concurrent-requests exactly1, max-exposure-usd positive<=1. Required positive input/output-usd-per-million-upper-bound<=1000 must conservatively cover the selected model including billed thinking. No pricing values are supplied by default. These bounds are safeguards, not a provider quote or approved financial allocation. Fixed smoke run uses exactly1 request regardless of a higher allowed cap. Process requests/exposure are shared across production adapter instances and never refunded/reset within a JVM; restarts/other processes/clients still require external controls.

Input admission estimates `2 × full serialized request UTF-8 bytes + 1024`; it is not tokenizer usage. No implicit countTokens call. Output maxTokens sent to SDK; actual prompt/candidate/total usage required and checked, including total-minus-input against output cap. Unknown usage/quota/over-cap stops the process ledger. Never claim guaranteed zero cost from local counters or generic Free Tier documentation.

Gemini tool proposals and strict evidence-selection requests are separate phases: up to2 proposal calls, final available call JSON-only; no fourth call, no tool execution in final phase. Server validators/PolicyExecutor/GroundingGate remain authoritative. All10 registry specs serialize through a pinned SDK dialect bridge; tool bounds/format/pattern are enforced server-side. Grounding raw root schema retains maxItems/additionalProperties. Combined tools+JSON schema is rejected rather than guessed supported. Fake provider protocol/regressions remain available.

Build/test from backend root: `mvn -o -B -ntp -pl ai-agent-service package`. Tests use fake SPI or synthetic loopback sockets only, no API key/Internet/DB/Docker. Google TLS/account/quota verification is NOT_RUN. Default production main remains AiAgentApplication.

For a **preflight-only** summary after packaging (no provider construction/call):

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiSyntheticSmoke' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

The separate CLI accepts only the optional literal `--execute-fixed-synthetic`, which is a future manual live action and was NOT run in10B.4A. It always prints model, fixed fixture `evidence-empty-v1`, fixed1/run/process counts, token/exposure caps, flags and verification statuses before attempting execution, never the key or completion. Live execution refuses unresolved gates. Do not mark account/region/free-tier/privacy/billing/transport VERIFIED without reviewed evidence. No scheduler, arbitrary prompt/fixture CLI parameter, domain tool or retry/fallback. Generic application orchestration remains unable to authorize live requests even when the provider initializes.

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
