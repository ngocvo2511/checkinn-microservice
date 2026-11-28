# Vietnamese NLU contract A/B comparison v1

24/11/2026. **PARTIAL — MORE_TARGETED_EVIDENCE_REQUIRED**. Comparator A attempted25/40. Comparator B attempted0/40. **Zero comparable live pairs**, all40 pairs NOT_COMPARABLE. There is no observed B improvement/regression, no B live compiler-error rate and no basis to select B for ordinary runtime.

## Frozen configurations and comparability

A: `gemini-3.5-flash-lite`, local `vietnamese-extraction-compact-v3`; prompt SHA256 `458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46`, schema `0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9`. These identities were recorded in the persisted plan before dispatch, not selected using new scores. No A prompt/parser/oracle tuning occurred during its run. Source branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b` plus pre-existing dirty work; HEAD alone does not identify the working implementation. Executable/source digests are in [snapshots](semantic-extraction-fix-v1/contract-snapshots-v1.json).

B: implemented `vietnamese-semantic-v1` plus local lightweight Java compiler and the same independent parser/backend boundary. Exact prompt/schema/compiler digests are in those snapshots. B has an **offline snapshot, not an executed frozen live campaign**. No runtime promotion, prompt tuning on live B results or provider substitution occurred.

Both plans use the same40 selected identities and original canonical business outcomes from oracle SHA256 `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`, dataset `9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950`, review TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION. Nine unresolved product cases and11 DENY candidates were not sent. No difficult selected case was removed. A uses its recorded actual UTC evaluation clock; B live clock is NOT_RUN. Original smoke dates remain valid and were not rewritten.

Primary comparison is **canonical typed business semantics against the original oracle**, not equality of incompatible raw schemas. Provider/schema failures stay in attempted accuracy denominators; NOT_RUN is separate. B raw model missing-slot metric is NOT_APPLICABLE, while semantic issues and compiled missing slots remain independently assessable. All context application/target resolution/actual READ dimensions remain NOT_RUN.

## Actual primary results

|Metric|A existing contract|B semantic + compiler|Delta|
|---|---:|---|---|
|Planned|40|40|0|
|Attempted|25|0|Not comparable|
|NOT_RUN|15|40|Not comparable|
|HTTP success|23/25 (92%)|NOT_RUN|N/A|
|Envelope / SDK valid|23/25 /23/25|NOT_RUN|N/A|
|Strict extraction schema valid|20/25 (80%)|NOT_RUN|N/A|
|Action accuracy|12/25 (48%)|NOT_RUN|N/A|
|Intent accuracy|10/25 (40%)|NOT_RUN|N/A|
|Canonical criteria accuracy|13/25 (52%)|NOT_RUN|N/A|
|Business missing-slot accuracy|15/25 (60%)|NOT_RUN|N/A|
|Exact business semantics|7/25 (28%)|NOT_RUN|N/A|
|Exact, schema-valid-only secondary|7/20 (35%)|NOT_RUN|N/A|
|Backend accepted|8/25 (32%)|NOT_RUN|N/A|
|Incorrect semantic proposal accepted|3/13 incorrect schema-valid proposals;3/25 attempted|NOT_RUN|N/A|
|Correct semantic proposal rejected|2/7 correct proposals;2/25 attempted|NOT_RUN|N/A|
|Applicable strict reference-price correctness|0/2|NOT_RUN|N/A|
|Spurious CLEAR|3/25 attempted|NOT_RUN|N/A|
|Parser exceptions|0/20 schema-valid evaluated|NOT_RUN|N/A|
|Median latency|1803ms, n25|NOT_RUN|N/A|
|Mean known total tokens/request|12093/23 =525.78;2 UNKNOWN|NOT_RUN|N/A|

Do not confuse legacy runner `falseAcceptance=0/0` / `falseRejection=17/25` with semantic false acceptance/rejection. Those old scores compare expected admission with actual admission; all selected oracle rows expect admission. The new descriptive analysis explicitly uses semantic correctness versus admission, yielding3 and2 respectively. Historical observation files and scoring assertions were not rewritten.

Technical smoke remains separate:3 HTTP200/schema-valid,2 exact accepted,1 reference-price semantic failure. Diagnostics and supplemental requests are not pooled into primary accuracy. [Full metrics](semantic-extraction-fix-v1/aggregate-metrics-v1.json), [CSV](semantic-extraction-fix-v1/observations-v1.csv), [40 pairs](semantic-extraction-fix-v1/paired-comparison-v1.json).

## Attribution and representative limitations

No paired improvements/regressions can be named. Offline semantic equivalents demonstrate only that B can express the original40 outcomes; a human/test constructing an equivalent proposal is not a Gemini success.

Observed A business representation errors include missing exclusive flags for VNE1-002/020 and unsolicited star CLEAR for002/010/020;025 also emits an invalid false exclusive flag. Four samples002/010/020/025 exhibit representation defects, with overlap with other errors. Five samples008/009/010/011/025 exhibit explicit fact/constraint errors: generic people became adults, invalid31/02 became28/02, reversed prices were swapped, an unsupported pool constraint was discarded, and explicit adults were omitted. These are multi-label observed axes, not exclusive causal bins or a count of all Vietnamese misunderstandings. Prompt conditioning versus schema representation versus model reasoning has not been causally isolated.

Correct proposals027 (`Chỉ giữ khách sạn 4 sao`) and028 (`Đổi điểm đến sang Huế`) are rejected because the existing conservative parser leaves parts of those refinement phrases unconsumed. This is actual corroboration limitation evidence, unlike the previous price-only diagnostics. No equality/validation rule was relaxed to hide it.

Incorrect proposals005/006/015 are admitted at the extraction boundary.005 retains incomplete stay criteria instead of expected clarification;006 omits required business missing slots;015 labels a selected-room question UNSUPPORTED/CLARIFY. These are not evidence of an unauthorized tool call: the local path executes no state or READ tool, and ordinary orchestration still has its own missing-state/policy gates. They do disprove a claim of zero semantic false acceptance at this boundary.

B compiler has zero mismatches in40 offline equivalent translations, plus positive/negative guarded tests. B live compiler correctness, independent parser false rejection, model raw semantic accuracy, token savings and latency are **NOT_TESTED**, not zero failures. Its removal list and price operator remove backend wrapper/flag generation duties, but ambiguity issue prediction remains model work. Existing corroboration limitations are preserved and documented, not repaired using the oracle.

## Technical stop, recommendation and next evidence

VNE1-029 and030 returned HTTP503 with provider `UNAVAILABLE`, message “This model is currently experiencing high demand.” The first failure retained its full17408 reservation and UNKNOWN actual usage; the runner waited15s then advanced to030 without retrying029. The second consecutive failure sealed the campaign. Baseline remaining031/032/033/037/039/040/041/042/043/046/047/048/049/050/051 remain NOT_RUN. No B diagnostic/full/generalization requests were dispatched afterward.

Architecture decision: **MORE_TARGETED_EVIDENCE_REQUIRED**. Keep the existing ordinary default; keep B as an explicit local experiment. Maintainability benefit is plausible: one local compiler, no framework/service/dependency, reuse typed contracts and validators. Actual model accuracy/safety/regressions/token/latency benefits are unmeasured. A has substantial observed defects and the corroborator has real false acceptance/rejection limitations; neither tested evidence nor novelty justifies automatic promotion.

Next work should target the observed ambiguity/refinement/reference and corroboration defects, then complete a newly authorized bounded paired evaluation without overwriting the sealed observations. Context-aware10B4E is premature until single-utterance quality and architecture selection have actual evidence. D07/D08 remain unaccepted, FULL_AGENT_E2E remains NOT_RUN, and no production readiness is claimed. This is a development paired comparison design, not a randomized causal experiment.
