# Batch10B.4C — simplify and actual live evaluation

24/11/2026, Asia/Ho_Chi_Minh. **SIMPLIFY_DONE_LIVE_BLOCKED**.

**SIMPLIFICATION_DONE**: local CLI no longer requires signed governance. A real Gemini request was executed, not merely prepared. **LIVE_INITIAL_SMOKE_PASS was not achieved**: VEX1-01 returned HTTP200 but shared validation reported MALFORMED_RESPONSE and unknown usage. STOP_ON_ANY_FAILURE correctly stopped after1 actual attempt. Main40 and remaining smoke2 are NOT_RUN; LIVE_NLU_EVALUATION_DONE is not achieved. No retry, alternate model, protocol change or supplemental call was made. This is no claim of production readiness, D07/D08 acceptance or full Agent E2E.

## Authorization, baseline and preservation

The developer explicitly authorized local synthetic execution, fixed model gemini-3.5-flash-lite, smoke≤3, main30–49 eligible samples, optional diagnostics≤18, batch≤70 actual attempts/concurrency1/retry0. Free Tier and quota15RPM/250000TPM/500RPD are developer attestations and a historical usage snapshot, not live quota/billing verification. No paid-tier switch or billing activation occurred. No new signing/financial approval ceremony was introduced.

Actual backend branch `feat/ai-agent-vietnamese-nlu`, HEAD `4c1908063e40194b989cfbe1d0db598064ec3e7b`, matching the task. Root AGENTS read; recursive backend AGENTS scan found none. Root is not a Git repository; frontend is outside scope. Existing tracked modifications: IDE compiler/encodings, Agent README, GeminiExtractionProtocol/Session/Smoke. Existing untracked signed authorization/evaluation/setup classes/tests/resources/reports were present before this task and preserved. The baseline Git status was inspected before edits. No stage/commit/push/stash/reset/clean/checkout/deletion, private dotenv read, unrelated environment dump, old sealed journal read or database action.

Initial `mvn -o -B -ntp -pl ai-agent-service package`: **953 tests, failures0/errors0/skips0, BUILD SUCCESS**,52.679s. Historical23PASS/16FAIL/0NOT_APPLICABLE/1NOT_RUN, E30NOT_RUN. No baseline discrepancy. Source/POM confirms Java21/Boot3.3.4/LangChain4j1.17.0; unchanged.

Current source, README, prior setup/operator/preflight/unblock/authorization/completion/offline evidence, VNE candidate/oracle/plan, 10B4B decisions/contracts and 10A architecture/evaluation material were inspected. Dated prerequisite statements are historical and do not override the developer's updated local-mode authorization.

## Architecture complexity audit

|Component|Current responsibility and actual consumers|Classification/action/reason|
|---|---|---|
|LlmProviderAdapter, GeminiLlmProviderAdapter|Provider contract and bounded legacy synthetic adapter; provider configuration/old smoke and orchestration use their respective seams|Essential boundary; retain. The local CLI reuses Exchange, not arbitrary live Agent admission.|
|GeminiHttpTransport|Single-attempt cancellable Apache HTTPS exchange used by adapter, old smoke, signed extraction and new local evaluation|Essential shared transport; reused unchanged. No second HTTP stack.|
|GeminiExtractionProtocol|Pinned fixtures, exact SDK serialization/model/endpoint/instruction/schema/body admission; smoke/signed runner/local CLI|Essential shared extraction protocol; unchanged.|
|VietnameseExtraction, VietnameseParser|Strict typed proposal validation and independent finite literal corroboration; orchestrator and extraction smoke|Essential safety/product logic; unchanged. Model understanding and grammar compatibility remain separate.|
|SearchCriteriaState, AgentOrchestrator|Typed provenance/state/revisions/turn loop, internal Agent consumers|Essential product runtime; untouched. Live Vietnamese ordinary ingress remains unavailable/FAKE gated.|
|PolicyExecutor, GroundingGate, ToolRegistry A01–A10|READ admission and evidence-only facts; ordinary orchestrator|Essential product security; untouched and not instantiated by local CLI.|
|GeminiBudget|Shared legacy provider/process exposure ledger; old adapter and signed smoke|Essential to existing path; preserved. Local finite campaign does not alter legacy run3/process9 or turn budgets.|
|GeminiExtractionSmoke|Shared exact SDK/wire/envelope/usage/proposal validation and corroboration; old extraction/signed runner/new local CLI|Essential evaluation primitive; reused through evaluate. No validator weakening.|
|GeminiEvaluationDataset, GeminiEvaluationMetrics|Pinned signed-review dataset loader, semantic canonicalization/input DTO and independent scorer; signed runner/tests, semantic utilities reused locally|Essential evaluation data/scoring. Signed dataset constructor still requires independent review; local plan uses a distinct hash-pinned technical oracle, never fake independent approval.|
|GeminiExtractionSession, GeminiExtractionJournal|Signed fixture permits/lifecycle/local tombstone; authorized extraction/old extraction smoke|Optional local governance; retained unchanged for signed mode, absent from local admission.|
|GeminiOperatorApproval, GeminiCampaignApproval|Ed25519, canonical signed scope/evidence/allowlists; signed tooling/runner/authorized extraction|Optional advanced governance; unchanged. Local mode never calls signature verification or manufactures grant objects.|
|GeminiSharedAllocation, GeminiEvaluationCampaign|Signed shared envelope/journal/process/window accounting; signed runner/authorized extraction|Optional advanced governance; local CLI has no financial reservation dependency.|
|GeminiAuthorizedExtraction, GeminiInitialSmokeOperator|Signed smoke admission/CLI; operator invocation/tests|Optional alternate admission, retained. Not ordinary Agent chat wiring.|
|GeminiEvaluationRunner, GeminiEvaluationPreflight|Signed campaign execution plus offline signed-scope preparation/verification|Retain optional path. Its execute method structurally couples dispatch to signed allocation; reusing shared evaluate/scorer is smaller than weakening/refactoring the signed runner.|
|GeminiLocalEvaluation|New explicit local admission, finite plan/pacing/result record; standalone PropertiesLauncher only|Essential simple local workflow, no Spring/controller/scheduler/startup hooks.|

Actual duplication is mostly admission/plan/report wrappers and repeated signed preflight preparation, not duplicated HTTP/schema engines. No component was proven globally unused merely because local evaluation does not need it. No bulk deletion. Residual compile-time coupling remains: Input/semantic helpers are in GeminiEvaluationDataset and canonicalization uses the existing pure operator JSON helper. This does not invoke signed admission; extracting pure helpers is an optional focused future cleanup, not required new infrastructure.

## Local execution design and exact commands

See [README supported commands](../../ai-agent-service/README.md) and [practical architecture decision](AI_AGENT_PRACTICAL_SECURITY_AND_EXECUTION_ARCHITECTURE.md). From backend root:

```powershell
mvn -o -B -ntp -pl ai-agent-service package
$evalJar = '.\ai-agent-service\target\ai-agent-service-0.0.1-SNAPSHOT.jar'
$localMain = '-Dloader.main=com.example.aiagent.model.GeminiLocalEvaluation'
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher
java $localMain -cp $evalJar org.springframework.boot.loader.launch.PropertiesLauncher --offline --mode vietnamese-nlu --max-requests 43
```

The combined invocation actually executed exactly once:

```powershell
java '-Dloader.main=com.example.aiagent.model.GeminiLocalEvaluation' -cp ai-agent-service/target/ai-agent-service-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher --live --mode vietnamese-nlu --max-requests 43 --output Docs/ai-agent/LOCAL_GEMINI_EVALUATION_RUN_V1.json
```

It exited nonzero with safe fixed console output `LOCAL_DEVELOPMENT_EVALUATION MALFORMED_RESPONSE attempts=1`. The file is CREATE_NEW/nonresumable; do not repeat this command or overwrite evidence. Future smoke-only syntax is the same main with `--live --mode initial-smoke --max-requests 3 --output <NEW_RESULT_PATH>`; supported offline/mocked tests cover it. No renewed live execution took place after the failure or later offline diagnostic changes.

No flags/default and explicit offline perform no credential lookup or network; actual combined offline CLI completed with OFFLINE_PREFLIGHT attempts0. Unknown flags including prompt/fixture/dataset/model/endpoint reject; no arbitrary input routing. Live requires output, conflicting offline/live rejects; count and complete pinned synthetic plan validate before credentials. Combined count includes smoke, not an additional phase budget; frozen maximum43, smoke-only exactly3. No supplemental CLI/dataset was added because no diagnostic supplemental sample was justified after failed smoke.

Credential supplier is exactly `System.getenv("GEMINI_API_KEY")`;1 actual lookup, available to child Java in this session. No key in arguments/source/reports/logs; no registry/secret-manager replacement. If a future child lacks a newly configured User variable, restart its parent terminal/IDE to refresh inherited environment; never paste the key. Current task had no credential-availability blocker.

Fixed original protocol/version/model/official HTTPS endpoint. One system instruction and one pinned synthetic utterance; no tools, full conversation history, synthetic context, real domain evidence or real records. Existing strict validators, corroboration and scorer remain authoritative. Ordinary source/config/flags untouched: OFF/FAKE/liveOFF;30s turn,3 model calls,2 rounds,6 proposals,6HTTP,10s/tool,retry0. No frontend/Gateway/Place/hotel/booking/payment/database/Supabase/Config Server change.

Local limits: in-process concurrency1, cap≤70 attempts and chosen43, min5s actual dispatch spacing,≤12 reservations/rolling60s,≤200000 fully reserved input/output tokens/rolling60s. Each call reserves16384+1024=17408, so rolling tokens effectively limit to11 calls/60s (191488). Monotonic30min deadline; call control≤10s, cancellation/interruption and remaining time rechecked; HTTP and SDK retry0. Pre-credential and actual-dispatch rolling admission share phase lifetime; no reset between smoke/main. CREATE_NEW durable reservation/in-flight metadata retains attempt identities. It is a bounded local record, not distributed/account-wide financial accounting. Never restart to evade the batch cap; unrelated project consumers remain outside these in-process limits. No zero-cost guarantee.

## Frozen selection/oracle

[Selection and exclusions](LOCAL_DEVELOPMENT_SEMANTIC_REVIEW_V1.md), [additive local oracle](../../ai-agent-service/src/main/resources/gemini/local-development-oracle-v1.json).

40 eligible distinct VNE1 selected before any response, alongside3 developer-approved VEX1. Review label **TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION**, Codex technical review against approved D1/D2/D3, not independent human approval. Oracle SHA256 `c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3`; final loader pins it. Original expected targets retained for selected cases; no post-response oracle tuning. Visible holdout is not blinded.

Excluded11 dataset DENY cases035/036/052–060; no remote dispatch. Excluded9 NEEDS_PRODUCT_REVIEW cases018/019/022/024/026/034/038/044/045 for missing-slot/action/typo/mixed-language price-unit ambiguity, detailed in review table. These exclusions occurred before live, not to improve scores. Selected coverage includes natural/informal/unaccented Vietnamese, numbers/reference prices, dates, explicit/ambiguous occupancy, unsupported constraints and references/CLEAR/REPLACE. Typographical case044 and mixed-language045 remain unresolved product review, so those coverage strata are not claimed. No hotel-name completeness or general context understanding is claimed.

027–033/051 test only single-utterance action/operation/reference extraction, no sent context. Full state application, selection resolution, multi-turn reasoning and tools remain NOT_RUN even if future extraction succeeds. This batch did not reach any main sample.

## Actual live evidence and metrics

[Original immutable first-run JSON](LOCAL_GEMINI_EVALUATION_RUN_V1.json), [interpretation/denominator corrections](LOCAL_GEMINI_EVALUATION_ANALYSIS_V1.json). Run ID `c1ca9634-4f9c-47a5-8fae-2c9f64180473`, started2026-11-24T06:50:44.570653Z (13:50:44 local).

|Phase|Planned/eligible|Attempted|HTTP success|Validated extraction|Validated exact semantic success|Backend accepted|NOT_RUN|
|---|---:|---:|---:|---:|---:|---:|---:|
|Initial smoke|3/3|1|1/1 (100%)|0/1 (0%)|0/1 (0%)|0;1 not evaluated|2|
|Main Vietnamese NLU|40/40|0|0/0 N/A|0/0 N/A|0/0 N/A|0/0 N/A|40|
|Supplemental|0/0|0|N/A|N/A|N/A|N/A|0|

VEX1-01 expected CRITERIA/SEARCH, cityName Đà Lạt REPLACE, missingSlots/inferredFields[], reference empty. Exact request digest `660335d24ae98c2bdd6280e8d8c34e10f8c523bb1b0fea2e5cf6bd61500c66b4`, unchanged from reviewed original. HTTP200 observed; MALFORMED_RESPONSE before any validated proposal. Schema/protocol rejection1/1; HTTP failures0/1. This proves HTTP endpoint acceptance, not authoritative schema compatibility or an understood Vietnamese answer. No safely retained proposal exists; actual action/intent/fields/reference/clarification and thinking usage are unavailable.

All-attempt validated action/intent/exact criteria/missing-slot/exact semantic yield0/1 (0%) because the failed response remains in denominator. **These are validated-pipeline success rates, not evidence of0% Vietnamese understanding.** Schema-valid semantic denominator0; field-level, clarification/reference accuracy and language-quality assessment are NOT_ASSESSABLE. No main linguistic results, category breakdown or statistical certainty can be inferred from this one failed validation.

Backend not evaluated1; model-correct/backend-accepted0, model-correct/backend-rejected0, model-incorrect/backend-accepted0, model-incorrect/backend-rejected0. Semantic false acceptance/rejection are N/A. The original generic score falseRejection1/1 merely compares expected admission to failed response; it cannot establish a correct model rejected by deterministic grammar. No parser limitation was demonstrated by this response.

Original raw score notRun0/1 reflected visited rows only, while the actual planned coverage was42/43 NOT_RUN (97.67%). Original JSON is retained, not silently repaired. Final local CLI adds remaining planned rows to scorer, tested offline. Companion analysis explicitly corrects interpretation. Provider/schema failures remain in attempted denominators; excluded DENY rows never enter remote denominator. Local exclusions20 are not20 executed premodel refusals and not live injection-resistance coverage.

Usage UNKNOWN:1 attempted response with no fully validated usage; input/output/total/thinking null. Raw aggregate numeric zero fields sum over0 known attempts and do not mean zero tokens or zero cost. Total-minus-input cannot safely be derived. No price/invoice claim. Unknown usage is itself a stop condition.

Latency n1: mean/median/p95/max2725ms; p95 is descriptive only. Evaluation loop duration2798ms, excludes JVM/plan startup. Rate-limit wait0ms. Only one dispatch, so pairwise spacing is vacuous, not a measured sustained-rate test. Reservation17408 tokens fits200000/min; full selected plan ceiling748544 reserved tokens across the paced run. Actual unknown consumption is not claimed measured compliance; no further request was made.1/70 actual batch attempts,1/43 selected cap, retry0; no budget violation observed. Account-wide quota remains unverified.

Actual domain/Supabase/READ/WRITE calls0; real customer/hotel/booking/payment data transmitted0 by fixed synthetic plan. No confirmation/provenance promotion or tool dispatch path. Unsafe inference/fabrication/tool flags observed0, but response validity was insufficient to certify model safety; injection resistance NOT_TESTED. FULL_AGENT_E2E NOT_RUN, E30NOT_RUN, D07/D08 unaccepted.

## Failure analysis and next work

|Priority/category|Evidence and scope|Conclusion/action/product impact/regression|
|---|---|---|
|P1 protocol/validation diagnostic gap|VEX1-01 HTTP200, MALFORMED_RESPONSE, null usage/proposal|Exact origin may be envelope allowlist, SDK parsing or strict extraction parsing. No raw body was retained, so identifying a keyword or blaming Gemini would be speculation. Add shape-only diagnostic observer (done offline) before next explicitly authorized smoke; retain strict validation. Test unknown nested keys/zero raw text retention. This unlocks actionable compatibility evidence.|
|P1 evaluation reporting|Original NOT_RUN scorer0/1 despite42 unattempted; generic false rejection/token0 ambiguous|Preserve first JSON and publish companion interpretation. Final local runner fixes planned scorer denominator and labels backend NOT_EVALUATED/unknown usage. Added full43 mocked continuation/denominator test. Prevents misleading product decisions.|
|P2 dataset/oracle decisions|Nine exclusions identified before dispatch|Resolve occupancy/missing-slot/typo/mixed-price conventions in a versioned oracle; never infer children0 or loosen unit rules. Add meaningful cases after product decisions. No production bug fix attempted.|
|Unassessed model/prompt/parser/state quality|No validated model proposal and no main responses|No actual evidence to rank city/date/number/intent/paraphrase/parser/CLEAR/reference quality failures. Avoid speculative NLU rewrite or prompt tuning against visible holdout.|

Shape-only diagnostics were added **after** the sole live observation. They record bounded boolean allowlist/structure flags and counts for root/candidates/content/parts/usage; never raw text/unknown field names/headers/IDs. They neither repair nor admit responses and cannot retroactively identify this failure. The original request prompt/schema/protocol was not changed; no corrected protocol version is claimed. If future evidence requires a material prompt/wire change, create a new version/digest, offline-test, and obtain developer confirmation before new live smoke per this task. Do not spend remaining attempts probing speculative changes.

Recommended next batch: BATCH10B4D-VIETNAMESE-NLU-QUALITY-IMPROVEMENT, beginning with one bounded diagnostic smoke after the present protocol stop is addressed; obtain actual baseline before prioritizing grammar versus prompt/model fixes. No further authorization infrastructure batch is recommended. Main evaluation remains blocked by failed smoke, not by signatures or credential provisioning.

## Verification and exact batch file scope

Executed focused `mvn -o -B -ntp -pl ai-agent-service -Dtest=GeminiLocalEvaluationTest test`:23PASS. Pre-live full package976PASS/0failures/0errors/0skips,1:02min. After live and offline diagnostics/denominator tests, required final `mvn -o -B -ntp -pl ai-agent-service package`: **978PASS/0failures/0errors/0skips, BUILD SUCCESS**,1:00min.25 new test invocations total; no existing test modified/removed/excluded. All automated tests offline; SDK plus fake HTTP and existing loopback/cross-JVM suites are not live provider acceptance.

New tests cover default/offline zero suppliers, arbitrary inputs/counts before key, fixed DENY exclusions/SDK bodies/no tools/context, three-call mocked smoke, shared rolling request/token/spacing/cap/deadline/cancellation, quota/schema/unknown/timeout/cancel/semantic stop/no retry, missing credentials/no transport, CREATE_NEW evidence, main semantic continuation/43-sample denominator and shape-only diagnostics privacy. Existing signed authorization tests passed unchanged. No automated test uses real GEMINI_API_KEY.

Historical final remains23PASS/16FAIL/0NOT_APPLICABLE/1NOT_RUN; E30 unchanged. Original runtime/test hashes rechecked:

|Original artifact|SHA256|
|---|---|
|VEX1 manifest and runtime copy|7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3|
|VNE1 dataset and runtime copy|9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950|
|Historical E01–E40|5a1b70efcbf18f7dadb0391e0db2a8105180f87c3860eeb721bfd89e43fcd15f|

Files created by this task only:

- `ai-agent-service/src/main/java/com/example/aiagent/model/GeminiLocalEvaluation.java`.
- `ai-agent-service/src/main/resources/gemini/local-development-oracle-v1.json`.
- `ai-agent-service/src/test/java/com/example/aiagent/model/GeminiLocalEvaluationTest.java`.
- This report, `AI_AGENT_PRACTICAL_SECURITY_AND_EXECUTION_ARCHITECTURE.md`, `LOCAL_DEVELOPMENT_SEMANTIC_REVIEW_V1.md`, `LOCAL_GEMINI_EVALUATION_RUN_V1.json`, `LOCAL_GEMINI_EVALUATION_ANALYSIS_V1.json` under Docs/ai-agent.

Only existing edited file: README prepended current local instructions/optional-governance clarification while preserving all historical sections. Prior dirty/untracked files are not attributed to this task. Old operator documents/reports preserved byte content; their local requirements are superseded by current README/decision rather than historical rewrite. Maven produced normal ignored target output. Final status remains unstaged; no index mutation. `git diff --check` passed (existing LF/CRLF warnings only). Local document links/JSON/hash checks and bounded secret-pattern review performed; no key/auth header/raw provider response persisted. No new `.env`/`.local` file.

Final verdict: simplification implemented and offline verified, one real provider response obtained, live evaluation blocked safely at first invalid/unknown-usage response. **Do not report DONE, meaningful Vietnamese accuracy, paid/free billing verification, full E2E or production readiness from these results.**
