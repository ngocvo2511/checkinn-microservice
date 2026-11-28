# Batch10B4D-QUALITY semantic consistency and Vietnamese NLU

24/11/2026, Asia/Ho_Chi_Minh. **LIVE_TECHNICAL_BLOCKED**.

Baseline: 1080 tests PASS; final 1134 tests PASS, failures0/errors0/skipped0. New actual Gemini HTTP attempts conservatively charged **15/120**, per-model {
  "gemini-3.5-flash-lite": 15
}. **Frozen40 and fresh20 are NOT_RUN** after critical technical stop at ATT-015. No new primary/fresh accuracy can be claimed. The completed first10 diagnostics gave8 exact/5 admitted; the later revision attempted5/10 (2 exact/2 admitted,2 compiler rejections,1 timeout), retaining the other5 as NOT_RUN. Offline B v1 replay fixes four backend false rejections. Raw schema validity is reported separately and is not semantic accuracy.

ATT-015 had status0, TIMEOUT, unknown usage and no retained response/termination proof. The existing policy sealed `segment-01-diagnostic-r2.json` as UNKNOWN_IN_FLIGHT. No fallback, retry, new campaign directory, primary40/fresh20 dispatch or budget reset followed. All15 ledger attempts retain full reservations, even though provider receipt of the last request cannot be verified. A local wrapper could discard proven termination metadata when status0 throws; a post-stop offline fix preserves that metadata for future explicitly authorized work without inferring termination. It does not relabel or reopen this sealed campaign. A regression asserts unknown-stopped campaigns cannot continue or read credentials. Live compatibility of this post-stop metadata fix is NOT_RUN. The final offline audit also preserves original fixture category/split metadata in the frozen plan builder; utterances, oracle expectations and wire meaning are unchanged, and old journals are not rewritten.

## Baseline and preservation

Backend `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`; frontend clean. Root AGENTS applies; no nested guide was found. Existing tracked/untracked service, test, report and IDE work was preserved, with [initial status](semantic-quality-v2/preexisting-status.txt), [source snapshot](semantic-quality-v2/source-before-v1.json) and [historical artifact hashes](semantic-quality-v2/historical-preservation-before.json). No Git mutation was performed. Actual source matched the reported baseline; dated README sections describe older separate signed paths, while current local evaluation uses explicit authorization. No new signature/allocation infrastructure was added.

Read previous final/corroboration/fallback/A-B reports, frozen contracts, actual retained envelopes and observations, current local runner/compiler/parser/routing/scoring/transport/SDK tests and original dataset/oracle. VEX1/VNE1/historical/oracle hashes remain unchanged; [final preservation](semantic-quality-v2/preserved-hashes-final-v3.json). Original provider failures and campaign records were never replaced by replays/retests.

## Actual replay and root causes

Before implementation, all40 retained B envelopes were replayed through envelope validation, the actual SDK with an in-memory exchange, strict raw schema, compiler, canonical parser and independent corroboration at the exact original B clock `2026-11-24T10:18:48.242851600Z`. It reproduced40 raw-valid,26 typed-valid,22 exact,18 correct accepted,4 correct rejected,4 incorrect rejected and14 compiler rejections. [Before](semantic-quality-v2/b1-replay-before-v1.json), [after](semantic-quality-v2/b1-replay-after-v2.json).

The14 were contradictory/incomplete model proposals, primarily H1/H2, not14 confirmed compiler defects. Strict rejection remains. Discovery wording caused H5 false rejections016/041/042/048; generalized bounded rules fixed all four. The same B v1 model output now has22 correct accepted,0 correct rejected,0 incorrect accepted,4 incorrect rejected; model semantics stay22/40. Label spelling/casing remains wrong under frozen B v1 exact scoring; v2 adds finite reviewed normalization without retroactive credit.

Historical20 typed-valid+3 invalid replay retains7/0/0/13 and3 strict rejections. Remaining A, old diagnostics, supplemental responses and actual2.5 fallback/probe envelopes were also replayed; [replay files](semantic-quality-v2/). Non200 attempts without a usable envelope are explicitly not replayable, never fabricated. No provider/key/domain call was used for offline replay.

## Implementation and evaluation freeze

V2 uses one structured call and one flat semantic proposal. Intent-first compatibility rules prohibit unrelated references, SEARCH+issues, CLARIFY+criteria, invented hotel names/default budgets, implicit occupancy and arbitrary removals. Java derives missing business slots and strict reference-price fields; it never adds missing user facts. Schema changes are descriptive field annotations only; original strict structural constraints remain. Model-specific serving adaptation/fallback order and ordinary defaults are unchanged.

Exact reviewed labels support casing/NFC/bounded whitespace and reviewed accentless aliases. Unreviewed accented spellings, unknown places, conflicts and wrong literal destinations reject. Discovery wording is generalized by bounded clauses and edge politeness, with unconsumed suffix and unsupported-filter checks retained. Selected/reference recognition does not grant target IDs/state or tool authority.

First diagnostic revision:10 actual attempts,8 exact,5 accepted; two full-stay failures and three correct clarifications rejected. Evidence justified one pre-freeze revision: field descriptions, one separate synthetic full-stay example, explicit generic-hotel/default-price prohibition, and candidate-only independently corroborated invalid-input clarification. An actual response exposed `VND mỗi phòng mỗi đêm` grammar coverage; the bounded unit clause now parses, while unknown currency/suffix tests reject. The same retained diagnostic responses replay8 exact/8 accepted, with only009/010/040 admission changes; no wrong proposal is repaired. The second revision planned10 diagnostics but attempted5 before critical stop; all attempted and NOT_RUN observations are retained separately with revision hashes and controls. See [diagnostic cohorts](semantic-quality-v2/diagnostic-revision-metrics-v2.json).

Final candidate prompt/schema/backend/source/wire hashes and planned order are captured in an offline freeze after controlled stop: [plan](semantic-quality-v2/frozen-plan-final-v3.json), [source](semantic-quality-v2/source-frozen-final-v3.json). This is not evidence of a frozen40 dispatch: all40 and fresh20 remain NOT_RUN. Fresh20 expectations were authored before the first new response and use the same pinned clock/candidate in the plan. They are **FRESH_DEVELOPMENT_GENERALIZATION**, not a blinded or external holdout; no fresh utterance was placed in prompt examples. Optional later candidate/retest was not run; no best-of-attempt substitution.

## Results and comparison

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


Same-model/same-identity/original-oracle B v1→B v2 exact semantics:

- FAIL -> PASS: 0 — 
- PASS -> FAIL: 0 — 
- PASS -> PASS: 0 — 
- FAIL -> FAIL: 0 — 
- NOT_COMPARABLE: 40 — VNE1-001, VNE1-002, VNE1-003, VNE1-004, VNE1-005, VNE1-006, VNE1-007, VNE1-008, VNE1-009, VNE1-010, VNE1-011, VNE1-012, VNE1-013, VNE1-014, VNE1-015, VNE1-016, VNE1-017, VNE1-020, VNE1-021, VNE1-023, VNE1-025, VNE1-027, VNE1-028, VNE1-029, VNE1-030, VNE1-031, VNE1-032, VNE1-033, VNE1-037, VNE1-039, VNE1-040, VNE1-041, VNE1-042, VNE1-043, VNE1-046, VNE1-047, VNE1-048, VNE1-049, VNE1-050, VNE1-051

All provider/compiler failures remain in attempted denominators. Missing business slots are Java-derived, not raw-model predictions. Fixed-model cohorts and fallback availability are separate in [model metrics](semantic-quality-v2/model-specific-metrics-v2.json). See [detailed comparison](VIETNAMESE_NLU_CONTRACT_B_QUALITY_COMPARISON_V3.md).

## Budget, fallback and performance

Concurrency1; aggregate≤120; global≤12/rolling60s; spacing≥5s; conservative17408 reserved tokens/attempt and≤200000/rolling60s; input16384/output1024; configured per-call deadline10s; zero automatic same-model retry; campaign≤60min from first dispatch. The timeout attempt elapsed10021ms including SDK/cleanup and logical12056ms; this is not proof that every measured elapsed duration was≤10s. Quota/credential/safety/unknown-in-flight failures stop. Capacity-only fallback retains every failed attempt and requires terminated503/UNAVAILABLE plus the existing allowlisted overload body and≥15s cooldown.

Actual phase/model/usage accounting and dispatch timing: [budget](semantic-quality-v2/budget-accounting-v2.json). Known input 10992, candidate output 602, total 11594; unknown usage attempts 1; thinking NOT_REPORTED. Full conservative reservation 261120, never refunded. Global observed rolling max 10 requests/174080 reserved tokens; minimum spacing 5.001770973205566s; first-to-last dispatch 547.0394718647003s. Earliest deadline/counters imported across segments. Previous same-day retained usage98 (91/1/5/1 by model) was accounted for in local RPD headroom; this is not account-wide quota proof or a zero-cost guarantee. Quota failures cannot trigger fallback.

[Fallback metrics](semantic-quality-v2/fallback-metrics-v2.json) distinguish additional attempts, cooldown, technical and correct semantic recovery. No capacity failure was deliberately induced; unused fallback reserve is not a claim of newly verified fallback quality. Attempt latency includes SDK/validation; provider-only latency is NOT_SEPARATELY_RECORDED. Mean/median/p95/max, logical latency and known token usage are in each cohort's metrics; absent thinking is NOT_REPORTED, not zero.

## Offline checks, safety and limits

Executed required `mvn -o -B -ntp -pl ai-agent-service package` at baseline, pre-live and final. Final1134 tests, +54; no failed/error/skipped checks in the final run. Focused/generalized tests cover compatibility, exact facts/omissions/additions, dates/occupancy, normalization, selected references, unknown suffixes, explicit units, removals, strict prices, unsafe fields and exact SDK wires. Actual recorded B40 and diagnostic responses are retained regression fixtures. Tests use synthetic/mock/loopback transport and never read a real API key or call Gemini. [Verification](semantic-quality-v2/verification-v2.json) lists intermediate failures separately; existing assertions were not weakened to hide them. The old041 rejection test now has a positive acceptance assertion and its separate classification test retains an explicitly test-owned invalid-calendar expectation; frozen business oracle files are unchanged.

Ordinary runtime remains OFF/FAKE/liveOFF; v2 is not promoted. No domain/Gateway/Supabase/payment/refund calls, WRITE tools, database/schema/frontend/dependency changes, commits/staging/push/reset/checkout. Recognition is evaluated; trusted target resolution, tool execution and FULL_AGENT_E2E are NOT_RUN. D07/D08 remain unaccepted; E30 remains NOT_RUN without authenticated ordered ingress. No production readiness claim.

No live evaluation process remains. Credential scan results, source/dependency/scope integrity, historical hashes and final changed/new file inventory are in [verification](semantic-quality-v2/verification-v2.json) and [artifact hashes](semantic-quality-v2/artifact-hashes-v2.json). Raw sanitized synthetic provider envelopes remain under gitignored `ai-agent-service/.local/semantic-quality-v2/responses/`, with attempt identity/provenance in exported segments. No real hotel/customer data was sent.

## Quality targets and next batch

Primary/fresh quality targets: **NOT_ASSESSED**, because40+20 planned samples were not dispatched. Critical regressions003/004 remain failures in both diagnostic revisions; no broad semantic improvement claim is made. Offline backend improvement is verified independently. Remaining defects and their precise proposals/stages are in [failure analysis](VIETNAMESE_NLU_REMAINING_FAILURE_ANALYSIS_V3.md).

Retain B v2 as a local development candidate. Do not promote or begin context-aware runtime implementation while critical single-utterance failures remain. Next work should target the actual remaining semantic failures below, particularly full explicit-fact retention or action consistency when observed. The flat optional contract still permits contradictory combinations; consider a small action-specific serving representation if failures persist, rather than expanding the independent grammar without bound. No authorization/infrastructure batch is recommended.
