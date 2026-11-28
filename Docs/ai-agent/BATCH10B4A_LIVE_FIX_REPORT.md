# Batch 10B.4A-LIVE-FIX

23/11/2026, Asia/Ho_Chi_Minh. **Code fix complete offline; live execution BLOCKED / NOT_RUN. Overall BLOCKED**, not DONE. No live Google request or real domain call. The current task explicitly says after offline checks: “Không gọi Gemini.” That stop instruction is honored alongside conditional authorization for at most one future request; no inference was used as a compatibility probe.

## 1. Git baseline

Backend branch `feat/ai-agent-read-service`, HEAD `fa388a2b2fa5787bafed7fa85324cb543a22acc7`; unchanged at completion. Initial tracked changes: `.idea/compiler.xml`, `.idea/encodings.xml`, Agent README, `GeminiSyntheticSmoke.java`, `GeminiProviderTest.java`. Initial untracked reports: LIVE_PREFLIGHT and LIVE_EXECUTION. Preserved all. No stage/commit/push/stash/reset/clean/checkout. Root AGENTS applies; no narrower Agent/Docs AGENTS found. Private IDE/run settings and dotenv were not read. Existing IDE files changed externally during work; this task did not edit them.

Read the three requested reports, Agent README/YAML/POM and named source/tests; historical PASS counts/readiness/branches do not substitute for fresh checks.

## 2. Files changed

All implementation changes under `ai-agent-service/`:

- New `model/GeminiSmokeWire.java`: exact-model/fixed-fixture SDK boundary transformation.
- New `model/GeminiSmokeApproval.java`: CLI-only trusted evidence/authorization configuration, defaults NOT_VERIFIED.
- New `model/GeminiSmokeDiagnostics.java`: bounded scalar diagnostics.
- Updated `GeminiProperties.java`: separate exact first-call validation; OPERATOR_CONFIRMED distinct from VERIFIED.
- Updated `GeminiSyntheticSmoke.java`: permit issuance, boolean key presence, safe outcomes, failure exit2 and process execution latch.
- Updated `GeminiLlmProviderAdapter.java`: optional internal permit path and wire transformation before input admission/one HTTP exchange.
- Updated `GeminiHttpTransport.java`: internal numeric status observer, no transport policy change.
- Updated `GeminiBudget.java`: read-only exposure snapshot.
- New `GeminiFirstCallTest.java`; additive coverage in `GeminiTransportTest.java` and `AgentFoundationTest.java`.
- Updated `application.yml` with disabled/default-NOT_VERIFIED smoke evidence bindings and README with IntelliJ setup and exact nonsecret envelope.

Only new document is this report. Previous report contents and earlier user edits preserved. No dependency/POM/version, Fake provider, fixture text, tool contracts, RequestProtocol, normal provider factory, orchestrator/domain implementation, frontend, Place, Config Server, schema or migration edits.

## 3. Model wire compatibility

Exact reviewed model `gemini-3.5-flash-lite`. Reviewed [Google model specification](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite), [migration guide](https://aistudio.google.com/docs/whats-new-gemini-3.6) and pinned [LangChain4j1.17.0 source](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-google-ai-gemini/1.17.0/langchain4j-google-ai-gemini-1.17.0-sources.jar), including BaseGeminiChatModel's unconditional candidateCount1. Source inspected from the previously downloaded exact-version archive, not a rolling SDK assumption.

Actual pinned SDK serialization is exercised offline, followed by the same guarded SPI and Apache transport against a synthetic loopback server. Evidence shows final wire has exact model URL, maxOutputTokens128, unchanged structured response schema/content, no tools and no candidateCount. Input/output limits and schema support are documented, but account/live model acceptance remains NOT_RUN. This is **OFFLINE_TECHNICALLY_VERIFIED** for the fixed request, not comprehensive Gemini3.5 SDK compatibility or Vietnamese dialogue acceptance.

SDK sends no configured temperature/topP/topK/thinkingBudget in this request. No last model-turn prefill. `returnThinking(false)` only controls returned thinking parsing; it does not disable billed reasoning. Default model thinking remains minimal;128 tokens can still truncate. STOP-only acceptance, unknown usage rejection, total-minus-input output accounting and timeout/retry behavior retained. No model-specific live request tried.

## 4. CandidateCount solution

For exact `gemini-3.5-flash-lite` only, `GeminiSmokeWire` requires the exact Google HTTPS POST generateContent endpoint and fixed serialized request shape. Root must contain only contents/systemInstruction/generationConfig. The generation config must contain the pinned SDK's reviewed six fields, exact maxOutputTokens, candidateCount1, application/json, unchanged fixture responseJsonSchema and false responseLogprobs/enableEnhancedCivicAnswers. Full fixed system/user text and roles must match; no tools, cached content or arbitrary parameters.

Strict duplicate/trailing/depth parsing,16KiB request parsing cap and integer range checks fail closed. The sole mutation is removal of candidateCount; headers/method/URL are copied unchanged, schema and message nodes are equal before/after. Regression asserts full tree equality after that sole deletion. Other models return the original HttpRequest unchanged. Any changed field/schema/fixture/content/endpoint fails UNSUPPORTED_PROTOCOL before transport. No SDK fork/reflection mutation/upgrade, second HTTP exchange or retry. Final serialized input is still admitted against8192 estimate; response64KiB cap, deadline and cancellation remain unchanged.

## 5. First-call permit

Separate states are explicit:

| Evidence | Representation | Meaning |
|---|---|---|
| Offline source/socket/wire tests | OFFLINE_TRANSPORT_SAFETY_VERIFIED | Reviewed offline safety; not live proof |
| Operator's conditional authorization | FIRST_CALL_AUTHORIZED | One fixed internal attempt if every other gate passes |
| Received HTTP status after execution | LIVE_TRANSPORT_OBSERVED diagnostic | Observation only; never automatically persists VERIFIED |

`FirstCallPermit` has a private constructor inside GeminiSyntheticSmoke; only package-owned harness authorization can mint it. It owns the compile-time evidence-empty-v1 ChatRequest, creating no arbitrary prompt/fixture selector. It binds immutable agent/limit object identities, exact model, current thread and request identity; AtomicBoolean consumes once. Generic generate still needs the existing identity-bound synthetic grant. `fixture(...)` cannot be used on a first-call adapter. Copied, cross-thread, reused, arbitrary, flag-based and tool/domain-shaped requests fail before HTTP.

CLI-only approval record is not registered in the ordinary application (asserted by foundation regression). Normal ProviderConfiguration still requires its prior transport VERIFIED path and cannot consume operator-only first-call permissions. Orchestrator using a first-call adapter is tested to fail SYNTHETIC_BOUNDARY with zero model/domain exchange. Production CLI additionally claims one atomic execution slot; shared JVM budget admits only one process request. No global switch replaces all controls.

First-call validation requires exact reviewed model, feature/live/synthetic flags; model documentation/wire VERIFIED; account-region-Free-Tier/data/quota-billing OPERATOR_CONFIRMED; pre-call transport NOT_VERIFIED; all four separate smoke approvals; exact budget/rates; key variable name GEMINI_API_KEY; existing key shape checks; timeout<=10s. All individual missing/mismatched conditions tested fail closed. No data terms or financial gate bypass.

## 6. Operator attestations

Recorded from the latest user message, **OPERATOR_CONFIRMED, not authenticated Google verification**:

- Exact model/project quota belongs to the project owning the key; RPM15, input TPM250000, RPD500.
- Project Free Tier; no linked paid billing.
- No other quota consumer during smoke.
- Free Tier synthetic-fixture data terms reviewed and accepted.
- Key configured locally in IntelliJ Run Configuration named Aiagentapplication.

No repeated privacy acceptance request is needed. Actual account/execution-region applicability still requires operator evidence for the combined account-region gate; a timezone is not proof. These confirmations were recorded in this report, not silently installed as VERIFIED runtime settings. No authenticated key/model/billing/quota check or account mutation performed.

## 7. Key JVM presence and IntelliJ

Packaged preflight ran in the standalone JVM available to this task: **GEMINI_API_KEY_PRESENT=false**. No value/length/prefix/suffix/hash printed. Key availability in the user's IntelliJ smoke JVM is **NOT_VERIFIED**, not inferred from a differently named run configuration. No secret copied into this shell, arguments, source or YAML; no dotenv/private IDE inspection.

Source `AiAgentApplication.main` starts the regular Boot application; it has no automatic inference hook and remains unchanged. Source `GeminiSyntheticSmoke.main` is the separate non-web CLI. The private IntelliJ configuration's actual selected class could not be inspected under the user's prohibition, so the name Aiagentapplication is not asserted as proof of its mapping.

[README first-call instructions](../../ai-agent-service/README.md#fixed-first-call-acceptance-10b4a-live-fix) describe a separate local Application configuration: main com.example.aiagent.model.GeminiSyntheticSmoke, Java21, Agent module classpath, backend working directory, operator-managed environment secret. Empty program arguments run preflight only and print key presence in that exact JVM. Run configurations do not automatically inherit another configuration's environment. No key in shared config/VM options/program arguments. This task did not create/edit IntelliJ settings or launch the full application.

## 8. Offline validation

Final `mvn -o -B -ntp -pl ai-agent-service package`: **BUILD SUCCESS**,13:03:51+07:00; **348 tests,0 failures,0 errors,0 skipped**.

| Suite | Fresh PASS count |
|---|---:|
| Foundation |32 |
| New first-call/wire/diagnostic tests |50 |
| Existing Gemini SDK/provider/synthetic boundary |75 |
| Apache transport (including new adapted-wire loopback case) |17 |
| Orchestration |66 |
| READ adapter regressions |108 |

Original297 cases retained plus51 new cases; one additive foundation assertion verifies no smoke approval bean in normal application. Tests deny external connections; sockets are synthetic127.0.0.1 only. Fake contracts/tool schemas remain covered. No frontend/hosted DB/live Google/real Gateway/provider tests run. No new dependency analysis required because dependencies unchanged.

Intermediate results reported separately: first341-case run had one failure because the duplicate-key fixture's whitespace-sensitive string replacement did not actually create duplicate JSON. Fixed the fixture to insert a guaranteed duplicate root field, retained rejection assertion; next341 passed. Later346 passed. Adding final orchestrator/socket coverage initially hit a test compile error for checked ToolRegistry.close; declared test exception, then final348 passed. No assertions weakened/skipped/deleted. Existing Java21 SecurityManager/ByteBuddy warnings remain.

Packaged preflight-only CLI (no arguments), executed after final package:

```text
provider=FAKE; model=fake-v1; reviewedModel=UNSET; fixture=evidence-empty-v1
agentEnabled=false; liveEnabled=false; syntheticOnly=false
runCap=0; processCap=0; concurrencyCap=0; inputTokenCap=0; outputTokenCap=0; exposure=0
all runtime verification/first-call attestations=NOT_VERIFIED
GEMINI_API_KEY_PRESENT=false; First-call local gates=FAIL
liveInference=NOT_RUN; liveTransport=NOT_VERIFIED
```

Exit0 means dry-run summary **PASS**; safe-default execution gates **FAIL/BLOCKED**. No permit/provider constructed or HTTP invoked by preflight; boolean environment lookup now occurs intentionally. Tests separately prove exact-envelope local checks PASS with simulated test-owned evidence, not operator readiness.

Git diff/check, direct new-file whitespace/high-confidence key/JWT/private-key pattern inspection and relative link checks PASS. Scope excludes IDE/private settings from inspection. No Place/frontend/domain changes by this task. These checks are scoped, not a comprehensive secret scanner or production security certification.

## 9. Exact execution envelope

Fixed, not widened: model gemini-3.5-flash-lite; fixture evidence-empty-v1; input8192/output128; run1/process1/concurrency1; local exposureUSD0.01; upper rates input1/output3 USD per million. Reservation `(8192*1+128*3)/1,000,000 = USD0.008576`. [Public standard pricing](https://aistudio.google.com/docs/pricing#gemini-3.5-flash-lite) reviewed23/11/2026 lists free quota and paid text rates$0.30/$2.50 including thinking; recheck before future execution. No paid priority/batch/grounding selection.

Provider timeout default10s or lower remaining deadline; turn hard cap30s unchanged. Retry0, no fallback/tools/domain/Gateway/real hotel/customer data/second attempt/scheduler/background job. Failed reservations remain charged; local ledger does not survive restart or coordinate shared project users and is not a global billing guarantee.

## 10. Was a live request sent?

**No. NOT_RUN**, both because current task says stop after offline fixes and because key is absent in the task's smoke JVM. No execute-fixed-synthetic invocation, direct SDK/curl/Postman bypass, model-list/key-validation/countTokens request or inference retry performed. Conditional approval is not consumed by an offline test.

## 11. Attempts and retries

Live Google transport attempts **0**, retries **0**, real domain/Gateway calls **0**. Test HTTP exchanges and public documentation/source reads are distinct. Diagnostic attempt count is an admitted transport invocation, not proof Google received bytes; timeout may leave remote acceptance unknown. Process execution latch and retained reservation prevent a second same-JVM attempt through production CLI.

## 12. Sanitized outcome

Live **NOT_RUN**; no false transport VERIFIED/OBSERVED evidence. Future CLI output is bounded scalar Result: reviewed model/fixture, attempts/retries, typed category, HTTP class, HTTP200 acceptance boolean or unknown, grounding outcome, usage or null, allowlisted finish, run latency, process exposure and observed transport state. Received200 is not model-entitlement or semantic-success proof. No raw prompt/completion/body/key/headers/session/customer labels. Failed execution exits2; no retry/fallback.

## 13. Usage and latency

Actual live prompt/candidate/total tokens and latency **NOT_AVAILABLE**, not zero. No live reservation. Offline synthetic values only exercise diagnostics and are not presented as usage/cost of Google. Unknown usage stays null and fails closed. MAX_TOKENS is a failure, not successful empty evidence or permission to increase128.

## 14. GroundingGate

Live **NOT_RUN**. Offline success validates fixed empty-evidence selection and yields deterministic clarification, no factual hotel/customer claim. Unknown evidence/tool output fails. Existing authoritative parser/ledger/policy contracts unchanged. Diagnostics do not bypass grounding even if HTTP succeeds.

## 15. Remaining blockers

Code/wire/permit/diagnostics fixes complete offline. Execution remains blocked until the intended separate IntelliJ smoke JVM reports key presence and operator config truthfully supplies reviewed gates, including applicable region eligibility. README documents the exact setup without secret transfer. No need to re-confirm the accepted data terms or already confirmed model/quota/billing/no-consumer facts unless they change. This turn stops without live; do not infer execution readiness from348 offline tests.

## 16. Batch10B.4B readiness

**NOT_READY / NOT_STARTED.** Offline fixes are reviewable; Google TLS/model acceptance/usage/schema/latency remain unobserved. No public chat, natural-language extraction, ingress, frontend, Booking WRITE, reservation/payment or production activation. Stop after10B.4A-LIVE-FIX.
