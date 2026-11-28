package com.example.aiagent.model;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Recorded synthetic provider compatibility; all exchanges remain offline. */
class GeminiCompatibilityTest {
    @TempDir Path directory;

    private String recorded() throws Exception {
        try (var input = getClass().getResourceAsStream("/gemini/compatibility/gemini-3.5-usage-metadata.json")) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private GeminiExtractionSmoke.Detailed evaluate(String raw) {
        return evaluate(raw, "VEX1-01");
    }
    private GeminiExtractionSmoke.Detailed evaluate(String raw, String fixture) {
        var plan = GeminiExtractionProtocol.fixture(fixture);
        var input = new GeminiEvaluationDataset.Input(fixture, plan.path("utterance").asText(), "", "", "", true,
                GeminiExtractionProtocol.body(fixture), GeminiExtractionProtocol.binding(fixture));
        return GeminiExtractionSmoke.evaluate(input, GeminiExtractionProtocol.INPUT_CAP,
                new LlmProviderAdapter.CallControl(java.time.Duration.ofSeconds(10)), () -> { },
                () -> java.time.Duration.ofSeconds(10), () -> "synthetic-noncredential",
                () -> (request, control, timeout) -> SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build(),
                GeminiExtractionUnblockTest.CLOCK);
    }

    @Test void actualMissingExclusiveFlagRemainsSchemaValidButBackendRejected() throws Exception {
        try (var input = getClass().getResourceAsStream("/gemini/compatibility/gemini-reference-price-missing-exclusive.json")) {
            String raw = new String(java.util.Objects.requireNonNull(input).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var result = evaluate(raw, "VEX1-02");
            assertThat(result.schemaValid()).isTrue(); assertThat(result.backendAccepted()).isFalse();
            assertThat(result.category()).isEqualTo("BACKEND_REJECTED");
            assertThat(result.pipeline().path("backendReason").asText()).isEqualTo("Uncorroborated extraction");
            assertThat(result.total()).isEqualTo(332);
        }
    }

    @Test void actualProviderResponsePassesSdkStrictExtractionAndBackend() throws Exception {
        String raw = recorded();
        var usage = GeminiLlmProviderAdapter.JSON.readTree(raw).path("usageMetadata");
        // Exact rejection predicate before this fix; extra fields are documented provider metadata.
        assertThat(java.util.Set.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount", "thoughtsTokenCount", "cachedContentTokenCount")
                .containsAll(GeminiExtractionProtocol.fields(usage))).isFalse();
        var result = evaluate(raw);
        assertThat(result.category()).isEqualTo("SUCCESS");
        assertThat(result.schemaValid()).isTrue(); assertThat(result.backendAccepted()).isTrue();
        assertThat(result.input()).isEqualTo(214); assertThat(result.output()).isEqualTo(79); assertThat(result.total()).isEqualTo(293);
        assertThat(result.pipeline().path("sdk").asText()).isEqualTo("PASS");
    }

    @Test void metadataExtensionsDoNotRelaxExtractionOrHideMissingUsage() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiLlmProviderAdapter.JSON.readTree(recorded());
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("usageMetadata")).putObject("futureMetadata").put("ignored", true);
        assertThat(evaluate(root.toString()).schemaValid()).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.at("/candidates/0/content/parts/0")).put("text", "{}");
        var invalid = evaluate(root.toString());
        assertThat(invalid.category()).isEqualTo("EXTRACTION_SCHEMA_INVALID");
        assertThat(invalid.backendAccepted()).isFalse(); assertThat(invalid.total()).isEqualTo(293);
        ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("usageMetadata")).remove("totalTokenCount");
        var unknown = evaluate(root.toString());
        assertThat(unknown.category()).isEqualTo("UNKNOWN_USAGE"); assertThat(unknown.total()).isNull();
    }

    @Test void thinkingAndCachedCountersAreNotDoubleCounted() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiLlmProviderAdapter.JSON.readTree(recorded());
        var usage = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("usageMetadata");
        usage.put("thoughtsTokenCount", 10).put("cachedContentTokenCount", 100).put("totalTokenCount", 303);
        var result = evaluate(root.toString());
        assertThat(result.category()).isEqualTo("SUCCESS"); assertThat(result.total()).isEqualTo(303);
        assertThat(result.pipeline().path("thinkingTokens").asInt()).isEqualTo(10);
        usage.put("thoughtsTokenCount", 400);
        assertThat(evaluate(root.toString()).category()).isEqualTo("UNKNOWN_USAGE");
    }

    @Test void containedMainSchemaFailureWithKnownUsageContinuesWithoutRetry() throws Exception {
        var time = new AtomicLong(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        String[] args = {"--live", "--mode", "vietnamese-nlu", "--max-requests", "43", "--output", directory.resolve("main.json").toString()};
        var plan = GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(args), GeminiExtractionUnblockTest.CLOCK);
        var report = GeminiLocalEvaluation.run(args, GeminiExtractionUnblockTest.CLOCK, () -> "synthetic-noncredential",
                () -> (request, control, timeout) -> {
                    int index = calls.getAndIncrement();
                    return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(
                            GeminiExtractionUnblockTest.response(index == 3 ? "{}" : plan.get(index).expected().toString())).build();
                }, time::get, time::addAndGet);
        assertThat(calls).hasValue(43);
        assertThat(report.path("stopReason").asText()).isEqualTo("COMPLETED");
        var failed = report.path("observations").get(3);
        assertThat(failed.path("category").asText()).isEqualTo("EXTRACTION_SCHEMA_INVALID");
        assertThat(failed.path("backendResult").asText()).isEqualTo("NOT_EVALUATED");
        assertThat(failed.path("pipeline").path("sdk").asText()).isEqualTo("PASS");
        assertThat(report.path("retries").asInt()).isZero();
    }

    @Test void batchHardCapIsOneHundredTwentyAcrossLimiterReservations() throws Exception {
        var time = new AtomicLong();
        var limiter = new GeminiLocalEvaluation.Limiter(time::get, time::addAndGet);
        for (int i = 0; i < 120; i++) limiter.reserve(121);
        assertThatThrownBy(() -> limiter.reserve(121)).isInstanceOf(ProviderException.class);
    }

    @Test void supplementalPricePlanHasFixedSeparateIdentitiesAndFrozenOracle() throws Exception {
        var options = GeminiLocalEvaluation.options(new String[]{"--offline", "--mode", "reference-price-diagnostic", "--max-requests", "3"});
        var probes = GeminiLocalEvaluation.plan(options, GeminiExtractionUnblockTest.CLOCK);
        assertThat(probes).extracting(GeminiLocalEvaluation.Sample::id).containsExactly("SUP1-REF-01", "SUP1-REF-02", "SUP1-REF-03");
        assertThat(probes).allMatch(s -> s.phase().equals("SUPPLEMENTAL") && s.expected().at("/fields/referencePriceExclusive/value").asBoolean());
        assertThat(probes).extracting(s -> s.input().fixtureId()).containsExactly("VNE1-002", "VNE1-020", "VNE1-050");
    }

    @Test void diagnosticIsOnePinnedRequestAndRawCaptureIsExplicit() throws Exception {
        var time = new AtomicLong();
        String raw = GeminiExtractionUnblockTest.response(GeminiExtractionUnblockTest.expected("VEX1-01"));
        for (boolean debug : new boolean[]{false, true}) {
            Path output = directory.resolve("run-" + debug + ".json");
            var args = new java.util.ArrayList<>(java.util.List.of("--live", "--mode", "compatibility-diagnostic",
                    "--max-requests", "1", "--output", output.toString()));
            if (debug) args.add("--debug-response");
            var report = GeminiLocalEvaluation.run(args.toArray(String[]::new), GeminiExtractionUnblockTest.CLOCK,
                    () -> "synthetic-noncredential", () -> (request, control, timeout) ->
                            SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(raw).build(),
                    time::get, time::addAndGet);
            assertThat(report.path("actualHttpAttempts").asInt()).isEqualTo(1);
            assertThat(report.path("observations").get(0).path("phase").asText()).isEqualTo("COMPATIBILITY_DIAGNOSTIC");
            assertThat(Files.exists(output.resolveSibling(output.getFileName() + ".responses"))).isEqualTo(debug);
        }
    }

    @Test void diagnosticsRedactCredentialsWithoutHidingSyntheticExtraction() {
        String raw = "{\"text\":\"Đà Lạt active-test-key Bearer abc.def.xyz\",\"x-goog-api-key\":\"other-key\",\"cookie\":\"session=value\"}";
        assertThat(GeminiLocalEvaluation.sanitizeResponse(raw, "active-test-key"))
                .contains("Đà Lạt").doesNotContain("active-test-key", "other-key", "session=value", "abc.def.xyz");
    }
}
