package com.example.aiagent.model;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Actual SDK serialization; fake exchange/key/monotonic clock. No live test dependency. */
class GeminiLocalEvaluationTest {
    @TempDir Path directory;
    final AtomicInteger keys = new AtomicInteger(), calls = new AtomicInteger();
    final AtomicLong time = new AtomicLong();
    static final Clock CLOCK = GeminiExtractionUnblockTest.CLOCK;
    String[] live(int cap) { return new String[]{"--live", "--mode", cap == 3 ? "initial-smoke" : "vietnamese-nlu",
            "--max-requests", "" + cap, "--output", directory.resolve("run.json").toString()}; }
    com.fasterxml.jackson.databind.node.ObjectNode run(String[] args, java.util.function.Supplier<GeminiLlmProviderAdapter.Exchange> exchange) throws Exception {
        return GeminiLocalEvaluation.run(args, CLOCK, () -> { keys.incrementAndGet(); return "synthetic-noncredential"; },
                exchange, time::get, time::addAndGet);
    }
    @Test void defaultsAndOfflineNeverReadCredentialsOrConstructTransport() throws Exception {
        var exchange = (java.util.function.Supplier<GeminiLlmProviderAdapter.Exchange>) () -> { throw new AssertionError("transport"); };
        assertThat(run(new String[0], exchange).path("stopReason").asText()).isEqualTo("OFFLINE_PREFLIGHT");
        assertThat(run(new String[]{"--offline", "--mode", "vietnamese-nlu", "--max-requests", "43"}, exchange).path("planned").asInt()).isEqualTo(43);
        assertThat(keys).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings = {"--prompt", "--model", "--endpoint", "--dataset", "--fixture", "--unknown"})
    void arbitraryInputsRejectedBeforeKeys(String flag) {
        assertThatThrownBy(() -> run(new String[]{"--live", flag, "arbitrary"}, () -> { throw new AssertionError(); })).isInstanceOf(RuntimeException.class);
        assertThat(keys).hasValue(0);
    }
    @ParameterizedTest @ValueSource(ints = {-1, 0, 2, 44, 70, 71})
    void invalidCountsRejectedBeforeKeys(int count) {
        assertThatThrownBy(() -> run(live(count), () -> { throw new AssertionError(); })).isInstanceOf(RuntimeException.class);
        assertThat(keys).hasValue(0);
    }
    @Test void pinnedPlanExcludesEveryDenyAndPreservesSdkBodies() throws Exception {
        var plan = GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(live(43)), CLOCK);
        assertThat(plan).hasSize(43);
        var deny = Set.of("VNE1-035", "VNE1-036", "VNE1-052", "VNE1-053", "VNE1-054", "VNE1-055", "VNE1-056", "VNE1-057", "VNE1-058", "VNE1-059", "VNE1-060");
        assertThat(plan).noneMatch(s -> deny.contains(s.id()));
        for (int i = 0; i < 3; i++) assertThat(plan.get(i).input().body()).isEqualTo(body("VEX1-%02d".formatted(i + 1)));
        assertThat(plan).allMatch(s -> !s.input().body().contains("\"tools\"") && !s.input().body().contains("syntheticContext"));
    }
    @Test void smokeUsesSharedProtocolAndStopsOnlyAfterThreeSuccesses() throws Exception {
        var result = run(live(3), () -> (request, control, timeout) -> {
            int n = calls.incrementAndGet();
            assertThat(request.url()).isEqualTo(ENDPOINT);
            assertThat(request.body()).isEqualTo(body("VEX1-%02d".formatted(n)));
            assertThat(timeout).isLessThanOrEqualTo(Duration.ofSeconds(10));
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiExtractionUnblockTest.response(
                    GeminiExtractionUnblockTest.expected("VEX1-%02d".formatted(n)))).build();
        });
        assertThat(result.path("stopReason").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("actualHttpAttempts").asInt()).isEqualTo(3);
        assertThat(keys).hasValue(3); assertThat(calls).hasValue(3);
        assertThat(Files.readString(directory.resolve("run.json"))).doesNotContain("synthetic-noncredential", "x-goog-api-key");
        assertThatThrownBy(() -> run(live(3), () -> { throw new AssertionError(); })).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(keys).hasValue(3);
    }
    @Test void limiterHonorsSpacingRollingTokensHardCapAndDeadline() throws Exception {
        var limiter = new GeminiLocalEvaluation.Limiter(time::get, time::addAndGet);
        var starts = new ArrayList<Long>();
        for (int i = 0; i < 60; i++) { limiter.reserve(60); starts.add(time.get()); }
        for (int i = 1; i < starts.size(); i++) assertThat(starts.get(i) - starts.get(i - 1)).isGreaterThanOrEqualTo(Duration.ofSeconds(5).toNanos());
        for (long start : starts) {
            long count = starts.stream().filter(t -> t <= start && t > start - Duration.ofSeconds(60).toNanos()).count();
            assertThat(count).isLessThanOrEqualTo(12);
            assertThat(count * (INPUT_CAP + OUTPUT_CAP)).isLessThanOrEqualTo(200000);
        }
        assertThatThrownBy(() -> limiter.reserve(60)).isInstanceOf(ProviderException.class);
        time.set(Duration.ofMinutes(60).toNanos()); assertThatThrownBy(limiter::check).isInstanceOf(ProviderException.class);
        Thread.currentThread().interrupt();
        try { assertThatThrownBy(limiter::check).isInstanceOf(ProviderException.class); } finally { Thread.interrupted(); }
    }
    @ParameterizedTest @ValueSource(strings = {"quota", "schema", "unknown", "timeout", "cancel", "semantic"})
    void technicalFailuresStopAndContainedSemanticFailuresContinueWithoutRetry(String kind) throws Exception {
        var result = run(live(43), () -> (request, control, timeout) -> {
            calls.incrementAndGet();
            if (kind.equals("quota")) throw new ProviderException(ProviderException.Category.QUOTA_EXCEEDED);
            if (kind.equals("timeout")) throw new ProviderException(ProviderException.Category.PROVIDER_TIMEOUT);
            if (kind.equals("cancel")) { control.cancel(); control.check(); }
            String raw = GeminiExtractionUnblockTest.response(kind.equals("schema") ? "{}" : GeminiExtractionUnblockTest.expected(kind.equals("semantic") ? "VEX1-02" : "VEX1-01"));
            if (kind.equals("unknown")) raw = raw.replace("usageMetadata", "missingUsage");
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build();
        });
        boolean contained = Set.of("schema", "semantic").contains(kind);
        assertThat(result.path("actualHttpAttempts").asInt()).isEqualTo(contained ? 43 : 1);
        assertThat(result.path("notRun").asInt()).isEqualTo(contained ? 0 : 42);
        if (contained) assertThat(result.path("stopReason").asText()).isEqualTo("COMPLETED");
        else assertThat(result.path("stopReason").asText()).isNotEqualTo("COMPLETED");
        assertThat(keys).hasValue(contained ? 43 : 1); assertThat(calls).hasValue(contained ? 43 : 1);
    }
    @Test void missingCredentialDoesNotConstructTransport() throws Exception {
        var result = GeminiLocalEvaluation.run(live(3), CLOCK, () -> null, () -> { throw new AssertionError(); }, time::get, time::addAndGet);
        assertThat(result.path("actualHttpAttempts").asInt()).isZero();
        assertThat(result.path("credentialLookups").asInt()).isEqualTo(1);
        assertThat(result.path("stopReason").asText()).isEqualTo("CONFIGURATION");
    }
    @Test void mainSemanticErrorsContinueWithOriginalObservationAndPlannedDenominator() throws Exception {
        var plan = GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(live(43)), CLOCK);
        var result = run(live(43), () -> (request, control, timeout) -> {
            int index = calls.getAndIncrement();
            String output = index == 3 ? GeminiExtractionUnblockTest.expected("VEX1-02") : plan.get(index).expected().toString();
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiExtractionUnblockTest.response(output)).build();
        });
        assertThat(calls).hasValue(43); assertThat(keys).hasValue(43);
        assertThat(result.path("stopReason").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("observations").get(3).path("semanticPass").asBoolean()).isFalse();
        assertThat(result.path("scores").path("notRun").path("denominator").asInt()).isEqualTo(43);
        assertThat(result.path("notRun").asInt()).isZero();
    }
    @Test void shapeDiagnosticsNeverPersistProviderTextOrArbitraryNames() {
        var diagnostics = GeminiLocalEvaluation.diagnose("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"private-synthetic-value\"}]},\"finishReason\":\"STOP\",\"untrusted-secret-name\":true}],\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1,\"totalTokenCount\":2}}");
        assertThat(diagnostics.path("candidateAllowlistMatch").asBoolean()).isFalse();
        assertThat(diagnostics.toString()).doesNotContain("private-synthetic-value", "untrusted-secret-name");
    }
    @Test void isolatedProviderFailureContinuesDistinctSampleAndRetainsReservation() throws Exception {
        var plan = GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(live(43)),CLOCK);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            int index = calls.getAndIncrement(); exchange.getRequestBody().readAllBytes();
            byte[] body = (index == 0 ? "{}" : GeminiExtractionUnblockTest.response(plan.get(index).expected().toString())).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(index == 0 ? 503 : 200,body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }); server.start();
        com.fasterxml.jackson.databind.node.ObjectNode result;
        try { result = run(live(43), () -> new GeminiHttpTransport(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).localDiagnostics()); }
        finally { server.stop(0); }
        assertThat(calls).hasValue(43);
        var failed = result.path("observations").get(0);
        assertThat(failed.path("httpStatus").asInt()).isEqualTo(503);
        assertThat(failed.path("actualUsageStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(failed.path("reservedInputTokens").asInt()).isEqualTo(INPUT_CAP);
        assertThat(failed.path("cooldownSeconds").asInt()).isEqualTo(15);
        assertThat(result.path("usage").path("unknownAttempts").asInt()).isEqualTo(1);
        assertThat(result.path("actualHttpAttempts").asInt()).isEqualTo(43);
    }
    @Test void hardCapIs120EvenWhenCallerAsksForMore() throws Exception {
        var limiter = new GeminiLocalEvaluation.Limiter(time::get,time::addAndGet);
        for(int i=0;i<120;i++) limiter.reserve(121);
        assertThatThrownBy(() -> limiter.reserve(121)).isInstanceOf(ProviderException.class);
        assertThat(limiter.reserved).isEqualTo(120);
    }
    @Test void sealedSiblingCampaignCannotRestartBeforeCredentialLookup() throws Exception {
        Files.writeString(directory.resolve("sealed.json"),"{\"version\":\"local-development-evaluation-v2\",\"stopReason\":\"UNAVAILABLE\",\"actualHttpAttempts\":2}");
        assertThatThrownBy(() -> run(live(3),()->{throw new AssertionError("transport");})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0);
    }
    @Test void aggregateHardCapSurvivesAnotherProcessReport() throws Exception {
        var old=GeminiLlmProviderAdapter.JSON.createObjectNode().put("version","local-development-evaluation-v2")
                .put("stopReason","COMPLETED").put("actualHttpAttempts",120).put("startedAt",CLOCK.instant().minusSeconds(600).toString())
                .put("protocolVersion",GeminiLocalExtractionContract.V3);
        var rows=old.putArray("observations");
        for(int i=0;i<120;i++) rows.addObject().put("status","ATTEMPTED").put("phase","VIETNAMESE_NLU").put("dispatchOffsetMillis",5000*i);
        Files.writeString(directory.resolve("previous.json"),old.toString());
        assertThatThrownBy(() -> run(live(3),()->{throw new AssertionError("transport");})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0);
    }
}
