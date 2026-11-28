package com.example.aiagent.orchestration;

import java.time.Instant;
import java.util.*;

/** Safe storage boundary: recent application messages, explicit criteria, projected evidence only. */
public record ConversationState(String conversationId, long revision, long totalMessages, List<Message> recentMessages,
                                SearchCriteriaState criteria, List<EvidenceLedger.Record> evidence,
                                Instant lastActivity, Instant expiresAt, SelectedTarget selection,
                                ResultSetReferences resultSet, List<String> confirmationEvents, Instant lastConfirmationAt) {
    public ConversationState(String conversationId, long revision, long totalMessages, List<Message> recentMessages,
                             SearchCriteriaState criteria, List<EvidenceLedger.Record> evidence, Instant lastActivity, Instant expiresAt, SelectedTarget selection) {
        this(conversationId, revision, totalMessages, recentMessages, criteria, evidence, lastActivity, expiresAt, selection, null, List.of(), null);
    }
    public ConversationState(String conversationId, long revision, long totalMessages, List<Message> recentMessages,
                             SearchCriteriaState criteria, List<EvidenceLedger.Record> evidence, Instant lastActivity, Instant expiresAt) {
        this(conversationId, revision, totalMessages, recentMessages, criteria, evidence, lastActivity, expiresAt, null);
    }
    public record Message(boolean user, String text) {
        @Override public String toString() { return "Message[user=" + user + "]"; }
    }
    public ConversationState {
        recentMessages = List.copyOf(recentMessages); evidence = List.copyOf(evidence); confirmationEvents = List.copyOf(confirmationEvents);
        if (confirmationEvents.size() > 32) throw new IllegalArgumentException("Too many confirmation receipts");
    }
    @Override public String toString() { return "ConversationState[revision=" + revision + ", totalMessages=" + totalMessages + "]"; }
}
