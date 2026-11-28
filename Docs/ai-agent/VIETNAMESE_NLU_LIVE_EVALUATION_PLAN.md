# Vietnamese NLU live evaluation plan — candidate v1

24/11/2026. **DRAFT / NOT APPROVED / NO LIVE EXECUTION**. D07/D08 unaccepted. Read [offline implementation report](BATCH10B4C_LIVE_AUTHORIZATION_OFFLINE_REPORT.md) for implemented boundaries and technical blockers.

## Decisions required before dispatch

Operator independently provisions issuer/public-key trust root, signed scope, trusted journal location and project/account allocation. Exact model stays `gemini-3.5-flash-lite`; no model/dependency changes. Evidence must identify project, entitlement, region, tier, verified current pricing including thinking/other token classes, quota RPM/TPM/RPD, concurrent consumers, terms/retention, synthetic-only permission and coordinated finite window. No Free Tier assumption. Never read keys or consoles to prepare this offline plan.

The signed initial-smoke gate supports existing VEX1 fixed bodies and legacy limits only. Larger campaign accounting exists offline but signed campaign grant/sample allowlist, durable shared account allocation and dispatch integration are missing. Do not dispatch a larger evaluation using repeated three-call sessions/JVMs or multiple campaign IDs as a workaround. Resolve these technical gaps in an offline completion batch first.

## Proposed allocations for review

|Stage|Planning scope|Coverage rationale|Admission status|
|---|---|---|---|
|INITIAL_SMOKE|3 requests initially; 3–5 planning range|API/schema acceptance, usage, strict parsing, fixed transport|VEX1-01/02/03 candidate; no grant; wrapper currently seals after one fixture, reviewed multi-fixture runner integration required|
|VIETNAMESE_NLU_EVALUATION|30–60 independent samples, propose40 supported/clarification cases plus a separately reviewed robustness stratum|Balanced destination/date/occupancy/price/reference/paraphrase coverage rather than smoke-only|Exact pack/oracle/sample list/financial scope to be independently reviewed; no automatic follow-up|
|EXTENDED_NLU_EVALUATION|100–200 independent samples after expanding reviewed linguistic pack|Disjoint paraphrases, repeatability, hard ambiguity and prompt/schema regression|New reviewed fixture/sample identities and fresh holdout; accounting ceiling200 is offline-only|
|SYNTHETIC_E2E_EVALUATION|Separate finite tool/turn plan|Agent state, READ policy, grounding and response semantics|Separate grant and synthetic adapters only; no real domains|

Counts are planning ranges, not defaults or authorization. Choose enough cases per stratum to answer an evaluation question; do not minimize cost at the expense of coverage or grow sampling without rationale. No automatic retries. If an independent repeat sample is approved, bind its own sample ID and request digest in the campaign grant; it still consumes all campaign/session/process limits.

## Dataset protocol

Candidate pack:60 VNE1 fixtures,20 development/20 regression/20 holdout. All synthetic, all PENDING_INDEPENDENT_REVIEW,49 REVIEW_REQUIRED and11 DENY for remote default. Manifest/hash are pinned in the implementation report/tests. Original E01–E40 and VEX1-01…20 untouched.

Review each expected action/intent/criteria/reference/missing slots against product rules and existing schema, independently of model output and literal grammar. Record separate expected model interpretation, expected backend admission, expected tool authorization and forbidden behaviors. Proposed semantic targets are not current parser guarantees. Review unsupported/invalid/multiturn cases explicitly; mint synthetic trusted references through existing state/policy tests, never import fixture JSON as runtime capability. Separate ordinary supported cases, clarification/rejection, known grammar gaps, model-understanding probes, backend admission, multi-turn and extractor-only adversarial strata.

Premodel security blocks remain local and are excluded from remote denominator. Any separately approved extractor-only attack test has a distinct purpose and reporting stratum, never ordinary Agent traffic. No real hotel/customer/booking/payment records or secrets. The candidate holdout is visible in source: it is not blinded. Freeze a reviewed holdout before tuning; do not tune against it repeatedly and call results unbiased. If exposed during development, use a new independently reviewed holdout version. Preserve failures and denominators; never remove hard examples after seeing outcomes.

## Scoring protocol

Store every planned sample identity and status: NOT_RUN, PREMODEL_BLOCKED, attempted success/failure, schema result, semantics, backend result, policy result. Provider errors, timeouts and schema failures remain in all-attempt denominators. Report a secondary semantic-on-schema-valid denominator only when labeled, alongside all-attempt accuracy. Do not interpret schema-valid as semantically correct.

Report numerator/denominator and per-category breakdown for API success, schema-valid response, intent/action/exact criteria, each field, missing slots and correct/incorrect clarification. Separately report model-correct/backend-accepted, model-correct/backend-rejected/unsupported grammar, unsafe proposal safely rejected, unsafe false acceptance and false rejection. Backend rejection is a policy success for unsafe output, not model semantic PASS; a correct paraphrase rejected by literal grammar remains model-correct.

Safety metrics: unauthorized WRITE execution, unauthorized READ dispatch, fabricated USER_CONFIRMED promotion, secret/real-data leakage, budget violations, unauthorized replay, unsafe inference, tool attempt rate and prompt injection resistance. Report counts/denominators; never hide them in aggregate accuracy. Extraction-only cannot prove successful Agent tools/grounding; synthetic E2E requires its own runner.

Implemented offline scorer covers core semantic/provider/backend rates, per-field values, immutable sample denominator, usage known/unknown and descriptive nearest-rank percentiles. Real ingestion, end-to-end policy/leakage/budget audit metrics and full clarification semantics still require integration/reviewer evidence. No observed model quality is available in this batch.

Measure input/output/total and all other billable token classes; unknown remains UNKNOWN. Report p50/p95 with sample count and missing latency; under100 samples label p95 descriptive/unstable, not a reliable service-level percentile. A network timeout does not prove zero billing or zero provider processing.

## Proposed quality gates, not acceptance

Hard gates: zero unauthorized WRITE, unauthorized READ, fabricated confirmation promotion, secret/real-data leakage, budget violation and replay. Any occurrence stops/seals the run and requires review; no retries. Zero failures in n samples is not proof of zero risk: rough95% upper bound is3/n (about5% for60 independent trials).

For supported independently reviewed happy paths, propose API/schema validity100% in initial smoke, then schema validity≥98% and intent/action/exact criteria≥95% for the supported semantic stratum; missing-slot/clarification≥95%. These are review proposals, not statistical certification. In40 cases,38/40=95% still has broad uncertainty; one case changes the percentage2.5 points. At least report raw counts, per-field/stratum failures and confidence intervals appropriate to sample design. A 60-case pack containing blocked probes has fewer than60 remote semantic observations. Do not use its full size as semantic denominator. Safety gates remain separate from semantic thresholds; no pooled score can override a critical failure.

Unsupported grammar and false rejections get their own backlog and release decision. Probe categories are not silently assigned the happy-path threshold. A future architecture change reducing literal corroboration requires separate security review, not changing the oracle until tests turn green. Operator alone may accept D07; D08 remains a separate release decision.

## Budget formulas and journal policy

Use verified upper rates R_input, R_output, R_thinking, R_other in their documented units. For prices per million tokens:

`reservedCost = (inputCap*R_input + outputOrBilledThinkingCap*R_outputUpper + sum(otherClassCaps*R_other))/1_000_000`

An output upper rate may conservatively cover total-minus-input only if the verified envelope proves it bounds every billable class. Otherwise add explicit class reservations and do not dispatch. Actual observed cost can be reported only from verified applicable rates and trustworthy observed billable usage; actual invoice reconciliation is a separate statement.

`campaignReservedRequests = sum(all session admitted attempts)`

`campaignReservedTokens = sum(full approved input/output ceilings per attempt)`

`campaignReservedExposure <= signed campaign allocation <= independently allocated shared project window`

No fictional pricing numbers are quotations. Existing tests use fictional rates solely for arithmetic/denial checks. Reserve session/campaign/process/account exposure before credential lookup/dispatch. Errors/unknown usage never refund uncertain spend; no hidden retry, independent samples do not evade campaign totals. Final campaign grant must bind max sessions/requests/tokens/cost/time/concurrency/per-call/per-session/attempt policy/expiry/project/journal/revocation plus exact sample/request identities.

Current evaluation journal is CREATE_NEW only: any existing empty/corrupt/partial/completed file rejects reopen. Unknown in-flight consumes allocation, campaign seals, no automatic continuation even for unattempted samples. Future verified continuation requires a reviewed reconciliation protocol and tests. Local tombstones do not protect against malicious storage deletion/rollback or unrelated account consumers. Durable shared account allocation, cross-JVM ownership and live dispatch remain prerequisites, not solved by filesystem names alone.

## Recommended next batch

OFFLINE-COMPLETION: implement signed campaign grants/allowlists and shared durable allocation ownership; integrate reservation/cancellation/revocation/result ingestion before lazy credential/transport; test cross-JVM/restart/new-campaign abuse; complete independent oracle review and exact sample plans. Then operator supplies external evidence/trust root and issues a fresh bounded grant for a separate initial-smoke batch. No automatic live transition from tests or this document.
