# Batch 10B.4D-EXTRACTION: evidence-first and model quality

24/11/2026, Asia/Ho_Chi_Minh. Final status: **EVIDENCE_FIRST_EVALUATION_PARTIAL**. Architecture decision: **MORE_EVIDENCE_REQUIRED**.

## Executive summary

Implemented local Contract C, strict source membership/type/completeness verification, deterministic canonical compilation and a parser-only comparator. Fixed timeout category propagation while preserving the earlier termination-metadata fix. The baseline and final offline builds pass. Actual new Gemini attempts: **21/120** (19 Gemini3.5 Lite,2 Gemini3.1 Lite). **17 HTTP200,1 HTTP503 High Demand,3 locally terminated timeouts;4 UNKNOWN usages**. All21 attempts retained full reservations. No fallback or retry occurred.

The fixed-model comparison stopped on **two consecutive logical timeouts**, ATT-020/021. Cumulative actual transient failures were4, below the separate5-failure ceiling. The mandatory stop is sealed; no further dispatch, credential lookup, directory restart or budget reset followed. **Frozen40:0/40 NOT_RUN; fresh20:0/20 NOT_RUN.** No quality-target acceptance or complete four-model comparison is claimed.

## Baseline and preservation

Verified backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`. Root is not a Git repository; work was confined to backend ai-agent-service and Docs/ai-agent. No nested AGENTS.md was found. Existing dirty/untracked source, tests, historical reports/fixtures and IDE modifications were preserved. No stage, commit, push, stash, checkout, reset or clean.

Required command, executed before changes and after evaluation: `mvn -o -B -ntp -pl ai-agent-service package`. Baseline **1134**; final **1235**; **101 new test cases**; failures0/errors0/skipped0; BUILD SUCCESS. Historical E01–E40 remains **23 PASS /16 FAIL /0 NOT_APPLICABLE /1 NOT_RUN**. Maven checks were offline, using sentinels/fakes/loopback only. [Final log](evidence-extraction-v1/final-package.log), [verification](evidence-extraction-v1/verification-v1.json).

Development failures were retained in the work transcript: an inaccessible package-private number helper at compilation, a negated-discovery request classification check, and a cold-start loopback test whose400ms deadline expired before dispatch. The helper call was removed in favor of the existing parser result; discovery meaning was separated from refinement policy; the loopback test now admits startup time while still proving socket closure. No existing assertion was weakened. Later full-package runs passed1224,1234 and1235 cases; final candidate/full verification both passed1235.

All original VEX1/VNE1/historical/oracle hashes and the original fresh20 hash `6cf41670b74d44f52d27beb8be5519e5911d11fe0ab445801308e59edcf3e42a` remain unchanged. [Preserved hashes](evidence-extraction-v1/preserved-frozen-hashes-prelive-v1.json). Fresh20 was reused from the prior pre-response dataset; no fresh response was obtained, no fresh utterance became a prompt example, and no business oracle was changed.

Two discrepancies were verified rather than copied forward: README's top “current” checkpoint was older than the actual1134-test implementation; the old quality runner's91 primary daily-attempt offset did not include the subsequent15-attempt campaign. The retained local journals give prior counts106/1/5/1; these are not account-wide complete usage. The new campaign uses those conservative known daily counts and a separate new120-attempt allowance, never the old unused105.

## Timeout/cancellation

ATT-015 from the previous campaign remains sealed UNKNOWN_IN_FLIGHT with its original hash. The post-stop wrapper already preserved a returned transport's termination boolean. A remaining category defect discarded the caught ProviderException category and converted a no-response terminated timeout to generic UNAVAILABLE. Reply now carries the safe typed failure, and the runner propagates it without inferring termination.

New loopback/mock checks cover timeout before dispatch, timeout after dispatch, completed cancellation/socket EOF, unknown termination blocking the next dispatch/reopen/key lookup, full unknown-usage reservations and cooldown. Existing transport/fallback/failure-policy cases remain passing. The local path is synchronous: transport return follows response/client/pool closure; SDK retries0 and HTTP retries disabled. No Future.cancel/interrupt-only termination claim is used.

Live ATT-017,020,021 naturally returned status0/TIMEOUT with callTerminated=true. ATT-017 safely advanced to a distinct sample after15s; ATT-020 advanced once; ATT-021 triggered the threshold. **The local timeout-metadata/continuation path was observed live**, beyond the initial successful technical smoke. Remote inference termination and remote concurrency remain unverified. [Focused timeout report](GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md).

## Contract C and implementation

One model call proposes request meaning and typed exact-substring facts. Java verifies membership, complete typed witnesses, role/operator/unit/negation, duplicates/overlap and unconsumed constraints before any canonical compilation. Missing facts are rejected, never recovered and counted as model success. Price exclusivity, normalized values, missing slots and business action/intent are Java credit. [Contract specification](VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md).

The compiler deliberately gates the existing deterministic original-utterance parser with complete evidence verification. It is not a general semantic normalizer beyond that parser's coverage. Existing canonical parsing and quality corroboration remain authoritative. A/B code paths, original prompts/schemas and signed/ordinary compatibility remain intact. The new CLI reuses existing SDK serialization, profiles, transport, fallback/accounting and limiter. No framework, dependency, runtime route or database change was added.

Revision1 diagnostics:8/8 HTTP200 and raw-schema valid;7/8 complete/exact/correctly admitted. Precision19/21 evidence facts; recall19/20 independently expected witnesses; request meaning8/8. VNE1-003/004/007 retained all dates/explicit occupancy;004 invented no destination or budget. One selected-room proposal split “phòng này” into ROOMS “phòng” and REFERENCE “này”; it was correctly rejected before canonical admission. The compatibility category EXTRACTION_SCHEMA_INVALID here denotes the extraction boundary, **not invalid raw JSON schema**.

Revision2 added only a target-noun/reference clarification and explicit numeric ROOMS requirement to the prompt. The validator was not relaxed. Two separately labeled checks passed, including “phòng đã chọn”. Revision1's failure remains in its own cohort; it was not replaced. Revision2 source/prompt/schema/wires and primary development model were frozen before comparison. No candidate tuning occurred afterward. [Revision1](evidence-extraction-v1/diagnostic-r1-observations-v1.json), [revision2](evidence-extraction-v1/diagnostic-r2-observations-v1.json), [frozen candidate](evidence-extraction-v1/frozen-candidate-v1.json).

## Offline replay and comparator

- Historical20 typed-valid actual responses:7 correct accepted /0 correct rejected /0 incorrect accepted /13 incorrect rejected; three typed-invalid actual proposals still strictly reject. Original missing provider responses were not fabricated.
- B v1 actual40 response replay:40 raw-schema valid,26 typed-valid,22 exact,22 correct accepted,0 correct rejected,0 incorrect accepted,4 incorrect rejected,14 compiler rejections. Previously fixed016/041/042/048 admissions remain passing.
- B v2 diagnostic revision1 recorded-response regression:8 exact/8 correct admissions,0 wrong admissions. Prior revision2 remains a separate5-attempt diagnostic cohort, not a frozen score.
- Parser-only offline comparison:40/40 frozen and20/20 fresh exact canonical outcomes using the existing supported grammar. This is **not Gemini or Contract C model accuracy**. The present data demonstrates no additional case coverage from adding the LLM to that deterministic path.

[Historical replay](evidence-extraction-v1/historical-actual-response-replay-v1.json), [B1 replay](evidence-extraction-v1/b1-actual-response-replay-v1.json), [parser comparator](evidence-extraction-v1/parser-only-comparator-v1.json).

## Controlled model comparison and unavailable cohorts

Balanced plan:8 shared identities ×4 exact model profiles, fixed-model/no fallback. Actual comparison:10/32 attempts. Gemini3.5 Lite:6 exact/correct admissions from8 attempts; the other2 were503/timeout, with semantic correctness NOT_EVALUATED. All6 received proposals were correct; that conditional observation does not replace the6/8 overall attempted result. Gemini3.1 Lite:2 timeouts,0 usable responses,6 NOT_RUN; intrinsic model quality cannot be scored. Both2.5 models:8 NOT_RUN each. No model winner, model change or statistical superiority is established. [Model report](GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md).

Frozen40 and fresh20 have complete immutable plans and machine-readable NOT_RUN observations, including original utterances/oracles/exclusions/clock. All40 B1/C pairs are NOT_COMPARABLE. Applicable95%/98%/reference-price targets remain **NOT_ASSESSED**, not zero-quality estimates or passes. [Frozen40](evidence-extraction-v1/frozen40-observations-v1.json), [fresh20](evidence-extraction-v1/fresh20-observations-v1.json), [comparison](VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md).

## Budget, performance and safety

Known input6787 + candidate output1222 = known total8009 tokens.4 usages UNKNOWN; thinking **NOT_REPORTED** for all21 attempts. Full reservations344064 input +21504 output =**365568**, including69632 retained for the four unknown-usage attempts. No refund or zero-cost guarantee.

Recorded dispatches19:24:01.084–19:34:43.483 local time,24/11/2026:642.400 seconds between first/last, within the60-minute aggregate deadline. Minimum journaled dispatch spacing5.002857s; maximum8/rolling60s and139264 reserved tokens/rolling60s. Model maxima8 RPM primary and2 RPM3.1 are below their15 RPM snapshots. Known same-day totals125/3/5/1 are local evidence only. Single local synchronous dispatch/exchange; remote inference concurrency not verified.

Attempt latency mean4822.48ms, median2513ms, p9510007ms, max10008ms. Configured10s deadlines do not imply wall latency≤10s: the observed maximum exceeds that by8ms; provider-only and SDK/cleanup components were not separately instrumented. Global limiter wait23537ms; model-specific waiting NOT_SEPARATELY_RECORDED. Three continuation cooldowns totaled45s; fallback cooldown0. [Accounting/performance](evidence-extraction-v1/budget-accounting-v1.json).

Observed incorrect canonical acceptance0; invented evidence acceptance0; unsafe inference/fabricated confirmation/unauthorized tools0. Upstream provider/evidence/compiler failures remain in overall attempted denominators and backend NOT_EVALUATED, not silently discarded. No Gateway/domain/Supabase/payment/refund/WRITE call, tool execution or state mutation occurred. No credential/header/JWT/cookie/real hotel/customer data was added to reports/source. Final scans and source/hash/process checks are in verification. Ordinary runtime remains OFF/FAKE/liveOFF; no primary model or dependencies changed.

## Architecture decision and limitations

**MORE_EVIDENCE_REQUIRED**. Evidence-first has a useful diagnostic retention signal, but frozen40/fresh20 and a balanced four-model comparison were not completed. Do not promote C or select a different model from these observations. Existing deterministic grammar already matches all60 development business outcomes; incremental LLM coverage is unproven. A future deterministic-first/hybrid responsibility review should require examples where an LLM adds value beyond that parser, rather than growing prompts/rules indefinitely. This is a proposal, not a runtime migration.

Do not start10B.4E context-aware NLU from this partial acceptance. D07/D08 remain unaccepted; E30 and FULL_AGENT_E2E remain NOT_RUN. Selected/refinement/comparison work here is recognition only; trusted context/target resolution and tools remain NOT_RUN. No production readiness claim.

All deliverables and failure-specific next actions: [contract](VIETNAMESE_EVIDENCE_FIRST_EXTRACTION_CONTRACT_V1.md), [model comparison](GEMINI_VIETNAMESE_NLU_MODEL_QUALITY_COMPARISON_V1.md), [contract comparison](VIETNAMESE_NLU_EVIDENCE_CONTRACT_COMPARISON_V1.md), [timeouts](GEMINI_LOCAL_TIMEOUT_AND_CONTINUATION_VERIFICATION_V1.md), [remaining failures](VIETNAMESE_NLU_EVIDENCE_FIRST_FAILURE_ANALYSIS_V1.md), [artifact hashes](evidence-extraction-v1/artifact-hashes-v1.json).
