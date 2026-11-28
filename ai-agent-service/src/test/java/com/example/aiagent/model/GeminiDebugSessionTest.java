package com.example.aiagent.model;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class GeminiDebugSessionTest {
    @TempDir Path temp;
    private GeminiSmokeDiagnostics.Result result(String category) {
        var d = new GeminiSmokeDiagnostics(); d.attempts = 1;
        var budget = new GeminiBudget(); budget.reserve(GeminiFirstCallTest.limits()); budget.release();
        return d.result(category, "NOT_RUN", System.nanoTime(), budget);
    }
    @Test void fiveReservationsPersistAcrossReopensAndSixthIsDenied() {
        var directory = temp.resolve("session"); GeminiDebugSession.initialize(directory);
        for (int i = 1; i <= 5; i++) try (var session = new GeminiDebugSession(directory)) {
            session.reserve(); session.complete(result("MALFORMED_RESPONSE"));
            assertThat(session.attempts()).isEqualTo(i);
            assertThat(session.exposure()).isEqualByComparingTo(new java.math.BigDecimal("0.008576").multiply(java.math.BigDecimal.valueOf(i)));
        } catch (Exception failure) { throw new AssertionError(failure); }
        try (var session = new GeminiDebugSession(directory)) {
            assertThatThrownBy(session::reserve).isInstanceOf(ProviderException.class);
            assertThat(session.attempts()).isEqualTo(5); assertThat(session.exposure()).isEqualByComparingTo("0.042880");
        } catch (Exception failure) { throw new AssertionError(failure); }
        assertThatThrownBy(() -> GeminiDebugSession.initialize(directory)).isInstanceOf(ProviderException.class);
    }
    @Test void crashPendingAndConcurrentOwnersFailClosed() throws Exception {
        var directory = temp.resolve("session"); GeminiDebugSession.initialize(directory);
        try (var session = new GeminiDebugSession(directory)) {
            assertThatThrownBy(() -> new GeminiDebugSession(directory)).isInstanceOf(ProviderException.class);
            session.reserve(); // Simulated crash: no completion, no refund on reopen.
        }
        assertThatThrownBy(() -> new GeminiDebugSession(directory)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> GeminiDebugSession.initialize(directory)).isInstanceOf(ProviderException.class);
    }
    @Test void quotaStopsSessionAndMissingOrTruncatedLedgerCannotBeReinitialized() throws Exception {
        var directory = temp.resolve("quota"); GeminiDebugSession.initialize(directory);
        try (var session = new GeminiDebugSession(directory)) { session.reserve(); session.complete(result("QUOTA_EXCEEDED")); }
        try (var session = new GeminiDebugSession(directory)) { assertThatThrownBy(session::reserve).isInstanceOf(ProviderException.class); }
        var missing = temp.resolve("missing"); Files.createDirectory(missing);
        assertThatThrownBy(() -> new GeminiDebugSession(missing)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> GeminiDebugSession.initialize(missing)).isInstanceOf(ProviderException.class);
        var truncated = temp.resolve("truncated"); GeminiDebugSession.initialize(truncated);
        Files.writeString(truncated.resolve("session.journal"), "partial");
        assertThatThrownBy(() -> new GeminiDebugSession(truncated)).isInstanceOf(ProviderException.class);
    }
}
