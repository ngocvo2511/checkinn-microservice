# Vietnamese semantic corroboration fix v1

24/11/2026. **SEMANTIC_CORROBORATION_FIX_DONE** for the observed historical defects. This status describes backend admission of the same retained model responses; it is not a model-accuracy improvement or production readiness claim.

## Actual response replay

Original source: [immutable baseline observations](semantic-extraction-fix-v1/baseline40-observations-v1.json), backed by retained synthetic provider envelopes in `ai-agent-service/.local/batch10b4d-fix/baseline40-v1.json.responses/`. The new replay command reads only retained response text, original utterance and original evaluation clock (`2026-11-24T08:20:13.585702100Z`). Original expected extraction and model correctness classification remain frozen. No provider, credential, domain or database calls.

All20 schema-valid responses were replayed before and after the fix. Three retained invalid extractions were also replayed and remain strictly rejected. Two historical provider503 failures have no extraction text and remain REPLAY_NOT_AVAILABLE; they are not fabricated. `GeminiHistoricalReplay` labels actual response text versus structured-response fallback and records source/response hashes. Historical files were not edited.

|Same20 schema-valid proposals|Old corroborator|Improved corroborator|
|---|---:|---:|
|Correct accepted|5|7|
|Correct rejected|2|0|
|Incorrect accepted|3|0|
|Incorrect rejected|10|13|
|False acceptance / incorrect|3/13 (23.08%)|0/13 (0%)|
|False rejection / correct|2/7 (28.57%)|0/7 (0%)|
|Backend classification accuracy|15/20 (75%)|20/20 (100%)|

Evidence: [before](semantic-corroboration-final-v1/replay-before-v1.json), [after](semantic-corroboration-final-v1/replay-after-v1.json), [machine matrix](semantic-corroboration-final-v1/backend-before-after-matrix-v1.json). These finite replay results do not establish zero error on arbitrary Vietnamese requests.

## Changed decisions and independent rules

* **VNE1-005**, incorrect accepted→incorrect rejected: independent literal dates contain exactly one endpoint. A single-utterance proposal must request CLARIFY/CLARIFY with precisely the missing opposite endpoint and no mutation. A CRITERIA proposal retaining incomplete stay fields cannot be admitted as a complete semantic action. Valid date evidence remains in the independent parser; no missing date/current-date default is invented or committed from the rejected model proposal. Destination-only D1 stays valid.
* **VNE1-006**, incorrect accepted→incorrect rejected: when literal dates/occupancy establish a stay request, Java independently derives absent destination/date/occupancy slots from literal fields. Model `missingSlots=[]` is insufficient. No trusted conversation state is assumed. Explicit zero children remains evidence; an omitted count stays missing. REFINE is not automatically converted into a new stay search.
* **VNE1-015**, incorrect accepted→incorrect rejected: independent selected-room/reference grammar requires SELECTED, exact whole original reference, empty criteria and no missing slots. Generic CLARIFY or UNSUPPORTED cannot replace that selected-room meaning. Comparison and ordinary criteria routes remain distinct. Extraction recognition never resolves an ordinal, creates an ID or calls a READ tool.
* **VNE1-027**, correct rejected→correct accepted: bounded keep/filter phrases (`chỉ giữ`, `giữ lại`, `chỉ lọc`) with explicit star ratings now indicate REFINE and consume their refinement wording. The proposed field set, operation and exact star value must still match all independent literal evidence.
* **VNE1-028**, correct rejected→correct accepted: destination replacement recognizes `đổi/chuyển điểm đến sang` and `tìm ở ... thay vì ...`. The prior destination is either the explicit phrase `điểm đến trước` or one exact reviewed destination label. Arbitrary discarded suffixes, unknown cities and multiple/conflicting criteria cannot be accepted by stripping text. Only reviewed destination evidence can corroborate replacement.

The original5 correct admissions and10 incorrect rejections are unchanged. All five changed decisions follow general rules; production source contains no VNE1 IDs, oracle reads or fixture-output lookup. Test/evaluation planners legitimately retain fixture IDs to reproduce and score observations.

## Validation dimensions and authority

`VietnameseExtraction.corroborate` now checks action, intent, independently required missing slots and reference route before existing exact full-field/operation/value checks. CLARIFY requires independent ambiguity/partial-date evidence; it cannot be used as an unconditional escape hatch. Unsupported/unverifiable parser intent cannot be silently relabeled. Inferred fields, unbound references, unrelated CLEAR, extra/omitted criteria, invented dates/occupancy and mismapped destinations still reject atomically. Proposal output is never used to corroborate itself.

The parser remains a conservative evidence grammar, not a replacement semantic model. Targeted changes identify explicit numeric/star/destination/refinement facts. Existing unknown/unconsumed constraints still fail closed. Reference candidate recognition is extraction evidence only; selected target resolution remains controlled by existing issued references and policy. No new domain routes, IDs, provenance claims, tools or conversation state are created.

Business missing slots are independent of raw B model schema: the semantic compiler may derive them, but the corroborator verifies the resulting canonical proposal against independent literal evidence. A model cannot label its own inferred facts USER_CONFIRMED. Existing SearchCriteriaState/PolicyExecutor/GroundingGate and READ-only A01–A10 remain unchanged.

## Negative/generalized regression

Retained actual008/009/010/011/025 protect against generic people→adults, invalid-date repair, reversed-price-range swapping, unsupported-pool dropping and omitted explicit adults/invalid exclusive flags.002/020 protect strict-price operator and unrelated star CLEAR. Invalid014 protects reference intent typing. The actual response test asserts7 correct admissions,13 incorrect rejections and3 typed validation rejections without changing original model classification.

Generalized tests cover keep/filter synonyms, destination replacement/known prior city, both single-date endpoints, incomplete/missing slots, generic people, selected-room/ordinal references, unknown destination and an unconsumed replacement suffix. Wrong star/destination and unjustified clarification remain rejected. The existing orchestration fixture that omitted all missing slots was updated to propose the independently required metadata; original NEED_DESTINATION/state/budget assertions remain unchanged.

## Remaining limits

This is single-utterance corroboration. Relative room increments, relative price changes and ambiguous deictic targets require trusted state and remain unsupported without it. Broader colloquial discovery wording, invalid-date clarification and unsupported/invalid quantity explanations may still conservatively reject correct proposals. No blanket admission or silent model repair was added to maximize replay/live scores. Full new live observations and any additional false rejection are reported separately in the [main report](BATCH10B4D_FINAL_SEMANTIC_CORROBORATION_AND_MODEL_FALLBACK_REPORT.md).

Historical E01–E40 stays23 PASS/16 FAIL/0 NOT_APPLICABLE/1 NOT_RUN in the full offline test; no historical expected result/manifest was rewritten. E30 remains NOT_RUN. D07/D08 and FULL_AGENT_E2E remain unaccepted/NOT_RUN.
