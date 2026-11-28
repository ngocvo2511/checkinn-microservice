package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.Permission;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.system.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.ProviderException.Category.*;

/** No live network/key. Actual pinned SDK wire, first-call grants and sanitized harness outcomes. */
@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("removal")
class GeminiFirstCallTest {
    @Test void documentedSignatureMetadataDoesNotReplaceStrictAnswerContent() {
        var raw = GeminiProviderTest.response("[{\"text\":\"{\\\"evidenceIds\\\":[]}\",\"thoughtSignature\":\"c3ludGhldGlj\"}]");
        var result = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build(), "synthetic-noncredential");
        assertThat(result.category()).isEqualTo("SUCCESS");
        assertThat(result.grounding()).isEqualTo("PASS");
        assertThat(result.signaturePresent()).isTrue(); assertThat(result.firstFailingStage()).isEqualTo("NONE");
    }
    @ParameterizedTest @ValueSource(strings = {"true", "42", "null", "\"\""})
    void invalidSignatureMetadataStillFailsClosed(String signature) {
        var raw = GeminiProviderTest.response("[{\"text\":\"{\\\"evidenceIds\\\":[]}\",\"thoughtSignature\":" + signature + "}]");
        var result = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build(), "synthetic-noncredential");
        assertThat(result.category()).isEqualTo("MALFORMED_RESPONSE"); assertThat(result.firstFailingStage()).isEqualTo("RAW_PARTS");
    }
    @Test void signatureCannotAuthorizeExtraClaimsOrThoughtContent() {
        for (var part : List.of("{\"text\":\"{\\\"evidenceIds\\\":[]}\",\"thought\":true}",
                "{\"text\":\"{\\\"evidenceIds\\\":[]}\",\"extra\":true}")) {
            var result = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiProviderTest.response("[" + part + "]")).build(), "synthetic-noncredential");
            assertThat(result.firstFailingStage()).isEqualTo("RAW_PARTS"); assertThat(result.category()).isEqualTo("MALFORMED_RESPONSE");
        }
        var raw = GeminiProviderTest.response("[{\"text\":\"{\\\"evidenceIds\\\":[\\\"invented\\\"]}\",\"thoughtSignature\":\"c3ludGhldGlj\"}]");
        var rejected = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build(), "synthetic-noncredential");
        assertThat(rejected.grounding()).isEqualTo("FAIL"); assertThat(rejected.firstFailingStage()).isEqualTo("GROUNDING");
        assertThat(rejected.parserErrorCode()).isEqualTo("GROUNDING_REJECTED");
    }
    static SecurityManager previous;
    static final AtomicInteger external = new AtomicInteger();
    @BeforeAll static void denyNetwork() {
        previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) { external.incrementAndGet(); throw new SecurityException("Offline first-call test"); }
        });
    }
    @AfterAll static void restore() { System.setSecurityManager(previous); assertThat(external).hasValue(0); }
    static AgentProperties agent(String model) {
        var base = GeminiProviderTest.agent();
        return new AgentProperties(base.enabled(), base.provider(), model, base.apiKeyEnvironmentVariable(), base.modelTimeout(),
                base.toolTimeout(), base.turnDeadline(), base.httpAttempts(), base.toolProposals(), base.modelRequests(), base.toolRounds(), 0, false);
    }
    static GeminiProperties limits(String... overrides) {
        var settings = new ArrayList<>(List.of("reviewed-model-id=" + GeminiSmokeApproval.MODEL, "max-process-requests=1",
                "max-exposure-usd=0.01", "output-usd-per-million-upper-bound=3", "transport-acceptance=NOT_VERIFIED",
                "account-region-free-tier-verification=OPERATOR_CONFIRMED", "data-terms-verification=OPERATOR_CONFIRMED",
                "quota-billing-verification=OPERATOR_CONFIRMED"));
        settings.addAll(List.of(overrides)); return GeminiProviderTest.limits(settings.toArray(String[]::new));
    }
    static GeminiSmokeApproval approvals(String... overrides) {
        var values = new HashMap<String, String>();
        values.put("offline-transport-safety", "OFFLINE_TRANSPORT_SAFETY_VERIFIED"); values.put("first-call", "FIRST_CALL_AUTHORIZED");
        values.put("no-paid-billing", "OPERATOR_CONFIRMED"); values.put("exclusive-quota", "OPERATOR_CONFIRMED");
        for (var override : overrides) { var pair = override.split("=", 2); values.put(pair[0], pair[1]); }
        return new Binder(new MapConfigurationPropertySource(values)).bind("", Bindable.of(GeminiSmokeApproval.class)).get();
    }
    static HttpRequest serialized() {
        var capture = new GeminiProviderTest.SdkFixture();
        GoogleAiGeminiChatModel.builder().apiKey("synthetic-noncredential").modelName(GeminiSmokeApproval.MODEL).maxRetries(0)
                .maxOutputTokens(128).returnThinking(false).httpClientBuilder(capture).logRequests(false).logResponses(false).build()
                .chat(GeminiRequestProtocol.prepare(SyntheticGeminiFixtures.request(SyntheticGeminiFixtures.SMOKE)));
        assertThat(capture.calls).isEqualTo(1);
        return HttpRequest.builder().method(HttpMethod.POST)
                .url("https://generativelanguage.googleapis.com/v1beta/models/" + GeminiSmokeApproval.MODEL + ":generateContent")
                .addHeader("x-goog-api-key", "synthetic-noncredential").body(capture.body).build();
    }
    static GeminiSmokeDiagnostics.Result run(GeminiLlmProviderAdapter.Exchange exchange, String key) {
        return GeminiSyntheticSmoke.runFixed(agent(GeminiSmokeApproval.MODEL), limits(), approvals(), key, exchange,
                new GeminiBudget(), new GeminiSmokeDiagnostics());
    }
    @Test void realSdkWireDropsOnlyCandidateCountForExactReviewedFixture(CapturedOutput logs) throws Exception {
        var original = serialized();
        var before = GeminiLlmProviderAdapter.JSON.readTree(original.body());
        assertThat(before.path("generationConfig").path("candidateCount").intValue()).isEqualTo(1);
        var adapted = GeminiSmokeWire.prepare(original, GeminiSmokeApproval.MODEL, 128);
        var after = GeminiLlmProviderAdapter.JSON.readTree(adapted.body());
        ((com.fasterxml.jackson.databind.node.ObjectNode) before.path("generationConfig")).remove("candidateCount");
        assertThat(after).isEqualTo(before); assertThat(adapted.headers()).isEqualTo(original.headers());
        assertThat(adapted.url()).isEqualTo(original.url());
        assertThat(after.path("generationConfig").has("candidateCount")).isFalse();
        assertThat(after.path("generationConfig").path("maxOutputTokens").intValue()).isEqualTo(128);
        assertThat(after.path("generationConfig").path("responseJsonSchema").path("additionalProperties").booleanValue()).isFalse();
        assertThat(after.has("tools")).isFalse();
        assertThat(adapted.body()).doesNotContain("synthetic-noncredential", "thinkingBudget", "temperature", "topP", "topK");
        assertThat(GeminiSmokeWire.prepare(original, "gemini-unreviewed-model", 128)).isSameAs(original);
        assertThat(logs).doesNotContain("synthetic-noncredential", "Synthetic scenario", "evidenceIds");
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "null", "{", "[]", "duplicate", "extra", "cap", "schema", "content", "tool", "candidate", "thinking"})
    void malformedOrAlteredSdkBodiesCannotBeRewritten(String kind) throws Exception {
        var original = serialized(); var root = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiLlmProviderAdapter.JSON.readTree(original.body());
        var config = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("generationConfig");
        String body;
        switch (kind) {
            case "extra" -> root.put("cachedContent", "unapproved");
            case "cap" -> config.put("maxOutputTokens", 129);
            case "schema" -> config.remove("responseJsonSchema");
            case "content" -> root.put("contents", "arbitrary prompt");
            case "tool" -> root.putArray("tools").addObject().put("googleSearch", true);
            case "candidate" -> config.put("candidateCount", 2);
            case "thinking" -> config.putObject("thinkingConfig").put("thinkingBudget", -1);
            default -> { }
        }
        body = switch (kind) { case "{}", "null", "{", "[]" -> kind;
            case "duplicate" -> original.body().replaceFirst("\\{", "{\"contents\":[],");
            default -> root.toString(); };
        var request = HttpRequest.builder().url(original.url()).method(original.method()).body(body).build();
        GeminiProviderTest.error(() -> GeminiSmokeWire.prepare(request, GeminiSmokeApproval.MODEL, 128), UNSUPPORTED_PROTOCOL);
    }
    @Test void wrongEndpointOrMethodCannotEnterTransformation() {
        var original = serialized();
        for (var request : List.of(HttpRequest.builder().url("https://example.invalid/").method(HttpMethod.POST).body(original.body()).build(),
                HttpRequest.builder().url(original.url()).method(HttpMethod.GET).body(original.body()).build()))
            GeminiProviderTest.error(() -> GeminiSmokeWire.prepare(request, GeminiSmokeApproval.MODEL, 128), UNSUPPORTED_PROTOCOL);
    }
    @ParameterizedTest @ValueSource(strings = {"live-enabled=false", "synthetic-only=false", "max-input-tokens=0", "max-input-tokens=8191",
            "max-output-tokens=129", "max-process-requests=2", "max-run-requests=2", "max-concurrent-requests=2", "max-exposure-usd=0.02",
            "input-usd-per-million-upper-bound=0", "output-usd-per-million-upper-bound=4", "model-verification=NOT_VERIFIED",
            "reviewed-model-id=gemini-other", "data-terms-verification=NOT_VERIFIED", "account-region-free-tier-verification=NOT_VERIFIED",
            "quota-billing-verification=NOT_VERIFIED", "transport-acceptance=VERIFIED"})
    void firstCallCannotBypassIndividualGatesOrEnvelope(String override) {
        GeminiProviderTest.error(() -> GeminiSyntheticSmoke.authorize(agent(GeminiSmokeApproval.MODEL), limits(override), approvals()), CONFIGURATION);
    }
    @ParameterizedTest @ValueSource(strings = {"offline-transport-safety=NOT_VERIFIED", "first-call=NOT_VERIFIED", "no-paid-billing=NOT_VERIFIED", "exclusive-quota=NOT_VERIFIED"})
    void firstCallNeedsEverySeparateAttestation(String override) {
        GeminiProviderTest.error(() -> GeminiSyntheticSmoke.authorize(agent(GeminiSmokeApproval.MODEL), limits(), approvals(override)), CONFIGURATION);
    }
    @Test void wrongModelGenericFactoryCopiedArbitraryAndToolRequestsAreDenied() {
        GeminiProviderTest.error(() -> GeminiSyntheticSmoke.authorize(agent("gemini-other"), limits(), approvals()), CONFIGURATION);
        var agent = agent(GeminiSmokeApproval.MODEL); var limits = limits(); var calls = new AtomicInteger();
        var meters = new SimpleMeterRegistry();
        try {
            var permit = GeminiSyntheticSmoke.authorize(agent, limits, approvals());
            var provider = new GeminiLlmProviderAdapter(agent, limits, meters, "synthetic-noncredential", (r, c, t) -> {
                calls.incrementAndGet(); return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiProviderTest.text()).build();
            }, new GeminiBudget(), permit);
            GeminiProviderTest.error(() -> new ProviderConfiguration().llmProviderAdapter(agent, limits, meters), CONFIGURATION);
            for (var request : List.of(SyntheticGeminiFixtures.request(SyntheticGeminiFixtures.SMOKE),
                    SyntheticGeminiFixtures.request("tool-v1"), ChatRequest.builder().messages(UserMessage.from("{\"synthetic\":true,\"hotelId\":\"real-shaped\"}")).build()))
                GeminiProviderTest.error(() -> provider.generate(request, GeminiProviderTest.control()), SYNTHETIC_BOUNDARY);
            GeminiProviderTest.error(() -> provider.fixture("text-v1", GeminiProviderTest.control()), SYNTHETIC_BOUNDARY);
            GeminiProviderTest.error(() -> provider.fixture(SyntheticGeminiFixtures.SMOKE, GeminiProviderTest.control()), SYNTHETIC_BOUNDARY);
            assertThat(calls).hasValue(0);
            provider.firstCall(GeminiProviderTest.control());
            GeminiProviderTest.error(() -> provider.firstCall(GeminiProviderTest.control()), SYNTHETIC_BOUNDARY);
            assertThat(calls).hasValue(1);
        } finally { meters.close(); }
    }
    @Test void permitIsThreadBoundAndSingleUseByRequestIdentity() throws Exception {
        var agent = agent(GeminiSmokeApproval.MODEL); var limits = limits();
        var permit = GeminiSyntheticSmoke.authorize(agent, limits, approvals());
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> GeminiProviderTest.error(() -> permit.consume(permit.request()), SYNTHETIC_BOUNDARY)).get(1, TimeUnit.SECONDS);
            worker.submit(() -> GeminiProviderTest.error(() -> permit.validate(agent, limits), SYNTHETIC_BOUNDARY)).get(1, TimeUnit.SECONDS);
        }
        GeminiProviderTest.error(() -> permit.consume(SyntheticGeminiFixtures.request(SyntheticGeminiFixtures.SMOKE)), SYNTHETIC_BOUNDARY);
        permit.consume(permit.request());
        GeminiProviderTest.error(() -> permit.consume(permit.request()), SYNTHETIC_BOUNDARY);
    }
    @Test void harnessReportsBoundedSuccessAndRetainsOneProcessReservation(CapturedOutput logs) {
        var count = new AtomicInteger(); var budget = new GeminiBudget(); var diagnostics = new GeminiSmokeDiagnostics();
        GeminiLlmProviderAdapter.Exchange exchange = (r, c, t) -> {
            count.incrementAndGet(); assertThat(r.body()).doesNotContain("candidateCount", "synthetic-noncredential");
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiProviderTest.text()).build();
        };
        var result = GeminiSyntheticSmoke.runFixed(agent(GeminiSmokeApproval.MODEL), limits(), approvals(), "synthetic-noncredential", exchange, budget, diagnostics);
        assertThat(result.category()).isEqualTo("SUCCESS"); assertThat(result.grounding()).isEqualTo("PASS");
        assertThat(result.attempts()).isEqualTo(1); assertThat(result.retries()).isZero(); assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.candidateTokens()).isEqualTo(8); assertThat(result.totalTokens()).isEqualTo(20);
        assertThat(result.finishReason()).isEqualTo("STOP"); assertThat(result.httpStatusClass()).isEqualTo("2xx");
        assertThat(result.localReservationUsd()).isEqualByComparingTo("0.008576");
        assertThat(result.latencyMillis()).isGreaterThanOrEqualTo(0); assertThat(result.liveTransport()).isEqualTo("LIVE_TRANSPORT_OBSERVED");
        var second = GeminiSyntheticSmoke.runFixed(agent(GeminiSmokeApproval.MODEL), limits(), approvals(), "synthetic-noncredential", exchange, budget, new GeminiSmokeDiagnostics());
        assertThat(second.category()).isEqualTo("BUDGET_EXCEEDED"); assertThat(count).hasValue(1);
        assertThat(result.toString()).doesNotContain("synthetic-noncredential", "evidenceIds", "Synthetic scenario");
        assertThat(logs).doesNotContain("synthetic-noncredential", "evidenceIds", "Synthetic scenario");
    }
    @ParameterizedTest @ValueSource(ints = {400, 403, 404, 429, 500})
    void failedAttemptIsReportedAndNeverRetried(int status) {
        var calls = new AtomicInteger();
        var result = run((r, c, t) -> { calls.incrementAndGet(); throw new dev.langchain4j.exception.HttpException(status, "secret-raw-body"); }, "synthetic-noncredential");
        assertThat(calls).hasValue(1); assertThat(result.attempts()).isEqualTo(1); assertThat(result.retries()).isZero();
        assertThat(result.category()).isEqualTo(status == 429 ? "QUOTA_EXCEEDED" : "UNAVAILABLE");
        assertThat(result.providerAccepted()).isEqualTo("false"); assertThat(result.grounding()).isEqualTo("NOT_RUN");
        assertThat(result.toString()).doesNotContain("secret-raw-body"); assertThat(result.localReservationUsd()).isEqualByComparingTo("0.008576");
    }
    @Test void missingKeyUnknownUsageAndGroundingFailureAreNotSuccessfulEmptyAnswers() {
        var calls = new AtomicInteger();
        var missing = run((r, c, t) -> { calls.incrementAndGet(); throw new AssertionError("Must not execute"); }, null);
        assertThat(missing.category()).isEqualTo("CONFIGURATION"); assertThat(missing.attempts()).isZero(); assertThat(calls).hasValue(0);
        var unknown = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                .body(GeminiProviderTest.text().replace("usageMetadata", "missingUsage")).build(), "synthetic-noncredential");
        assertThat(unknown.category()).isEqualTo("UNKNOWN_USAGE"); assertThat(unknown.promptTokens()).isNull();
        var rejected = run((r, c, t) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                .body(GeminiProviderTest.response("[{\"text\":\"{\\\"evidenceIds\\\":[\\\"invented\\\"]}\"}]")).build(), "synthetic-noncredential");
        assertThat(rejected.category()).isEqualTo("MALFORMED_RESPONSE"); assertThat(rejected.grounding()).isEqualTo("FAIL");
    }
    @ParameterizedTest @ValueSource(strings = {"PROVIDER_TIMEOUT", "CANCELLED", "malformed", "MAX_TOKENS"})
    void timeoutCancellationMalformedAndTruncationStopAfterOneAttempt(String fault) {
        var calls = new AtomicInteger();
        var result = run((r, c, t) -> {
            calls.incrementAndGet();
            if (fault.equals("PROVIDER_TIMEOUT") || fault.equals("CANCELLED")) throw new ProviderException(ProviderException.Category.valueOf(fault));
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                    .body(fault.equals("malformed") ? "{" : GeminiProviderTest.text().replace("STOP", "MAX_TOKENS")).build();
        }, "synthetic-noncredential");
        assertThat(result.category()).isEqualTo(fault.equals("malformed") || fault.equals("MAX_TOKENS") ? "MALFORMED_RESPONSE" : fault);
        assertThat(result.attempts()).isEqualTo(1); assertThat(result.retries()).isZero(); assertThat(calls).hasValue(1);
        assertThat(result.grounding()).isEqualTo("NOT_RUN");
    }
    @Test void approvalsHaveSafeDefaultsAndPreflightDistinguishesMissingGates() {
        var defaults = new Binder(new MapConfigurationPropertySource(Map.of("first-call", "NOT_VERIFIED")))
                .bind("", Bindable.of(GeminiSmokeApproval.class)).get();
        GeminiProviderTest.error(defaults::validate, CONFIGURATION);
        assertThat(GeminiSyntheticSmoke.firstCallPreflight(agent(GeminiSmokeApproval.MODEL), limits(), defaults)).isEqualTo("FAIL");
        assertThat(GeminiSyntheticSmoke.firstCallPreflight(agent(GeminiSmokeApproval.MODEL), limits(), approvals())).isEqualTo("PASS");
    }
    @Test void normalOrchestratorCannotConsumeFirstCallPermitOrContactGateway() throws Exception {
        var agent = agent(GeminiSmokeApproval.MODEL); var limits = limits(); var meters = new SimpleMeterRegistry();
        var calls = new AtomicInteger(); var clock = java.time.Clock.systemUTC();
        var permit = GeminiSyntheticSmoke.authorize(agent, limits, approvals());
        var provider = new GeminiLlmProviderAdapter(agent, limits, meters, "synthetic-noncredential", (r, c, t) -> {
            calls.incrementAndGet(); throw new AssertionError("No model exchange allowed");
        }, new GeminiBudget(), permit);
        try (var registry = new com.example.aiagent.tools.ToolRegistry(true, java.net.URI.create("http://127.0.0.1:1"),
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1), clock);
             var orchestrator = new com.example.aiagent.orchestration.AgentOrchestrator(agent, provider, registry,
                     new com.example.aiagent.orchestration.InMemoryConversationStore(clock), clock, meters)) {
            var result = orchestrator.turn(new com.example.aiagent.orchestration.AgentOrchestrator.Request("synthetic-local-session", 0,
                    "arbitrary input", new com.example.aiagent.orchestration.PolicyExecutor.Scope(
                            com.example.aiagent.orchestration.PolicyExecutor.Intent.CANCELLATION, null, null),
                    Map.of(), new java.util.concurrent.atomic.AtomicBoolean()));
            assertThat(result.answer().category()).isEqualTo("MODEL_SYNTHETIC_BOUNDARY");
            assertThat(result.budget().httpAttempts()).isZero(); assertThat(calls).hasValue(0);
        } finally { meters.close(); }
    }
}
