# Gemini local model fallback v1

24/11/2026. Scope: `LOCAL_DEVELOPMENT_EVALUATION` only, explicit standalone `GeminiLocalFallbackEvaluation`. Ordinary Agent defaults OFF/FAKE/liveOFF, runtime provider/default model and turn budgets are unchanged. This is not runtime fallback.

## Verified model profiles

|Order / exact API ID|RPM|TPM|RPD|Evidence / restrictions|
|---|---:|---:|---:|---|
|1 `gemini-3.5-flash-lite`|15|250000|500|Developer primary snapshot; public Free Tier|
|2 `gemini-3.1-flash-lite`|15|250000|500|Developer active Free Tier snapshot, usage 0|
|3 `gemini-2.5-flash-lite`|10|250000|20|Developer active Free Tier snapshot, usage 0; existing-user access restriction|
|4 `gemini-2.5-flash`|5|250000|20|Developer active Free Tier snapshot, usage 0; deprecated, existing-user access restriction|

Official [3.5 Lite](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite), [3.1 Lite](https://aistudio.google.com/docs/models/gemini-3.1-flash-lite), [2.5 Lite](https://aistudio.google.com/docs/models/gemini-2.5-flash-lite), [2.5 Flash](https://aistudio.google.com/docs/models/gemini-2.5-flash) pages confirm these exact IDs and structured output capabilities. Public [pricing](https://aistudio.google.com/docs/pricing) lists Free Tier text input/output. Numeric project quotas are developer-provided: the current [rate limit documentation](https://aistudio.google.com/docs/rate-limits) directs active quotas to AI Studio; they cannot be copied between models. No API key/project/account/billing change.

Important date boundary: Google documents automatic routing of 2.5 Flash to 3.5 Lite from **01/12/2026**. This snapshot is for 24/11/2026 only. A mismatching returned `modelVersion` is a protocol failure, never silently attributed to the requested model. Reverify profiles for a later campaign.

All profiles use pinned LangChain4j 1.17.0 SDK serialization, JSON MIME type, unchanged strict local business schema, maxOutputTokens1024, retries0, returnThinking(false), built-in tools disabled. No explicit thinking configuration or sampling parameter is sent. The [thinking defaults](https://aistudio.google.com/docs/thinking) differ: 3.5/3.1 Lite minimal, 2.5 Lite off, 2.5 Flash on. Output/thinking consumption remains bounded by the existing total-output validator. `returnThinking(false)` is not a claim that reasoning tokens are zero. Offline SDK capture verifies each exact model endpoint/configuration. Live support is separately measured; offline serialization alone does not prove provider acceptance.

The [structured-output guide](https://aistudio.google.com/docs/structured-output) describes a JSON Schema subset and unenforced keywords (including string lengths/uniqueItems). Existing strict Java validators remain authoritative. No schema keyword or business meaning is silently dropped locally. Every planned sample records its model-specific SDK wire digest; prompt/schema/compiled business contracts retain separate identities.

## Selection and failure policy frozen before dispatch

Only enum `GeminiLocalModelProfile` can select a model, always on the fixed Google HTTPS host. Dataset/user/model text cannot choose model/endpoint. The old signed and ordinary paths retain primary-only defaults. A returned model identity, when present, must equal the selected profile.

Fallback requires an actual **terminated HTTP503** with provider JSON error code503, status UNAVAILABLE and an explicitly allowlisted high-demand/overloaded capacity message. Model-generated answer text and arbitrary substrings are not triggers. Each failed attempt retains its body (bounded/sanitized), identity and full17408 token reservation. No same-model retry, answer shopping or failback. Each logical sample has at most4 attempts/3 transitions, in developer order; ineligible profiles are skipped. A failed model is unavailable for the rest of this run: later samples use the stable remaining availability segment without rechecking primary. No promotion into ordinary defaults.

Cooldown after each eligible failure is at least15s or a longer valid Retry-After. A cooldown exceeding the original deadline stops; invalid Retry-After stops. At most8 cumulative capacity failures are allowed; the no-failback run-local state can exhaust all four profiles sooner. Exhaustion records `ALL_ELIGIBLE_MODELS_UNAVAILABLE`, seals the campaign and leaves remaining samples NOT_RUN. No semantic success is invented from a fallback failure.

400/401/403/429 stop without fallback. Schema/semantic/compiler/backend rejection never switches models. Safety/authority failure, unknown/inconsistent successful usage and unknown in-flight state stop. Other terminated transient failures use the existing two-consecutive/five-cumulative policy and ≥15s cooldown to advance only to a distinct sample. Eligible capacity failures use the bounded selector rather than accidentally consuming that two-consecutive threshold before the chain can run. Full reservations and counters survive switches.

## Accounting and persistence

Reuse existing local monotonic limiter: concurrency1, aggregate≤120, global spacing≥5s, ≤12 requests/rolling60s, ≤200000 reserved tokens/rolling60s, input16384/output1024, per-call≤10s, campaign≤60min. Full17408 reservations make the practical rolling maximum11. Model queues enforce stricter RPM/TPM and model RPD counts across all phases; both input and output reservations count conservatively. Quota exhaustion stops; it cannot trigger another model.

Phase ceilings: technical3, remaining A15, B diagnostics8, B40 40, supplemental24, extra fallback attempts30. First attempts consume their planned phase; additional attempts consume fallback reserve AND aggregate budget. A segment starting on an available fallback still consumes the original sample phase. Planned90 logical samples use original reviewed data/oracles; twelve supplemental A samples are selected in original identity order before any response, two are separately labeled029/030 retests and ten are predeclared paraphrases.

The CREATE_NEW `campaign.json`/named segment records and shared OS file lock guard one local campaign directory. The full plan, expected values, model profiles, contract snapshots and wire hashes are fsynced before dispatch; every dispatch is persisted IN_FLIGHT before transport. Responses are unique attempt files, not overwritten by recovery. Stopped/crashed/sealed records cannot reopen. Distinct planned phases import completed records and a known HTTP400-stopped protocol segment after investigation, preserving aggregate/phase/model counts, rolling reservations, unavailable models and the earliest original deadline. Unknown/in-flight, quota, credential, safety and exhausted-chain stops cannot continue. Each predecessor hash is recorded; this never resumes a sealed sample loop. This is a cooperative local journal, not account-wide or distributed quota infrastructure. Do not launch another directory to restart this batch's allowance. Existing sealed previous-batch records are not imported as fresh budget.

Each attempt records logical sample/attempt ID, requested primary, actual model, model index, predecessor/trigger, HTTP/error evidence, known/UNKNOWN usage, reservation, dispatch time, latency, cooldown and pipeline. Logical terminal result is separate. Counts, fixed-model quality and mixed-model availability must remain separate; cross-model A/B is descriptive only. Offline replay is never new model accuracy.

## Runtime constraints for a future batch

No runtime fallback is implemented. Any later integration must share the existing turn≤30s/model calls≤3/tool rounds≤2/proposals≤6/HTTP≤6/tool≤10s/retry0, preserve policy/grounding and must not import the local four-attempt allowance into ordinary chat. Context-aware relative changes/target resolution remain future work.

Actual offline/live status and transitions: [main report](BATCH10B4D_FINAL_SEMANTIC_CORROBORATION_AND_MODEL_FALLBACK_REPORT.md), [model profile evidence](semantic-corroboration-final-v1/model-profiles-v1.json).

## Observed400 and wire revision

The first actual2.5 Lite request (A, VNE1-051) returned400 INVALID_ARGUMENT: schema constraint too many serving states. The segment stopped immediately; 2.5 Flash was not tried to hide this defect. The retained400 and preceding503 failures remain immutable. A serving-only `gemini-25-bounded-wire-v1` revision removes descriptions, numeric/string/array length bounds, uniqueness, date format and pattern hints from the **2.5 provider representation**. It preserves property names, types, enums/const, required keys, alternatives and additionalProperties semantics. Prompt meaning and original strict local schema/parser/compiler remain unchanged: invalid numeric/date outputs still reject. Primary/3.1 wire representations are unchanged. Offline tests verify the separation; exact model-specific wire digests identify the revision. Later live compatibility, or NOT_RUN, is stated in the main report.

An explicit `MODEL_VERIFICATION --recheck-primary` is a separately planned reserve attempt, never automatic failback inside a frozen evaluation. It consumes fallback/model-specific verification reserve (not the already used technical3). A known terminated successful probe can begin a new availability segment. Old failed attempts/capacity counts remain; later phases import state without another health probe. This batch uses chronologically numbered segment names. A verification success is not a fallback recovery of the earlier failed logical sample.


Serving v2 (`gemini-25-bounded-wire-v2`) additionally represents each `const` as an equivalent single-valued `enum`, a documented Gemini schema keyword. It does not rename operations or repair responses. Fixed2.5 Lite A probes under v1/v2 both reachedHTTP200 after the earlier serving400; v1 emitted invalid EQUAL, v2 emitted REPLACE with an invalid reference. Both were strictly rejected and retained. A semantic/schema correctness is not claimed. B probe/recovery evidence was obtained with v1 and is labeled accordingly. Two such deliberate model-specific verification calls were charged to the same reserve; they are not automatic same-model retries or primary A replacements.

For a planned fixed-model reserve verification only, `--verification-model` accepts an exact ID from the typed allowlist and requires MODEL_VERIFICATION with fallback disabled; `--verification-protocol` accepts only the frozen A v3 or semantic-v1 contract. These operator CLI choices are never available to user utterances, HTTP requests or model output. Eligibility and selectedForSegment are distinct report fields; original early segment metadata is retained with its dispatch-selection interpretation documented in the exported metrics.
