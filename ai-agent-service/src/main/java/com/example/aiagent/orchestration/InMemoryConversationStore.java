package com.example.aiagent.orchestration;

import java.time.*;
import java.util.*;

/** Single-process local fake-testing storage. Restart loses all sessions. Busy sessions are never evicted. */
public final class InMemoryConversationStore implements ConversationStore {
    private final Map<String, ConversationState> states = new HashMap<>();
    private final Set<String> busy = new HashSet<>();
    private final Clock clock;
    private final Duration ttl;
    private final int capacity;
    public InMemoryConversationStore(Clock clock) { this(clock, Duration.ofMinutes(30), 1000); }
    public InMemoryConversationStore(Clock clock, Duration ttl, int capacity) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(30)) > 0 || capacity < 1 || capacity > 1000)
            throw new IllegalArgumentException("Unsafe state bounds");
        this.clock = clock; this.ttl = ttl; this.capacity = capacity;
    }
    private void expire() { states.entrySet().removeIf(e -> !busy.contains(e.getKey()) && !clock.instant().isBefore(e.getValue().expiresAt())); }
    @Override public synchronized Optional<ConversationState> findById(String id) { expire(); return Optional.ofNullable(states.get(id)); }
    @Override public synchronized Lease begin(String id, long expectedRevision) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid local conversation ID");
        expire();
        if (busy.contains(id)) throw new TurnBudget.Stop("SESSION_BUSY");
        var existing = states.get(id);
        if ((existing == null ? 0 : existing.revision()) != expectedRevision) throw new TurnBudget.Stop("REVISION_CONFLICT");
        if (existing == null && states.size() + busy.stream().filter(k -> !states.containsKey(k)).count() >= capacity)
            throw new TurnBudget.Stop("SESSION_CAPACITY");
        var state = existing == null ? new ConversationState(id, 0, 0, List.of(), SearchCriteriaState.empty(), List.of(), clock.instant(), clock.instant().plus(ttl)) : existing;
        busy.add(id);
        return new Lease() {
            private boolean closed, committed;
            @Override public ConversationState state() { return state; }
            @Override public void commit(ConversationState next) {
                synchronized (InMemoryConversationStore.this) {
                    if (closed || committed || !next.conversationId().equals(id) || next.revision() != expectedRevision + 1
                            || next.recentMessages().size() > 8 || next.evidence().size() > 24)
                        throw new TurnBudget.Stop("REVISION_CONFLICT");
                    states.put(id, new ConversationState(id, next.revision(), next.totalMessages(), next.recentMessages(), next.criteria(),
                            next.evidence(), clock.instant(), clock.instant().plus(ttl)));
                    committed = true;
                }
            }
            @Override public void close() { synchronized (InMemoryConversationStore.this) { if (!closed) { busy.remove(id); closed = true; } } }
        };
    }
}
