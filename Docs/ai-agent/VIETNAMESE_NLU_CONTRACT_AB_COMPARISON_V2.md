# Vietnamese NLU contract A/B comparison v2

24/11/2026. **PARTIAL: 37/40 same-model comparable primary pairs.** Original A identity coverage40/40, B40/40 actually attempted. Architecture decision: **SIMPLIFIED_CONTRACT_PROMISING_BUT_INCONCLUSIVE**. Neither contract meets the95% quality targets; do not promote B or start context-aware runtime integration on these results alone.

## Frozen contracts and scoring

Actual primary quality model is **gemini-3.5-flash-lite** for both original40 first attempts. A reuses immutable25 historical first attempts and adds15 remaining original identities. For VNE1-051 the first new A attempt is the primary503; fallback3.1's503 and2.5 Lite's400 are separate availability attempts, not substitute A first-attempt quality. Original029/030503 are unchanged even when later deliberate retests return200. There is no mixed-model pooling in the primary table.

A remains `vietnamese-extraction-compact-v3`, instruction SHA256 `458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46`, schema SHA256 `0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9`. B remains `vietnamese-semantic-v1`; prompt/schema/compiler are unchanged from the previous experiment. B diagnostics led only to an independently verified explicit-removal parser fix before B40, not a rewritten oracle or repaired model answer. [Frozen source snapshots](semantic-corroboration-final-v1/frozen-source-snapshot-v3.json), [contract snapshots](semantic-corroboration-final-v1/contract-snapshots-v1.json).

Both use original oracle SHA256 `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`, dataset `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`, all40 selected identities, original expected outcomes and comparable24/11/2026 civil-date clocks. Historical and new A responses were replayed with the final backend; B40 used that frozen backend live. SDK1.17.0, primary wire configuration, output1024, provider-default minimal thinking and retries0 are comparable. Same stable API model ID is observed, not a claim about unexposed internal weights. This is a descriptive development comparison, not a randomized causal experiment.

Provider/schema/compiler failures stay in attempted denominators. Raw B schema validity, raw meaning correctness, canonical compilation and independent admission are separate. The direct raw-meaning scorer compares raw proposed intent/criteria/operators/removals/references/issues with an oracle translation; it does not award model missing-slot credit for Java derivation. Raw model missing-slot accuracy for B is NOT_APPLICABLE. Canonical downstream missing slots remain applicable. No context/state/target resolution/actual READ dimension is scored as completed.

## Primary results: exact business semantics against the original oracle

|Metric|A /40|B /40|Descriptive delta|
|---|---:|---:|---:|
|Planned / attempted|40 /40|40 /40|0|
|HTTP200|37 (92.5%)|40 (100%)|+7.5pp|
|Raw contract schema valid|32 (80%, typed A)|40 (100%, raw B)|Different raw contracts|
|Compiled canonical typed extraction valid|32 (80%)|26 (65%)|−15pp|
|Canonical action accuracy|20 (50%)|24 (60%)|+10pp|
|Canonical intent accuracy|16 (40%)|24 (60%)|+20pp|
|Canonical criteria accuracy|20 (50%)|24 (60%)|+10pp|
|Business missing-slot accuracy|27 (67.5%)|26 (65%)|−2.5pp|
|Exact business semantics|12 (30%)|22 (55%)|+25pp including provider differences|
|Backend accepted / correct accepted|9 /9|18 /18|+9 correct admissions|
|Incorrect accepted|0/20 incorrect typed proposals|0/4 incorrect typed proposals|0 observed in each|
|Correct rejected|3/12 (25%)|4/22 (18.18%)|−6.82pp rate; +1 count|
|Backend NOT_EVALUATED|8:5 schema+3 provider|14 compiler rejections|Different failure layers|
|Reference-price correctness|0/3|3/3|+3|
|Spurious CLEAR|6|0|−6|
|Compiler rejections|N/A|14|Not14 proven Java defects|
|Median attempt latency|1809.5ms|1175.5ms|−634ms|
|Mean known total tokens/request|516.30, n37|478.35, n40|−37.95|

B raw model action/intent are each32/40 (80%), direct raw criteria meaning25/40 (62.5%), exact raw meaning22/40 (55%). Canonical action/intent24/40 are lower because strict compilation rejects contradictory raw proposals. Raw schema100% therefore does **not** mean semantic success100%, canonical validity100% or target achievement. Fourteen compiler FAIL records contain issues with SEARCH/criteria, criteria with CLARIFY, forbidden references on ordinary search or unsupported price semantics. They are observed rejected model-contract violations; no compiler implementation defect was established and no output was silently repaired.

Original A business accuracy is unchanged by backend replay. Its historical admission matrix alone changes5/2/3/10→7/0/0/13; new A contributes2 correct accepted/3 correct rejected/0 incorrect accepted/7 incorrect rejected among12 typed-valid proposals. [Final historical replay](semantic-corroboration-final-v1/replay-after-final-v1.json), [new A replay](semantic-corroboration-final-v1/remaining-a-backend-replay-v1.json).

## Paired outcomes

Same actual model, sample, business oracle and final corroborator: **37 comparable primary pairs**, A12/37 exact→B19/37 exact, net+7/37 (+18.92pp). The full40 descriptive+25pp additionally includes three A provider failures; it is not a pure semantic-quality delta.

* FAIL→PASS11:001,002,008,012,014,015,016,020,037,048,050.
* PASS→FAIL4:003,004,007,049.
* PASS→PASS8:017,021,027,028,031,032,041,042.
* FAIL→FAIL14:005,006,009,010,011,013,023,025,033,039,040,043,046,047.
* NOT_COMPARABLE3, provider failure in original A:029,030,051.
* Primary cross-model pairs0. No identity is silently substituted with a fallback or retest.

The predeclared supplemental A subset uses first12 original selected identities, before observing results. Against B40 on the same model:4 FAIL→PASS,2 PASS→FAIL,6 FAIL→FAIL. These are12 overlapping identities, not12 extra distinct primary samples or24 pairs. Supplemental A itself is2/12 exact.029/030 deliberate A retests are separately1/2 exact, bothHTTP200; old503 remain unchanged. Ten generalized B paraphrases are6/10 exact (primary6/9;2.5 Lite0/1 after an actual primary503). Those9/1 model cohorts must not be pooled as primary10-sample quality.

Machine evidence: [all pairs](semantic-corroboration-final-v1/paired-comparisons-v1.json), [original40 A](semantic-corroboration-final-v1/combined-a-original40-v1.json), [B40](semantic-corroboration-final-v1/b40-v1.json), [supplemental](semantic-corroboration-final-v1/generalization-and-retests-v1.json), [model-specific metrics](semantic-corroboration-final-v1/model-specific-metrics-v1.json).

## Remaining failures and recommendation

B improves explicit price representation: all three strict reference-price cases are correct, without unrelated CLEAR. Selected/comparison and some ambiguity cases improve. The observed historical false acceptances remain blocked;027/028 refinements admit correctly.

Nevertheless, B regresses complete dated/occupancy requests003/004/007 and casing049. Raw schema alone cannot enforce meaningful action/issue/criteria combinations. Correct B proposals016/041/042/048 are still conservatively rejected by independent literal coverage for natural discovery wording. Selected-room013/033 are still misclassified as UNVERIFIABLE. Canonical known-label spelling/casing remains a source of failure (043/049). These are concrete defects, not justification to trust model-proposed fields or weaken full operation checks.

**SIMPLIFIED_CONTRACT_PROMISING_BUT_INCONCLUSIVE**: retain ordinary defaults and keep B local. Its price/removal maintainability benefit and paired improvement are real in this corpus, but55% exact, four regressions and additional false rejections are far below target. Next work should target contradictory clarification/reference proposals, full date/occupancy retention, finite reviewed-label normalization and observed natural discovery grammar, then run a new explicitly bounded evaluation. Context-aware10B4E is deferred; do not add authorization/infrastructure work to conceal these NLU failures. D07/D08 unaccepted, E30 and FULL_AGENT_E2E NOT_RUN, no production readiness claim.
