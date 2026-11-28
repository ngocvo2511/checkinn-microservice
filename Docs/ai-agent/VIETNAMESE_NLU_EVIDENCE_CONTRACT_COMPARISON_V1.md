# Vietnamese NLU evidence-contract comparison v1

24/11/2026. **EVIDENCE_FIRST_EVALUATION_PARTIAL**. A/B figures below are frozen historical evidence or offline replay, never new model calls in this batch.

|Metric / planned40|A v3 historical|B v1 historical|B v1 improved backend replay|C r2 frozen40|C r2 fresh20|
|---|---:|---:|---:|---|---|
|HTTP200|37|40|40|NOT_RUN|NOT_RUN|
|Raw schema valid|32|40|40|NOT_RUN|NOT_RUN|
|Canonical typed valid|32|26|26|NOT_RUN|NOT_RUN|
|Exact business semantics|12|22|22|NOT_RUN|NOT_RUN|
|Correct accepted|9|18|22|NOT_RUN|NOT_RUN|
|Correct rejected (false rejection)|3|4|0|NOT_RUN|NOT_RUN|
|Incorrect accepted|0|0|0|NOT_RUN|NOT_RUN|
|Incorrect rejected|20|4|4|NOT_RUN|NOT_RUN|
|Compiler rejections|N/A|14|14|NOT_RUN|NOT_RUN|
|Backend NOT_EVALUATED|8|14|14|NOT_RUN|NOT_RUN|

The four B1 admission repairs016/041/042/048 are preserved. Raw22 and typed26 did not improve in replay; only backend acceptance changed. No replay is called Gemini improvement. [Current actual-response replay](evidence-extraction-v1/b1-actual-response-replay-v1.json).

## Diagnostic revisions are not primary cohorts

Historical B2 revision1:10 actual attempts,8 exact, originally5 correct admissions,8 after backend replay. Historical B2 revision2:5 attempts,2 exact/2 admitted,2 compiler rejections,1 timeout; remaining5 NOT_RUN. They are not merged into one frozen cohort and do not establish80% overall accuracy.

C revision1 diagnostics:8 actual attempts,8 raw-schema valid,8 request meanings correct,7 complete/exact/correct admissions; one evidence-type/completeness rejection. Precision19/21, recall19/20; missing witness1, wrong-type fragments2, nonmember/invented0, duplicates/overlap0. Date/occupancy/unsupported/removal retention are recorded separately by type. Backend matrix7 correct accepted/0 correct rejected/0 incorrect accepted/0 incorrect rejected/1 upstream NOT_EVALUATED; correct admission7/8 overall, precision7/7. Rejecting the incomplete selected expression was safe and does not make its model proposal correct.

C revision2 reference checks:2/2 raw/complete/exact/correct admissions, precision2/2 and recall2/2. One is a labeled retest, one a predeclared generalized control. They do not replace revision1's failure or supply a frozen40 denominator. The prompt changed at that explicit boundary; the validator did not loosen. Frozen comparison/frozen40/fresh20 candidate thereafter stayed unchanged.

The fixed-model comparison received6 correct proposals from10 total attempts;4 technical failures have semantic NOT_EVALUATED. No result from another model substituted for them. [All phase metrics](evidence-extraction-v1/phase-metrics-v1.json).

## Raw, canonical and admission metrics

Raw membership/type/precision/recall/completeness and request meaning are model dimensions. Normalized dates/amounts/destination, canonical action/intent, exclusivity, missing slots and mutation operations are Java/compiler dimensions. No model credit is awarded for derived values. Canonical validity, action, intent, criteria, missing-slot accuracy, exact semantics, compiler/evidence/completeness rejections and backend confusion matrices are separate fields in the observation/metric artifacts.

Overall rates retain every attempted provider/evidence/compiler failure. Backend classification uses typed-valid proposals and explicitly retains upstream NOT_EVALUATED. False-accept/false-reject rates with denominator0 are NOT_APPLICABLE, not evidence of measured0% risk. Admission precision uses all admitted typed proposals; end-to-end correct admission uses all attempted primary logical samples. Full matching and denominator definitions were frozen in [scoring plan](evidence-extraction-v1/scoring-and-campaign-plan-v1.json).

## Paired outcomes

FAIL→PASS0, PASS→FAIL0, PASS→PASS0, FAIL→FAIL0, **NOT_COMPARABLE40**. All original40 identities are listed in [paired outcomes](evidence-extraction-v1/paired-comparisons-v1.json); C primary observations are absent. No new same-model primary B1/C improvement can be estimated. Diagnostic retention changes are a representation/prompt signal, not a causal model capability improvement. Historical compiler/corroboration fixes, current evidence representation, model selection and provider availability effects remain separate.

## Parser-only comparison and targets

Offline deterministic comparator: **40/40 frozen and20/20 fresh canonical exact**,0 HTTP/key reads. It reuses supported parser/corroboration and local business derivation; this is not model accuracy or live full-stack acceptance. These development cases demonstrate no incremental case coverage from an LLM. [Per-case comparator](evidence-extraction-v1/parser-only-comparator-v1.json).

Frozen40 required≥38 exact, raw schema40/40; fresh20 required≥19 exact. Evidence completeness/request/canonical dimensions≥95%, reference-price100%, incorrect/unsafe/invented-evidence acceptance0 were development targets. Primary/fresh targets are **NOT_ASSESSED** because0/40 and0/20 dispatched. No production readiness or broad generalization claim follows from diagnostics or offline tests. Architecture decision **MORE_EVIDENCE_REQUIRED**; deterministic-first/hybrid responsibility review is a bounded future proposal, with incremental LLM value still to demonstrate. Do not start context-aware runtime migration here.
