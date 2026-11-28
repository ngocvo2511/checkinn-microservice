## Current: Batch 10B.4D-EXTRACTION (24/11/2026)

**EVIDENCE_FIRST_EVALUATION_PARTIAL**. Local evidence-first Contract C and strict membership/type/completeness checks are implemented; ordinary runtime remains OFF/FAKE/liveOFF. Baseline1134/final1235 offline tests pass. New synthetic Gemini attempts21/120:17 HTTP200,1 High Demand503,3 locally terminated timeouts. The fixed-model comparison sealed at two consecutive timeouts; no further dispatch or credential lookup followed. Frozen40/fresh20 remain NOT_RUN; no model winner or production acceptance.

[Main extraction report](../Docs/ai-agent/BATCH10B4D_EXTRACTION_EVIDENCE_FIRST_AND_MODEL_QUALITY_REPORT.md), [Contract C](../Docs/ai-agent/VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md), [model comparison](../Docs/ai-agent/GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md), [local timeouts](../Docs/ai-agent/GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md). The local CLI `GeminiEvidenceEvaluation` is separate from ordinary/signed paths; its fixed campaign directory is sealed and must not be resumed or relocated to evade the stop. Offline plans/comparator are exported with `--offline-export`; that mode reads no key or provider. Do not repeat live commands against this completed attempt allowance.

The sections below preserve earlier checkpoints verbatim; their older “current” labels are historical.

## Current: Batch 10B.4D-FIX (24/11/2026)

**SEMANTIC_EXTRACTION_IMPLEMENTED_LIVE_PARTIAL**. Technical smoke3/3 HTTP200; existing v3 baseline25/40 attempted,15 NOT_RUN. Two consecutive HTTP503 responses sealed the campaign after28 total attempts. Simplified diagnostics/evaluation/generalization have zero live attempts; no A/B improvement is claimed. [Main report](../Docs/ai-agent/BATCH10B4D_FIX_SEMANTIC_EXTRACTION_SIMPLIFICATION_REPORT.md), [semantic contract](../Docs/ai-agent/VIETNAMESE_SEMANTIC_EXTRACTION_CONTRACT_V1.md), [A/B partial results](../Docs/ai-agent/VIETNAMESE_NLU_CONTRACT_AB_COMPARISON_V1.md).

From the backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
$evalJar = '.\ai-agent-service\target\ai-agent-service-0.0.1-SNAPSHOT.jar'
$localMain = '-Dloader.main=com.example.aiagent.model.GeminiLocalEvaluation'
# Offline only; no key lookup or HTTP. Exact same reviewed40 for both contracts:
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode baseline40 --max-requests 40 --protocol vietnamese-extraction-compact-v3
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode baseline40 --max-requests 40 --protocol vietnamese-semantic-v1
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode semantic-diagnostic --max-requests 8 --protocol vietnamese-semantic-v1
# Commands for a separately authorized future campaign, NOT a restart of this sealed batch:
# Keep EVERY phase output in the SAME campaign directory; create its parent first.
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode baseline40 --max-requests 40 --protocol vietnamese-extraction-compact-v3 --debug-response --output ai-agent-service/.local/AUTHORIZED-FUTURE/baseline-A.json
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode baseline40 --max-requests 40 --protocol vietnamese-semantic-v1 --debug-response --output ai-agent-service/.local/AUTHORIZED-FUTURE/baseline-B.json
# Recompute this batch's descriptive evidence from retained synthetic observations; no HTTP:
python Docs/ai-agent/semantic-extraction-fix-v1/analyze.py
```

Local-only failure policy:400/401/403/429 stop; isolated500/502/503/504 or terminated timeout/connection failures may advance to the next distinct sample after≥15s (longer valid Retry-After respected within deadline). Two consecutive or five cumulative transient failures stop. Unknown in-flight state, successful response with untrustworthy usage, envelope/SDK failures and critical safety violations stop. Ordinary semantic and safely isolated extraction-schema errors continue. No sample retries; no key/model/account fallback. Signed smoke/ordinary runtime retain their own gates.

Local error evidence records exact HTTP status when available, bounded sanitized provider error body, exception class, termination, usage/reservations and continuation reason. Inspect `observations[].providerErrorBody`, `httpStatus`, `pipeline`, `continuationReason`, and `actualUsageStatus` in result JSON; request headers are never persisted. `--debug-response` additionally captures sanitized successful synthetic envelopes in `<output>.responses/`. Non-200/timeout actual usage stays UNKNOWN; each dispatch retains16384+1024 reservation, never refunded or double-counted as actual usage.

An exclusive sibling `local-campaign.lock` and prior immutable sibling result JSON preserve the aggregate120 ceiling, phase ceilings5/40/15/40/20, rolling rate/token reservations and original60min deadline across local processes. A sealed/uncertain prior run prevents new dispatch. This is cooperative local accounting; do not move/remove reports or change directories to evade it. Independent account consumers remain outside its scope. Effective rolling maximum is11 calls with17408 reserved tokens each, under200000; minimum dispatch spacing5s; call timeout≤10s, concurrency1, retries0.

`vietnamese-semantic-v1` is local experimental only. Raw semantic schema, compiler translation and existing independent corroboration are separate recorded layers. No default Agent protocol, feature flag, tool authorization or trusted provenance changes. Raw model missing-slot accuracy is NOT_APPLICABLE for B; compiled missing slots and model semantic issues have distinct responsibilities. Offline compiler equivalence is not live model accuracy. Existing observed semantic false acceptance/rejection limitations are documented in the reports.

The sections below retain previous checkpoint commands and results; the current local policy above supersedes their stop-on-any-provider-failure and in-process-only descriptions. Signed tooling remains optional advanced governance for local synthetic evaluation.

> Previous local workflow (24/11/2026): signed validators and ordinary Agent security remain unchanged.

## Local synthetic Gemini evaluation

Build from the backend root with `mvn -o -B -ntp -pl ai-agent-service package`. All Maven tests are offline. Java 21, Boot 3.3.4 and LangChain4j 1.17.0 are unchanged.

```powershell
$evalJar = '.\ai-agent-service\target\ai-agent-service-0.0.1-SNAPSHOT.jar'
$localMain = '-Dloader.main=com.example.aiagent.model.GeminiLocalEvaluation'
# No flags and explicit offline preflight both perform zero credential reads/network calls.
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode vietnamese-nlu --max-requests 43
# Initial smoke only, with a NEW result filename:
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode initial-smoke --max-requests 3 --output Docs/ai-agent/local-smoke-NEXT.json
# Combined flow: 3 smoke + 40 main samples, ONE shared bounded run:
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode vietnamese-nlu --max-requests 43 --output Docs/ai-agent/local-nlu-NEXT.json
```

The previous immutable V1 run stopped after one HTTP200 response. The compatibility follow-up reproduced a rejection of documented `usageMetadata.promptTokensDetails` and `serviceTier`, then fixed only provider metadata handling. Prompt/schema/request digests remain unchanged. See the [compatibility and evaluation report](../Docs/ai-agent/BATCH10B4C_LIVE_COMPATIBILITY_FIX_AND_EVALUATION_REPORT.md). Existing result files are never resumed or overwritten. Keep diagnostic and final smoke observations distinct; a failed final smoke is not replaced by a diagnostic success.

The only credential lookup is `System.getenv("GEMINI_API_KEY")`, after CLI/synthetic plan/count/model/body checks, a durable local reservation and rate admission. The developer's existing Windows User variable worked in this batch. Never paste it into chat or pass it in Java arguments. If a future terminal/IDE cannot see a newly configured User variable, restart that parent application so its child Java process inherits it. Do not dump environment variables or open private dotenv files.

No flags means OFFLINE_PREFLIGHT. Live requires explicit `--live` and `--output`; `--offline` and `--live` conflict. Default smoke requires exactly3; combined count3–43 includes smoke. Local development continues after ordinary semantic failures and isolated extraction-schema failures with trustworthy usage and no critical safety flags. Semantic smoke still requires3/3 exact outcomes and expected admission; technical readiness is reported separately. Signed initial smoke and ordinary Agent gates are unchanged. `--protocol` selects an explicit frozen local contract (`vietnamese-extraction-compact-v1`, `-v2`, or `-v3`); default/pinned signed v1 is preserved. No arbitrary prompt/model/endpoint flags. The pinned [local oracle](src/main/resources/gemini/local-development-oracle-v1.json) has40 technically reviewed VNE1 samples; [selection/review](../Docs/ai-agent/LOCAL_DEVELOPMENT_SEMANTIC_REVIEW_V1.md) distinguishes Codex review from human approval. No full history/context, real records, tools or domain clients.

Concurrency1, retries0, call≤10s, overall≤60min, input≤16384/output≤1024. Local rolling ceilings:≤12 requests/60s,≥5s dispatch spacing,≤200000 fully reserved tokens/60s; reserving17408/call makes the effective rate≤11 calls/60s. Shared counters cover smoke/main. The local hard ceiling is120 actual attempts; this frozen pack's combined maximum is43. No distributed financial ledger, signed allocation/private signing key or trust-root setup is needed. Process-local limits do not coordinate unrelated account consumers, verify live quota/tier or guarantee zero cost. Developer Free Tier/quota15RPM/250000TPM/500RPD is an attestation, not live account verification; no billing upgrade is performed.

Result JSON uses CREATE_NEW and records safe structured synthetic diagnostics, IDs/hashes, usage (unknown remains unknown), latency, all planned NOT_RUN rows and stop reason. Raw provider bodies are omitted by default; request headers are never persisted. Historical first run: [machine evidence](../Docs/ai-agent/LOCAL_GEMINI_EVALUATION_RUN_V1.json), [interpreted metrics](../Docs/ai-agent/LOCAL_GEMINI_EVALUATION_ANALYSIS_V1.json). The first historical raw response was not retained; its exact failure cannot be recovered retroactively.

Local synthetic raw-response debugging is explicit:

```powershell
# Exactly one VEX1-01 diagnostic; does not count as final smoke.
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode compatibility-diagnostic --max-requests 1 --debug-response --output .local/diagnostic-NEXT.json
# Three predeclared equivalent reference-price probes; separate from the primary campaign.
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode reference-price-diagnostic --max-requests 3 --debug-response --output .local/reference-price-NEXT.json
# Frozen final smoke + 40 reviewed cases; raw responses stay in the ignored .local directory.
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode vietnamese-nlu --max-requests 43 --debug-response --output .local/nlu-NEXT.json
```

Create the output parent directory first. `--debug-response` writes one sanitized response body per fixture to `<output filename>.responses/<fixture ID>.json`, using CREATE_NEW. It captures no request/response headers, redacts the active key and common credential fields/patterns, and retains synthetic extraction text. Only use it with the pinned synthetic local plan; ordinary runtime logging stays OFF. Do not copy credentials or real customer data into diagnostic files. A recorded synthetic response for offline reproduction lives in `src/test/resources/gemini/compatibility/gemini-3.5-usage-metadata.json`.

Results distinguish `pipeline.envelope`, `pipeline.sdk`, `pipeline.extraction`, failure stage/class/location, backend corroboration, strict semantics and known usage. `EXTRACTION_SCHEMA_INVALID` is a contained model-output failure; it may continue in all local phases only with trustworthy usage and no safety flags. Local semantic smoke failures do not stop main evaluation. Missing usage, provider/envelope/SDK failure, quota, cancellation and safety stop the run. Detail token breakdowns overlap top-level counters and are not added again; absent thinking usage remains unreported, not measured zero.

Counters are shared across smoke/main within one process. For a multi-process batch keep all run records and sum actual attempts (prompt diagnostics≤12, smoke≤3, primary≤40, improved≤40, supplemental≤25, total≤120); do not reset counters by restarting. Allow at least60 seconds between separate CLI processes to clear the preceding rolling reservation window, and keep the whole live batch within60 minutes. No automatic retries or model changes. This is a bounded local development workflow, not account-wide quota coordination.

This CLI is separate from Spring startup/ordinary chat and does not enable the Agent. Defaults stay `AGENT_ENABLED=false`, providerFAKE, livefalse. Ordinary turn30s/model3/tool rounds2/proposals6/HTTP6/tool10s/retries0 and A01–A10 policy/grounding/provenance remain unchanged. D07/D08 unaccepted; FULL_AGENT_E2E NOT_RUN. See the [practical architecture decision](../Docs/ai-agent/AI_AGENT_PRACTICAL_SECURITY_AND_EXECUTION_ARCHITECTURE.md).


### Batch 10B.4D commands and current evidence

```powershell
# Frozen smoke3 + primary40; one shared in-process limiter, no semantic gate:
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode vietnamese-nlu --protocol vietnamese-extraction-compact-v3 --max-requests 43
# For a newly authorized campaign with a fresh filename:
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode vietnamese-nlu --protocol vietnamese-extraction-compact-v3 --max-requests 43 --debug-response --output .local/nlu-baseline-NEW.json
# A later improved campaign uses the same mode/40 identities with a separately frozen version and NEW output.
```

`quality-diagnostic --max-requests 6` selects six fixed synthetic price probes; `quality-baseline --max-requests 50` selects7 diagnostics +3 smoke +40 primary in one process. Do not rerun these to evade phase/aggregate caps. Intermediate `stopReason=COMPLETED` in a forcibly interrupted older run is not proof that the plan completed; check planned/attempted/notRun and external accounting.

Current batch stopped after14 actual attempts on a provider non-200 failure with unknown usage. **Do not treat these commands as instructions to retry that stopped campaign.** Primary40 and improved40 are NOT_RUN. V2/V3 do not meet the reference-price quality target; keep them experimental. [Report and versioned artifacts](../Docs/ai-agent/BATCH10B4D_VIETNAMESE_NLU_QUALITY_IMPROVEMENT_REPORT.md).

Offline recorded-response regression (no key/network to Gemini):

```powershell
mvn -o -B -ntp -pl ai-agent-service '-Dtest=GeminiCompatibilityTest,GeminiNluQualityTest,GeminiLocalEvaluationTest' test
```

This replays recorded compatibility responses and fake proposals through existing validators; it is not a new live evaluation or a before/after backend improvement campaign. No batch replay campaign was run because parser/backend semantics did not change. [Machine evidence](../Docs/ai-agent/nlu-quality-v1/aggregate-metrics-v1.json), [offline replay status](../Docs/ai-agent/nlu-quality-v1/offline-replay-v1.json). Raw synthetic bodies remain in ignored `.local/batch10b4d/*.json.responses/`; contract snapshots preserve exact instruction/schema bytes and digests.

---

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


## Batch 10B.4C-LIVE-AUTHORIZATION-OFFLINE

**AUTHORIZATION_OFFLINE_PARTIAL**. Package-private Ed25519 approval verification and an explicit initial-smoke gate now bind trusted issuer/key ID, evidence references, exact fixture/request identities, limits and journal before shared process reservation and lazy credential/transport lookup. No trust root or real approval is provisioned. No new bean/CLI/public endpoint or environment lookup; feature defaults and ordinary/legacy smoke caps remain unchanged.

Separate offline campaign accounting/journal is tested through200 requests with concurrency1/retry0; it has no live dispatch. Signed campaign/sample grants, durable shared account allocation and expanded runner integration remain incomplete. Existing campaign journals never resume after restart. The new60-fixture candidate pack and scorer are proposals pending independent oracle review, not model-quality evidence. See [implementation/status report](../Docs/ai-agent/BATCH10B4C_LIVE_AUTHORIZATION_OFFLINE_REPORT.md) and [evaluation plan](../Docs/ai-agent/VIETNAMESE_NLU_LIVE_EVALUATION_PLAN.md). No live execution is authorized or performed by this checkpoint.


## Batch 10B.4C-LIVE-AUTHORIZATION-OFFLINE-COMPLETION

**AUTHORIZATION_OFFLINE_COMPLETION_DONE / READY_FOR_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK**, within single-machine trusted-filesystem synthetic extraction scope. Allocation-bound signed smoke sequences now execute VEX1-01/02/03 in one session without changing run3/process9 caps. Signed campaign-v1 binds exact reviewed oracle/dataset/sample/context/request identities; integrated runner supports30/60/100/200 fully mocked requests, continuation for ordinary semantic failure, strict critical stops and immutable result ingestion.

Shared allocation reserves whole signed envelopes under an OS lock, retains consumption across JVMs/restarts and seals uncertain/crashed state. Existing campaign tombstones never resume. Ordinary Agent budgets/FAKE-only extraction, feature defaults and legacy smoke restrictions remain unchanged. No live request, actual credential lookup, domain call or production grant occurred.

[Completion report](../Docs/ai-agent/BATCH10B4C_LIVE_AUTHORIZATION_OFFLINE_COMPLETION_REPORT.md), [operator setup/dry-run guide](../Docs/ai-agent/GEMINI_LIVE_OPERATOR_SETUP_GUIDE.md), [candidate technical review](../Docs/ai-agent/VIETNAMESE_NLU_CANDIDATE_REVIEW.md). Offline operator tool has template/bind-scope/canonicalize/envelope/verify only; no execute command or key-environment lookup. TRUST_ROOT_PROVISIONING_REQUIRED, BILLING_QUOTA_UNVERIFIED and INDEPENDENT_ORACLE_REVIEW_REQUIRED remain; actual schema/NLU/D07/D08/production acceptance is unproven. No automatic live transition.

## Batch 10B.4C operator setup and preflight recheck

**OPERATOR_SETUP_PREFLIGHT_AWAITING_OPERATOR**. Offline baseline943/final953 tests PASS, historical23/16/0/1 unchanged. A separate operator-only PropertiesLauncher main `GeminiInitialSmokeOperator` now provides a practical future initial-smoke path through existing signed v2/allocation/session/process/wire gates; default invocation denies.10 focused guarded tests use test-owned keys/sentinels/mock HTTP only. No ordinary Agent configuration changed.

[Exact fixture/request review and evidence](../Docs/ai-agent/BATCH10B4C_OPERATOR_SETUP_AND_PREFLIGHT_RECHECK_REPORT.md), [consolidated operator actions and future command](../Docs/ai-agent/GEMINI_INITIAL_SMOKE_OPERATOR_ACTIONS.md). Account/billing/quota/privacy evidence, independent fixture approval, actual trust root/persistent journal and fresh signature/read-only preflight remain operator prerequisites. Gemini live attempts0; actual credential reads0; real domain calls0. Separate explicit live authorization is still required.

## Batch10B4D-FINAL local evaluation

The latest local synthetic workflow uses explicit developer authorization and the existing backend `GEMINI_API_KEY`; it requires no signed approval. Historical signed-tool instructions above describe separate paths. Ordinary defaults remain OFF/FAKE/liveOFF. These standalone commands invoke no domain clients, database or frontend.

From `checkinn`, offline verification:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
```

From `ai-agent-service`, replay retained actual synthetic responses into a **new** file (no provider/key lookup):

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiHistoricalReplay' -cp target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher ../Docs/ai-agent/semantic-extraction-fix-v1/baseline40-observations-v1.json .local/replay-review-v1.json
```

Offline full-plan preflight, then fixed-primary B40 evaluation (omitting `--fallback` never switches models):

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiLocalFallbackEvaluation' -cp target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --output-dir .local/new-authorized-evaluation
java '-Dloader.main=com.example.aiagent.model.GeminiLocalFallbackEvaluation' -cp target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --live --phase B40 --output-dir .local/new-authorized-evaluation
```

For a separately authorized local fallback campaign, add `--fallback`. The only order is3.5 Lite→3.1 Lite→2.5 Lite→2.5 Flash; reverify quota/eligibility for the new campaign. `--phase` accepts TECHNICAL/REMAINING_A/B_DIAGNOSTIC/B40/SUPPLEMENTAL or ALL. Default is offline; no arbitrary model, endpoint, prompt or dataset argument is accepted.

Distinct planned phases within one allowance use the **same directory** and a new identity, such as `--segment segment-03-b40`. The CLI imports counters/model availability/earliest deadline from immutable sibling records. A stopped segment cannot reopen. After investigating/fixing a known400, a separately planned reserve probe can use `--phase MODEL_VERIFICATION --recheck-primary --segment segment-01-model-verification`; it is not an automatic retry of the failed sample. Never use another directory or rename a phase to replenish this batch's120 attempts, phase limits or quota. Quota/credential/safety/unknown-in-flight stops cannot continue through a new segment.

Each `responses/ATT-NNN.json` is an actual synthetic provider envelope. The segment JSON separates actualExtractionText, semanticProposal, compiledExtraction and pipeline. `providerErrorBody` retains bounded, sanitized error evidence; headers/credentials are never persisted. Inspect attempt/model/status before diagnosing schema, semantic or provider failure. Do not copy private environment files or real business data into this path.

Offline analysis from `checkinn`:

```powershell
python Docs/ai-agent/semantic-corroboration-final-v1/analyze.py
```

See [final report](../Docs/ai-agent/BATCH10B4D_FINAL_SEMANTIC_CORROBORATION_AND_MODEL_FALLBACK_REPORT.md), [fallback design](../Docs/ai-agent/GEMINI_MODEL_FALLBACK_DESIGN_V1.md), [corroboration rules](../Docs/ai-agent/VIETNAMESE_SEMANTIC_CORROBORATION_FIX_V1.md) and [A/B v2](../Docs/ai-agent/VIETNAMESE_NLU_CONTRACT_AB_COMPARISON_V2.md). Model-specific quality, logical availability, actual HTTP attempts, paired outcomes, usage/latency and replay are separate machine evidence. Mock fallback tests and mixed-model availability are not fixed-model NLU quality/live recovery.


A separately planned, fixed allowlisted-model reserve verification can use `--phase MODEL_VERIFICATION --verification-model gemini-2.5-flash-lite --segment segment-05-model-verification` (without `--fallback`). The optional `--verification-protocol vietnamese-extraction-compact-v3` selects the frozen A contract; default is semantic-v1. Only those two contracts and the four typed model IDs are accepted. Such probes consume the same aggregate/model/reserve limits, retain every failure, and never replace primary observations. All original batch directories/segments are sealed evidence; do not rerun these examples as a continuation of the finished allowance.

## Batch10B4D-QUALITY candidate

`vietnamese-semantic-v2` is a standalone development candidate. A v3 and B v1 retain their contracts and ordinary runtime compatibility; defaults remain OFF/FAKE/liveOFF. The v2 instruction defines action-specific combinations and full explicit-stay retention. Its flat schema adds field descriptions, keeping the same strict structural constraints. Java derives missing business slots and normalizes only exact reviewed destination labels/casing/NFC/whitespace or reviewed accentless aliases. Unknown spellings do not fuzzy-map. Every compiled value still requires independent utterance corroboration; model output never grants confirmation, target identity or execution authority.

The quality path recognizes literal invalid-calendar, reversed price/stay and invalid occupancy evidence only to admit an exact empty clarification. It never corrects those values or updates state. This behavior is local to the candidate evaluation; existing signed and ordinary paths keep their compatibility behavior. Discovery grammar accepts bounded natural search/politeness clauses while retaining unknown suffixes and unsupported filters.

Offline commands from `checkinn`:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
java '-Dloader.main=com.example.aiagent.model.GeminiQualityEvaluation' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --output-dir ai-agent-service/.local/new-explicitly-authorized-quality-campaign
```

Default invocation only writes a new preflight. The dedicated plan contains ten targeted diagnostics, the original eligible40 and twenty predeclared `FRESH_DEVELOPMENT_GENERALIZATION` samples. The evaluation clock is explicitly pinned to the original B40 clock; this is dated synthetic development evidence. `--live --phase DIAGNOSTIC|FROZEN40|FRESH` requires a new explicit authorization; all phases of one allowance use the same directory and unique `--segment` names. Existing campaign directories are sealed evidence, not reusable budgets. The existing local runner supplies the shared120 cap, phase ceilings20/40/20, fallback reserve20, rolling reservations, fixed model order and critical-stop behavior. Known previous same-day model attempts reduce remaining local RPD headroom; account-wide usage remains unverified and429 stops. No key is read by offline tests/preflight or replay.

Envelope replay without a provider or real credential lookup:

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiSemanticReplay' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher ai-agent-service/.local/batch10b4d-final/segment-03-b40.json ai-agent-service/.local/new-b1-replay.json
```

This replays actual retained envelopes through an in-memory exchange and the real SDK, preserving original text, expected outcomes and clock. Replay is backend evidence, not new Gemini quality. See the [quality report](../Docs/ai-agent/BATCH10B4D_QUALITY_SEMANTIC_CONSISTENCY_AND_NLU_IMPROVEMENT_REPORT.md), [consistency decisions](../Docs/ai-agent/VIETNAMESE_SEMANTIC_CONSISTENCY_FIX_V2.md) and [versioned evidence](../Docs/ai-agent/semantic-quality-v2/). D07/D08, authenticated E30 ingress and FULL_AGENT_E2E remain outside this batch.
