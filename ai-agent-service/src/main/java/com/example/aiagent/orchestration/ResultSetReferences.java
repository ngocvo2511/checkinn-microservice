package com.example.aiagent.orchestration;

import com.example.aiagent.tools.ToolId;
import java.time.*;
import java.util.*;

/** Identity only, never inventory evidence. Minted from the cards actually rendered by this application. */
public final class ResultSetReferences {
    public record Identity(String hotelId, String name, int ordinal) {}
    private final String session;
    private final long criteriaRevision;
    private final Instant expiresAt;
    private final List<Identity> entries;
    private ResultSetReferences(String session, long revision, Instant expiresAt, List<Identity> entries) {
        this.session = session; this.criteriaRevision = revision; this.expiresAt = expiresAt; this.entries = List.copyOf(entries);
    }
    static ResultSetReferences fromAnswer(String session, SearchCriteriaState criteria, EvidenceLedger ledger,
                                          GroundingGate.Answer answer, Clock clock) {
        for (var fact : answer.facts()) {
            var record = ledger.find(fact.evidenceId()).orElse(null);
            if (record == null || record.toolId() != ToolId.A01) continue;
            var entries = new ArrayList<Identity>();
            for (var item : fact.values().path("items")) {
                String id = item.path("hotelId").asText(), name = item.path("name").asText();
                if (!record.hotelIds().contains(id) || name.length() > 200 || entries.size() >= 5) return null;
                entries.add(new Identity(id, name, entries.size()));
            }
            return entries.isEmpty() ? null : new ResultSetReferences(session, criteria.revision(), clock.instant().plusSeconds(300), entries);
        }
        return null;
    }
    List<Identity> resolve(ConversationState state, Clock clock, List<Integer> ordinals, List<String> names) {
        if (!session.equals(state.conversationId()) || criteriaRevision != state.criteria().revision()
                || clock.instant().isBefore(expiresAt.minusSeconds(300))
                || !clock.instant().isBefore(expiresAt) || !clock.instant().isBefore(state.expiresAt())) return List.of();
        var result = new ArrayList<Identity>();
        if (!names.isEmpty()) {
            for (String name : names) {
                var matches = entries.stream().filter(e -> com.example.aiagent.nlu.VietnameseParser.normalize(e.name()).equals(name)).toList();
                if (matches.size() != 1) return List.of();
                result.add(matches.getFirst());
            }
        } else {
            if (ordinals.isEmpty()) { if (entries.size() != 2) return List.of(); result.addAll(entries); }
            else for (int ordinal : ordinals) {
                if (ordinal < 0 || ordinal >= entries.size()) return List.of();
                result.add(entries.get(ordinal));
            }
        }
        return result.size() == 2 && !result.getFirst().hotelId().equals(result.getLast().hotelId()) ? List.copyOf(result) : List.of();
    }
    @Override public String toString() { return "ResultSetReferences[revision=" + criteriaRevision + ", count=" + entries.size() + "]"; }
}
