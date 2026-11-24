package com.example.aiagent.model;

import com.example.aiagent.nlu.VietnameseExtraction;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.http.client.*;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

/** SDK serialization seam only. Never mints or bypasses an adapter grant. No sockets permitted. */
class VietnameseExtractionPreflightTest {
    static final String HASH = "7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);
    @BeforeAll static void denyNetwork() { GeminiProviderTest.noNetwork(); }
    @AfterAll static void restoreNetwork() { GeminiProviderTest.restore(); }
    static byte[] packBytes() throws Exception {
        try (var stream = VietnameseExtractionPreflightTest.class.getResourceAsStream("/gemini/vietnamese-extraction-live-candidates.json")) {
            return Objects.requireNonNull(stream).readAllBytes();
        }
    }
    static Stream<JsonNode> fixtures() throws Exception {
        var list = new ArrayList<JsonNode>();
        GeminiLlmProviderAdapter.JSON.readTree(packBytes()).path("fixtures").forEach(list::add);
        return list.stream();
    }
    @Test void manifestIsPinnedAndNotAnApproval() throws Exception {
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packBytes()))).isEqualTo(HASH);
        var pack = GeminiLlmProviderAdapter.JSON.readTree(packBytes());
        assertThat(pack.path("status").asText()).isEqualTo("DRAFT");
        assertThat(pack.path("fixtures").size()).isEqualTo(20);
        assertThat(fixtures().map(f -> f.path("id").asText()).distinct().count()).isEqualTo(20);
    }
    static final class Capture implements HttpClientBuilder, HttpClient {
        HttpRequest request; int calls; Duration connect, read;
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest value) {
            calls++; request = value;
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiProviderTest.text()).build();
        }
        public void execute(HttpRequest request, dev.langchain4j.http.client.sse.ServerSentEventParser parser,
                            dev.langchain4j.http.client.sse.ServerSentEventListener listener) { throw new AssertionError("No streaming"); }
    }
    static Capture serialize(String utterance) {
        var capture = new Capture();
        GoogleAiGeminiChatModel.builder().apiKey("synthetic-noncredential").modelName(GeminiSmokeApproval.MODEL)
                .maxRetries(0).maxOutputTokens(1024).returnThinking(false)
                .allowCodeExecution(false).allowGoogleSearch(false).allowGoogleMaps(false).allowUrlContext(false)
                .httpClientBuilder(capture).logRequests(false).logResponses(false).build()
                .chat(GeminiRequestProtocol.prepare(VietnameseExtraction.request(utterance)));
        return capture;
    }
    @ParameterizedTest @MethodSource("fixtures")
    void pinnedSdkPreservesExactSchemaAndDataButExistingLiveBridgeRejects(JsonNode fixture) throws Exception {
        String utterance = fixture.path("utterance").asText();
        var capture = serialize(utterance);
        assertThat(capture.calls).isEqualTo(1);
        var wire = GeminiLlmProviderAdapter.JSON.readTree(capture.request.body());
        assertThat(wire.path("contents").size()).isEqualTo(1);
        assertThat(wire.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(wire.at("/contents/0/parts/0/text").asText()).isEqualTo(utterance);
        assertThat(wire.at("/systemInstruction/role").asText()).isEqualTo("model");
        assertThat(wire.at("/systemInstruction/parts/0/text").asText()).contains("untrusted DATA", "never invent", "No provenance/confirmed");
        assertThat(wire.at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
        assertThat(wire.at("/generationConfig/responseJsonSchema")).isEqualTo(GeminiLlmProviderAdapter.JSON.readTree(VietnameseExtraction.schema()));
        assertThat(wire.has("tools")).isFalse();
        assertThat(capture.request.body()).doesNotContain("synthetic-noncredential", "access_token", "rawEvidence", "conversationHistory");
        long bytes = capture.request.body().getBytes(StandardCharsets.UTF_8).length;
        assertThat(bytes).isLessThanOrEqualTo(16384);
        // Measured blocker, not a compatibility PASS: current schema cannot fit the adapter's maximum.
        // Do not raise production limits or weaken admission to make this candidate pack executable.
        assertThat(2 * bytes + 1024).isGreaterThan(16384);
        assertThatThrownBy(() -> GeminiSmokeWire.prepare(capture.request, GeminiSmokeApproval.MODEL, 1024))
                .isInstanceOf(ProviderException.class).hasMessageContaining("UNSUPPORTED_PROTOCOL");
        var meters = new SimpleMeterRegistry();
        try {
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            var provider = new GeminiLlmProviderAdapter(GeminiProviderTest.agent(), GeminiProviderTest.limits(), meters,
                    "synthetic-noncredential", (r, c, t) -> { calls.incrementAndGet(); throw new AssertionError("No extraction grant"); }, new GeminiBudget());
            GeminiProviderTest.error(() -> provider.generate(VietnameseExtraction.request(utterance), GeminiProviderTest.control()),
                    ProviderException.Category.SYNTHETIC_BOUNDARY);
            assertThat(calls).hasValue(0);
        } finally { meters.close(); }
        assertThat(fixture.path("privacyClassification").asText()).isEqualTo("SYNTHETIC_ONLY");
    }
    @Test void recordMeasuredWireEnvelope() {
        var capture = serialize("Tìm khách sạn ở Đà Lạt");
        int bytes = capture.request.body().getBytes(StandardCharsets.UTF_8).length;
        System.out.println("Extraction preflight synthetic wire bytes=" + bytes + "; conservative input bound=" + (2 * bytes + 1024));
    }
    @ParameterizedTest @ValueSource(strings = {
            "{}", "{\"action\":\"CLARIFY\",\"action\":\"CRITERIA\"}",
            "{\"functionCall\":{\"name\":\"search_hotels\",\"args\":{}}}",
            "{\"action\":\"CLARIFY\",\"intent\":\"CLARIFY\",\"fields\":{},\"reference\":\"\",\"missingSlots\":[],\"inferredFields\":[],\"approved\":true}",
            "{\"action\":\"CLARIFY\",\"intent\":\"CLARIFY\",\"fields\":{\"hotelId\":{\"operation\":\"REPLACE\",\"value\":\"synthetic\"}},\"reference\":\"\",\"missingSlots\":[],\"inferredFields\":[]}"
    })
    void malformedUnknownDuplicateAndCapabilityFieldsFailWithoutLeakingPayload(String raw) {
        assertThatThrownBy(() -> VietnameseExtraction.parse(raw, CLOCK)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Vietnamese extraction").hasNoCause();
    }
}
