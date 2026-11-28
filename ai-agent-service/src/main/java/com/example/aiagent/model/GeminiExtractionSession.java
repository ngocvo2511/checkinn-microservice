package com.example.aiagent.model;

import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Internal contract only: no bean, CLI, JSON approval parser, default issuer, key source or live transport. */
final class GeminiExtractionSession implements AutoCloseable {
    enum State { DRAFT, REVIEWED, AWAITING_OPERATOR_APPROVAL, APPROVED_FOR_SEPARATE_LIVE_BATCH, EXPIRED, REVOKED, SEALED }
    /** Trusted infrastructure seam. No implementation wired into production. Tests inject a synthetic verifier. */
    interface ApprovalVerifier { Reviewed verify(byte[] opaqueArtifact); }
    static ApprovalVerifier unavailableIssuer() { return artifact -> { throw denied(); }; }
    record Caps(int requests, int inputPerCall, int outputPerCall, int inputTotal, int outputTotal, int total,
                int concurrency, int retries, Duration callTimeout, int processRequests,
                BigDecimal exposureUsd, BigDecimal inputRateUpperBound, BigDecimal outputRateUpperBound) { }
    record Reviewed(String sessionId, State state, String purpose, String manifest, String model, String protocol,
                    String classification, String operatorIdentity, String approvalReference, Instant notBefore, Instant expires,
                    Map<String, Binding> fixtures, Caps caps, Path journalDirectory) {
        Reviewed { fixtures = Map.copyOf(fixtures); }
        @Override public String toString() { return "ExtractionReview[payloads=omitted]"; }
    }
    record Metadata(String fixtureId, String protocolVersion, String requestDigest, String modelIdentity,
                    String startTimestamp, String endTimestamp, long latencyMillis, String httpStatusCategory,
                    Integer inputTokens, Integer outputTokens, Integer totalTokens,
                    String schemaValidationResult, String semanticResult, String stopReason) { }
    /** Cannot be constructed/copied from JSON; owner, session, thread, request digest and identity are mandatory. */
    static final class Capability {
        private final GeminiExtractionSession owner;
        private final Thread thread;
        private final String fixture, body;
        private boolean consumed;
        private Capability(GeminiExtractionSession owner, String fixture, String body) {
            this.owner = owner; this.thread = Thread.currentThread(); this.fixture = fixture; this.body = body;
        }
        @Override public String toString() { return "ExtractionCapability[payloads=omitted]"; }
    }
    private final Reviewed review;
    private final Clock clock;
    private final GeminiExtractionJournal journal;
    private final long deadline;
    private final Set<String> issued = new HashSet<>();
    private final Set<String> consumed = new HashSet<>();
    private final Set<Capability> capabilities = Collections.newSetFromMap(new IdentityHashMap<>());
    private State state;
    private int reservedInput, reservedOutput, requests;
    private boolean active, closed;
    private long callDeadline;
    private Metadata pending;
    private LlmProviderAdapter.CallControl activeControl;
    private GeminiExtractionSession(Reviewed review, Path directory, Clock clock) {
        this.review = review; this.clock = clock; this.state = review.state();
        deadline = System.nanoTime() + Duration.between(clock.instant(), review.expires()).toNanos();
        journal = new GeminiExtractionJournal(directory, review.sessionId());
    }
    static GeminiExtractionSession open(byte[] artifact, ApprovalVerifier verifier, Path directory, Clock clock) {
        try {
            if (artifact == null || artifact.length == 0 || artifact.length > 65536 || verifier == null) throw denied();
            Reviewed reviewed = verifier.verify(artifact.clone());
            validate(reviewed, clock);
            if (reviewed.journalDirectory() == null || !reviewed.journalDirectory().isAbsolute()
                    || !reviewed.journalDirectory().normalize().equals(directory.toAbsolutePath().normalize())) throw denied();
            return new GeminiExtractionSession(reviewed, directory, clock);
        } catch (Exception failure) { throw denied(); }
    }
    private static void validate(Reviewed r, Clock clock) {
        if (r == null || r.state() != State.APPROVED_FOR_SEPARATE_LIVE_BATCH || !PURPOSE.equals(r.purpose())
                || !MANIFEST.equals(r.manifest()) || !MODEL.equals(r.model()) || !VERSION.equals(r.protocol())
                || !"SYNTHETIC_ONLY".equals(r.classification()) || r.sessionId() == null || !r.sessionId().matches("[a-zA-Z0-9-]{1,100}")
                || r.operatorIdentity() == null || !r.operatorIdentity().matches("[a-zA-Z0-9_-]{1,100}")
                || r.approvalReference() == null || !r.approvalReference().matches("[a-zA-Z0-9_-]{1,100}")
                || r.notBefore() == null || r.expires() == null || clock.instant().isBefore(r.notBefore())
                || !clock.instant().isBefore(r.expires()) || !r.notBefore().isBefore(r.expires())
                || Duration.between(r.notBefore(), r.expires()).compareTo(Duration.ofMinutes(5)) > 0) throw denied();
        Caps c = r.caps();
        if (c == null || c.requests() < 1 || c.requests() > 3 || c.requests() > r.fixtures().size() || r.fixtures().size() > 3
                || c.inputPerCall() < 1024 || c.inputPerCall() > INPUT_CAP || c.outputPerCall() != OUTPUT_CAP
                || c.inputTotal() < 1 || c.inputTotal() > c.requests() * c.inputPerCall()
                || c.outputTotal() < 1 || c.outputTotal() > c.requests() * c.outputPerCall()
                || c.total() < 1 || c.total() > c.inputTotal() + c.outputTotal() || c.concurrency() != 1 || c.retries() != 0
                || c.callTimeout() == null || c.callTimeout().isNegative() || c.callTimeout().isZero()
                || c.callTimeout().compareTo(Duration.ofSeconds(10)) > 0 || c.processRequests() < c.requests() || c.processRequests() > 9
                || !positive(c.exposureUsd(), BigDecimal.ONE) || !positive(c.inputRateUpperBound(), new BigDecimal("1000"))
                || !positive(c.outputRateUpperBound(), new BigDecimal("1000"))
                || c.inputRateUpperBound().multiply(BigDecimal.valueOf((long) c.requests() * c.inputPerCall()))
                .add(c.outputRateUpperBound().multiply(BigDecimal.valueOf((long) c.requests() * c.outputPerCall())))
                .compareTo(c.exposureUsd().multiply(BigDecimal.valueOf(1_000_000))) > 0) throw denied();
        for (var item : r.fixtures().entrySet()) {
            if (!binding(item.getKey()).equals(item.getValue()) || !eligible(item.getKey(), clock)) throw denied();
            long estimate = 2L * bytes(body(item.getKey())) + 1024;
            if (estimate > c.inputPerCall()) throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED);
        }
    }
    private static boolean positive(BigDecimal value, BigDecimal max) { return value != null && value.signum() > 0 && value.compareTo(max) <= 0; }
    synchronized Capability issue(String id) {
        check(); if (!review.fixtures().containsKey(id) || issued.contains(id)) throw denied();
        String body = body(id); if (!hash(body).equals(review.fixtures().get(id).requestDigest())) throw denied();
        var result = new Capability(this, id, body); issued.add(id); capabilities.add(result); return result;
    }
    /** Reserve full ceilings, not actual usage; errors/cancellation never refund a fixture or token allocation. */
    synchronized String reserve(Capability permit, int attempt, LlmProviderAdapter.CallControl control) {
        check(); control.check();
        if (permit == null || permit.owner != this || permit.thread != Thread.currentThread() || !capabilities.contains(permit)
                || permit.consumed || attempt != 0 || active || consumed.contains(permit.fixture)) throw denied();
        Caps caps = review.caps();
        if (requests >= caps.requests() || reservedInput + caps.inputPerCall() > caps.inputTotal()
                || reservedOutput + caps.outputPerCall() > caps.outputTotal()
                || (long) reservedInput + reservedOutput + caps.inputPerCall() + caps.outputPerCall() > caps.total())
            throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED);
        permit.consumed = true; consumed.add(permit.fixture); requests++;
        reservedInput += caps.inputPerCall(); reservedOutput += caps.outputPerCall(); active = true; activeControl = control;
        callDeadline = System.nanoTime() + caps.callTimeout().toNanos();
        pending = new Metadata(permit.fixture, VERSION, hash(permit.body), MODEL, clock.instant().toString(), null, 0,
                "NOT_RUN", null, null, null, "NOT_RUN", "NOT_RUN", "RESERVED");
        try { journal.append(pending); } catch (RuntimeException failure) { state = State.SEALED; throw failure; }
        return permit.body;
    }
    synchronized String fixtureId(Capability permit) { if (permit == null || permit.owner != this) throw denied(); return permit.fixture; }
    synchronized Duration remaining() {
        check(); return Duration.ofNanos(Math.min(review.caps().callTimeout().toNanos(),
                Math.min(active ? callDeadline - System.nanoTime() : deadline - System.nanoTime(),
                Math.min(deadline - System.nanoTime(), Duration.between(clock.instant(), review.expires()).toNanos()))));
    }
    synchronized void check() {
        if (closed || state != State.APPROVED_FOR_SEPARATE_LIVE_BATCH) throw denied();
        if (clock.instant().isBefore(review.notBefore())) throw denied();
        if (!clock.instant().isBefore(review.expires()) || System.nanoTime() >= deadline) { state = State.EXPIRED; throw denied(); }
        if (active && System.nanoTime() >= callDeadline) {
            if (activeControl != null) activeControl.cancel();
            throw new ProviderException(ProviderException.Category.PROVIDER_TIMEOUT);
        }
    }
    synchronized void complete(String http, Integer input, Integer output, Integer total, String schema, String semantics,
                               String reason, long latencyMillis) {
        if (!active || pending == null) throw denied();
        // Terminal changes during an in-flight call cannot be turned into successful completion.
        if (state != State.APPROVED_FOR_SEPARATE_LIVE_BATCH) reason = state.name();
        String journalReason = reason.equals("SUCCESS") && requests >= review.caps().requests() ? "SUCCESS_SEALED" : reason;
        var record = new Metadata(pending.fixtureId(), VERSION, pending.requestDigest(), MODEL, pending.startTimestamp(),
                clock.instant().toString(), latencyMillis, http, input, output, total, schema, semantics, journalReason);
        try { journal.append(record); }
        finally {
            active = false; pending = null; activeControl = null;
            if (!reason.equals("SUCCESS") || requests >= review.caps().requests()) state = State.SEALED;
        }
    }
    synchronized void revoke() { state = State.REVOKED; if (activeControl != null) activeControl.cancel(); }
    synchronized void seal() { state = State.SEALED; if (activeControl != null) activeControl.cancel(); }
    synchronized State state() { return state; }
    Caps caps() { return review.caps(); }
    synchronized int requests() { return requests; }
    @Override public synchronized void close() {
        if (closed) return; closed = true; state = State.SEALED; if (activeControl != null) activeControl.cancel(); journal.close();
    }
}
