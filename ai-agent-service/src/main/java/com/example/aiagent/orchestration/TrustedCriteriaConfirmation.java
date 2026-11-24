package com.example.aiagent.orchestration;

import java.time.Instant;
import java.util.*;

/** INTERNAL_ONLY capability event. No Jackson constructor, public factory, endpoint or client trusted flag.
 * A future authenticated server adapter must map its verified action to this package-private seam.
 */
public final class TrustedCriteriaConfirmation {
    enum Source { INTERNAL_APPLICATION_ADAPTER }
    private final ConversationState sessionBinding;
    private final long revision, criteriaRevision;
    private final String eventId, actionId;
    private final Instant occurredAt;
    private final Map<String, SearchCriteriaState.Slot> fields;
    private final Source source;
    private TrustedCriteriaConfirmation(ConversationState binding, long revision, long criteriaRevision, String eventId,
                                        String actionId, Instant occurredAt, Map<String, SearchCriteriaState.Slot> fields) {
        this.sessionBinding = Objects.requireNonNull(binding); this.revision = revision; this.criteriaRevision = criteriaRevision;
        this.eventId = eventId; this.actionId = actionId; this.occurredAt = occurredAt;
        this.fields = Map.copyOf(fields); this.source = Source.INTERNAL_APPLICATION_ADAPTER;
    }
    // Package-private by design: a user/model/JSON payload cannot mint this authority.
    static TrustedCriteriaConfirmation explicitInternalAction(ConversationState binding, long revision, long criteriaRevision,
            String eventId, String actionId, Instant occurredAt, Map<String, SearchCriteriaState.Slot> fields) {
        return new TrustedCriteriaConfirmation(binding, revision, criteriaRevision, eventId, actionId, occurredAt, fields);
    }
    String conversationId() { return sessionBinding.conversationId(); }
    long revision() { return revision; }
    String eventId() { return eventId; }
    Instant occurredAt() { return occurredAt; }
    Map<String, SearchCriteriaState.Slot> validatedPatch(ConversationState state, Instant now) {
        // Object identity is a local session capability, NOT authenticated user ownership.
        // Binding is intentionally single-revision; no superseding busy turn or session recreation.
        if (state != sessionBinding || source != Source.INTERNAL_APPLICATION_ADAPTER || revision != state.revision()
                || criteriaRevision != state.criteria().revision() || !now.isBefore(state.expiresAt())
                || eventId == null || !eventId.matches("[A-Za-z0-9_-]{1,100}")
                || actionId == null || !actionId.matches("[A-Za-z0-9_-]{1,100}") || occurredAt == null
                || occurredAt.isAfter(now) || occurredAt.isBefore(state.lastActivity()) || !now.isBefore(occurredAt.plusSeconds(30))
                || state.lastConfirmationAt() != null && !occurredAt.isAfter(state.lastConfirmationAt())
                || state.confirmationEvents().contains(eventId) || state.confirmationEvents().size() >= 32
                || !fields.keySet().containsAll(Set.of("adults", "children", "rooms"))
                || !Set.of("adults", "children", "rooms", "checkIn", "checkOut").containsAll(fields.keySet())
                || fields.containsKey("checkIn") != fields.containsKey("checkOut"))
            throw new IllegalArgumentException("Confirmation not admitted");
        var patch = new HashMap<String, SearchCriteriaState.Slot>();
        fields.forEach((key, slot) -> {
            if (slot.origin() == SearchCriteriaState.Origin.MODEL_PROPOSED || slot.value().isNull())
                throw new IllegalArgumentException("Confirmation not admitted");
            patch.put(key, new SearchCriteriaState.Slot(slot.value(), SearchCriteriaState.Origin.USER_CONFIRMED));
        });
        return Map.copyOf(patch);
    }
    @Override public String toString() { return "TrustedCriteriaConfirmation[INTERNAL_ONLY]"; }
}
