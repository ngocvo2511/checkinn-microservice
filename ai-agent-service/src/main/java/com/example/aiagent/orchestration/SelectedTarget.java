package com.example.aiagent.orchestration;

import com.example.aiagent.tools.ToolId;
import java.time.*;
import java.util.*;

/** Server-owned identity binding. Construction is restricted to verified session evidence. */
public final class SelectedTarget {
    public enum Type { HOTEL, ROOM }
    private final String conversationId, evidenceId, hotelId, roomTypeId;
    private final long criteriaRevision;
    private final Instant expiresAt;
    private SelectedTarget(String conversationId, EvidenceLedger.Record record, String hotelId, String roomTypeId) {
        this.conversationId = conversationId; this.evidenceId = record.id(); this.hotelId = hotelId;
        this.roomTypeId = roomTypeId; this.criteriaRevision = record.criteriaRevision();
        this.expiresAt = record.retrievedAt().plusSeconds(30);
    }
    /** Internal selection event: an evidence reference + zero-based public projection ordinal, never a target ID. */
    static SelectedTarget bind(ConversationState state, String evidenceId, Type type, int ordinal, Clock clock) {
        if (type == null || ordinal < 0 || ordinal >= 5) throw new IllegalArgumentException("Invalid selection");
        var record = new EvidenceLedger(state.evidence(), state.criteria().revision(), "selection", clock)
                .find(evidenceId).orElseThrow(() -> new IllegalArgumentException("Selection evidence unavailable"));
        String hotel, room = null;
        if (record.toolId() == ToolId.A02 && type == Type.HOTEL && ordinal == 0) {
            hotel = record.facts().path("hotelId").asText();
        } else if (record.toolId() == ToolId.A04 && ordinal == 0) {
            hotel = record.facts().path("hotelId").asText();
            if (type == Type.ROOM) room = record.facts().path("roomTypeId").asText();
        } else if (record.toolId() == ToolId.A03 && ordinal < record.facts().path("rooms").size()) {
            var item = record.facts().path("rooms").get(ordinal);
            hotel = item.path("hotelId").asText();
            if (type == Type.ROOM) room = item.path("roomTypeId").asText();
        } else throw new IllegalArgumentException("Unsupported selection evidence");
        if (!record.hotelIds().contains(hotel) || type == Type.ROOM && !Objects.equals(hotel, record.roomHotels().get(room)))
            throw new IllegalArgumentException("Invalid selection relationship");
        return new SelectedTarget(state.conversationId(), record, hotel, room);
    }
    public Optional<PolicyExecutor.Scope> scope(ConversationState state, PolicyExecutor.Intent intent, Clock clock) {
        if (!conversationId.equals(state.conversationId()) || criteriaRevision != state.criteria().revision()
                || !clock.instant().isBefore(expiresAt)) return Optional.empty();
        var record = new EvidenceLedger(state.evidence(), criteriaRevision, "selection", clock).find(evidenceId);
        if (record.isEmpty() || !record.get().hotelIds().contains(hotelId)
                || roomTypeId != null && !Objects.equals(hotelId, record.get().roomHotels().get(roomTypeId))) return Optional.empty();
        if ((intent == PolicyExecutor.Intent.AVAILABILITY || intent == PolicyExecutor.Intent.ROOM_DETAIL) && roomTypeId == null)
            return Optional.empty();
        return Optional.of(new PolicyExecutor.Scope(intent, hotelId, roomTypeId));
    }
    @Override public String toString() { return "SelectedTarget[revision=" + criteriaRevision + "]"; }
}
