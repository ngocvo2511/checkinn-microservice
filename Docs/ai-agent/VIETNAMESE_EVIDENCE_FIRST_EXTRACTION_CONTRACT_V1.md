# Vietnamese evidence-first extraction v1

24/11/2026. Experimental **local development evaluation** contract `vietnamese-evidence-v1`. Ordinary Agent remains OFF/FAKE/liveOFF. This document specifies the candidate before its first live response; actual outcomes are in the main report.

Revision history: revision1 is retained in its pre-live snapshot and eight diagnostic observations. Revision2 (`candidate-r2-reference-phrase`) added a prompt instruction to retain the target noun plus its qualifier in REFERENCE and to require a number for ROOMS. It did not relax the schema or validator. Its two diagnostic checks and the later fixed-model comparison are separate cohorts. Comparison/frozen40/fresh20 candidate sources and wire digests were then frozen; the provider-limited final outcome is [reported separately](BATCH10B4D_EXTRACTION_EVIDENCE_FIRST_AND_MODEL_QUALITY_REPORT.md).

## Structure and request vocabulary

One structured call returns exactly `{ "request": "FIND_HOTEL", "facts": [{ "type": "DESTINATION", "text": "Đà Lạt" }] }`. There are no normalized values, business actions, missing slots, mutation operations, IDs, provenance or tool arguments in the model contract.

Requests: FIND_HOTEL, EXPLORE_HOTEL, CHANGE_CRITERIA, ASK_SELECTED, COMPARE, UNSUPPORTED, UNCERTAIN. Request meaning describes the utterance, including a search containing invalid facts; Java independently decides whether it must clarify. Missing destination/counts do not change a search into a model-decided clarification.

Evidence types: DESTINATION, HOTEL, STAY_DATES, ADULTS, CHILDREN, ROOMS, PRICE, STARS, REMOVAL, REFERENCE, UNSUPPORTED, AMBIGUOUS. Facts are unordered. A stay interval can be one STAY_DATES clause; explicitly labeled single endpoints must retain their role words. A price clause must retain its operator, reference qualifier, every bound and explicit units. Generic people remain AMBIGUOUS. Invalid calendar dates and unsupported constraints remain evidence.

## Membership, meaning and completeness

Every submitted text must be an **exact original substring**. No model offsets or rewritten source are trusted. Case/accent/whitespace normalization is used only to compare independently discovered lexical witnesses after exact membership has passed. Duplicate keys, extra fields, nulls, trailing JSON, more than24 facts, strings over200 characters, control/format/unpaired surrogate characters, nesting over5 and response payloads over8192 bytes reject locally. Provider responses remain bounded separately to64KiB.

Independent witnesses come from bounded lexical clauses and the existing deterministic request recognizer, without consulting the proposal or business oracle. They distinguish adults/children/rooms, date roles, strict/reference prices, complete removal clauses, selected expressions and negated unsupported constraints. Known witnesses are removed before unconsumed meaningful tokens are counted as unsupported evidence; a finite grammar filler vocabulary is not an unlimited natural-language recognizer.

A fact must cover at least one complete witness of its stated type and cannot swallow a witness of another type. Invented text, wrong types, overlapping/duplicate facts and omitted witnesses reject the entire proposal. Expected ordering is immaterial. One fact may group same-type witnesses; splitting a required meaningful clause so that no fact retains its complete operator/role/unit is not equivalent. A reference may quote the target expression rather than the entire question; Java binds the canonical reference to the original utterance.

Membership alone is not semantic correctness. For example `2 trẻ em` under ADULTS, a checkout date without `trả phòng`, a negated pool constraint without its negation, or a nightly price without its explicit unit fail independent verification. Unsupported trailing words cannot disappear behind a valid destination. Absent evidence never means CLEAR.

## Normalization and canonical compilation

The implementation deliberately reuses the existing parser over the original source **only after complete typed evidence verification**. It does not recover omitted facts or repair a rejected proposal. This is a parser-gated evidence experiment, not proof of a general semantic compiler independent of deterministic grammar. The separately measured parser-only comparator uses that same deterministic derivation without Gemini and receives no model-quality credit.

Java preserves existing civil-date/year/calendar checks, reviewed destination labels, Vietnamese numbers, explicit occupancy including zero children, price units/operators, removal rules and canonical field operations. Missing business prerequisites are derived locally. Invalid/reversed dates, ranges, counts, ambiguous people and unsupported criteria clarify atomically without a state patch. The resulting closed canonical proposal is validated by `VietnameseExtraction.parse` and the existing quality corroboration boundary. No original business oracle is read by the compiler.

Examples:

- Full stay: destination + complete dates + adults + explicit zero children + rooms compile together. Dropping any fact rejects before compilation.
- Missing destination: dates and occupancy compile with missing cityName; no city or budget is invented.
- `giá tham khảo dưới hai triệu`: complete PRICE evidence normalizes to maxPrice2000000 plus Java-derived referencePriceExclusive=true. That flag is not model credit.
- `Bỏ giới hạn giá đi`: explicit REMOVAL compiles only minPrice/maxPrice CLEAR. Other fields are untouched.
- `Phòng này còn không?`: ASK_SELECTED + REFERENCE `Phòng này`; canonical SELECTED recognition only. Trusted target resolution and tool execution are NOT_RUN.
- `31/02/2026`, generic people and reversed prices are retained, never repaired into valid criteria.

## Provider compatibility and authority

The existing SDK, model profiles, model-specific schema adaptation, fixed Google endpoint, output cap1024, no automatic retries and bounded transport are reused. Provider schema simplification on2.5 does not relax the strict local validator. Prompt/schema and per-model serialized wire digests are frozen separately. Model thinking defaults may differ; unreported thinking is NOT_REPORTED, never zero by assumption.

No Spring bean, runtime route, tool capability, state update, trusted occupancy confirmation, provenance grant or catalog ID resolution is introduced. PolicyExecutor, GroundingGate, SearchCriteriaState, existing A01–A10 and ordinary runtime budgets remain unchanged. Original A/B prompts, schemas, wire identities and sealed observations remain historical evidence.

## Known limitations

Witness coverage is bounded by reviewed grammar, and can reject otherwise reasonable evidence spans/paraphrases. Repeated identical substrings have no model-supplied offsets; overlapping ambiguous occurrences fail closed. Exotic number/date/place formulations and unknown destinations can expose H5 coverage gaps. The frozen/fresh development data already has substantial parser coverage; good canonical outcomes therefore cannot establish incremental LLM value. Context resolution, live inventory, tools and full Agent E2E are outside this experiment.
