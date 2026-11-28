package com.example.aiagent.model;

import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Supplier;
import java.util.*;
import java.util.function.BooleanSupplier;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Explicit internal initial-smoke entry. No controller, bean, CLI or ordinary ChatRequest entry. */
final class GeminiAuthorizedExtraction {
    private GeminiAuthorizedExtraction() { }
    static List<GeminiExtractionSmoke.Outcome> sequence(boolean intent, boolean enabled, BooleanSupplier kill,
            byte[] artifact, GeminiOperatorApproval verifier, Path directory, GeminiProperties limits,
            Clock clock, BooleanSupplier cancelled, BooleanSupplier revoked, Supplier<String> credentials) {
        return sequence(intent, enabled, kill, artifact, verifier, directory, limits, GeminiBudget.PROCESS, clock,
                cancelled, revoked, credentials, GeminiHttpTransport::new);
    }
    static List<GeminiExtractionSmoke.Outcome> sequence(boolean intent, boolean enabled, BooleanSupplier kill,
            byte[] artifact, GeminiOperatorApproval verifier, Path directory, GeminiProperties limits, GeminiBudget process,
            Clock clock, BooleanSupplier cancelled, BooleanSupplier revoked, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport) {
        gates(intent, enabled, kill.getAsBoolean(), verifier, limits);
        var verified = verifier.sequence(artifact); // Exactly once, ordered list and continuation policy are signed.
        var review = verified.approval().review(); var caps = review.caps();
        GeminiSharedAllocation.directory(review.journalDirectory(), directory);
        var reservation = new GeminiSharedAllocation.Reservation(review.sessionId(), List.of(review.sessionId()),
                verified.fixtureOrder().stream().map(id -> hash(review.sessionId() + ":" + id)).toList(), caps.requests(),
                caps.inputTotal(), caps.outputTotal(), caps.total(), caps.exposureUsd());
        var results = new ArrayList<GeminiExtractionSmoke.Outcome>(); boolean reconciled = true;
        try (var allocation = new GeminiSharedAllocation(verified.allocation(), reservation, review.journalDirectory(), directory, clock);
             var session = GeminiExtractionSession.openReviewed(review, directory, clock)) {
            for (String fixture : verified.fixtureOrder()) {
                if (kill.getAsBoolean() || cancelled.getAsBoolean() || revoked.getAsBoolean()) { session.seal(); break; }
                final GeminiLlmProviderAdapter.Exchange[] exchange = new GeminiLlmProviderAdapter.Exchange[1];
                var control = new LlmProviderAdapter.CallControl(caps.callTimeout());
                var result = GeminiExtractionSmoke.run(session, session.issue(fixture), 0, control, () -> {
                    if (kill.getAsBoolean() || cancelled.getAsBoolean() || revoked.getAsBoolean()) { session.revoke(); throw denied(); }
                    String key = credentials.get(); exchange[0] = transport.get(); return key;
                }, (request, cancellation, timeout) -> {
                    if (kill.getAsBoolean() || cancelled.getAsBoolean() || revoked.getAsBoolean()) { session.revoke(); throw denied(); }
                    var response = exchange[0].execute(request, cancellation, timeout);
                    if (kill.getAsBoolean() || cancelled.getAsBoolean() || revoked.getAsBoolean()) { session.revoke(); throw denied(); }
                    return response;
                }, limits, process, clock);
                results.add(result);
                if (!result.category().equals("SUCCESS")) { reconciled = false; break; }
            }
            session.seal(); allocation.finish(reconciled);
        }
        return List.copyOf(results);
    }
    private static void gates(boolean executionIntent, boolean featureEnabled, boolean killSwitch,
                              GeminiOperatorApproval verifier, GeminiProperties limits) {
        if (!executionIntent || !featureEnabled || killSwitch || verifier == null || limits == null
                || !limits.liveEnabled() || !limits.syntheticOnly()
                || limits.modelVerification() != GeminiProperties.Verification.VERIFIED
                || limits.quotaBillingVerification() != GeminiProperties.Verification.VERIFIED
                || limits.accountRegionFreeTierVerification() != GeminiProperties.Verification.VERIFIED
                || limits.dataTermsVerification() != GeminiProperties.Verification.VERIFIED) throw denied();
    }
    static GeminiExtractionSmoke.Outcome execute(boolean executionIntent, boolean featureEnabled, boolean killSwitch,
            byte[] artifact, GeminiOperatorApproval verifier, Path directory, String fixture,
            GeminiProperties limits, Clock clock, LlmProviderAdapter.CallControl control,
            Supplier<String> credentials) {
        // V1 has no signed shared allocation. Retained as a deny-only compatibility entry;
        // future external execution must use the allocation-bound v2 sequence (including one fixture).
        throw denied();
    }
    static GeminiExtractionSmoke.Outcome execute(boolean executionIntent, boolean featureEnabled, boolean killSwitch,
            byte[] artifact, GeminiOperatorApproval verifier, Path directory, String fixture,
            GeminiProperties limits, GeminiBudget process, Clock clock, LlmProviderAdapter.CallControl control,
            Supplier<String> credentials, Supplier<GeminiLlmProviderAdapter.Exchange> transport) {
        gates(executionIntent, featureEnabled, killSwitch, verifier, limits);
        try (var session = GeminiExtractionSession.open(artifact, verifier, directory, clock)) {
            // The existing run path reserves durable session + shared process BEFORE invoking this supplier.
            final GeminiLlmProviderAdapter.Exchange[] exchange = new GeminiLlmProviderAdapter.Exchange[1];
            return GeminiExtractionSmoke.run(session, session.issue(fixture), 0, control, () -> {
                String key = credentials.get(); exchange[0] = transport.get(); return key;
            }, (request, cancellation, timeout) -> exchange[0].execute(request, cancellation, timeout), limits, process, clock);
        }
    }
}
