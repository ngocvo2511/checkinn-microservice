package com.example.aiagent.model;

import com.example.aiagent.nlu.VietnameseExtraction;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;

/** Offline replay of retained actual synthetic responses. Never constructs a provider or reads credentials. */
public final class GeminiHistoricalReplay {
    private GeminiHistoricalReplay() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("source report and new output required");
        var json = GeminiLlmProviderAdapter.JSON;
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        var historical = json.readTree(source);
        var clock = Clock.fixed(Instant.parse(historical.path("startedAt").asText()), ZoneOffset.UTC);
        var report = json.createObjectNode().put("version", "actual-historical-replay-v1")
                .put("sourceSha256", GeminiExtractionProtocol.hash(source)).put("evaluationClock", clock.instant().toString())
                .put("model", historical.path("model").asText()).put("contract", historical.path("protocolVersion").asText())
                .put("actualHttpAttempts", 0).put("modelSemanticClassification", "FROZEN_HISTORICAL");
        var rows = report.putArray("observations");
        for (var old : historical.path("observations")) {
            if (!old.path("status").asText().equals("ATTEMPTED")) continue;
            var row = rows.addObject().put("sampleId", old.path("sampleId").asText());
            String raw = old.path("actualExtractionText").asText(null);
            if (raw == null && old.hasNonNull("actualExtraction")) {
                raw = old.path("actualExtraction").isTextual() ? old.path("actualExtraction").asText() : old.path("actualExtraction").toString();
                row.put("evidence", "STRUCTURED_RESPONSE_REPLAY");
            } else row.put("evidence", "ACTUAL_RESPONSE_TEXT_REPLAY");
            row.put("historicalSchemaValid", old.path("schemaValid").asBoolean())
                    .put("semanticPass", old.path("semanticPass").asBoolean()).put("historicalBackendAccepted", old.path("backendAccepted").asBoolean());
            if (raw == null) { row.put("status", "REPLAY_NOT_AVAILABLE"); continue; }
            row.put("responseSha256", GeminiExtractionProtocol.hash(raw)).set("expectedExtraction", old.path("expectedExtraction"));
            row.put("utterance", old.path("utterance").asText()).put("schemaValid", false).put("backendAccepted", false);
            try {
                var proposal = VietnameseExtraction.parse(raw, clock); row.put("schemaValid", true);
                var effective = GeminiExtractionSmoke.corroborate(proposal, old.path("utterance").asText(), clock);
                row.put("backendAccepted", proposal.action() == VietnameseExtraction.Action.CLARIFY || effective.clarification() == null);
                row.put("status", "REPLAYED");
            } catch (IllegalArgumentException | DateTimeException rejected) {
                row.put("status", row.path("schemaValid").asBoolean() ? "REPLAYED" : "SCHEMA_REJECTED");
                row.put("reason", row.path("schemaValid").asBoolean() ? "INDEPENDENT_CORROBORATION_REJECTED" : "STRICT_TYPED_VALIDATION_REJECTED");
            }
        }
        var matrix = report.putObject("matrix");
        for (boolean correct : new boolean[]{true,false}) for (boolean accepted : new boolean[]{true,false}) {
            int count = 0;
            for (var row : rows) if (row.path("schemaValid").asBoolean() && row.path("semanticPass").asBoolean() == correct
                    && row.path("backendAccepted").asBoolean() == accepted) count++;
            matrix.put((correct ? "correct" : "incorrect") + (accepted ? "Accepted" : "Rejected"), count);
        }
        Files.writeString(Path.of(args[1]), json.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardOpenOption.CREATE_NEW);
        System.out.println(matrix);
    }
}
