package com.example.aiagent.tools;

import java.util.*;
import java.util.concurrent.atomic.*;

/** Server-owned context, never deserialized from model arguments. No conversation budget executor. */
public final class ToolExecutionContext {
    public record Attempt(int sequence, ToolId toolId, String route, String requestId) {}
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<Attempt> ledger = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Long revision;
    private final Integer confirmedRooms;
    private final Integer confirmedAdults;
    private final Integer confirmedChildren;
    private final Map<String, String> publicRoomHotels;
    private final Set<String> accommodationCodes;
    private final Set<String> amenityCodes;
    private Runnable attemptGuard = () -> { };
    private java.util.function.LongSupplier remainingNanos = () -> Long.MAX_VALUE;
    private String traceparent;
    public void traceparent(String value) {
        if (value != null && !value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]")) throw new IllegalArgumentException("Invalid server trace context");
        this.traceparent = value;
    }
    String traceparent() { return traceparent; }

    /** Application-owned turn guard, invoked at the actual HTTP admission point. */
    public void bindTurnLimits(Runnable attemptGuard, java.util.function.LongSupplier remainingNanos) {
        this.attemptGuard = Objects.requireNonNull(attemptGuard);
        this.remainingNanos = Objects.requireNonNull(remainingNanos);
    }
    long remainingNanos() { return remainingNanos.getAsLong(); }

    public ToolExecutionContext(Long revision, Integer confirmedRooms, Map<String, String> publicRoomHotels,
                                Set<String> accommodationCodes, Set<String> amenityCodes) {
        this(revision, confirmedRooms, null, null, publicRoomHotels, accommodationCodes, amenityCodes);
    }
    public ToolExecutionContext(Long revision, Integer confirmedRooms, Integer confirmedAdults, Integer confirmedChildren,
                                Map<String, String> publicRoomHotels, Set<String> accommodationCodes, Set<String> amenityCodes) {
        if (revision != null && revision < 0) throw new IllegalArgumentException("Negative revision");
        this.revision = revision; this.confirmedRooms = confirmedRooms;
        this.confirmedAdults = confirmedAdults; this.confirmedChildren = confirmedChildren;
        this.publicRoomHotels = Map.copyOf(publicRoomHotels);
        this.accommodationCodes = Set.copyOf(accommodationCodes); this.amenityCodes = Set.copyOf(amenityCodes);
    }
    public static ToolExecutionContext empty() { return new ToolExecutionContext(null, null, Map.of(), Set.of(), Set.of()); }
    public Long criteriaRevision() { return revision; }
    public Integer confirmedRooms() { return confirmedRooms; }
    public Integer confirmedAdults() { return confirmedAdults; }
    public Integer confirmedChildren() { return confirmedChildren; }
    public String publicHotelFor(String room) { return publicRoomHotels.get(room); }
    public Set<String> accommodationCodes() { return accommodationCodes; }
    public Set<String> amenityCodes() { return amenityCodes; }
    public void cancel() { cancelled.set(true); }
    public boolean cancelled() { return cancelled.get(); }
    public int attempts() { return attempts.get(); }
    public List<Attempt> attemptLedger() { synchronized (ledger) { return List.copyOf(ledger); } }
    synchronized void beforeAttempt(ToolId tool, String requestId) {
        checkCancelled();
        attemptGuard.run();
        ledger.add(new Attempt(attempts.incrementAndGet(), tool, tool.path(), requestId));
    }
    void checkCancelled() {
        if (cancelled() || Thread.currentThread().isInterrupted()) throw new ToolFailure(ToolError.Category.CANCELLED);
    }
}
