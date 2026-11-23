# Batch 10B.1 — LangChain4j compatibility và skeleton report

Ngày22/11/2026, Asia/Ho_Chi_Minh. **DONE cho10B.1 offline foundation; Batch10B overall chưa completed.** Không tự chuyển10B.2. Không live Gemini/OpenAI, hosted Supabase, provider khách sạn/thanh toán, Docker stack, migration hay domain calls.

## 1. Baseline Git

- Backend branch `ci/github-actions`, HEAD `5f7d22b16f8a7ff4e3d8d68e45f84dd5b64851cd`.
- Frontend branch `fix/frontend-ci-remediation`, HEAD `1c972c009a1439c85b7673895d31f932ec833b06`; clean trước và sau công việc.
- Backend trước chỉnh sửa: `.idea/compiler.xml`, `.idea/encodings.xml` modified; năm Markdown10A untracked: architecture, tool design, evaluation, architecture review, implementation plan. Giữ IDE edits nguyên trạng; chỉnh tài liệu10A đúng phạm vi task, giữ phần lịch sử/bằng chứng còn lại. Không stage/commit/push/stash/reset/clean/checkout.
- Đọc root AGENTS.md; discovery cả hidden files không phát hiện AGENTS service-specific. Đọc canonical PLAN/AUDIT dưới `Docs/standardization`, sáu tài liệu AI yêu cầu và source controller/service/error/tracing/POM liên quan.
- Source và tài liệu khớp baseline10A nhưng không suy từ báo cáo cũ: root aggregator13modules trước thay đổi, mỗi service có Boot parent riêng, root không phải inherited dependency parent. Chưa có Agent module.
- Contradictions: PLAN/AUDIT còn historical text Gateway no tests/tracing bên cạnh checkpoint9G/9H mới; source có GatewayTrace/filter/tests. READ_TOOL_CONTRACT còn Gateway NOT_RUN ở intro9B, retry-once gợi ý và contact opt-in; không dùng các câu lịch sử này để override10B.1 retry0 hay proposed minimal projection. Architecture10A còn framework candidate và D01–D08 pending; checkpoint/register cập nhật theo chỉ thị hiện tại, không giả định các đề xuất10A khác đã duyệt. Không sửa historical reports hoặc canonical PLAN/AUDIT để làm đẹp trạng thái.

## 2. Compatibility findings

Nguồn chính thức kiểm ngày22/11/2026:

- [LangChain4j Spring Boot integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/): starter line hiện tại beta, Boot3.5+/4, không chèn vào Boot3.3.4.
- [Core Maven metadata](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-core/maven-metadata.xml), [Gemini metadata](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-google-ai-gemini/maven-metadata.xml), [OpenAI metadata](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-open-ai/maven-metadata.xml).
- [Pinned Gemini POM](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-google-ai-gemini/1.17.0/langchain4j-google-ai-gemini-1.17.0.pom), [pinned OpenAI POM](https://repo.maven.apache.org/maven2/dev/langchain4j/langchain4j-open-ai/1.17.0/langchain4j-open-ai-1.17.0.pom).
- [Gemini integration](https://docs.langchain4j.dev/integrations/language-models/google-ai-gemini/), [OpenAI integration](https://docs.langchain4j.dev/integrations/language-models/open-ai/), [chat memory](https://docs.langchain4j.dev/tutorials/chat-memory/). Rolling docs là hướng dẫn, tagged source/POM và compiled tests là bằng chứng pinned version.
- [Gemini1.17.0 source](https://github.com/langchain4j/langchain4j/blob/1.17.0/langchain4j-google-ai-gemini/src/main/java/dev/langchain4j/model/googleai/GoogleAiGeminiChatModel.java), [OpenAI1.17.0 source](https://github.com/langchain4j/langchain4j/blob/1.17.0/langchain4j-open-ai/src/main/java/dev/langchain4j/model/openai/OpenAiChatModel.java).

Spike **trước skeleton**: temporary isolated POM trong OS temp, Boot3.3.4 + Java21 + Gemini/OpenAI.1.24.0 resolve/compile được nhưng OpenAI kéo `langchain4j-reactive-streaming:1.24.0-beta34`; rejected cho application vì chưa được phép dùng beta. POM1.20.2/1.22/1.23 cũng có reactive beta;1.17.0 không có. Spike1.17.0 compile release21/dependency:tree SUCCESS, không cần thay framework hoặc nâng Boot. Download artifact/metadata không phải provider calls.

| Capability | Kết quả và giới hạn |
|---|---|
| Java21 | javac release21, Temurin21.0.3; actual module compile/test PASS |
| Boot |3.3.4 context PASS với core; không dùng Spring starter |
| Cloud | Agent không có Cloud BOM/client; các module giữ2023.0.2/2023.0.3, Config4.0.0/Cloud2025.1.0-RC1, Eureka3.3.0 như baseline |
| Gemini/OpenAI | Integration libraries1.17.0 resolve/compile, builders instantiate offline, fixture JSON text/usage exchange qua HTTP SPI giả PASS; không live/runtime provider acceptance |
| Tools | ToolSpecification + AiMessage.toolExecutionRequests giữ id/name/arguments; không callback/execution engine. SDK tool/schema behavior phụ thuộc model, chưa live verified |
| Structured output | ResponseFormat/JsonSchema compile/test contract; chưa claim schema adherence trên model thật |
| Memory | Không ChatMemory/AI Services; caller cấp messages từng exchange. Chưa session/history/revision store; memory eviction khác user-visible history |
| Timeout/cancel | CallControl monotonic positive deadline≤30s, cooperative cancel/interruption và fake timeout. SDK timeout setters có; live socket/body cancellation/total deadline chưa implemented/proven |
| Retry | SDK mặc định2 theo pinned source; không dựa default. Candidate tests đặt maxRetries(0), synthetic503 cho mỗi provider → đúng1 transport invocation |
| Logging | SDK request/response logging explicitly false trong tests; package dev.langchain4j OFF mặc định; không application payload logger |
| Conflicts | Boot manages Jackson2.17.2 thay declared2.21/2.21.3; constructors và fixture serialization/parsing PASS. Không có dependency beta/snapshot trong LangChain graph đã chọn; chưa chứng minh mọi provider JSON/schema branch |

Không coi builder/fixture tests là live integration. Không dùng Gemini Google GenAI experimental integration. Gemini AI API integration phù hợp hướng API key/Free Tier; quota/pricing/model/account/terms vẫn chưa phê duyệt.

## 3. Versions đã chọn

| Thành phần | Version |
|---|---|
| Java / Maven thực thi | Temurin21.0.3 / Maven3.9.9 |
| Agent Spring Boot parent |3.3.4 |
| LangChain4j core, google-ai-gemini, open-ai, http-client, http-client-jdk |1.17.0 stable |
| Spring Framework (Boot managed) |6.1.13 |
| Jackson / SLF4J (Boot managed) |2.17.2 /2.0.16 |
| Micrometer (Boot managed) |1.13.4; tracing bridge Brave |

Application artifact giữ convention0.0.1-SNAPSHOT của service, không phải dependency framework snapshot. Không import AI BOM vào root, không thay versions của bất kỳ service hiện có.

## 4. Core vs starter decision

**Option A: core được chọn.** Ít coupling với Spring release train, explicit beans, một model exchange mỗi invocation, không framework-owned loop/tool executor/retry/memory. Application có chỗ để bổ sung proposal validation, policy executor, revision, grounding, cancellation ở phase sau.

Option B có auto configuration/AI Services tiện lợi nhưng starter hiện beta và baseline Boot mismatch; không được phê duyệt beta, không cần Boot riêng mới hơn để hoàn thành batch này. Core không tự đảm bảo security; policy executor vẫn phải triển khai và kiểm thử trước domain tools. Không kết luận core tốt hơn cho mọi dự án.

## 5. Files changed

- Root `pom.xml`: chỉ thêm module ai-agent-service.
- `ai-agent-service/pom.xml`, `README.md`.
- `src/main/java/com/example/aiagent/AiAgentApplication.java`.
- `config/AgentProperties.java`.
- `model/LlmProviderAdapter.java`, `FakeLlmProviderAdapter.java`, `ProviderException.java`, `ProviderConfiguration.java`.
- `observability/RequestCorrelationFilter.java`.
- `src/main/resources/application.yml`.
- `src/test/java/com/example/aiagent/AgentFoundationTest.java`.
- Năm tài liệu10A/10B plan có sẵn untracked được cập nhật checkpoint/decision; report hiện tại được tạo mới. READ_TOOL_CONTRACT và historical reports không sửa.

Không thay application source/config Gateway, Hotel, Booking, Payment/Refund, Place hoặc frontend; không schema/migration/proto/event edits. Maven compile sinh ignored target output qua lifecycle chuẩn, không hand-edit generated output.

## 6. Architecture implemented

Spring MVC application độc lập, loopback127.0.0.1:8091 mặc định. Không discovery registration/config fetch, DB/JPA, Flyway, RabbitMQ, domain gRPC hoặc service implementation dependency. Không privileged identity/secrets resolution. Actuator health/liveness/readiness chỉ chứng minh process/context, không model hoặc domain readiness.

Không chat controller/placeholder. POST proposed chat path404 cả khi không có endpoint, không gọi provider. Chưa có HTTP chat error boundary để claim standard chat envelope; sẽ reuse conventions tại phase ingress. Request correlation filter phát X-Request-ID từ active span, fallback UUID, Cache-Control:no-store; không trust caller request ID làm auth.

## 7. Provider abstraction

`generate(ChatRequest, CallControl) → ChatResponse`: LangChain4j-native provider-neutral messages, per-request model options, tool declarations, JSON schema, typed AI proposal structures, token usage (nullable), finish reason (nullable), metadata được giữ. Không lossy string-only wrapper. Objects này chứa payload/raw metadata: tuyệt đối không log toString hoặc gửi nguyên metadata downstream.

Typed ProviderException category DISABLED/TIMEOUT/CANCELLED/UNAVAILABLE/MALFORMED_RESPONSE, fixed safe messages, không giữ raw cause/body. Fake support deadline/cancel; live mapping/auth/rate-limit/provider-specific error categories và cancellation transport là việc sau khi có approval. Orchestration không import Gemini/OpenAI; provider-specific references hiện chỉ nằm trong compatibility tests.

## 8. Fake provider behavior

Immutable deterministic scenarios TEXT/TOOL_PROPOSAL/TIMEOUT/FAILURE/MALFORMED. Default trả câu có nhãn fake, không giả facts khách sạn. Tool proposal là data id/name/JSON arguments synthetic, không thực thi. Fixed usage12input/8output, finish STOP hoặc TOOL_EXECUTION.

Constructor scripted ChatResponse cho tests phase sau cung cấp arbitrary proposals/metadata/usage; giữ unknown usage/finish reason là null, không tạo zero. SDK response builder tự từ chối null aiMessage; scenario MALFORMED mô phỏng typed parser failure. Scenario chỉ do test/constructor chọn, không do user text. Không network/key/file lookup.

## 9. Configuration và feature flags

| Property dưới checkinn.agent | Default / validation |
|---|---|
| enabled |false; bean gate chặn trước counter/model invocation |
| provider |FAKE; GEMINI/OPENAI/unknown bị từ chối startup trong10B.1 |
| model-id |fake-v1; bounded identifier, chưa chọn real model |
| api-key-environment-variable |GEMINI_API_KEY; chỉ tên nguồn, không đọc env hoặc bind secret |
| model-timeout |10s, positive≤turn deadline (default kỹ thuật, không duyệt đề xuất8s10A) |
| tool-timeout |10s, positive≤10s và≤turn |
| turn-deadline |30s, positive≤30s |
| http-attempts / tool-proposals |6 /6, range1..6 |
| model-requests / tool-rounds |3 /2, range1..3 /1..2 |
| automatic-retries |0 only |
| log-payloads |false only |

Unknown properties fail binding, không silent typo. Có thể giảm budgets; không tăng hard caps. Đây là validated configuration foundation, chưa phải runtime turn-budget executor. Feature ON chỉ cho internal fake generation; không tạo ingress/tools/live calls. Actuator chỉ expose health, không env/configprops/prometheus endpoint mặc định; metrics tồn tại trong registry cho wiring sau.

## 10. Tests và commands

Chạy từ backend, ngày22/11/2026:

| Command/check | Result |
|---|---|
| java -version / mvn -version |Java21.0.3 / Maven3.9.9 |
| Maven Central metadata/POM và tagged official source review |PASS; phát hiện beta transitive1.24.0 |
| `mvn -N -B -ntp dependency:get "-Dartifact=dev.langchain4j:langchain4j-google-ai-gemini:1.24.0"` và OpenAI cùng version |SUCCESS (candidate research only) |
| Temporary spike `mvn -f <temp>/checkinn-10b1-compat/pom.xml -B -ntp compile dependency:tree` cho1.24.0 rồi1.17.0 |SUCCESS; chọn1.17.0 sau tree inspection |
| `mvn -B -ntp -pl ai-agent-service test` |27/0fail/0error/0skip tại intermediate checkpoint |
| `mvn -o -B -ntp -pl ai-agent-service test` cuối |**32/0fail/0error/0skip**, BUILD SUCCESS19:04:02 |
| `mvn -B -ntp -pl ai-agent-service dependency:tree -Dverbose "-DoutputFile=target/dependency-tree.txt"` |SUCCESS |
| `mvn -B -ntp -pl ai-agent-service help:effective-pom "-Doutput=target/effective-pom.xml"` |SUCCESS |
| `mvn -B -ntp -pl '!place-service' -DskipTests compile` |**BUILD SUCCESS**,13service + root14entries19:03:06; Place excluded; tests NOT_RUN by compile |
| git diff --check + direct new-file whitespace/UTF-8/link checks |PASS (final review) |

32tests gồm11 test methods +21 invalid configuration variants. Coverage: context/no key/no DB/AMQP/RPC, featureOFF/no model invocation, health/readiness/chat404, W3C continuation at local health, deterministic text/usage/finish, tool proposal/schema contract/no execution, faults, cancel/deadline, scripted optional metadata, provider builder/key nonlogging, candidate JSON serialization/usage parsing with fixture HTTP SPI,503retry0, invalid caps/provider/typo validation, reduced-limit enabled fake/metrics.

Test JVM Java21 SecurityManager deny-all outbound connect guard installed before context, zero checkConnect invocations asserted after tests; Maven -o also prevents artifact fetch. MockMvc không mở server socket; SDK fixture SPI không network. Guard là test-only deprecated Java21 facility, không production security bypass; cần thay khi nâng Java. Dynamic Mockito agent/deprecation warnings không phải skipped assertions.

Intermediate failures đã sửa, không che giấu: initial unquoted PowerShell -Dartifact split gây Maven plugin-resolution failure (không build service); initial AssertJ Runnable compile mismatch; test JVM thiếu java.security.manager=allow; một test tạo null-aiMessage fixture bị SDK từ chối trước fake. Đã quote argument, sửa callable, cấu hình test-only guard và assert đúng SDK boundary; typed MALFORMED test vẫn giữ. Một Python edit attempt lỗi decode trước khi ghi file, rerun UTF-8. Final32tests không fail/skip.

**NOT_RUN:** existing backend unit/integration suites, frontend/npm/Playwright, full reactor gồm Place, database/provider/canonical Docker/Gateway integration,40case evaluation/200model runs. Không cần paid booking/refund lifecycle. Compile không chứng minh regression behavioral của tất cả service hoặc production readiness.

## 11. Dependency tree findings

Resolved graph excerpt:

```text
ai-agent-service (Boot parent3.3.4)
+- langchain4j-core:1.17.0
+- langchain4j-google-ai-gemini:1.17.0
|  +- langchain4j-http-client:1.17.0
|  +- langchain4j-http-client-jdk:1.17.0 (runtime)
|  +- jackson-annotations:2.17.2 (managed from2.21)
|  +- jackson-core/databind:2.17.2 (managed from2.21.3)
+- langchain4j-open-ai:1.17.0
|  +- same core/http/Jackson (duplicates omitted)
|  +- jtokkit:1.1.0
+- slf4j-api:2.0.16
+- micrometer-core/observation:1.13.4
```

Verbose tree generated at `ai-agent-service/target/dependency-tree.txt`, effective POM at `target/effective-pom.xml` (ignored build evidence, regenerate with commands above). No unresolved artifact/omitted-for-conflict entries in selected tree; no AI prerelease dependency. Boot version management overrides are visible, not hidden. Fixture tests cover real SDK serializer/parsers with managed Jackson, not every provider feature; live branch compatibility remains a risk to verify before enablement.

Root aggregation adds no shared dependency/BOM to sibling modules. Existing selected reactor compile SUCCESS, so no observed compile conflict affecting existing services. Không claim toàn ecosystem conflict-free/security audited.

## 12. Security checks

No live provider beans, API-key reads, outbound transport/tool executors, DB/migration/discovery clients, domain writes, arbitrary HTTP URL/SQL/code/browser/Place tools. No JWT/cookie/header forwarding; there is no domain HTTP client. No fallback Gemini→OpenAI. Disabled provider gate tested before counting/generation.

Only fixed safe provider error strings; no prompt/response/tool payload logs by application. SDK logging OFF, payload logging config rejected, synthetic key/message/response sentinels absent from captured logs in applicable tests. No real credentials in fixtures. Health-only actuator exposure and loopback binding reduce accidental ingress; this is not ingress admission/auth policy completion.

W3C trace continuity proven only for MockMvc Agent health request with known traceparent. Không Gateway→Agent→Gateway or outbound trace verified. No external collector/exporter configured.

## 13. Limitations

No Gemini/OpenAI live adapter implementation/acceptance. Candidate integrations **design + compile + offline SDK fixture verified**, not live runtime verified. No10READ tool adapters/registry/policy executor, full turn loop, evidence grounding, revision store, persistent memory, public chat, frontend, Gateway route, Docker image/stack wiring,40case evaluator hoặc production readiness.

CallControl cooperative deadline/cancel suffices for immediate fake, not proof a future blocking SDK call can be aborted. Per-turn budgets currently configuration only. Live transport must enforce remaining time, cancel in-flight work, disable retries and sanitize errors before any future activation. Fake scripted usage is synthetic, not billed token/cost evidence.

Managed Jackson is older than SDK declared dependencies; limited offline exchange PASS does not close all live compatibility risks. No upgrade of all microservices attempted. Boot3.3.4 retained for compatibility baseline, not a claim about current production support/security posture.

## 14. Remaining decisions

D01 APPROVED; D02 PARTIALLY_APPROVED Gemini first/OpenAI later, real model/account/region/data terms/finite spend cap pending. D03 APPROVED only stated hard-limit subset; all other10A caps remain proposals. D06 APPROVED local pilot/OFF only. D04 tool allow-list/projection, D05 ingress/state/privacy/retention, D07 evaluation thresholds, D08 operator/release approval remain NOT_APPROVED. See current [decision register](BATCH10A_ARCHITECTURE_REVIEW.md#decisions-required).

No silent approval inferred; no real calls until budget and provider data terms explicitly approved even if Free Tier. No provider model chosen from fixture identifiers.

## 15. Readiness for Batch 10B.2

**Technical foundation READY for review**: isolated Java21 module compiles, core compatibility checked, fake offline/no key, OFF default, no domain tools/DB/migration, hard-cap validation,32tests, selected-reactor compile, report and extension boundary present.

Proceeding10B.2 requires user review/instruction and D04 allow-list/projection approval. Do not mark all10B complete or start10B.2 automatically. Deployment/UI/live-provider readiness remains closed.
