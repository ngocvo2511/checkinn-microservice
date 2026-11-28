package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Offline operator tooling ONLY. No execute command, credential lookup or transport construction. */
public final class GeminiEvaluationPreflight {
    private GeminiEvaluationPreflight() { }
    public static void main(String[] args) throws Exception {
        try {
            if (args.length == 3 && args[0].equals("template")) {
                writeNew(Path.of(args[2]), GeminiOperatorApproval.JSON.writeValueAsBytes(template(args[1])));
                System.out.println("UNSIGNED_NOT_APPROVED_NOT_EXECUTABLE");
            } else if (args.length == 3 && args[0].equals("canonicalize")) {
                writeNew(Path.of(args[2]), GeminiOperatorApproval.canonical(GeminiOperatorApproval.JSON.readTree(read(Path.of(args[1]), 262144))));
                System.out.println("CANONICAL_BYTES_ONLY_NOT_APPROVED");
            } else if (args.length == 4 && args[0].equals("envelope")) {
                var payload = GeminiOperatorApproval.JSON.readTree(read(Path.of(args[1]), 262144));
                byte[] signature = read(Path.of(args[2]), 64); if (signature.length != 64) throw denied();
                ObjectNode envelope = GeminiOperatorApproval.JSON.createObjectNode().put("algorithm", "Ed25519");
                envelope.set("payload", payload); envelope.put("signature", Base64.getEncoder().encodeToString(signature));
                byte[] bytes = GeminiOperatorApproval.JSON.writeValueAsBytes(envelope); if (bytes.length > 262144) throw denied();
                writeNew(Path.of(args[3]), bytes); System.out.println("ENVELOPE_PREPARED_REQUIRES_VERIFICATION_NO_EXECUTION");
            } else if (args.length == 6 && args[0].equals("bind-scope")) {
                JsonNode scope = GeminiOperatorApproval.JSON.readTree(read(Path.of(args[2]), 65536));
                var dataset = new GeminiEvaluationDataset(read(Path.of(args[3]), 131072), Clock.systemUTC());
                ObjectNode payload = (ObjectNode) template(args[1]).path("payload");
                if (!scope.isArray() || scope.isEmpty() || scope.size() > 200) throw denied();
                var samples = payload.putArray("samples"); var sessions = new HashSet<String>(); var ids = new HashSet<String>();
                for (var item : scope) {
                    if (!fields(item).equals(Set.of("sampleId", "sessionId", "fixtureId"))
                            || !GeminiSharedAllocation.identity(item.path("sampleId").textValue()) || !ids.add(item.path("sampleId").textValue())
                            || !GeminiSharedAllocation.identity(item.path("sessionId").textValue())) throw denied();
                    var input = dataset.input(item.path("fixtureId").asText());
                    if (!input.remoteEligible() || !dataset.expectation(input.fixtureId()).remoteApproved()) throw denied();
                    sessions.add(item.path("sessionId").textValue());
                    samples.add(GeminiOperatorApproval.JSON.valueToTree(new GeminiCampaignApproval.Sample(item.path("sampleId").textValue(),
                            item.path("sessionId").textValue(), input.fixtureId(), input.contextDigest(), input.binding())));
                }
                payload.put("oracleVersion", dataset.oracleVersion()).put("oracleHash", dataset.oracleHash());
                payload.put("inputTokens", (long) samples.size() * INPUT_CAP).put("outputTokens", (long) samples.size() * OUTPUT_CAP);
                var policy = (ObjectNode) payload.path("policy"); policy.put("campaignId", args[4]).put("requests", samples.size())
                        .put("sessions", sessions.size()).put("tokens", (long) samples.size() * (INPUT_CAP + OUTPUT_CAP));
                writeNew(Path.of(args[5]), GeminiOperatorApproval.JSON.writeValueAsBytes(payload));
                System.out.println("BOUND_UNSIGNED_PAYLOAD_STILL_DRAFT_NOT_EXECUTABLE");
            } else if (args.length == 9 && args[0].equals("verify")) {
                var verifier = loadVerifier(Path.of(args[1]), args[2], args[3], Path.of(args[4]), Clock.systemUTC());
                byte[] artifact = read(Path.of(args[5]), 262144); Path directory = Path.of(args[7]);
                if (!args[8].equals("OFFLINE_ONLY")) throw denied();
                // Version peek only selects a closed verifier; it grants no trust or execution authority.
                var version = GeminiOperatorApproval.JSON.readTree(artifact).path("payload").path("version").asText();
                if (version.equals("operator-extraction-sequence-v2")) {
                    var sequence = verifier.sequence(artifact);
                    GeminiExtractionSession.validate(sequence.approval().review(), Clock.systemUTC());
                    GeminiSharedAllocation.directory(sequence.approval().review().journalDirectory(), directory);
                    System.out.println("SIGNED_SMOKE_PREFLIGHT_PASS_NO_EXECUTION");
                } else {
                    var result = GeminiEvaluationRunner.preflight(artifact, read(Path.of(args[6]), 131072), verifier, directory, Clock.systemUTC());
                    System.out.println(GeminiOperatorApproval.JSON.writeValueAsString(result));
                    System.out.println("SIGNED_CAMPAIGN_PREFLIGHT_PASS_NO_EXECUTION");
                }
            } else throw denied();
        } catch (Exception failure) { throw new IllegalArgumentException("OFFLINE_PREFLIGHT_REJECTED", null); }
    }
    static GeminiOperatorApproval loadVerifier(Path publicKey, String issuer, String keyId, Path revoked, Clock clock) throws Exception {
        if (!GeminiSharedAllocation.identity(issuer) || !GeminiSharedAllocation.identity(keyId)) throw denied();
        var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(read(publicKey, 4096)));
        var values = GeminiOperatorApproval.JSON.readTree(read(revoked, 16384));
        if (!values.isArray() || values.size() > 200) throw denied(); var references = new HashSet<String>();
        for (var value : values) if (!value.isTextual() || !GeminiSharedAllocation.identity(value.textValue()) || !references.add(value.textValue())) throw denied();
        return new GeminiOperatorApproval(Map.of(keyId, new GeminiOperatorApproval.Root(issuer, key)), references, clock);
    }
    private static byte[] read(Path file, int cap) throws Exception {
        try (var input = Files.newInputStream(file)) { byte[] bytes = input.readNBytes(cap + 1); if (bytes.length > cap || bytes.length == 0) throw denied(); return bytes; }
    }
    private static void writeNew(Path file, byte[] bytes) throws Exception { Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
    static ObjectNode template(String kind) {
        if (!Set.of("initial-smoke", "nlu", "extended").contains(kind)) throw denied();
        var root = GeminiOperatorApproval.JSON.createObjectNode().put("algorithm", "Ed25519").put("signature", "UNSIGNED_NOT_APPROVED_NOT_EXECUTABLE");
        ObjectNode payload = root.putObject("payload");
        payload.put("version", kind.equals("initial-smoke") ? "operator-extraction-sequence-v2" : "operator-evaluation-campaign-v1")
                .put("issuer", "REPLACE_ISSUER").put("keyId", "REPLACE_KEY_ID");
        var evidence = GeminiOperatorApproval.JSON.createObjectNode();
        for (String field : List.of("projectReference", "pricingReference", "quotaReference", "privacyReference", "allocationReference", "coordinatedWindowReference", "verifiedAt", "expires")) evidence.put(field, "UNVERIFIED_REPLACE");
        var allocation = GeminiOperatorApproval.JSON.createObjectNode().put("id", "REPLACE_ALLOCATION_ID").put("project", "REPLACE_PROJECT")
                .put("notBefore", "REPLACE_UTC_TIMESTAMP").put("expires", "REPLACE_UTC_TIMESTAMP").put("requests", 0).put("inputTokens", 0)
                .put("outputTokens", 0).put("totalTokens", 0).put("exposure", 0).put("concurrency", 1);
        allocation.putArray("campaigns"); payload.set("allocation", allocation);
        if (kind.equals("initial-smoke")) {
            payload.put("continuation", "STOP_ON_ANY_FAILURE"); var order = payload.putArray("fixtureOrder");
            var approval = payload.putObject("approval").put("version", "operator-extraction-approval-v1").put("issuer", "REPLACE_ISSUER")
                    .put("keyId", "REPLACE_KEY_ID").put("evaluationMode", "INITIAL_SMOKE").put("revocationPolicy", "DENY_ON_REVOCATION_OR_UNCERTAINTY");
            approval.set("evidence", evidence);
            var review = approval.putObject("review").put("sessionId", "REPLACE_SESSION_ID").put("state", "DRAFT").put("purpose", PURPOSE)
                    .put("manifest", MANIFEST).put("model", MODEL).put("protocol", VERSION).put("classification", "SYNTHETIC_ONLY")
                    .put("operatorIdentity", "REPLACE_OPERATOR").put("approvalReference", "REPLACE_APPROVAL_REF").put("notBefore", "REPLACE_UTC_TIMESTAMP")
                    .put("expires", "REPLACE_UTC_TIMESTAMP").put("journalDirectory", "REPLACE_ABSOLUTE_TRUSTED_DIRECTORY");
            var fixtures = review.putObject("fixtures");
            for (String id : List.of("VEX1-01", "VEX1-02", "VEX1-03")) { order.add(id); fixtures.set(id, GeminiOperatorApproval.JSON.valueToTree(binding(id))); }
            review.set("caps", GeminiOperatorApproval.JSON.valueToTree(new GeminiExtractionSession.Caps(3, INPUT_CAP, OUTPUT_CAP,
                    3 * INPUT_CAP, 3 * OUTPUT_CAP, 3 * (INPUT_CAP + OUTPUT_CAP), 1, 0, java.time.Duration.ofSeconds(10), 9,
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)));
        } else {
            payload.put("operatorReference", "REPLACE_OPERATOR").put("approvalReference", "REPLACE_APPROVAL_REF").put("state", "DRAFT")
                    .put("purpose", GeminiCampaignApproval.PURPOSE).put("model", MODEL).put("protocol", VERSION)
                    .put("datasetVersion", GeminiEvaluationDataset.DATASET_VERSION).put("datasetHash", GeminiEvaluationDataset.HASH)
                    .put("oracleVersion", "INDEPENDENT_ORACLE_REVIEW_REQUIRED").put("oracleHash", "REPLACE_REVIEWED_ORACLE_HASH")
                    .put("classification", "SYNTHETIC_ONLY").put("notBefore", "REPLACE_UTC_TIMESTAMP").put("continuation", "CONTINUE_SEMANTIC_ONLY")
                    .put("revocationPolicy", "DENY_ON_REVOCATION_OR_UNCERTAINTY").put("pricingCoverage", "UNVERIFIED")
                    .put("inputTokens", 0).put("outputTokens", 0).put("journalDirectory", "REPLACE_ABSOLUTE_TRUSTED_DIRECTORY");
            payload.set("evidence", evidence); payload.putArray("samples");
            payload.set("policy", GeminiOperatorApproval.JSON.valueToTree(new GeminiEvaluationCampaign.Policy("REPLACE_CAMPAIGN_ID",
                    kind.equals("nlu") ? GeminiEvaluationCampaign.Mode.VIETNAMESE_NLU_EVALUATION : GeminiEvaluationCampaign.Mode.EXTENDED_NLU_EVALUATION,
                    GeminiEvaluationDataset.HASH, 0, 0, 0, java.math.BigDecimal.ZERO, 1, 0, 0, INPUT_CAP, OUTPUT_CAP, 0,
                    java.time.Duration.ZERO, java.time.Instant.EPOCH, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "REPLACE_ALLOCATION_ID")));
        }
        return root;
    }
}
