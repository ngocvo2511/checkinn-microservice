package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.Permission;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionSession.*;

/** Actual SDK, fake HTTP, test-only issuer and temporary journals. Not operator/live authorization. */
@SuppressWarnings("removal")
class GeminiExtractionUnblockTest {
    @TempDir Path directory;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);
    static SecurityManager previous;
    static final AtomicInteger sockets = new AtomicInteger(), environment = new AtomicInteger();
    final AtomicInteger keys = new AtomicInteger(), calls = new AtomicInteger();
    @BeforeAll static void denyExternalAndKeys() {
        previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission p) {
                if (p.getName().equals("getenv.GEMINI_API_KEY") || p.getName().equals("getenv.*")) {
                    environment.incrementAndGet(); throw new SecurityException("Offline key guard");
                }
            }
            @Override public void checkConnect(String host, int port) { sockets.incrementAndGet(); throw new SecurityException("Offline network guard"); }
        });
    }
    @AfterAll static void restore() {
        System.setSecurityManager(previous); assertThat(sockets).hasValue(0); assertThat(environment).hasValue(0);
    }
    static Caps caps(int requests) {
        return new Caps(requests, INPUT_CAP, OUTPUT_CAP, requests * INPUT_CAP, requests * OUTPUT_CAP,
                requests * (INPUT_CAP + OUTPUT_CAP), 1, 0, Duration.ofSeconds(10), 9,
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2")); // Fictional test rates only.
    }
    Reviewed review(String... ids) {
        var bindings = new LinkedHashMap<String, Binding>(); for (String id : ids) bindings.put(id, binding(id));
        return new Reviewed(UUID.randomUUID().toString(), State.APPROVED_FOR_SEPARATE_LIVE_BATCH, PURPOSE, MANIFEST, MODEL, VERSION,
                "SYNTHETIC_ONLY", "TEST_ONLY_OPERATOR", "TEST_ONLY_REFERENCE", CLOCK.instant(), CLOCK.instant().plusSeconds(60), bindings, caps(ids.length), directory.toAbsolutePath().normalize());
    }
    static Reviewed replace(Reviewed r, String field, Object value) {
        return new Reviewed(field.equals("sessionId") ? (String) value : r.sessionId(),
                field.equals("state") ? (State) value : r.state(), field.equals("purpose") ? (String) value : r.purpose(),
                field.equals("manifest") ? (String) value : r.manifest(), field.equals("model") ? (String) value : r.model(),
                field.equals("protocol") ? (String) value : r.protocol(), field.equals("classification") ? (String) value : r.classification(),
                r.operatorIdentity(), r.approvalReference(), field.equals("notBefore") ? (Instant) value : r.notBefore(),
                field.equals("expires") ? (Instant) value : r.expires(), field.equals("fixtures") ? castBindings(value) : r.fixtures(),
                field.equals("caps") ? (Caps) value : r.caps(), r.journalDirectory());
    }
    @SuppressWarnings("unchecked") static Map<String, Binding> castBindings(Object v) { return (Map<String, Binding>) v; }
    GeminiExtractionSession open(Reviewed r) { return GeminiExtractionSession.open(new byte[]{1}, artifact -> r, directory, CLOCK); }
    static GeminiProperties limits(int requests) {
        return GeminiProviderTest.limits("reviewed-model-id=" + MODEL, "max-input-tokens=16384", "max-output-tokens=1024",
                "max-run-requests=" + requests, "max-process-requests=9", "max-exposure-usd=1");
    }
    static LlmProviderAdapter.CallControl control() { return new LlmProviderAdapter.CallControl(Duration.ofSeconds(10)); }
    static String expected(String id) {
        var fixture = fixture(id);
        var root = GeminiLlmProviderAdapter.JSON.createObjectNode().put("action", fixture.path("expectedAction").asText())
                .put("intent", fixture.path("expectedIntent").asText()).put("reference", fixture.path("reference").asText());
        var fields = root.putObject("fields"); fixture.path("expectedCriteria").fields().forEachRemaining(e ->
                fields.putObject(e.getKey()).put("operation", "REPLACE").set("value", e.getValue()));
        root.set("missingSlots", fixture.path("expectedMissingSlots")); root.putArray("inferredFields"); return root.toString();
    }
    static String response(String raw) {
        var part = GeminiLlmProviderAdapter.JSON.createObjectNode().put("text", raw);
        return GeminiProviderTest.response("[" + part + "]");
    }
    GeminiExtractionSmoke.Outcome run(GeminiExtractionSession s, Capability c, String response, int count) {
        return run(s, c, response, count, control(), null);
    }
    GeminiExtractionSmoke.Outcome run(GeminiExtractionSession s, Capability c, String raw, int count,
                                      LlmProviderAdapter.CallControl control, Runnable duringTransport) {
        return GeminiExtractionSmoke.run(s, c, 0, control, () -> { keys.incrementAndGet(); return "synthetic-noncredential"; },
                (request, cc, timeout) -> {
                    calls.incrementAndGet(); assertThat(request.url()).isEqualTo(ENDPOINT);
                    assertThat(request.body()).isEqualTo(body(s.fixtureId(c)));
                    assertThat(request.headers().keySet()).containsExactlyInAnyOrder("Content-Type", "Accept", "x-goog-api-key");
                    assertThat(timeout).isLessThanOrEqualTo(Duration.ofSeconds(10));
                    if (duringTransport != null) duringTransport.run();
                    return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build();
                }, limits(count), new GeminiBudget(), CLOCK);
    }
    @Test void measureEveryFixtureWithActualSdkAndKeepAllBackendConstraints() throws Exception {
        var measurements = GeminiLlmProviderAdapter.JSON.createArrayNode();
        for (int i = 1; i <= 20; i++) {
            String id = "VEX1-%02d".formatted(i), utterance = utterance(id);
            var original = VietnameseExtractionPreflightTest.serialize(utterance).request;
            String compact = body(id);
            var row = measurements.addObject().put("id", id).put("systemBytes", bytes(instruction())).put("userBytes", bytes(utterance))
                    .put("schemaBeforeBytes", bytes(VietnameseExtraction.schema())).put("schemaAfterBytes", bytes(schema()))
                    .put("bodyBeforeBytes", bytes(original.body())).put("bodyAfterBytes", bytes(compact))
                    .put("estimateBefore", 2 * bytes(original.body()) + 1024).put("estimateAfter", 2 * bytes(compact) + 1024)
                    .put("outputCap", OUTPUT_CAP).put("inputCap", INPUT_CAP)
                    .put("fixtureDigest", hash(utterance)).put("instructionDigest", hash(instruction()))
                    .put("schemaDigest", hash(schema())).put("requestDigest", hash(compact));
            assertThat(row.path("estimateAfter").asInt()).isLessThanOrEqualTo(INPUT_CAP);
            assertThat(row.path("estimateBefore").asInt()).isGreaterThan(INPUT_CAP);
            assertThat(bytes(compact)).isLessThanOrEqualTo(BYTE_CAP);
            var wire = GeminiLlmProviderAdapter.JSON.readTree(compact);
            assertThat(wire.at("/generationConfig/responseJsonSchema")).isEqualTo(GeminiLlmProviderAdapter.JSON.readTree(VietnameseExtraction.schema()));
            assertThat(wire.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(1024);
            assertThat(wire.has("tools")).isFalse();
            assertThat(wire.at("/contents/0/role").asText()).isEqualTo("user");
            assertThat(wire.at("/contents/0/parts/0/text").asText()).isEqualTo(utterance);
            assertThat(wire.at("/systemInstruction/parts/0/text").asText()).isEqualTo(instruction());
        }
        Files.writeString(Path.of("target/extraction-request-measurements.json"), measurements.toPrettyString());
        assertThat(hash(VietnameseExtractionPreflightTest.packBytes())).isEqualTo(MANIFEST);
    }
    @ParameterizedTest @ValueSource(strings = {"VEX1-01", "VEX1-02", "VEX1-03"})
    void fullPipelineAcceptsLiteralProposalOnceAndSeals(String id) throws Exception {
        try (var s = open(review(id))) {
            var c = s.issue(id); var result = run(s, c, response(expected(id)), 1);
            assertThat(result).isEqualTo(new GeminiExtractionSmoke.Outcome("SUCCESS", "PASS", "PASS", true));
            assertThat(s.state()).isEqualTo(State.SEALED); assertThat(s.requests()).isEqualTo(1);
            assertThatThrownBy(() -> run(s, c, response(expected(id)), 1)).isInstanceOf(ProviderException.class);
            assertThat(keys).hasValue(1); assertThat(calls).hasValue(1);
            s.close(); // Windows exclusive file lock must be released before an independent journal reader.
            List<String> lines;
            try (var files = Files.list(directory)) { lines = Files.readAllLines(files.findFirst().orElseThrow()); }
            assertThat(lines).hasSize(2);
            var end = GeminiLlmProviderAdapter.JSON.readTree(lines.getLast());
            assertThat(end.path("inputTokens").asInt()).isEqualTo(12);
            assertThat(end.path("outputTokens").asInt()).isEqualTo(8);
            assertThat(end.path("totalTokens").asInt()).isEqualTo(20);
            assertThat(end.path("stopReason").asText()).isEqualTo("SUCCESS_SEALED");
            assertThat(fields(end)).containsExactlyInAnyOrder("fixtureId", "protocolVersion", "requestDigest", "modelIdentity", "startTimestamp",
                    "endTimestamp", "latencyMillis", "httpStatusCategory", "inputTokens", "outputTokens", "totalTokens", "schemaValidationResult", "semanticResult", "stopReason");
            assertThat(String.join("\n", lines)).doesNotContain("synthetic-noncredential", utterance(id), "x-goog-api-key", "fields", "Đà Lạt");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"purpose", "manifest", "model", "protocol", "classification", "expired", "future", "long-window",
            "unknown-fixture", "fixture-digest", "instruction-digest", "schema-digest", "request-digest", "DRAFT", "REVIEWED", "AWAITING_OPERATOR_APPROVAL",
            "EXPIRED", "REVOKED", "SEALED", "caps-input", "caps-output", "caps-request", "caps-concurrency", "caps-retry", "unsafe-fixture"})
    void forgedOrMismatchedReviewsFailBeforeAnyKeyOrTransport(String change) {
        var original = review("VEX1-01"); var r = original;
        if (Set.of("purpose", "manifest", "model", "protocol", "classification").contains(change)) r = replace(r, change, "WRONG");
        else if (change.equals("expired")) r = replace(r, "expires", CLOCK.instant());
        else if (change.equals("future")) r = replace(r, "notBefore", CLOCK.instant().plusSeconds(1));
        else if (change.equals("long-window")) r = replace(r, "expires", CLOCK.instant().plusSeconds(301));
        else if (change.equals("unknown-fixture")) r = replace(r, "fixtures", Map.of("unknown", binding("VEX1-01")));
        else if (change.endsWith("-digest")) {
            var b = binding("VEX1-01");
            r = replace(r, "fixtures", Map.of("VEX1-01", new Binding(change.equals("fixture-digest") ? "mutated" : b.fixtureDigest(),
                    change.equals("instruction-digest") ? "mutated" : b.instructionDigest(), change.equals("schema-digest") ? "mutated" : b.schemaDigest(),
                    change.equals("request-digest") ? "mutated" : b.requestDigest())));
        } else if (change.startsWith("caps-")) {
            var c = r.caps(); r = replace(r, "caps", new Caps(change.equals("caps-request") ? 4 : 1,
                    change.equals("caps-input") ? 1024 : INPUT_CAP, change.equals("caps-output") ? 1025 : OUTPUT_CAP,
                    c.inputTotal(), c.outputTotal(), c.total(), change.equals("caps-concurrency") ? 2 : 1,
                    change.equals("caps-retry") ? 1 : 0, c.callTimeout(), 9, c.exposureUsd(), c.inputRateUpperBound(), c.outputRateUpperBound()));
        } else if (change.equals("unsafe-fixture")) r = review("VEX1-19");
        else r = replace(r, "state", State.valueOf(change));
        var invalid = r;
        assertThatThrownBy(() -> open(invalid)).isInstanceOf(ProviderException.class).hasNoCause();
        assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
        assertThat(directory.toFile().list()).isEmpty();
    }
    @Test void missingOrArbitraryJsonCannotMintApproval() {
        for (byte[] bytes : Arrays.asList(null, new byte[0], "{\"approved\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            assertThatThrownBy(() -> GeminiExtractionSession.open(bytes, unavailableIssuer(), directory, CLOCK)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> GeminiExtractionSession.open(new byte[]{1}, artifact -> null, directory, CLOCK)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> GeminiExtractionSmoke.run(null, null, 0, control(), () -> { keys.incrementAndGet(); return "forbidden"; },
                (r,c,t) -> { calls.incrementAndGet(); throw new AssertionError(); }, limits(1), new GeminiBudget(), CLOCK)).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
    }
    @Test void capabilityIsPrivateIdentityBoundToThreadSessionAndAttempt() throws Exception {
        try (var first = open(review("VEX1-01")); var other = open(review("VEX1-01"))) {
            var cap = first.issue("VEX1-01");
            assertThat(Capability.class.getDeclaredConstructors()).allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers()));
            assertThatThrownBy(() -> other.reserve(cap, 0, control())).isInstanceOf(ProviderException.class);
            assertThatThrownBy(() -> first.reserve(cap, 1, control())).isInstanceOf(ProviderException.class);
            assertThatThrownBy(() -> first.issue("VEX1-02")).isInstanceOf(ProviderException.class);
            assertThatThrownBy(() -> first.issue("VEX1-01")).isInstanceOf(ProviderException.class);
            try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(() -> assertThatThrownBy(() -> first.reserve(cap, 0, control())).isInstanceOf(ProviderException.class)).get(2, TimeUnit.SECONDS);
            }
            assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
            assertThat(run(first, cap, response(expected("VEX1-01")), 1).category()).isEqualTo("SUCCESS");
        }
    }
    @ParameterizedTest @ValueSource(strings = {"empty", "partial", "corrupt", "reserved", "complete"})
    void crashRestartAndJournalUncertaintyNeverResume(String contents) throws Exception {
        var r = review("VEX1-01");
        try (var s = open(r)) {
            if (contents.equals("reserved")) s.reserve(s.issue("VEX1-01"), 0, control());
            if (contents.equals("complete")) run(s, s.issue("VEX1-01"), response(expected("VEX1-01")), 1);
        }
        Path file; try (var files = Files.list(directory)) { file = files.findFirst().orElseThrow(); }
        // Simulate pre-existing uncertain bytes only inside test-owned temporary directory.
        if (contents.equals("partial")) Files.writeString(file, "{\"fixtureId\":");
        if (contents.equals("corrupt")) Files.writeString(file, "CORRUPT\n");
        byte[] original = Files.readAllBytes(file);
        assertThatThrownBy(() -> open(r)).isInstanceOf(ProviderException.class);
        assertThat(Files.readAllBytes(file)).isEqualTo(original);
    }
    @ParameterizedTest @ValueSource(strings = {"REVOKED", "SEALED"})
    void terminalStateRejectsBeforeKey(String state) {
        try (var s = open(review("VEX1-01"))) {
            var cap = s.issue("VEX1-01"); if (state.equals("REVOKED")) s.revoke(); else s.seal();
            assertThatThrownBy(() -> run(s, cap, response(expected("VEX1-01")), 1)).isInstanceOf(ProviderException.class);
            assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"input", "output", "total", "concurrent", "requests"})
    void tokenCountAndConcurrentAdmissionRemainBounded(String type) {
        var r = review("VEX1-01", "VEX1-02"); var c = r.caps();
        if (!type.equals("concurrent")) r = replace(r, "caps", new Caps(type.equals("requests") ? 1 : 2, INPUT_CAP, OUTPUT_CAP,
                type.equals("input") || type.equals("requests") ? INPUT_CAP : c.inputTotal(),
                type.equals("output") || type.equals("requests") ? OUTPUT_CAP : c.outputTotal(),
                type.equals("input") || type.equals("output") || type.equals("total") || type.equals("requests")
                        ? (type.equals("input") ? INPUT_CAP + c.outputTotal() : type.equals("output") ? c.inputTotal() + OUTPUT_CAP : INPUT_CAP + OUTPUT_CAP)
                        : c.total(), 1, 0, c.callTimeout(), 9,
                c.exposureUsd(), c.inputRateUpperBound(), c.outputRateUpperBound()));
        try (var s = open(r)) {
            var first = s.issue("VEX1-01"); var second = s.issue("VEX1-02");
            s.reserve(first, 0, control());
            if (!type.equals("concurrent")) s.complete("2xx", 12, 8, 20, "PASS", "PASS", "SUCCESS", 1);
            assertThatThrownBy(() -> s.reserve(second, 0, control())).isInstanceOf(ProviderException.class);
            assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"cancel-before", "timeout-before", "cancel-during", "late", "revoke-during"})
    void cancelledExpiredLateResponsesCannotSucceed(String kind) {
        try (var s = open(review("VEX1-01"))) {
            var c = s.issue("VEX1-01"); var control = kind.equals("timeout-before") ? new LlmProviderAdapter.CallControl(Duration.ofNanos(1)) : control();
            if (kind.equals("cancel-before")) control.cancel();
            if (kind.endsWith("before")) {
                assertThatThrownBy(() -> run(s, c, response(expected("VEX1-01")), 1, control, null)).isInstanceOf(ProviderException.class);
                assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
            } else {
                var result = run(s, c, response(expected("VEX1-01")), 1, control, () -> {
                    if (kind.equals("revoke-during")) s.revoke(); else control.cancel();
                });
                assertThat(result.backendAccepted()).isFalse(); assertThat(result.category()).isNotEqualTo("SUCCESS");
                assertThat(s.state()).isEqualTo(State.SEALED);
            }
        }
    }
    static Stream<Arguments> badResponses() {
        String good = response(expected("VEX1-01"));
        return Stream.of(
                Arguments.of("", "MALFORMED_RESPONSE"), Arguments.of("{", "MALFORMED_RESPONSE"),
                Arguments.of("{\"candidates\":[]}", "MALFORMED_RESPONSE"),
                Arguments.of(good.replace("\"candidates\":[", "\"candidates\":[{},"), "MALFORMED_RESPONSE"),
                Arguments.of(good.replace("\"STOP\"", "\"MAX_TOKENS\""), "MALFORMED_RESPONSE"),
                Arguments.of(good.replace("\"STOP\"", "\"SAFETY\""), "SAFETY_REFUSAL"),
                Arguments.of("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", "SAFETY_REFUSAL"),
                Arguments.of(good.replace(",\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":8,\"totalTokenCount\":20}", ""), "UNKNOWN_USAGE"),
                Arguments.of(good.replace("\"promptTokenCount\":12", "\"promptTokenCount\":-1"), "UNKNOWN_USAGE"),
                Arguments.of(good.replace("\"promptTokenCount\":12", "\"promptTokenCount\":1.5"), "UNKNOWN_USAGE"),
                Arguments.of(good.replace("\"totalTokenCount\":20", "\"totalTokenCount\":999999"), "BUDGET_EXCEEDED"),
                Arguments.of(GeminiProviderTest.response("[{\"functionCall\":{\"name\":\"search_hotels\",\"args\":{}}}]"), "UNSUPPORTED_PROTOCOL"),
                Arguments.of(response("{}"), "MALFORMED_RESPONSE"),
                Arguments.of(response(expected("VEX1-01").replace("\"SEARCH\"", "12")), "MALFORMED_RESPONSE"),
                Arguments.of(response(expected("VEX1-01").replace("\"fields\":{", "\"fields\":{\"USER_CONFIRMED\":true,")), "MALFORMED_RESPONSE"),
                Arguments.of(response(expected("VEX1-01").replace("\"action\":", "\"action\":\"CLARIFY\",\"action\":")), "MALFORMED_RESPONSE"),
                Arguments.of(response(expected("VEX1-01").replace("\"intent\":", "\"approved\":true,\"intent\":")), "MALFORMED_RESPONSE"),
                Arguments.of("x".repeat(65537), "MALFORMED_RESPONSE"),
                Arguments.of(good.replace("\"candidates\":", "\"unexpected\":true,\"candidates\":"), "MALFORMED_RESPONSE"));
    }
    @ParameterizedTest @MethodSource("badResponses")
    void malformedSafetyToolsAndUsageStopWithoutRawLeak(String raw, String category) throws Exception {
        try (var s = open(review("VEX1-01"))) {
            var result = run(s, s.issue("VEX1-01"), raw, 1);
            assertThat(result.category()).isEqualTo(category); assertThat(result.backendAccepted()).isFalse();
            assertThat(s.state()).isEqualTo(State.SEALED); assertThat(calls).hasValue(1);
        }
        try (var files = Files.list(directory)) {
            String journal = Files.readString(files.findFirst().orElseThrow());
            assertThat(journal).doesNotContain("synthetic-noncredential", "search_hotels", "approved", "unexpected");
        }
    }
    @Test void validButUncorroboratedProposalIsModelFailureAndBackendSafetySuccess() {
        String unsafe = expected("VEX1-01").replace("Đà Lạt", "Hà Nội");
        try (var s = open(review("VEX1-01"))) {
            var result = run(s, s.issue("VEX1-01"), response(unsafe), 1);
            assertThat(result).isEqualTo(new GeminiExtractionSmoke.Outcome("BACKEND_REJECTED", "PASS", "FAIL", false));
        }
    }
    @Test void quotaAndUnknownTransportErrorsDoNotRetryOrLeak() {
        try (var s = open(review("VEX1-01"))) {
            var outcome = GeminiExtractionSmoke.run(s, s.issue("VEX1-01"), 0, control(), () -> "synthetic-noncredential",
                    (r,c,t) -> { calls.incrementAndGet(); throw new dev.langchain4j.exception.HttpException(429, "raw-secret-sentinel"); },
                    limits(1), new GeminiBudget(), CLOCK);
            assertThat(outcome.category()).isEqualTo("QUOTA_EXCEEDED"); assertThat(calls).hasValue(1);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"url", "method", "tools", "schema", "prompt", "header"})
    void substitutedWireCannotMatchApprovedDigest(String field) throws Exception {
        var root = (ObjectNode) GeminiLlmProviderAdapter.JSON.readTree(body("VEX1-01"));
        ((ObjectNode) root.path("generationConfig")).put("candidateCount", 1);
        if (field.equals("tools")) root.putArray("tools");
        if (field.equals("schema")) ((ObjectNode) root.path("generationConfig")).putObject("responseJsonSchema");
        if (field.equals("prompt")) ((ObjectNode) root.at("/contents/0/parts/0")).put("text", "arbitrary substituted utterance");
        var builder = HttpRequest.builder().method(field.equals("method") ? HttpMethod.GET : HttpMethod.POST)
                .url(field.equals("url") ? "https://example.invalid" : ENDPOINT).body(root.toString());
        if (field.equals("header")) builder.addHeader("Authorization", "synthetic-forbidden");
        var wire = builder.build();
        assertThatThrownBy(() -> admit(wire, body("VEX1-01"))).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
    }
    @Test void actualPerCallDeadlineRejectsTransportThatReturnsLate() {
        var r = review("VEX1-01"); var c = r.caps();
        r = replace(r, "caps", new Caps(1, INPUT_CAP, OUTPUT_CAP, INPUT_CAP, OUTPUT_CAP, INPUT_CAP + OUTPUT_CAP, 1, 0,
                Duration.ofMillis(500), 9, c.exposureUsd(), c.inputRateUpperBound(), c.outputRateUpperBound()));
        try (var s = open(r)) {
            var outcome = run(s, s.issue("VEX1-01"), response(expected("VEX1-01")), 1, control(), () -> {
                try { Thread.sleep(600); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
            assertThat(outcome.category()).isEqualTo("PROVIDER_TIMEOUT"); assertThat(outcome.backendAccepted()).isFalse();
            assertThat(calls).hasValue(1);
            assertThat(s.state()).isEqualTo(State.SEALED);
        }
    }
    @Test void sessionExpiryIsRecheckedBeforeAnySecretLookup() {
        var now = new AtomicReference<>(CLOCK.instant());
        var clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        var r = review("VEX1-01");
        try (var s = GeminiExtractionSession.open(new byte[]{1}, artifact -> r, directory, clock)) {
            var permit = s.issue("VEX1-01"); now.set(r.expires());
            assertThatThrownBy(() -> run(s, permit, response(expected("VEX1-01")), 1)).isInstanceOf(ProviderException.class);
            assertThat(s.state()).isEqualTo(State.EXPIRED); assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
        }
    }
    @Test void threeRequestSessionIsBoundedAndReservationsRemainDurableBeforeKey() {
        var shared = new GeminiBudget();
        try (var s = open(review("VEX1-01", "VEX1-02", "VEX1-03"))) {
            for (String id : List.of("VEX1-01", "VEX1-02", "VEX1-03")) {
                var result = GeminiExtractionSmoke.run(s, s.issue(id), 0, control(), () -> {
                    keys.incrementAndGet(); assertThat(s.requests()).isEqualTo(keys.get());
                    try (var files = Files.list(directory)) { assertThat(Files.size(files.findFirst().orElseThrow())).isGreaterThan(0); }
                    catch (java.io.IOException failure) { throw new AssertionError(failure); }
                    return "synthetic-noncredential";
                }, (request, c, t) -> { calls.incrementAndGet(); return SuccessfulHttpResponse.builder().statusCode(200)
                        .headers(Map.of()).body(response(expected(id))).build(); }, limits(3), shared, CLOCK);
                assertThat(result.category()).isEqualTo("SUCCESS");
            }
            assertThat(s.state()).isEqualTo(State.SEALED); assertThat(keys).hasValue(3); assertThat(calls).hasValue(3);
        }
    }
    @Test void processCeilingCannotBeResetByOpeningAnotherSession() {
        var process = new GeminiBudget();
        for (int i = 0; i < 10; i++) try (var s = open(review("VEX1-01"))) {
            var result = GeminiExtractionSmoke.run(s, s.issue("VEX1-01"), 0, control(), () -> { keys.incrementAndGet(); return "synthetic-noncredential"; },
                    (r, c, t) -> { calls.incrementAndGet(); return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                            .body(response(expected("VEX1-01"))).build(); }, limits(1), process, CLOCK);
            assertThat(result.category()).isEqualTo(i < 9 ? "SUCCESS" : "BUDGET_EXCEEDED");
        }
        assertThat(keys).hasValue(9); assertThat(calls).hasValue(9);
    }
    @Test void financialEnvelopeSubstitutionFailsBeforeReservationAndKey() {
        try (var s = open(review("VEX1-01"))) {
            var changed = GeminiProviderTest.limits("reviewed-model-id=" + MODEL, "max-input-tokens=16384", "max-output-tokens=1024",
                    "max-run-requests=1", "max-process-requests=9", "max-exposure-usd=1", "input-usd-per-million-upper-bound=2");
            assertThatThrownBy(() -> GeminiExtractionSmoke.run(s, s.issue("VEX1-01"), 0, control(),
                    () -> { keys.incrementAndGet(); return "synthetic-noncredential"; }, (r,c,t) -> { calls.incrementAndGet(); throw new AssertionError(); },
                    changed, new GeminiBudget(), CLOCK)).isInstanceOf(ProviderException.class);
            assertThat(keys).hasValue(0); assertThat(calls).hasValue(0); assertThat(s.requests()).isZero();
        }
    }
    @Test void changingJournalDirectoryCannotReplayVerifiedSession() throws Exception {
        var r = review("VEX1-01"); Path alternate = Files.createDirectory(directory.resolve("alternate"));
        assertThatThrownBy(() -> GeminiExtractionSession.open(new byte[]{1}, artifact -> r, alternate, CLOCK)).isInstanceOf(ProviderException.class);
        assertThat(alternate.toFile().list()).isEmpty(); assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings = {"VEX1-16", "VEX1-17", "VEX1-18", "VEX1-19", "VEX1-20"})
    void premodelRefusedFixturesCannotEnterTheNewExtractionPath(String id) {
        assertThatThrownBy(() -> open(review(id))).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(calls).hasValue(0);
    }
}
