# Vietnamese NLU contract B quality comparison v3

24/11/2026. **LIVE_TECHNICAL_BLOCKED**. Main comparison: B v1→B v2. A v3 is a frozen comparator; no new A40 campaign was run.

|Metric|A v3 frozen /40|B v1 frozen /40|B v1 backend replay /40|B v2 frozen /40|B v2 fresh /20|
|---|---:|---:|---:|---:|---:|
|HTTP200|37|40|40|NOT_RUN|NOT_RUN|
|Raw schema valid|32|40|40|NOT_RUN|NOT_RUN|
|Raw exact meaning|N/A|22|22|NOT_RUN|NOT_RUN|
|Canonical typed valid|32|26|26|NOT_RUN|NOT_RUN|
|Canonical action|20|24|24|NOT_RUN|NOT_RUN|
|Canonical intent|16|24|24|NOT_RUN|NOT_RUN|
|Canonical criteria|20|24|24|NOT_RUN|NOT_RUN|
|Business missing slots|27|26|26|NOT_RUN|NOT_RUN|
|Exact business semantics|12|22|22|NOT_RUN|NOT_RUN|
|Correct accepted|9|18|22|NOT_RUN|NOT_RUN|
|Correct rejected|3|4|0|NOT_RUN|NOT_RUN|
|Incorrect accepted|0|0|0|NOT_RUN|NOT_RUN|
|Incorrect rejected|20|4|4|NOT_RUN|NOT_RUN|
|Backend NOT_EVALUATED|8|14|14|NOT_RUN|NOT_RUN|
|Compiler rejections|N/A|14|14|NOT_RUN|NOT_RUN|


Raw action/intent/criteria/exact meaning, date/occupancy retention, destination, price operator/value, reference/removal/unsupported recognition, schema/compiler, derived missing slots and backend confusion matrices are independently recorded in [frozen40](semantic-quality-v2/frozen40-observations-v2.json) and [fresh20](semantic-quality-v2/fresh-observations-v2.json). The raw scorer is the unchanged original strict oracle translation; it does not call the compiler to award model facts. A value normalized by Java is canonical credit, not retroactive raw spelling credit. Missing-slot accuracy is a Java/compiler dimension for B. No normalized-equivalence score replaces frozen primary scoring.

## Paired outcomes

- FAIL -> PASS: 0 — 
- PASS -> FAIL: 0 — 
- PASS -> PASS: 0 — 
- FAIL -> FAIL: 0 — 
- NOT_COMPARABLE: 40 — VNE1-001, VNE1-002, VNE1-003, VNE1-004, VNE1-005, VNE1-006, VNE1-007, VNE1-008, VNE1-009, VNE1-010, VNE1-011, VNE1-012, VNE1-013, VNE1-014, VNE1-015, VNE1-016, VNE1-017, VNE1-020, VNE1-021, VNE1-023, VNE1-025, VNE1-027, VNE1-028, VNE1-029, VNE1-030, VNE1-031, VNE1-032, VNE1-033, VNE1-037, VNE1-039, VNE1-040, VNE1-041, VNE1-042, VNE1-043, VNE1-046, VNE1-047, VNE1-048, VNE1-049, VNE1-050, VNE1-051

Only same actual model/sample/oracle and successful provider observations are comparable. Failed provider attempts remain failures/NOT_COMPARABLE rather than being replaced by fallback or a better retest. Primary and fallback quality cohorts remain separate; see [per-model metrics](semantic-quality-v2/model-specific-metrics-v2.json). Diagnostics revisions are developmental overlapping subsets, not extra independent primary samples. Optional later candidate targeted retest: NOT_RUN.

## Backend decisions and denominators

B v2 frozen backend: {
  "correctAccepted": 0,
  "correctRejected": 0,
  "incorrectAccepted": 0,
  "incorrectRejected": 0,
  "backendNotEvaluated": 0,
  "falseAcceptanceRate": null,
  "falseRejectionRate": null,
  "backendClassificationAccuracy": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  },
  "correctAdmissionPrecision": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  },
  "endToEndCorrectAdmission": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  }
}.

Fresh backend: {
  "correctAccepted": 0,
  "correctRejected": 0,
  "incorrectAccepted": 0,
  "incorrectRejected": 0,
  "backendNotEvaluated": 0,
  "falseAcceptanceRate": null,
  "falseRejectionRate": null,
  "backendClassificationAccuracy": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  },
  "correctAdmissionPrecision": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  },
  "endToEndCorrectAdmission": {
    "numerator": 0,
    "denominator": 0,
    "rate": null
  }
}.

Here correct/incorrect labels describe proposal semantics; “correct rejected” is a false rejection, while “incorrect rejected” is a correct backend decision. Classification accuracy=(correct accepted+incorrect rejected)/typed evaluated. Admission precision=correct accepted/all accepted; end-to-end correct admission=correct accepted/all attempted. Compiler/provider failures remain in overall attempted denominators and separately NOT_EVALUATED for backend classification. Rates with zero applicable denominator are null/N/A, never fabricated zero success.

## Performance, fallback and targets

Frozen attempt latency: {
  "n": 0,
  "mean": null,
  "median": null,
  "p95": null,
  "maximum": null,
  "percentileMethod": "nearest rank"
}; fresh: {
  "n": 0,
  "mean": null,
  "median": null,
  "p95": null,
  "maximum": null,
  "percentileMethod": "nearest rank"
}. Logical distributions and mean known tokens are in the same machine metrics. Provider-only latency is not separately recorded. Thinking absence is NOT_REPORTED. [Budget/usage](semantic-quality-v2/budget-accounting-v2.json), [fallback](semantic-quality-v2/fallback-metrics-v2.json). Baseline B v1 median1175.5ms and mean known total478.35 tokens/request are historical descriptive values, not a controlled latency experiment. New prompt/schema size and run timing differ.

Targets: frozen raw40/40, typed/action/intent/criteria/missing/exact≥38/40, reference-price3/3, spurious CLEAR0, incorrect/unsafe/confirmation acceptance0; fresh exact≥19/20. Primary/fresh target assessment: **NOT_ASSESSED** after controlled stop. These are development targets, never grounds for oracle changes or dropping failed attempts. The20 fresh samples were frozen before any new response, use the same candidate/clock and are FRESH_DEVELOPMENT_GENERALIZATION, not external/blinded evaluation.

Retain B v2 as a local development candidate. Do not promote or begin context-aware runtime implementation while critical single-utterance failures remain. Next work should target the actual remaining semantic failures below, particularly full explicit-fact retention or action consistency when observed. The flat optional contract still permits contradictory combinations; consider a small action-specific serving representation if failures persist, rather than expanding the independent grammar without bound. No authorization/infrastructure batch is recommended.

Ordinary runtime remains OFF/FAKE/liveOFF; v2 is not promoted. No domain/Gateway/Supabase/payment/refund calls, WRITE tools, database/schema/frontend/dependency changes, commits/staging/push/reset/checkout. Recognition is evaluated; trusted target resolution, tool execution and FULL_AGENT_E2E are NOT_RUN. D07/D08 remain unaccepted; E30 remains NOT_RUN without authenticated ordered ingress. No production readiness claim.
