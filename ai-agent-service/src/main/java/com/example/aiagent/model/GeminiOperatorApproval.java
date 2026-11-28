package com.example.aiagent.model;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Offline-capable Ed25519 verifier. Trust roots MUST come from operator provisioning, never the artifact.
 * No bean, key generator, environment lookup or signing API. A signature is not a dispatch permit. */
final class GeminiOperatorApproval implements GeminiExtractionSession.ApprovalVerifier {
    static final ObjectMapper JSON = GeminiLlmProviderAdapter.JSON.copy().registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    record Root(String issuer, PublicKey key) { }
    record Evidence(String projectReference, String pricingReference, String quotaReference,
                    String privacyReference, Instant verifiedAt, Instant expires,
                    String allocationReference, String coordinatedWindowReference) { }
    record Claims(String version, String issuer, String keyId, String evaluationMode,
                  String revocationPolicy, Evidence evidence, GeminiExtractionSession.Reviewed review) {
        @Override public String toString() { return "OperatorClaims[redacted]"; }
    }
    record SequenceClaims(String version, String issuer, String keyId, Claims approval, List<String> fixtureOrder,
                          String continuation, GeminiSharedAllocation.Limits allocation) {
        SequenceClaims { fixtureOrder = List.copyOf(fixtureOrder); }
    }
    private final Map<String, Root> roots;
    private final Set<String> revoked;
    private final Clock clock;
    GeminiOperatorApproval(Map<String, Root> roots, Set<String> revoked, Clock clock) {
        this.roots = Map.copyOf(roots); this.revoked = Set.copyOf(revoked); this.clock = Objects.requireNonNull(clock);
        for (var root : roots.values()) if (!Set.of("EdDSA", "Ed25519").contains(root.key().getAlgorithm())) throw denied();
    }
    @Override public GeminiExtractionSession.Reviewed verify(byte[] artifact) { return claims(artifact).review(); }
    Claims claims(byte[] artifact) {
        try {
            Claims c = JSON.treeToValue(signedPayload(artifact, 65536), Claims.class);
            validateClaims(c); return c;
        } catch (Exception failure) { throw denied(); }
    }
    private void validateClaims(Claims c) {
        try {
            if (revoked.contains(c.review().approvalReference()) || !"operator-extraction-approval-v1".equals(c.version())
                    || !"DENY_ON_REVOCATION_OR_UNCERTAINTY".equals(c.revocationPolicy())
                    || !"INITIAL_SMOKE".equals(c.evaluationMode())
                    || c.review().state() != GeminiExtractionSession.State.APPROVED_FOR_SEPARATE_LIVE_BATCH
                    || !PURPOSE.equals(c.review().purpose()) || !MODEL.equals(c.review().model())
                    || !VERSION.equals(c.review().protocol()) || !MANIFEST.equals(c.review().manifest())
                    || !"SYNTHETIC_ONLY".equals(c.review().classification())
                    || clock.instant().isBefore(c.review().notBefore()) || !clock.instant().isBefore(c.review().expires())) throw denied();
            Evidence e = c.evidence();
            if (e.verifiedAt().isAfter(clock.instant()) || !clock.instant().isBefore(e.expires())
                    || e.expires().isBefore(c.review().expires())) throw denied();
            for (String reference : List.of(e.projectReference(), e.pricingReference(), e.quotaReference(), e.privacyReference(),
                    e.allocationReference(), e.coordinatedWindowReference(), c.review().operatorIdentity(), c.review().approvalReference()))
                if (!reference.matches("[A-Za-z0-9_-]{1,100}")) throw denied();
            for (var entry : c.review().fixtures().entrySet()) if (!binding(entry.getKey()).equals(entry.getValue())) throw denied();
        } catch (Exception failure) { throw denied(); }
    }
    SequenceClaims sequence(byte[] artifact) {
        try {
            var c = JSON.treeToValue(signedPayload(artifact, 65536), SequenceClaims.class);
            if (!"operator-extraction-sequence-v2".equals(c.version()) || !c.issuer().equals(c.approval().issuer())
                    || !c.keyId().equals(c.approval().keyId()) || !"STOP_ON_ANY_FAILURE".equals(c.continuation())
                    || c.fixtureOrder().isEmpty() || c.fixtureOrder().size() > 3
                    || c.fixtureOrder().stream().anyMatch(id -> !Set.of("VEX1-01", "VEX1-02", "VEX1-03").contains(id))
                    || new HashSet<>(c.fixtureOrder()).size() != c.fixtureOrder().size()
                    || !new HashSet<>(c.fixtureOrder()).equals(c.approval().review().fixtures().keySet())
                    || c.fixtureOrder().size() != c.approval().review().caps().requests()) throw denied();
            validateClaims(c.approval()); GeminiSharedAllocation.validate(c.allocation(), clock);
            checkReference(c.allocation().id()); checkReference(c.approval().review().sessionId());
            if (!c.allocation().project().equals(c.approval().evidence().projectReference())
                    || !c.allocation().id().equals(c.approval().evidence().allocationReference())
                    || !c.allocation().campaigns().contains(c.approval().review().sessionId())
                    || c.approval().review().notBefore().isBefore(c.allocation().notBefore())
                    || c.approval().review().expires().isAfter(c.allocation().expires())) throw denied();
            return c;
        } catch (Exception failure) { throw denied(); }
    }
    /** Shared verifier for distinct versioned artifacts. Caller must validate its closed payload contract. */
    JsonNode signedPayload(byte[] artifact, int maximumBytes) {
        try {
            if (artifact == null || artifact.length == 0 || artifact.length > maximumBytes || maximumBytes > 262144) throw denied();
            JsonNode envelope = JSON.readTree(artifact);
            if (!fields(envelope).equals(Set.of("algorithm", "payload", "signature"))
                    || !"Ed25519".equals(envelope.path("algorithm").textValue()) || !envelope.path("signature").isTextual()) throw denied();
            JsonNode payload = envelope.path("payload"); Root root = roots.get(payload.path("keyId").textValue());
            if (root == null || !root.issuer().equals(payload.path("issuer").textValue()) || revoked.contains(payload.path("keyId").textValue())) throw denied();
            byte[] signature = Base64.getDecoder().decode(envelope.path("signature").textValue());
            if (signature.length != 64) throw denied();
            Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(root.key());
            verifier.update(canonical(payload));
            if (!verifier.verify(signature)) throw denied();
            return payload;
        } catch (Exception failure) { throw denied(); }
    }
    void checkReference(String reference) { if (revoked.contains(reference)) throw denied(); }
    /** V1 canonical profile: recursively sorted object keys, array order retained, Jackson compact UTF-8.
     * Signers must sign these exact bytes, not arbitrary input JSON. Not an RFC8785 claim. */
    static byte[] canonical(JsonNode value) {
        try { return JSON.writeValueAsString(sorted(value)).getBytes(StandardCharsets.UTF_8); }
        catch (Exception failure) { throw denied(); }
    }
    private static JsonNode sorted(JsonNode value) {
        if (value.isNumber()) return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());
        if (value.isObject()) {
            ObjectNode result = JSON.createObjectNode(); var names = new TreeSet<String>(); value.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, sorted(value.get(name))); return result;
        }
        if (value.isArray()) { ArrayNode result = JSON.createArrayNode(); value.forEach(item -> result.add(sorted(item))); return result; }
        return value;
    }
}
