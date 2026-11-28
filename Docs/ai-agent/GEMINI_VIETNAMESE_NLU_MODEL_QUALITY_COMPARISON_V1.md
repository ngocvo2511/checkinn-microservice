# Gemini Vietnamese NLU model comparison v1

24/11/2026. **PARTIAL; NO MODEL WINNER**. Frozen candidate: `vietnamese-evidence-v1`, `candidate-r2-reference-phrase`. Ordinary runtime unchanged.

## Shared design and serving differences

Predeclared identities: VNE1-002/003/004/007/011/013/029/041. Coverage: strict reference price, full stay, missing destination, explicit occupancy, unsupported pool, selected room, removal, natural discovery. Original business oracle and clock `2026-11-24T10:18:48.242851600Z` were shared. No fresh20 model-selection data or responses were used.

Same semantic contract, prompt meaning, output budget1024, compiler, corroboration and scoring; one call per fixed-model sample, retries0, **fallback disabled**. Exact profiles: gemini-3.5-flash-lite, gemini-3.1-flash-lite, gemini-2.5-flash-lite, gemini-2.5-flash.3.5/3.1 retain the original SDK serving schema;2.5 uses the existing bounded serving adaptation (including equivalent const/enums and removal of serving hints), while strict local validation remains unchanged. No model received a larger output/thinking allowance. No explicit thinking or sampling configuration was sent; provider defaults may differ. Model-specific wire digests are frozen in the plan; this is semantic-contract control, not identical-wire equality across all model families.

## Actual outcomes

|Requested exact model|Planned|Attempted|HTTP200|Raw exact|Canonical exact|Correct admitted|NOT_RUN|
|---|---:|---:|---:|---:|---:|---:|---:|
|gemini-3.5-flash-lite|8|8|6|6|6|6|0|
|gemini-3.1-flash-lite|8|2|0|0|0|0|6|
|gemini-2.5-flash-lite|8|0|0|0|0|0|8|
|gemini-2.5-flash|8|0|0|0|0|0|8|

Actual reported identity for the6 successful comparison envelopes: `gemini-3.5-flash-lite`. The3.1 attempts returned no response/modelVersion, so reported identity is NOT_REPORTED; requested identity is recorded, not substituted for provider observation. Both2.5 serving paths remain live NOT_RUN in this batch. Prior-batch compatibility evidence is historical, not a new comparison result.

Primary3.5 evidence precision13/13; observed retention13/19 expected witness units over **all8 attempted** samples. Its503/timeout supply no evidence and remain in the overall denominator. Conditional on the6 received valid proposals, explicit evidence/request/canonical outcomes were correct; no hallucinated/duplicate/contradictory evidence or incorrect acceptance was observed in those6. This conditional100% is not overall model accuracy. Canonical action/intent/criteria/missing-slot correctness:6/8 overall, with2 provider NOT_EVALUATED. Backend matrix:6 correct accepted,0 correct rejected,0 incorrect accepted,0 incorrect rejected,2 NOT_EVALUATED. Overall correct admission6/8; admission precision6/6. False acceptance among incorrect typed proposals has denominator0 and is NOT_APPLICABLE.

3.1 has0/2 observed correct admissions and2/2 technical failures; **semantic/model quality NOT_EVALUATED**, not proof it misunderstands Vietnamese. Its remaining6 and all16 planned2.5 observations are NOT_RUN. Retention by date/occupancy/reference/removal and every metric denominator are in [model metrics](evidence-extraction-v1/model-specific-metrics-v1.json). No failed observation was replaced by another model.

## Performance, tokens and availability

Comparison3.5 attempt latency mean5286.375ms/median4885.5ms/p95=max10007ms; logical mean6155.375ms/median6621ms/p95=max12387ms. Known tokens2597 input +417 candidate output =3014;2 UNKNOWN usage attempts.3.1 attempt mean/median10007ms, p95=max10008ms; logical mean11327.5ms, max12645ms; both usages UNKNOWN. No actual token total can be claimed for them. Thinking NOT_REPORTED; provider-only/SDK/cleanup latency NOT_SEPARATELY_RECORDED. No2.5 latency/usage observations.

ATT-013 returned locally terminated503 High Demand; fixed-model design intentionally prevented fallback. ATT-017/020/021 were locally terminated timeouts; no timeout fallback. ATT-020/021 were consecutive and sealed the campaign. All4 actual technical failures count, even though later distinct3.5 samples succeeded. The runner's capacity-selector counter0 describes no selector transition; actual HTTP evidence contains1 High Demand503. [Attempts](evidence-extraction-v1/actual-attempts-v1.json), [fallback/technical failures](evidence-extraction-v1/fallback-metrics-v1.json).

Quota limits were developer snapshots, not guarantees or account-wide proof. Retained same-day prior counts106/1/5/1; new whole-batch counts19/2/0/0. No quota/billing/model probe or key change. Stop was the transient failure threshold, not observed429. Planned8/model is already small; actual imbalance and missing responses prevent comparative superiority or a four-model quality conclusion.

## Decision

Retain the predeclared `gemini-3.5-flash-lite` development default; no new evaluation cohort could dispatch after the mandatory stop. Do not select a winner, promote C, change ordinary runtime primary or interpret a model name as quality evidence. Architecture recommendation **MORE_EVIDENCE_REQUIRED**. Plans, raw/schema/canonical/backend/latency/token dimensions and exact actual identities remain separately available in [comparison observations](evidence-extraction-v1/model-comparison-observations-v1.json).
