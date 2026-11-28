# Vietnamese semantic extraction contract v1

24/11/2026. Implemented as **local experimental only** in `VietnameseSemanticCompiler`; no ordinary Agent/default/signed-protocol promotion. Live diagnostics and evaluation are **NOT_RUN** because the current campaign stopped after two consecutive HTTP503 responses. This document describes implemented behavior, not measured model improvement.

The existing contract asks Gemini to choose action/intent, internal field names, REPLACE/CLEAR wrappers, derived `referencePriceExclusive`, missing slots and inferred-field hints. The hypothesis is that a smaller meaning-oriented representation reduces that burden. Current evidence establishes representation defects but does not establish that this alternative improves Gemini quality.

## Actual representation

The exact instruction, full JSON Schema and SHA256 digests are in [contract snapshots](semantic-extraction-fix-v1/contract-snapshots-v1.json). The schema is produced by `VietnameseSemanticCompiler.schema()` and transmitted through the existing pinned SDK serializer. No new endpoint, model, SDK or dependency exists.

Root is a closed object. Only `intent` is required. Unknown fields, null, duplicate keys, trailing content and coercion are rejected. Optional properties are omitted when unmentioned; absence means **NO_CHANGE**.

|Property|Type and meaning|
|---|---|
|intent|SEARCH, BROWSE, REFINE, SELECTED, COMPARISON, CLARIFY, UNSUPPORTED, UNVERIFIABLE|
|destination|Nonempty literal destination label, maximum120 characters|
|hotel|Nonempty explicit hotel name, maximum200 characters; never an ID|
|arrival, departure|Explicit ISO civil dates; valid calendar dates, no invented year/checkout|
|adults|Integer1–40, explicitly adult guests|
|children|Integer0–40; explicit zero is retained|
|rooms|Integer1–20|
|stars|Distinct integer star ratings1–5, at most5 entries; existing typed validator retained|
|price|Closed object described below|
|remove|Distinct allowlisted DESTINATION, HOTEL, PRICE, STARS; at most4|
|issues|Distinct semantic ambiguity/invalidity enums below; at most14|
|reference|Exact complete utterance,1–200 characters; only SELECTED/COMPARISON|

No internal IDs, provenance, confirmation, tool proposals, headers, URLs/capabilities or actual hotel facts are representable as privileged fields. Any literal label still passes the existing independent validators and security preflight. Search/discovery normally maps to SEARCH; explicit exploration can use BROWSE; changing/keeping/removing filters uses REFINE. These are proposal semantics, not tool authorization.

## Price meaning and translation

`price` requires `kind`, `operator`, `amount`, `unit`. Optional `upper` is present exactly for RANGE. Amounts are exact nonnegative JSON numbers subject to existing typed validation and numeric/response bounds; the compiler does not guess units, round, swap or repair them.

|Property|Enum/meaning|
|---|---|
|kind|REFERENCE or STAY|
|operator|LT, LTE, GT, GTE, RANGE|
|unit|VND or VND_PER_ROOM_NIGHT|
|amount|Explicit VND amount; model converts explicit triệu/nghìn into VND|
|upper|Explicit upper VND bound for RANGE|

|Supported semantic combination|Canonical compiled criteria|
|---|---|
|REFERENCE + LT, no arrival/departure|`maxPrice REPLACE amount`, `referencePriceExclusive REPLACE true`|
|LTE + explicit nightly unit|`maxPrice REPLACE amount`, no exclusive flag|
|GTE + explicit nightly unit|`minPrice REPLACE amount`|
|RANGE + explicit nightly unit|`minPrice REPLACE amount`, `maxPrice REPLACE upper`|
|GT; dated LT; STAY LT; inclusive/range without nightly unit|Explicit compiler rejection `Unsupported price semantics`; never inclusive conversion|

Operator vocabulary includes GT so an unsupported meaning can be recorded distinctly; it is **not** an implemented strict-lower-bound business filter. Invalid/reversed ranges are rejected by existing typed criteria validation. A model can instead propose CLARIFY with an appropriate issue. Compiler rejection does not silently become a successful clarification or semantic PASS.

Actual implemented example:

```json
{"intent":"SEARCH","destination":"Nha Trang","price":{"kind":"REFERENCE","operator":"LT","amount":2000000,"unit":"VND"}}
```

Compiles to:

```json
{"action":"CRITERIA","intent":"SEARCH","fields":{"cityName":{"operation":"REPLACE","value":"Nha Trang"},"maxPrice":{"operation":"REPLACE","value":2000000},"referencePriceExclusive":{"operation":"REPLACE","value":true}},"reference":"","missingSlots":[],"inferredFields":[]}
```

This is the contract B translation defined before any B dispatch, not a post-hoc repair of A outputs. The local runner keeps raw proposal and compiled extraction separately. Wrong LT/LTE or amount remains wrong and must fail independent corroboration; the compiler never replaces it using the oracle/parser.

## No-change, removal and ambiguity

`{"intent":"SEARCH","destination":"Huế"}` only replaces cityName. No absent field becomes CLEAR. `{"intent":"REFINE","remove":["STARS"]}` produces only `starRatings CLEAR`; PRICE produces both minPrice/maxPrice CLEAR. DESTINATION and HOTEL remove only their corresponding literal fields. A replacement and removal of the same field conflict and fail. Actual user removal evidence is still required by the independent parser.

CLARIFY/UNSUPPORTED/UNVERIFIABLE cannot carry mutations. Semantic issues require CLARIFY, rather than partially applying otherwise valid criteria. Issue-to-slot translation is explicit:

|Semantic issue|Compiled missing slots|
|---|---|
|DESTINATION_UNRESOLVED|cityName|
|ARRIVAL_MISSING / DEPARTURE_MISSING|checkIn / checkOut|
|DATE_INVALID|checkIn, checkOut|
|DATE_ORDER_INVALID|None; generic safe clarification|
|GUESTS_AMBIGUOUS|adults, children, rooms|
|ADULTS_INVALID / CHILDREN_INVALID / ROOMS_INVALID|Corresponding occupancy field|
|PRICE_RANGE_INVALID|minPrice, maxPrice|
|PRICE_UNIT_MISSING|maxPrice|
|PRICE_INVALID / UNSUPPORTED_FILTER / OTHER|None; safe clarification without a fabricated filter|

For non-refinement CRITERIA with dates or occupancy, the backend computes missing destination/date/occupancy members from the supplied semantic fields. Destination-only discovery needs none. A single date under CRITERIA is rejected: the model must explicitly propose clarification. No date/occupancy defaults or history are supplied. REFINE does not invent missing context or resolve existing state.

Raw model `missingSlots` accuracy is **NOT_APPLICABLE** because B has no such property. Model semantic-issue correctness remains a model responsibility; the compiler only maps issues and derives missing prerequisites. Compiled business missing-slot accuracy is a separate applicable metric. Compiler-produced slots cannot make an incorrect model ambiguity claim count as a raw model success.

## Independent evidence and authority

Pipeline: Gemini proposal → strict semantic validation → pure Java translation → existing typed extraction validation → existing independent `GeminiExtractionSmoke.corroborate`/`VietnameseParser`/reference routing → admission observation. Translation itself grants no authority. Corroboration runs before any state/tool application; this standalone runner has neither state application nor domain clients.

Existing complete-field equality, exact operations, exact numeric comparison, literal destination mapping, finite reference grammar, date/occupancy validators and rejection of unconsumed/ambiguous constraints remain in force. The compiler does not copy parser values into model proposals, drop unsupported constraints or self-corroborate using model text. Parsing a compiled proposal retains MODEL_PROPOSED provenance; it cannot mint USER_PROVIDED, USER_CONFIRMED or trusted selection/occupancy events. PolicyExecutor, GroundingGate and SearchCriteriaState remain unchanged.

Important observed limit: the existing corroborator does not validate every oracle missing-slot/action distinction. Baseline A had three semantically incorrect admitted proposals and two correct rejected proposals. Preserving that boundary is not a claim of perfect semantic admission. B shares those limitations until separately fixed and measured; unsupported phrases are not accepted by loosening equality.

SELECTED/COMPARISON compile to existing corresponding action, intent CLARIFY, empty fields and exact reference. No target IDs or trusted references are minted. Actual reference resolution, conversation state, authenticated ordered ingress and actual READ execution remain **NOT_RUN**. Context-dependent dimensions are not scored as successful context understanding.

## Bounds and verification

Raw proposal≤8192 UTF-8 bytes, nesting≤6, strings≤200 (destination≤120), numeric lexeme≤24 characters; unknown types/enums/fields, duplicate keys and trailing tokens fail. Arrays and removals are bounded/unique. Existing typed validation additionally applies future civil-date/stay/occupancy/filter/price constraints under the supplied clock. No automatic JSON repair or numeric coercion exists. Provider envelope≤64KiB and output cap1024 tokens remain unchanged.

[Offline compiler evidence](semantic-extraction-fix-v1/compiler-verification-v1.json) covers equivalent proposals for all40 original canonical outcomes, **not40 live Gemini responses**. Focused tests additionally cover inclusive/range prices, explicit dates/occupancy, refinement, removal/no-change, clarification, selected references, unsupported/unverifiable outcomes, wrong operator/amount/unit, invented facts, ambiguous destinations, fabricated IDs/provenance/tools, invalid bounds and malformed JSON. Local fake-SDK integration records raw schema/compiled/backend layers; all automated tests remain offline.

[Eight diagnostic plans](semantic-extraction-fix-v1/simplified-diagnostics-v1.json) and [full40 request plan](semantic-extraction-fix-v1/simplified-request-plan-v1.json) are frozen offline artifacts only. They provide no raw model accuracy, latency, token or A/B evidence. Promotion requires a future bounded paired live comparison and targeted corroboration fixes, not the existence of this implementation.
