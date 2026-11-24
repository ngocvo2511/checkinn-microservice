package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.orchestration.*;
import com.example.aiagent.tools.ToolRegistry;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.security.Permission;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.ProviderException.Category.*;

/** Actual pinned Gemini SDK/serializer/parser; fake HTTP SPI, with external networking denied. */
@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("removal")
class GeminiProviderTest {
    static SecurityManager previous;
    static final AtomicInteger network = new AtomicInteger();
    static final String MODEL = "gemini-offline-fixture"; // Deliberately NOT a deployable/verified model.
    final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    @BeforeAll static void noNetwork() {
        previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) { network.incrementAndGet(); throw new SecurityException("Offline SDK test"); }
        });
    }
    @AfterAll static void restore() { System.setSecurityManager(previous); assertThat(network).hasValue(0); }
    @AfterEach void closeMeters() { meters.close(); }
    static AgentProperties agent() {
        return new AgentProperties(true, AgentProperties.Provider.GEMINI, MODEL, "GEMINI_API_KEY", Duration.ofSeconds(2),
                Duration.ofSeconds(2), Duration.ofSeconds(3), 6, 6, 3, 2, 0, false);
    }
    static GeminiProperties limits(String... overrides) {
        var values = new HashMap<String, String>();
        values.put("live-enabled", "true"); values.put("synthetic-only", "true"); values.put("reviewed-model-id", MODEL);
        for (String field : List.of("model-verification", "account-region-free-tier-verification", "data-terms-verification", "quota-billing-verification", "transport-acceptance"))
            values.put(field, "VERIFIED"); // Test-owned simulated attestations, no operator evidence generated.
        values.put("max-input-tokens", "8192"); values.put("max-output-tokens", "128");
        values.put("max-run-requests", "1"); values.put("max-process-requests", "3"); values.put("max-concurrent-requests", "1");
        values.put("max-exposure-usd", "0.10"); values.put("input-usd-per-million-upper-bound", "1"); values.put("output-usd-per-million-upper-bound", "2");
        for (String override : overrides) { var pair = override.split("=", 2); values.put(pair[0], pair[1]); }
        return new Binder(new MapConfigurationPropertySource(values)).bind("", Bindable.of(GeminiProperties.class)).get();
    }
    static LlmProviderAdapter.CallControl control() { return new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)); }
    static void error(Runnable action, ProviderException.Category category) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ProviderException.class,
                ex -> { assertThat(ex.category()).isEqualTo(category); assertThat(ex.getCause()).isNull(); });
    }
    static String response(String parts) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":" + parts
                + "},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":8,\"totalTokenCount\":20}}";
    }
    static String text() { return response("[{\"text\":\"{\\\"evidenceIds\\\":[]}\"}]"); }
    final class FakeExchange implements GeminiLlmProviderAdapter.Exchange {
        final AtomicInteger calls = new AtomicInteger();
        final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
        String raw = text(); int status = 200;
        @Override public SuccessfulHttpResponse execute(HttpRequest request, LlmProviderAdapter.CallControl control, Duration timeout) {
            calls.incrementAndGet(); requests.add(request);
            if (status != 200) throw new dev.langchain4j.exception.HttpException(status, "synthetic-raw-error-do-not-leak");
            return SuccessfulHttpResponse.builder().statusCode(status).headers(Map.of()).body(raw).build();
        }
    }
    GeminiLlmProviderAdapter provider(GeminiLlmProviderAdapter.Exchange fake) {
        return new GeminiLlmProviderAdapter(agent(), limits(), meters, "synthetic-noncredential", fake, new GeminiBudget());
    }
    @Test void realSdkSerializesStructuredFixtureAndParsesUsageWithoutPayloadLogs(CapturedOutput output) throws Exception {
        var fake = new FakeExchange(); var result = provider(fake).fixture(SyntheticGeminiFixtures.SMOKE, control());
        assertThat(result.aiMessage().text()).isEqualTo("{\"evidenceIds\":[]}");
        assertThat(result.finishReason().name()).isEqualTo("STOP");
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(12);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(8);
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(20);
        assertThat(fake.calls).hasValue(1);
        var sent = fake.requests.getFirst(); var json = GeminiLlmProviderAdapter.JSON.readTree(sent.body());
        assertThat(sent.url()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent");
        assertThat(json.path("generationConfig").path("maxOutputTokens").intValue()).isEqualTo(128);
        assertThat(json.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(json.path("generationConfig").path("responseJsonSchema").path("additionalProperties").booleanValue()).isFalse();
        assertThat(json.has("tools") && !json.path("tools").isNull() && !json.path("tools").isEmpty()).isFalse();
        assertThat(sent.body()).doesNotContain("synthetic-noncredential", "Authorization", "Cookie", "ownerId", "contactEmail");
        assertThat(sent.headers().keySet()).allMatch(k -> Set.of("content-type", "user-agent", "x-goog-api-key").contains(k.toLowerCase(Locale.ROOT)));
        assertThat(output).doesNotContain("synthetic-noncredential", result.aiMessage().text(), "Synthetic scenario");
        assertThat(meters.counter("agent.gemini.requests").count()).isEqualTo(1);
    }
    @Test void textToolProposalsAndToolResultMessagesTraverseActualSdkAcrossSeparateCalls() throws Exception {
        var fake = new FakeExchange(); var provider = provider(fake);
        assertThat(provider.fixture("text-v1", control()).aiMessage().text()).contains("evidenceIds");
        fake.raw = response("[{\"functionCall\":{\"name\":\"list_accommodation_types\",\"args\":{}}}]");
        var proposal = provider.fixture("tool-v1", control());
        assertThat(proposal.aiMessage().toolExecutionRequests()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("list_accommodation_types"); assertThat(p.arguments()).isEqualTo("{}");
        });
        assertThat(proposal.finishReason().name()).isEqualTo("TOOL_EXECUTION");
        var tools = GeminiLlmProviderAdapter.JSON.readTree(fake.requests.get(1).body()).path("tools");
        assertThat(tools.get(0).path("functionDeclarations").get(0).path("name").asText()).isEqualTo("list_accommodation_types");
        assertThat(fake.requests.get(1).body()).doesNotContain("responseJsonSchema");
        fake.raw = text(); provider.fixture("tool-result-v1", control());
        assertThat(fake.requests.get(2).body()).contains("functionCall", "functionResponse", "types");
        assertThat(fake.calls).hasValue(3);
        error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED);
        assertThat(fake.calls).hasValue(3);
    }
    @ParameterizedTest @ValueSource(ints = {301, 302, 307, 308, 429, 500, 503})
    void errorsHaveOneAttemptNoRetryRedirectOrFallback(int status) {
        var fake = new FakeExchange(); fake.status = status; var provider = provider(fake);
        error(() -> provider.fixture("text-v1", control()), status == 429 ? QUOTA_EXCEEDED : UNAVAILABLE);
        assertThat(fake.calls).hasValue(1);
        if (status == 429) {
            error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED);
            assertThat(fake.calls).hasValue(1);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"", "null", "{}", "[]", "{", "{\"candidates\":[]}", "{\"candidates\":null}"})
    void malformedOrEmptyJsonFailsClosed(String raw) {
        var fake = new FakeExchange(); fake.raw = raw;
        error(() -> provider(fake).fixture("text-v1", control()), MALFORMED_RESPONSE); assertThat(fake.calls).hasValue(1);
    }
    @ParameterizedTest @ValueSource(strings = {"OTHER", "MAX_TOKENS", "FINISH_REASON_UNSPECIFIED", "FUTURE_NEW_REASON", "MALFORMED_FUNCTION_CALL"})
    void unsupportedFinishReasonsAreNeverSuccessfulEmptyAnswers(String reason) {
        var fake = new FakeExchange(); fake.raw = text().replace("STOP", reason);
        error(() -> provider(fake).fixture("text-v1", control()), MALFORMED_RESPONSE);
    }
    @ParameterizedTest @ValueSource(strings = {"SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"})
    void safetyRefusalIsTypedBeforeSdkAttemptsEmptyAiMessage(String reason) {
        var fake = new FakeExchange(); fake.raw = text().replace("STOP", reason);
        error(() -> provider(fake).fixture("text-v1", control()), SAFETY_REFUSAL);
    }
    @Test void promptSafetyFeedbackIsTypedWithoutCandidateOrUsage() {
        var fake = new FakeExchange(); fake.raw = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        error(() -> provider(fake).fixture("text-v1", control()), SAFETY_REFUSAL);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"text\":\"\"}", "{\"text\":\" \"}", "{\"functionCall\":{\"name\":\"x\",\"args\":\"{}\"}}", "{\"inlineData\":{}}"})
    void malformedPartsAreRejected(String part) {
        var fake = new FakeExchange(); fake.raw = response("[" + part + "]");
        error(() -> provider(fake).fixture("text-v1", control()), MALFORMED_RESPONSE);
    }
    @Test void missingUsageIsUnknownNotFabricatedZeroAndStopsProcessBudget() {
        var fake = new FakeExchange(); fake.raw = text().replace("\"usageMetadata\"", "\"notUsage\"");
        var provider = provider(fake); error(() -> provider.fixture("text-v1", control()), UNKNOWN_USAGE);
        fake.raw = text(); error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED);
        assertThat(fake.calls).hasValue(1); assertThat(meters.counter("agent.gemini.usage.unknown").count()).isEqualTo(1);
        assertThat(meters.find("agent.gemini.tokens").counter()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"\"promptTokenCount\":-1", "\"promptTokenCount\":1.5", "\"promptTokenCount\":null"})
    void invalidUsageIsUnknown(String replacement) {
        var fake = new FakeExchange(); fake.raw = text().replace("\"promptTokenCount\":12", replacement);
        error(() -> provider(fake).fixture("text-v1", control()), UNKNOWN_USAGE);
    }
    @Test void actualUsageOverCapsStopsFutureCallsWithoutRefundingReservation() {
        var fake = new FakeExchange(); fake.raw = text().replace("\"totalTokenCount\":20", "\"totalTokenCount\":999999");
        var provider = provider(fake); error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED);
        error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED); assertThat(fake.calls).hasValue(1);
    }
    @Test void estimatedInputCapRejectsBeforeHttp() {
        var fake = new FakeExchange();
        var provider = new GeminiLlmProviderAdapter(agent(), limits("max-input-tokens=1024"), meters, "synthetic-noncredential", fake, new GeminiBudget());
        error(() -> provider.fixture(SyntheticGeminiFixtures.SMOKE, control()), BUDGET_EXCEEDED); assertThat(fake.calls).hasValue(0);
    }
    @Test void finiteExposureReservationRejectsBeforeHttp() {
        var fake = new FakeExchange();
        var provider = new GeminiLlmProviderAdapter(agent(), limits("max-exposure-usd=0.000001"), meters, "synthetic-noncredential", fake, new GeminiBudget());
        error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED); assertThat(fake.calls).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings = {"hello", "{\"synthetic\":true,\"prompt\":\"hello\"}", "{\"hotelId\":\"11111111-1111-4111-8111-111111111111\",\"name\":\"backend-shaped\"}", "Cookie: synthetic-session", "email@example.invalid"})
    void arbitraryUserBackendCredentialAndPiiInputsCannotSelfAttest(String prompt) {
        var fake = new FakeExchange(); var provider = provider(fake);
        error(() -> provider.generate(ChatRequest.builder().messages(UserMessage.from(prompt)).build(), control()), SYNTHETIC_BOUNDARY);
        assertThat(fake.calls).hasValue(0);
    }
    @Test void matchingFixtureContentFromAnotherCallerIsNotTrustedProvenance() {
        var fake = new FakeExchange(); var provider = provider(fake);
        error(() -> provider.generate(SyntheticGeminiFixtures.request("text-v1"), control()), SYNTHETIC_BOUNDARY);
        error(() -> provider.fixture("unapproved-fixture", control()), SYNTHETIC_BOUNDARY); assertThat(fake.calls).hasValue(0);
    }
    @Test void crossThreadAndReentrantRequestsCannotReuseGrant() {
        var holder = new AtomicReference<GeminiLlmProviderAdapter>(); var attempts = new AtomicInteger();
        var provider = provider((request, control, timeout) -> {
            attempts.incrementAndGet();
            error(() -> holder.get().generate(SyntheticGeminiFixtures.request("text-v1"), control()), SYNTHETIC_BOUNDARY);
            try (var worker = Executors.newSingleThreadExecutor()) {
                worker.submit(() -> error(() -> holder.get().generate(SyntheticGeminiFixtures.request("text-v1"), control()), SYNTHETIC_BOUNDARY)).get(1, TimeUnit.SECONDS);
            } catch (Exception ex) { throw new AssertionError(ex); }
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(text()).build();
        }); holder.set(provider);
        provider.fixture("text-v1", control()); assertThat(attempts).hasValue(1);
    }
    @ParameterizedTest @ValueSource(strings = {"live-enabled=false", "synthetic-only=false", "reviewed-model-id=gemini-other",
            "model-verification=NOT_VERIFIED", "account-region-free-tier-verification=NOT_VERIFIED", "data-terms-verification=NOT_VERIFIED",
            "quota-billing-verification=NOT_VERIFIED", "transport-acceptance=NOT_VERIFIED", "max-input-tokens=0", "max-input-tokens=16385",
            "max-output-tokens=0", "max-output-tokens=1025", "max-run-requests=0", "max-run-requests=4", "max-process-requests=0",
            "max-process-requests=10", "max-concurrent-requests=0", "max-concurrent-requests=2", "max-exposure-usd=0", "max-exposure-usd=1.01",
            "input-usd-per-million-upper-bound=0", "output-usd-per-million-upper-bound=-1"})
    void missingOrInvalidGatesAndBudgetsFailInitialization(String setting) {
        error(() -> new GeminiLlmProviderAdapter(agent(), limits(setting), meters, "synthetic-noncredential", new FakeExchange(), new GeminiBudget()), CONFIGURATION);
    }
    @Test void missingKeyFailsWithoutAnyNetworkOrRawSecretException() {
        for (String key : Arrays.asList(null, "", " ", "synthetic\r\nheader"))
            error(() -> new GeminiLlmProviderAdapter(agent(), limits(), meters, key, new FakeExchange(), new GeminiBudget()), CONFIGURATION);
    }
    @Test void apiKeyAloneDoesNotEnableLiveAndPreflightDoesNotContainKey(CapturedOutput output) {
        var defaults = new Binder(new MapConfigurationPropertySource(Map.of("live-enabled", "false")))
                .bind("", Bindable.of(GeminiProperties.class)).get();
        error(() -> new GeminiLlmProviderAdapter(agent(), defaults, meters, "synthetic-noncredential", new FakeExchange(), new GeminiBudget()), CONFIGURATION);
        assertThat(GeminiSyntheticSmoke.preflight(agent(), defaults)).contains("fixedRequests=1", "NOT_VERIFIED", "liveEnabled=false")
                .doesNotContain("synthetic-noncredential", "GEMINI_API_KEY");
        assertThat(output).doesNotContain("synthetic-noncredential");
    }
    @Test void cancelledAndExpiredControlsDoNotDispatch() {
        var fake = new FakeExchange(); var provider = provider(fake); var cancelled = control(); cancelled.cancel();
        error(() -> provider.fixture("text-v1", cancelled), CANCELLED);
        error(() -> provider.fixture("text-v1", new LlmProviderAdapter.CallControl(Duration.ofNanos(1))), TIMEOUT);
        assertThat(fake.calls).hasValue(0);
    }
    @Test void preflightSeparatesLocalChecksFromUnverifiedExternalEvidence() {
        var summary = GeminiSyntheticSmoke.preflight(agent(), limits());
        assertThat(summary).contains("reviewedModel=" + MODEL, "concurrencyCap=1", "fixedRequests=1",
                "feature=PASS", "provider=PASS", "live=PASS", "synthetic=PASS", "reviewedModelMatch=PASS",
                "budgets=PASS", "oneRequestExposure=PASS", "keyValidity=NOT_VERIFIED",
                "accountEvidence=NOT_VERIFIED", "liveInference=NOT_RUN", "OPERATOR_ATTESTATIONS_ONLY");
        assertThat(GeminiSyntheticSmoke.preflight(agent(), limits("max-exposure-usd=0.000001")))
                .contains("budgets=PASS", "oneRequestExposure=FAIL");
        assertThat(GeminiSyntheticSmoke.preflight(agent(), limits("reviewed-model-id=unsafe\nmodel")))
                .contains("reviewedModel=INVALID", "reviewedModelMatch=FAIL").doesNotContain("unsafe\nmodel");
    }
    @Test void lateResponseIsRejectedAndConcurrencyIsBounded() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var attempts = new AtomicInteger();
        var provider = provider((request, control, timeout) -> {
            attempts.incrementAndGet(); entered.countDown();
            try { assertThat(release.await(2, TimeUnit.SECONDS)).isTrue(); } catch (InterruptedException ex) { throw new AssertionError(ex); }
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(text()).build();
        });
        var control = control();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> error(() -> provider.fixture("text-v1", control), CANCELLED));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            error(() -> provider.fixture("text-v1", control()), BUDGET_EXCEEDED);
            control.cancel(); release.countDown(); future.get(1, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(attempts).hasValue(1);
    }
    @Test void genericOrchestrationNeverSendsConversationOrCallsGateway() throws Exception {
        var fake = new FakeExchange(); var provider = provider(fake); var clock = Clock.systemUTC();
        try (var registry = new ToolRegistry(true, URI.create("http://127.0.0.1:1"), Duration.ofSeconds(1), Duration.ofSeconds(1), clock);
             var orchestrator = new AgentOrchestrator(agent(), provider, registry, new InMemoryConversationStore(clock), clock, meters)) {
            var result = orchestrator.turn(new AgentOrchestrator.Request("synthetic-local-session", 0, "untrusted arbitrary input",
                    new PolicyExecutor.Scope(PolicyExecutor.Intent.CANCELLATION, null, null), Map.of(), new AtomicBoolean()));
            assertThat(result.answer().category()).isEqualTo("MODEL_SYNTHETIC_BOUNDARY");
            assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().httpAttempts()).isZero();
            assertThat(fake.calls).hasValue(0);
        }
    }
    @Test void existingRegistryRawChildSchemasAndGroundingSchemaTraversePinnedSdkOffline() throws Exception {
        try (var registry = new ToolRegistry(false, URI.create("http://127.0.0.1:1"), Duration.ofSeconds(1), Duration.ofSeconds(1), Clock.systemUTC())) {
            for (var specification : registry.specifications()) {
                var request = ChatRequest.builder().messages(UserMessage.from("Fixed synthetic SDK schema probe."))
                        .toolSpecifications(specification).build();
                var transport = new SdkFixture();
                var model = dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.builder().apiKey("synthetic-noncredential")
                        .modelName(MODEL).maxRetries(0).httpClientBuilder(transport).logRequests(false).logResponses(false).build();
                model.chat(GeminiRequestProtocol.prepare(request));
                assertThat(transport.calls).isEqualTo(1); assertThat(transport.body).contains(specification.name());
            }
            var schema = dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder().additionalProperties(false)
                    .addProperty("evidenceIds", dev.langchain4j.model.chat.request.json.JsonRawSchema.from("{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":6}"))
                    .required("evidenceIds").build();
            var request = ChatRequest.builder().messages(UserMessage.from("Fixed synthetic evidence probe."))
                    .responseFormat(dev.langchain4j.model.chat.request.ResponseFormat.builder().type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
                            .jsonSchema(dev.langchain4j.model.chat.request.json.JsonSchema.builder().name("grounded_evidence_selection").rootElement(schema).build()).build()).build();
            var transport = new SdkFixture();
            var model = dev.langchain4j.model.googleai.GoogleAiGeminiChatModel.builder().apiKey("synthetic-noncredential")
                    .modelName(MODEL).maxRetries(0).httpClientBuilder(transport).logRequests(false).logResponses(false).build();
            model.chat(GeminiRequestProtocol.prepare(request));
            var sent = GeminiLlmProviderAdapter.JSON.readTree(transport.body).path("generationConfig").path("responseJsonSchema");
            assertThat(sent.path("properties").path("evidenceIds").path("maxItems").intValue()).isEqualTo(6);
            assertThat(sent.path("additionalProperties").booleanValue()).isFalse();
            error(() -> GeminiRequestProtocol.prepare(ChatRequest.builder().messages(request.messages()).responseFormat(request.responseFormat())
                    .toolSpecifications(registry.specifications().getFirst()).build()), UNSUPPORTED_PROTOCOL);
        }
    }
    static final class SdkFixture implements HttpClientBuilder, HttpClient {
        int calls; String body; Duration connect, read;
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest request) {
            calls++; body = request.body(); return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(text()).build();
        }
        public void execute(HttpRequest request, dev.langchain4j.http.client.sse.ServerSentEventParser parser,
                            dev.langchain4j.http.client.sse.ServerSentEventListener listener) { throw new AssertionError("No streaming"); }
    }
}
