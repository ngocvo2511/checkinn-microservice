package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.aiagent.nlu.*;
import java.time.Clock;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Pinned synthetic inputs; reviewer oracle is separately hashed and signed by campaign authority.
 * No supplied context is sent to Gemini: current protocol is single-utterance extraction only. */
final class GeminiEvaluationDataset {
    static final String HASH = "9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950";
    static final String DATASET_VERSION = "vietnamese-nlu-evaluation-candidates-v1";
    record Expectation(JsonNode response, boolean backendExpected, String grammarCategory, boolean remoteApproved) {
        Expectation { response = response.deepCopy(); }
        @Override public JsonNode response() { return response.deepCopy(); }
    }
    record Oracle(String version, String datasetHash, String reviewStatus, String reviewerReference,
                  Map<String, Expectation> fixtures) {
        Oracle { fixtures = Map.copyOf(fixtures); }
    }
    record Input(String fixtureId, String utterance, String contextDigest, String category, String split,
                 boolean remoteEligible, String body, Binding binding) {
        @Override public String toString() { return "EvaluationInput[payload=omitted]"; }
    }
    private final Map<String, JsonNode> fixtures;
    private final Map<String, Input> inputs = new HashMap<>();
    private final Oracle oracle;
    private final String oracleHash;
    GeminiEvaluationDataset(byte[] reviewedOracle, Clock clock) {
        try {
            if (reviewedOracle == null || reviewedOracle.length < 1 || reviewedOracle.length > 131072) throw denied();
            byte[] snapshot = reviewedOracle.clone();
            oracle = GeminiOperatorApproval.JSON.readValue(snapshot, Oracle.class);
            oracleHash = hash(snapshot);
            if (!HASH.equals(oracle.datasetHash()) || !GeminiSharedAllocation.identity(oracle.version())
                    || !"INDEPENDENTLY_REVIEWED".equals(oracle.reviewStatus())
                    || !GeminiSharedAllocation.identity(oracle.reviewerReference())
                    || oracle.fixtures().isEmpty() || oracle.fixtures().size() > 60) throw denied();
            try (var stream = getClass().getResourceAsStream("/gemini/vietnamese-nlu-evaluation-candidates-v1.json")) {
                byte[] bytes = Objects.requireNonNull(stream).readNBytes(131073);
                if (!HASH.equals(hash(bytes))) throw denied();
                var values = new LinkedHashMap<String, JsonNode>();
                for (var item : GeminiOperatorApproval.JSON.readTree(bytes).path("fixtures"))
                    if (values.put(item.path("id").asText(), item) != null) throw denied();
                if (values.size() != 60) throw denied(); fixtures = Map.copyOf(values);
            }
            for (var entry : oracle.fixtures().entrySet()) {
                if (!fixtures.containsKey(entry.getKey())) throw denied();
                var proposal = VietnameseExtraction.parse(entry.getValue().response().toString(), clock);
                if ((proposal.action() == VietnameseExtraction.Action.SELECTED || proposal.action() == VietnameseExtraction.Action.COMPARISON)
                        && !proposal.reference().equals(fixtures.get(entry.getKey()).path("utterance").asText())) throw denied();
                if (!Set.of("SUPPORTED", "UNSUPPORTED_GRAMMAR", "EXPECTED_CLARIFICATION", "EXPECTED_REJECTION", "REFERENCE_EXTRACTION_ONLY")
                        .contains(entry.getValue().grammarCategory())) throw denied();
            }
        } catch (Exception failure) { throw denied(); }
    }
    synchronized Input input(String id) {
        return inputs.computeIfAbsent(id, key -> {
            JsonNode f = fixtures.get(key); if (f == null) throw denied();
            String utterance = f.path("utterance").asText(); VietnameseSecurityPreflight.validate(utterance);
            if (!"SYNTHETIC_ONLY".equals(f.path("privacyClassification").asText())) throw denied();
            boolean eligible = !"DENY".equals(f.path("remoteDefault").asText()) && !VietnameseSecurityPreflight.refused(utterance);
            String body = syntheticBody(utterance);
            var binding = new Binding(hash(utterance), hash(instruction()), hash(schema()), hash(body));
            return new Input(key, utterance, hash(GeminiOperatorApproval.canonical(f.path("syntheticContext"))),
                    f.path("category").asText(), f.path("split").asText(), eligible, body, binding);
        });
    }
    Expectation expectation(String id) { var result = oracle.fixtures().get(id); if (result == null) throw denied(); return result; }
    String oracleHash() { return oracleHash; }
    String oracleVersion() { return oracle.version(); }
    static ObjectNode semantic(JsonNode protocol) {
        ObjectNode result = GeminiOperatorApproval.JSON.createObjectNode();
        result.set("action", protocol.path("action")); result.set("intent", protocol.path("intent"));
        result.set("criteria", protocol.path("fields")); result.set("reference", protocol.path("reference"));
        result.set("missingSlots", protocol.path("missingSlots")); result.set("inferredFields", protocol.path("inferredFields"));
        for (String field : List.of("missingSlots", "inferredFields")) {
            var names = new TreeSet<String>(); result.path(field).forEach(n -> names.add(n.asText()));
            var array = result.putArray(field); names.forEach(array::add);
        }
        // Numeric representation and unordered missing-slot hints do not create linguistic errors.
        try { return (ObjectNode) GeminiOperatorApproval.JSON.readTree(GeminiOperatorApproval.canonical(result)); }
        catch (Exception failure) { throw denied(); }
    }
}
