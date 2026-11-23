package com.example.aiagent.orchestration;

import java.util.Optional;

/** Future storage implementations must preserve exclusive turn admission and optimistic commit semantics. */
public interface ConversationStore {
    Optional<ConversationState> findById(String id);
    Lease begin(String id, long expectedRevision);
    interface Lease extends AutoCloseable {
        ConversationState state();
        void commit(ConversationState state);
        @Override void close();
    }
}
