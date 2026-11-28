package com.example.aiagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.*;

@SuppressWarnings("removal")
class GeminiOperatorAuthorizationTest {
    @TempDir Path directory;
    KeyPair signer;
    GeminiOperatorApproval verifier;
    ObjectNode payload;
    AtomicInteger keys = new AtomicInteger(), transports = new AtomicInteger(), dispatches = new AtomicInteger();
    @BeforeAll static void networkGuard() { GeminiExtractionUnblockTest.denyExternalAndKeys(); }
    @AfterAll static void restoreGuard() { GeminiExtractionUnblockTest.restore(); }
    @BeforeEach void prepare() throws Exception {
        signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); // Test-owned only, never persisted.
        verifier = new GeminiOperatorApproval(Map.of("test-key", new GeminiOperatorApproval.Root("TEST_ISSUER", signer.getPublic())), Set.of(), CLOCK);
        var helper = new GeminiExtractionUnblockTest(); helper.directory = directory;
        var evidence = new GeminiOperatorApproval.Evidence("TEST_PROJECT", "TEST_PRICE", "TEST_QUOTA", "TEST_PRIVACY",
                CLOCK.instant(), CLOCK.instant().plusSeconds(120), "TEST_ALLOCATION", "TEST_WINDOW");
        payload = GeminiOperatorApproval.JSON.valueToTree(new GeminiOperatorApproval.Claims("operator-extraction-approval-v1",
                "TEST_ISSUER", "test-key", "INITIAL_SMOKE", "DENY_ON_REVOCATION_OR_UNCERTAINTY", evidence, helper.review("VEX1-01")));
    }
    byte[] signed(ObjectNode value) throws Exception {
        var signature = Signature.getInstance("Ed25519"); signature.initSign(signer.getPrivate());
        signature.update(GeminiOperatorApproval.canonical(value));
        ObjectNode envelope = GeminiOperatorApproval.JSON.createObjectNode().put("algorithm", "Ed25519");
        envelope.set("payload", value); envelope.put("signature", Base64.getEncoder().encodeToString(signature.sign()));
        return GeminiOperatorApproval.JSON.writeValueAsBytes(envelope);
    }
    GeminiExtractionSmoke.Outcome execute(byte[] artifact, GeminiProperties limits, GeminiBudget budget, Path target) {
        return GeminiAuthorizedExtraction.execute(true, true, false, artifact, verifier, target, "VEX1-01", limits, budget, CLOCK,
                control(), () -> {
                    keys.incrementAndGet();
                    try (var files = Files.list(directory)) { assertThat(Files.size(files.findFirst().orElseThrow())).isGreaterThan(0); }
                    catch (Exception e) { throw new AssertionError(e); }
                    return "TEST_SENTINEL_NOT_A_CREDENTIAL";
                }, () -> {
                    transports.incrementAndGet(); assertThat(keys).hasValue(1);
                    return (request, control, timeout) -> {
                        dispatches.incrementAndGet(); assertThat(request.url()).isEqualTo(ENDPOINT);
                        assertThat(request.body()).isEqualTo(body("VEX1-01"));
                        return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(response(expected("VEX1-01"))).build();
                    };
                });
    }
    @Test void signedApprovalDispatchesExactMockOnceAndCannotReplayAfterRestart() throws Exception {
        var decoded = GeminiOperatorApproval.JSON.treeToValue(payload, GeminiOperatorApproval.Claims.class);
        assertThat(decoded.review().journalDirectory()).isEqualTo(directory);
        byte[] artifact = signed(payload);
        assertThat(verifier.verify(artifact)).isEqualTo(decoded.review());
        assertThat(execute(artifact, limits(1), new GeminiBudget(), directory).category()).isEqualTo("SUCCESS");
        assertThatThrownBy(() -> execute(artifact, limits(1), new GeminiBudget(), directory)).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(1); assertThat(transports).hasValue(1); assertThat(dispatches).hasValue(1);
        try (var files = Files.list(directory)) { assertThat(Files.readString(files.findFirst().orElseThrow())).doesNotContain("TEST_SENTINEL", "Đà Lạt"); }
    }
    @ParameterizedTest @ValueSource(strings = {"signature", "key", "keyId", "issuer", "root", "unknown", "missing", "purpose", "model", "protocol", "manifest", "fixture", "request", "schema", "expired", "future", "revoked", "sealed", "evidence", "mode", "version", "financial", "fractional-cap", "string-cap", "missing-cap"})
    void rejectsInvalidArtifactBeforeCredentialLookup(String kind) throws Exception {
        ObjectNode review = (ObjectNode) payload.path("review");
        switch (kind) {
            case "issuer" -> payload.put("issuer", "UNTRUSTED");
            case "keyId" -> payload.put("keyId", "UNKNOWN_KEY");
            case "root" -> payload.put("publicKey", Base64.getEncoder().encodeToString(signer.getPublic().getEncoded()));
            case "unknown" -> review.put("approved", true);
            case "missing" -> review.remove("operatorIdentity");
            case "purpose", "model", "protocol", "manifest" -> review.put(kind, "WRONG");
            case "fixture", "request", "schema" -> ((ObjectNode) review.path("fixtures").path("VEX1-01")).put(kind.equals("fixture") ? "fixtureDigest" : kind + "Digest", "0".repeat(64));
            case "expired" -> review.put("expires", CLOCK.instant().minusSeconds(1).toString());
            case "future" -> review.put("notBefore", CLOCK.instant().plusSeconds(1).toString());
            case "revoked" -> verifier = new GeminiOperatorApproval(Map.of("test-key", new GeminiOperatorApproval.Root("TEST_ISSUER", signer.getPublic())), Set.of("test-key"), CLOCK);
            case "sealed" -> review.put("state", "SEALED");
            case "evidence" -> ((ObjectNode) payload.path("evidence")).put("expires", CLOCK.instant().minusSeconds(1).toString());
            case "mode" -> payload.put("evaluationMode", "UNRESTRICTED");
            case "version" -> payload.put("version", "v999");
            case "financial" -> ((ObjectNode) review.path("caps")).put("inputRateUpperBound", 2);
            case "fractional-cap" -> ((ObjectNode) review.path("caps")).put("retries", 0.2);
            case "string-cap" -> ((ObjectNode) review.path("caps")).put("requests", "1");
            case "missing-cap" -> ((ObjectNode) review.path("caps")).remove("retries");
            default -> { }
        }
        byte[] artifact = signed(payload);
        if (kind.equals("signature")) {
            ObjectNode envelope = (ObjectNode) GeminiOperatorApproval.JSON.readTree(artifact);
            envelope.put("signature", Base64.getEncoder().encodeToString(new byte[64])); artifact = envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        if (kind.equals("key")) verifier = new GeminiOperatorApproval(Map.of("test-key", new GeminiOperatorApproval.Root("TEST_ISSUER", KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic())), Set.of(), CLOCK);
        final byte[] candidate = artifact;
        assertThatThrownBy(() -> execute(candidate, limits(1), new GeminiBudget(), directory)).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(transports).hasValue(0); assertThat(dispatches).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings = {"duplicate", "algorithm", "oversize", "null", "tampered", "malformed"})
    void malformedEnvelopesDeny(String kind) throws Exception {
        byte[] artifact = signed(payload);
        String json = new String(artifact, java.nio.charset.StandardCharsets.UTF_8);
        artifact = switch (kind) {
            case "duplicate" -> json.replace("\"algorithm\":", "\"algorithm\":\"Ed25519\",\"algorithm\":").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case "algorithm" -> json.replace("Ed25519", "HMAC").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case "oversize" -> new byte[65537];
            case "null" -> null;
            case "tampered" -> json.replace("TEST_PROJECT", "OTHER_PROJECT").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            default -> new byte[]{123};
        };
        final byte[] bad = artifact;
        assertThatThrownBy(() -> execute(bad, limits(1), new GeminiBudget(), directory)).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(transports).hasValue(0);
    }
    @Test void budgetAndDirectoryRejectionsPrecedeKey() throws Exception {
        byte[] artifact = signed(payload); var budget = new GeminiBudget();
        for (int i=0;i<9;i++) { budget.reserve(limits(1)); budget.release(); }
        assertThat(execute(artifact, limits(1), budget, directory).category()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(keys).hasValue(0); assertThat(transports).hasValue(0);
        Path other = Files.createDirectory(directory.resolve("other"));
        assertThatThrownBy(() -> execute(artifact, limits(1), new GeminiBudget(), other)).isInstanceOf(ProviderException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"intent", "feature", "kill", "billing"})
    void explicitGatesNeverLookupKey(String kind) throws Exception {
        byte[] artifact = signed(payload);
        var limits = kind.equals("billing") ? GeminiProviderTest.limits("quota-billing-verification=NOT_VERIFIED") : limits(1);
        assertThatThrownBy(() -> GeminiAuthorizedExtraction.execute(!kind.equals("intent"), !kind.equals("feature"), kind.equals("kill"), artifact,
                verifier, directory, "VEX1-01", limits, new GeminiBudget(), CLOCK, control(), () -> { keys.incrementAndGet(); return "TEST"; },
                () -> { transports.incrementAndGet(); throw new AssertionError(); })).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(transports).hasValue(0);
    }
}
