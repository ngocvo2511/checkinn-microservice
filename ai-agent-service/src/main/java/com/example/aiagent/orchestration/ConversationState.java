package com.example.aiagent.orchestration;

import java.time.Instant;
import java.util.*;

/** Safe storage boundary: recent application messages, explicit criteria, projected evidence only. */
public record ConversationState(String conversationId, long revision, long totalMessages, List<Message> recentMessages,
                                SearchCriteriaState criteria, List<EvidenceLedger.Record> evidence,
                                Instant lastActivity, Instant expiresAt) {
    public record Message(boolean user, String text) {
        @Override public String toString() { return "Message[user=" + user + "]"; }
    }
    public ConversationState { recentMessages = List.copyOf(recentMessages); evidence = List.copyOf(evidence); }
    @Override public String toString() { return "ConversationState[revision=" + revision + ", totalMessages=" + totalMessages + "]"; }
}
