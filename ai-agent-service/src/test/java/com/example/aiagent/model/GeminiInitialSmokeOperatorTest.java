package com.example.aiagent.model;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

class GeminiInitialSmokeOperatorTest {
    @TempDir Path directory;
    @BeforeAll static void guard() { denyExternalAndKeys(); }
    @AfterAll static void unguard() { restore(); }
    @ParameterizedTest @ValueSource(strings = {"success", "intent", "unsigned", "signature", "evidence", "directory", "allocation", "kill", "revoke"})
    void operatorPathReusesAuthorizationBeforeAnyCredentialOrTransport(String kind) throws Exception {
        var support = new GeminiEvaluationTestSupport(directory);
        var payload = support.sequence();
        if (kind.equals("unsigned")) payload = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiEvaluationPreflight.template("initial-smoke").path("payload");
        if (kind.equals("evidence")) ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("approval").path("evidence")).remove("pricingReference");
        if (kind.equals("directory")) ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("approval").path("review")).put("journalDirectory", directory.resolve("absent").toString());
        if (kind.equals("allocation")) ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("allocation")).put("requests", 0);
        byte[] artifact = support.sign(payload);
        if (kind.equals("signature")) {
            var envelope = GeminiOperatorApproval.JSON.readTree(artifact);
            ((com.fasterxml.jackson.databind.node.ObjectNode) envelope).put("signature", Base64.getEncoder().encodeToString(new byte[64]));
            artifact = GeminiOperatorApproval.JSON.writeValueAsBytes(envelope);
        }
        Path key = directory.resolve("public.der"), revoked = directory.resolve("revoked.json"), approval = directory.resolve("approval.json"), control = directory.resolve("control");
        Files.write(key, support.signer.getPublic().getEncoded()); Files.writeString(revoked, kind.equals("revoke") ? "[\"TEST_KEY\"]" : "[]");
        Files.write(approval, artifact); Files.writeString(control, kind.equals("kill") ? "STOP" : "RUN");
        String[] args = {kind.equals("intent") ? "disabled" : "--execute-initial-smoke", "--enable-synthetic-live", key.toString(), "TEST_ISSUER", "TEST_KEY", revoked.toString(), approval.toString(), directory.toString(), control.toString()};
        var keys = new AtomicInteger(); var factories = new AtomicInteger(); var calls = new AtomicInteger();
        org.assertj.core.api.ThrowableAssert.ThrowingCallable invoke = () -> {
            var result = GeminiInitialSmokeOperator.run(args, CLOCK, () -> { keys.incrementAndGet(); return "TEST_SENTINEL"; },
                    () -> { factories.incrementAndGet(); return (request, cancellation, timeout) -> {
                        String id = "VEX1-%02d".formatted(calls.incrementAndGet());
                        assertThat(request.url()).isEqualTo(ENDPOINT); assertThat(request.body()).isEqualTo(body(id));
                        return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(response(expected(id))).build();
                    }; }, new GeminiBudget());
            assertThat(result).hasSize(3); assertThat(result).allMatch(r -> r.category().equals("SUCCESS"));
        };
        if (kind.equals("success")) { assertThatCode(invoke).doesNotThrowAnyException(); assertThat(calls).hasValue(3); }
        else { assertThatThrownBy(invoke).isInstanceOf(Exception.class); assertThat(keys).hasValue(0); assertThat(factories).hasValue(0); assertThat(calls).hasValue(0); }
    }
    @Test void defaultMainCannotLookupEnvironmentOrInitializeTransport() {
        assertThatThrownBy(() -> GeminiInitialSmokeOperator.main(new String[0])).hasMessage("INITIAL_SMOKE_STOPPED_NO_RETRY");
    }
}
