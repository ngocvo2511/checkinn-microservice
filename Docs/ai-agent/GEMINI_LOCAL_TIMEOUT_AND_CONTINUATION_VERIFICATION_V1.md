# Gemini local timeout and continuation verification v1

24/11/2026. **LOCAL_TIMEOUT_PATH_OBSERVED_LIVE; CAMPAIGN_SEALED_AT_TRANSIENT_THRESHOLD**.

Previous ATT-015 remains immutable: status0/TIMEOUT, unknown usage, no response/termination proof, sealed UNKNOWN_IN_FLIGHT. Its original hash is still `83a6c72fcb704af74297b7ad62e8de43530d8a6b2184f4491caf76d99dd70043`. It was never reopened/reclassified as proven termination retroactively.

Actual source already had a post-stop wrapper that returned observed transport termination after an exception. Remaining defect: it discarded the safe ProviderException category; the runner then synthesized UNAVAILABLE when Reply had no response. Reply now retains the typed category, and the runner propagates it. No category, timeout exception, Future.cancel or interrupt alone can prove termination.

Local evidence: classic HTTP execution is synchronous; its response callback completes before return. Response streams, HTTP client and connection pool close before transport finally marks termination. The evaluation worker is the calling thread; no queued asynchronous response delivery/state callback exists on this local path. SDK maxRetries0 and Apache automatic retries disabled. A pre-dispatch expired control has no fabricated transport-termination proof. An unknown exchange/worker lifecycle stops and cannot dispatch/lookup credentials again. The existing ordinary runtime worker pool is not migrated by this batch.

Offline: GeminiEvidenceTimeoutTest covers pre-dispatch expiry, real loopback timeout/cancellation with server-observed EOF/reset, completed synchronous call, unknown termination stopping/reopen/key lookup, known timeout continuing only to another sample after≥15s, full16384+1024 UNKNOWN reservations and transient threshold. Existing GeminiTransportTest/GeminiLocalFailurePolicyTest/GeminiLocalModelFallbackTest cover bounded bodies, socket aborts, no retries, rate/model caps, fallback exclusion and sealed campaigns. All pass in final1235-test package; no real key/provider use in Maven.

Technical smoke phase used1/4 allowed attempts: successful HTTP200/known usage. It alone did not verify timeout behavior. Later **natural**, not deliberately induced, live observations:

|Attempt|Model|HTTP/category|Local termination|Usage|Continuation|
|---|---|---|---|---|---|
|ATT-017|3.5 Lite|0 / TIMEOUT|true|UNKNOWN|15s cooldown, distinct next sample|
|ATT-020|3.1 Lite|0 / TIMEOUT|true|UNKNOWN|15s cooldown, distinct next sample|
|ATT-021|3.1 Lite|0 / TIMEOUT|true|UNKNOWN|mandatory stop; no next dispatch|

These verify delivery of actual termination/category metadata and bounded local continuation. No successful response replaced these attempts, no same-sample retry and no timeout fallback occurred. The earlier503 also retained UNKNOWN usage and a full reservation. Total4 unknown-usage attempts retain69632 reserved tokens. All21 reservations365568 remain charged conservatively.

Policy: terminated timeout can advance only when local resources/worker/delivery are ended; cooldown≥15s.503 capacity fallback requires actual terminated allowlisted capacity evidence and eligibility/reserve/deadline, and is disabled in fixed-model comparison.400/401/403/429, unreconciled successful usage, critical safety/authority, unknown local termination and exhausted budget/deadline stop. Two consecutive logical transient failures or five cumulative actual transient failures stop; recovered capacity attempts still count. Actual stop: two consecutive timeouts, cumulative4; no new campaign/directory/process bypass or credential lookup followed.

Configured deadline10s; observed timeout wall latencies10007/10006/10008ms include unseparated SDK/cleanup overhead. The excess above configured time is7/6/8ms, not separately measured provider processing. Local closure does **not** prove remote inference stopped, nor remote inference concurrency1. Late remote responses cannot enter the closed local exchange; no remote termination API/probe was used. [Classifications](evidence-extraction-v1/timeout-classifications-v1.json), [all failures/reservations](evidence-extraction-v1/budget-accounting-v1.json), [sealed segments](evidence-extraction-v1/segments-v1.json).
