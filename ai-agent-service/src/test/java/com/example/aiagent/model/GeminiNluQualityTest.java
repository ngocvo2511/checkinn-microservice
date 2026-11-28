package com.example.aiagent.model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.atomic.*;
import java.util.Map;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import static org.assertj.core.api.Assertions.*;
class GeminiNluQualityTest {
    @Test void versionedPlanFitsAndPreservesV1() throws Exception {
        String body = GeminiExtractionProtocol.syntheticBody(GeminiLocalExtractionContract.request("Tìm khách sạn ở Đà Lạt", GeminiLocalExtractionContract.V2));
        var versions = GeminiLlmProviderAdapter.JSON.createArrayNode();
        for (String version : java.util.List.of(GeminiExtractionProtocol.VERSION, GeminiLocalExtractionContract.V2, GeminiLocalExtractionContract.V3)) {
            String instruction = GeminiLocalExtractionContract.instruction(version), schema = GeminiLocalExtractionContract.schema(version);
            versions.addObject().put("version", version).put("instruction", instruction).put("schema", schema)
                    .put("instructionDigest", GeminiExtractionProtocol.hash(instruction)).put("schemaDigest", GeminiExtractionProtocol.hash(schema));
        }
        java.nio.file.Files.writeString(Path.of("target/nlu-quality-contract-versions.json"), versions.toPrettyString());
        assertThat(GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(new String[]{"--mode", "quality-baseline", "--protocol", GeminiLocalExtractionContract.V3, "--max-requests", "50"}), GeminiExtractionUnblockTest.CLOCK)).hasSize(50);
        assertThat(GeminiExtractionProtocol.hash(GeminiExtractionProtocol.instruction())).isEqualTo("48b6b4eb7d57cbf1d13ce6a561bc0f82ff72a94de8c38a99ad9670754f64f8eb");
    }
    @TempDir Path directory;
    @Test void smokeSemanticAndIsolatedSchemaFailuresContinueToAllForty() throws Exception {
        var args = new String[]{"--live", "--mode", "vietnamese-nlu", "--max-requests", "43", "--output", directory.resolve("continued.json").toString()};
        var clock = GeminiExtractionUnblockTest.CLOCK;
        var plan = GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(args), clock);
        var time = new AtomicLong(); var calls = new AtomicInteger();
        var report = GeminiLocalEvaluation.run(args, clock, () -> "synthetic-noncredential", () -> (request, control, timeout) -> {
            int n = calls.getAndIncrement();
            String raw = n == 0 ? GeminiExtractionUnblockTest.expected("VEX1-02") : n == 1 ? "{}" : plan.get(n).expected().toString();
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiExtractionUnblockTest.response(raw)).build();
        }, time::get, time::addAndGet);
        assertThat(calls).hasValue(43);
        assertThat(report.path("observations").get(0).path("semanticPass").asBoolean()).isFalse();
        assertThat(report.path("observations").get(1).path("category").asText()).isEqualTo("EXTRACTION_SCHEMA_INVALID");
        assertThat(report.path("notRun").asInt()).isZero();
        assertThat(report.path("retries").asInt()).isZero();
    }
    @Test void descriptionsNeverChangeV1ValidationConstraints() throws Exception {
        var old = GeminiLlmProviderAdapter.JSON.readTree(GeminiExtractionProtocol.schema());
        var improved = GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalExtractionContract.schema(GeminiLocalExtractionContract.V3));
        stripDescriptions(improved);
        assertThat(improved).isEqualTo(old);
        assertThat(GeminiLocalExtractionContract.instruction(GeminiLocalExtractionContract.V3)).contains("CLEAR only on an explicit", "BOTH maxPrice REPLACE X and referencePriceExclusive REPLACE true");
    }
    @Test void criticalUnsafeOutputStillStopsBeforeNextSample() throws Exception {
        var time = new AtomicLong(); var calls = new AtomicInteger();
        String[] args = {"--live", "--mode", "vietnamese-nlu", "--max-requests", "43", "--output", directory.resolve("unsafe.json").toString()};
        var report = GeminiLocalEvaluation.run(args, GeminiExtractionUnblockTest.CLOCK, () -> "synthetic-noncredential", () -> (request, control, timeout) -> {
            calls.incrementAndGet();
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(GeminiExtractionUnblockTest.response("{\"provenance\":\"USER_CONFIRMED\"}")).build();
        }, time::get, time::addAndGet);
        assertThat(calls).hasValue(1);
        assertThat(report.path("stopReason").asText()).isEqualTo("UNSAFE_OUTPUT");
    }
    static void stripDescriptions(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove("description");
        node.forEach(GeminiNluQualityTest::stripDescriptions);
    }
}

