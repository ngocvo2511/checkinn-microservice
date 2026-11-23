# Batch 10B.3 — Orchestration, policy, state and grounding report

22/11/2026, Asia/Ho_Chi_Minh. **DONE — internal offline READ MVP scope.** Unit/fake provider/loopback mock HTTP verified; no production or live-provider acceptance claim. Batch10B.4 and later are not implemented.

## 1. Git baseline

Before implementation:

| Repository | Branch | HEAD | Status |
|---|---|---|---|
| Backend | `ci/github-actions` | `5f7d22b16f8a7ff4e3d8d68e45f84dd5b64851cd` | Modified `.idea/compiler.xml`, `.idea/encodings.xml`, `pom.xml`; untracked `ai-agent-service/` and seven AI documents (architecture, evaluation, tool design,10A review,10B plan,10B1/2 reports) |
| Frontend | `fix/frontend-ci-remediation` | `1c972c009a1439c85b7673895d31f932ec833b06` | Clean |

After implementation: branches/HEAD unchanged, frontend clean. Backend preserves all baseline edits/untracked work and adds this report; changes made in this batch are restricted to Agent source/tests/README and four AI design documents. No stage/commit/push/stash/reset/clean/checkout. Root POM/IDE files were not edited. `git diff` shows tracked baseline changes only because Agent and the design documents were already untracked; direct source/whitespace/link checks additionally cover those files.

## 2. Scope

Read root AGENTS and current Agent/provider/registry/context/result/error/client/projection/config/tests and AI design/contract/10B1/2 reports. Consulted canonical standardization PLAN/AUDIT/current checkpoints; no database implementation. The old READ contract's retry1/older checkedAt fallback is superseded for Agent by approved retry0/strict adapter contract. No domain API/business/schema/proto/event changes.

New files under `ai-agent-service/src/main/java/com/example/aiagent/orchestration/`: AgentOrchestrator, PolicyExecutor, TurnBudget, ConversationState, SearchCriteriaState, ConversationStore, InMemoryConversationStore, EvidenceLedger, GroundingGate, OrchestrationConfiguration. Added `src/test/java/com/example/aiagent/orchestration/OrchestrationTest.java`.

Extended FakeLlmProviderAdapter scripted steps and ToolRegistry/ToolExecutionContext policy validation, remaining deadline, actual-attempt guard and server-owned trace propagation. No new dependencies/version/configuration changes. Updated Agent README, AI_AGENT_READ_ARCHITECTURE, AI_AGENT_READ_EVALUATION_PLAN, BATCH10B_AI_READ_IMPLEMENTATION_PLAN, BATCH10A_ARCHITECTURE_REVIEW.

## 3. Architecture implemented

Java-only internal request → feature/input/session admission → explicit typed criteria patch → bounded model request → passive tool proposals → PolicyExecutor → fixed READ registry → projected evidence → model follow-up → evidence-selection validation/deterministic facts → optimistic state commit. Tool execution is sequential. No AI Services, framework callback, unbounded loop, provider fallback, automatic retry, public chat endpoint or domain WRITE.

Internal scope/criteria are supplied by the trusted local test caller. This batch does not implement a natural-language extraction or authenticated ingress boundary. Model responses cannot modify criteria or session revision. That separation is necessary before any later external ingress is considered.

## 4. PolicyExecutor

`executeRound` reserves a whole proposal batch and round before any execution. Per proposal: closed tool name/GET policy; actual offered declarations; recomputed current permission; shared strict JSON/schema/business validation; confirmed dates/occupancy/filter match; user target match; public room→hotel evidence; current revision/freshness; duplicate fingerprint; HTTP guard/deadline. Invalid/unknown/WRITE/unoffered/arbitrary URL/method/changed arguments do not reach HTTP. Denied proposals consume admitted proposal count, not HTTP attempts.

Duplicate key uses ToolId + canonical fingerprint (object order, numeric representation, set-valued arrays, search paging/sort defaults normalized) + criteria revision + relevant room/hotel relationship. Duplicate failures are not retried either. No successful cache replay/global cache is implemented. The registry remains a Java internal adapter API for direct10B.2 regressions; application model proposals have exactly one dispatch path through PolicyExecutor.

## 5. TurnBudget

Monotonic start and absolute deadline, remaining time, cancellation flag, counters for model reservations/proposals/rounds and actual adapter ledger entries. Configuration is validated at construction and cannot exceed the approved limits:

| Limit | Enforced maximum |
|---|---|
| Turn duration | 30s |
| Model requests | 3 |
| Tool proposals | 6 |
| Actual tool HTTP attempts, including failures | 6 |
| Tool rounds | 2 |
| Individual tool | min(configured cap≤10s, remaining turn) |
| Automatic retry/fallback | 0 / none |

Model reservation precedes provider submission; proposal batch/round reservations occur inside policy. Overlarge batch is rejected atomically before any execution; its rejected excess is not counted as admitted proposals. HTTP count is the sum of actual request-local attempt ledgers from10B.2. The guard is invoked immediately before the single HTTP execute. Tests send six failed HTTP calls, observe count6 and block admission7; reduced limits exercise HTTP stops inside orchestration. Counters never reset on faults/denial/timeout/tool changes. Budget stop returns safe zero-fact output without an extra apology model call.

Provider Future waits poll cancellation/turn deadline; bounded2 daemon workers/no queue. Tool registry cancels HTTP/Future on timeout. This guarantees bounded caller waiting under tested conditions, not forced thread termination or live network cancellation for an arbitrary noncooperative provider. Hard30s boundary is tested with monotonic ticker; shorter real loopback/provider deadlines exercise actual cancellation without a30s test sleep. Existing adapter/config tests cover10s maximum.

## 6. Tool exposure rules

Application-owned Intent, never model self-authorization. SEARCH needs destination/name, dates and confirmed occupancy; BROWSE allows preliminary undated catalog without stay/occupancy/price filters. A07/A08 appear only when supplied filters require code validation. SEARCH exposes A01 after dependencies are present. DETAIL/ROOMS/REVIEWS target one explicit public ID. A05 appears only after current public room evidence and complete stay criteria; A04 can resolve that explicit room first. A06 uses the structured destination query. A09 is suppressed when current A01 summary already suffices. A10 is general cancellation policy only. New dependencies become offered in a later model request, not through hidden HTTP or same-response privilege escalation.

## 7. ConversationState

Opaque local conversation ID, optimistic state revision, total message count, recent messages, structured criteria/criteria revision (including confirmed occupancy), evidence references/records, last activity and expiry. No total8-message ceiling: tested10 user turns/20 total messages while recent state/model window stays≤8. The model context additionally includes fixed policy and bounded application-generated criteria/evidence data, never the whole conversation history.

Persisted recent user messages are deterministic intent summaries; persisted assistant messages are safe application messages. Raw current user prose exists only temporarily in the fake request; raw model prose/tool transcripts are not persisted. There are no API-key/JWT/cookie fields or credential/header objects in the state model. Structured destination/name/filter values remain local state data; this is not approval for sending real user data to a live provider or a completed privacy policy.

## 8. SearchCriteriaState

Supported backend fields: destination(cityId/provinceId/cityName), hotelName, checkIn/out, rooms/adults/children, min/max base unit price, accommodationTypes, amenityCodes, starRatings, guestRatings. Values carry USER_PROVIDED/USER_CONFIRMED/MODEL_PROPOSED/BACKEND_DERIVED provenance. Occupancy requires USER_CONFIRMED; model-proposed values never enter confirmed search arguments. Typed bounds/date/UUID/filter validation precedes state commit, with full registry business validation again before HTTP. No invented dates/children/default stock/price conversions.

## 9. CriteriaRevision

Revision increments on material values/provenance changes. Location/date/occupancy edits replace their group; omitted members become missing rather than silently retaining old occupancy or a mismatched date. Other filters remain structured independent of recent history. All evidence carries the revision; old-revision records are excluded from model context and final facts. Tools cannot submit different dates/occupancy/filter values from confirmed state. Final evidence additionally must match current intent/selected hotel/room, so changing targets cannot cite another hotel's still-fresh record at the same criteria revision.

## 10. ConversationStore

Storage-independent interface uses find + exclusive Lease/state/commit/close. In-memory implementation synchronizes admission and commit, rejects same-session concurrent turns as SESSION_BUSY, checks expected revision, and permits one revision-incrementing commit. Invalid updates do not commit. Valid explicit user updates and safe summaries survive timeout/cancellation/provider failure; newly obtained evidence of an interrupted/failed turn is discarded. Previously stored evidence is never promoted across revisions.

Idle TTL30m, active capacity1000; lower safe bounds supported in constructor; expired nonbusy sessions are evicted, active leases are not evicted, capacity exhaustion fails closed. Snapshots use immutable collections/defensive JSON copies; evidence bound24 and recent bound8. Restart loses state. No database/Redis/migration/replication/history restoration/authenticated ownership. TTL is active in-memory lifecycle, not a durable-history retention decision.

## 11. EvidenceLedger

Only successful OK/EMPTY projected tool observations create records: evidence UUID, tool ID, normalized argument fingerprint, source route, generated request ID, retrieval time, server checkedAt where provided, criteria revision, hotel/room identities, projected facts, status and turn ID. Validity is CURRENT/OLD_REVISION/EXPIRED/PREVIOUS_TURN_INVENTORY. Failure never becomes empty/success; available=false is a valid negative observation.

Conservative local freshness30s checks retrieval time and server checkedAt (including per-offer A01 checkedAt); future timestamps fail closed. This is a local safety choice, not approval of pending quantitative SLOs. A01/A05 are never reused across turns even within30s. No cross-session evidence/global cache. Current records are bounded24; stale evidence is filtered rather than sent as current fact. Approval remains the public service's effective predicate; Agent does not infer approval from stock/isActive or fabricate approval metadata.

## 12. Response grounding

LangChain4j JSON response schema and authoritative strict parser permit only `{evidenceIds:[...]}` (≤6). Unknown/duplicate/cross-session/old-revision/stale/wrong-target citations, prose and extra factual keys are rejected. Model cannot supply hotel/price/hold/refund/booking/payment facts. Application renders whitelisted values from cited projections, retaining null/EMPTY semantics and base price/observation/general policy caveats. No final payable total, hold, guaranteed inventory, personalized refund, booking/payment status, invented amenities or Place facts can be emitted by the model contract.

Descriptions/FAQ/free-text policies remain untrusted data in the user-role context and never become system instructions or deterministic factual narrative. Renderer omits them; policy is checked independently even when the fake model follows an injected WRITE instruction. Name/label fields are source display strings, not executable instructions; future UI must escape them. The deliberately narrow answer contract sacrifices free-form synthesis and some available descriptive facts to make sensitive claims provable. No keyword filter or LLM judge is the safety gate.

## 13. Fake provider orchestration

FakeLlmProviderAdapter accepts a test-owned queue of request→response steps. Script exhaustion is a typed malformed-response error, no replay/fallback. Tests inspect actual declared tool sets and projected observations, select generated evidence IDs from model context, then validate structured answer/state/counters/HTTP behavior. No key lookup or live provider bean. Existing default Fake behavior/foundation assertions remain compatible.

## 14. Tests and commands

Executed from backend root, offline Maven cache:

| Command / scope | Outcome |
|---|---|
| `mvn -o -B -ntp -pl ai-agent-service -DskipTests compile` | PASS, initial implementation compile |
| `mvn -o -B -ntp -pl ai-agent-service -Dtest=OrchestrationTest test` | PASS, initial44 cases |
| `mvn -o -B -ntp -pl ai-agent-service test` | PASS, intermediate192 tests (32+52+108) |
| `mvn -o -B -ntp -pl ai-agent-service package` | PASS on successive implementation checkpoints; final results below |
| `mvn -o -B -ntp -pl ai-agent-service,api-gateway,hotel-service,booking-service -DskipTests compile` | PASS4/4; Place excluded; sibling tests not run |
| `git diff --check` in both repos + direct untracked whitespace/local-link/static source inspection | PASS |
| Dependency tree | N/A: no dependency changes in10B.3 |

Final Agent package: **202 tests, 0 failures, 0 errors, 0 skipped**: AgentFoundationTest32, OrchestrationTest62, ReadToolsTest108. Includes offline SDK fake-transport/network-denial tests; no actual Gemini/OpenAI API traffic. All count assertions inspect actual counters or loopback requests, not response keywords alone. Intermediate PASS counts are checkpoints, not additive test totals. Standard test JVM warnings (SecurityManager deprecation/Byte Buddy) and existing Hotel Lombok/MapStruct compile warnings were not “fixed” by changing unrelated source.

Unit verified; fake provider verified; mock HTTP verified. **Real Gateway integration NOT_RUN; live LLM integration NOT_RUN; hosted DB/real Hotel/payment providers/Docker/FE NOT_RUN.** Full40-case model evaluation/quantitative D07 acceptance NOT_RUN. No stack was started and no migrations/integration flags used.

## 15. Security findings

No out-of-scope fixes required. Fixed GET allow-list, anonymous transport, no arbitrary route/method/header, no credential forwarding, retries/redirects disabled and TLS verification unchanged. Public dependency failures remain typed unavailable/unknown. Strict tool validation and deterministic rendering independently block model/tool-text instruction escalation and fabricated sensitive claims. No raw payload/PII/credential logging or prompt-bearing spans added.

Metrics: turn duration/model/proposal/HTTP/round/denial/evidence counts, bounded result/tool error categories, tool/turn timeout and cancellation counters. Result carries generated correlation ID or current trace ID. Payload-free server turn span, captured model worker context and server-owned traceparent→mock HTTP are implemented. Mock trace header assertion is not proof of real Gateway→Agent→Hotel trace propagation. Direct UTF-8/newline/whitespace inspection covered37 source/document files;115 local link targets resolved. Git tracked diff checks also pass in both repositories.

## 16. Known limitations

Internal synthetic local testing only, single JVM; typed request patches rather than natural-language extraction; fixed recent8 summaries rather than durable full transcripts; no authenticated session ownership or restart restoration; conservative freshness may require re-read; descriptive policies/amenities narrative omitted; structured evidence-selection output only. No persistent history/deletion controls, cross-replica serialization, external ingress admission/rate limiting, token/spend policy or verified live transport cancellation. These are explicitly deferred, not missing guarantees concealed by fake tests.

## 17. Remaining decisions

D02 PARTIALLY_APPROVED: provider ordering only; model/account/region/data terms/finite spend still open. D03 APPROVED only for stated hard subset, not all historical10A caps/parallelism/admission/SLOs. D04 APPROVED only internalA01–A10/minimal projection; no full-registry exposure/public tool API/WRITE. D05 PARTIALLY_APPROVED only this local ephemeral state; ingress/ownership/privacy/retention/durable restoration still open. D07/D08 remain NOT_APPROVED/pending. No status promoted to full approval.

## 18. Readiness for Batch10B.4

Technical offline prerequisite is ready: bounded policy/orchestration, enforced budgets, structured revisioned state/store, grounded evidence and deterministic regressions/package all pass. **Not authorized/ready for live Gemini/OpenAI rollout until D02 and applicable privacy/spend/cancellation gates are resolved.** No automatic transition to10B.4. Stop here; no frontend/Gateway ingress/live provider/persistence work performed.
