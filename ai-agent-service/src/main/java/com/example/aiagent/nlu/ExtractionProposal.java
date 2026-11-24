package com.example.aiagent.nlu;

import com.example.aiagent.orchestration.SearchCriteriaState;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.time.Clock;
import java.util.*;

/** Untrusted extraction data, never tool instructions or catalog identities. */
public record ExtractionProposal(Intent intent, Map<String, Change> fields, String clarification) {
    public enum Intent { SEARCH, BROWSE, REFINE, CLARIFY, UNSUPPORTED, UNVERIFIABLE }
    public enum Operation { REPLACE, CLEAR }
    public enum Provenance { EXPLICIT_USER, MODEL_PROPOSED }
    public record Change(Operation operation, JsonNode value, Provenance provenance) {
        public Change {
            Objects.requireNonNull(operation); Objects.requireNonNull(provenance);
            if (operation == Operation.CLEAR ? value != null : value == null || value.isNull())
                throw new IllegalArgumentException("Invalid change");
            if (value != null) value = value.deepCopy();
        }
        @Override public JsonNode value() { return value == null ? null : value.deepCopy(); }
        @Override public String toString() { return "Change[" + operation + "," + provenance + "]"; }
    }
    private static final Set<String> ALLOWED = Set.of("cityName", "hotelName", "checkIn", "checkOut", "adults", "children", "rooms", "minPrice", "maxPrice", "referencePriceExclusive", "starRatings");
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(200).maxNumberLength(24).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public ExtractionProposal {
        Objects.requireNonNull(intent); fields = Map.copyOf(fields);
        if (!ALLOWED.containsAll(fields.keySet()) || clarification != null && clarification.length() > 200)
            throw new IllegalArgumentException("Unsupported extraction");
    }
    @Override public String toString() { return "ExtractionProposal[intent=" + intent + ", fields=" + fields.keySet() + "]"; }
    /** Protocol seam for offline fake proposals only. It cannot mint explicit-user provenance. */
    public static ExtractionProposal parseModelJson(String raw, Clock clock) {
        try {
            if (raw == null || raw.length() > 4096) throw new IllegalArgumentException();
            var root = JSON.readTree(raw);
            if (!root.isObject() || root.size() != 2 || !root.path("intent").isTextual() || !root.path("fields").isObject()) throw new IllegalArgumentException();
            var changes = new HashMap<String, Change>();
            root.get("fields").fields().forEachRemaining(e -> {
                var change = e.getValue();
                if (!change.isObject() || !change.path("operation").isTextual()) throw new IllegalArgumentException();
                var op = Operation.valueOf(change.get("operation").textValue());
                if (change.size() != (op == Operation.CLEAR ? 1 : 2) || op == Operation.REPLACE && !change.has("value")) throw new IllegalArgumentException();
                changes.put(e.getKey(), new Change(op, op == Operation.CLEAR ? null : change.get("value"), Provenance.MODEL_PROPOSED));
            });
            var proposal = new ExtractionProposal(Intent.valueOf(root.get("intent").textValue()), changes, null);
            SearchCriteriaState.empty().update(proposal.patch(false), clock);
            return proposal;
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid extraction proposal"); }
    }
    /** Explicit parser output only may update confirmed user state. Model proposals are never promoted. */
    public Map<String, SearchCriteriaState.Slot> patch(boolean explicitOnly) {
        var result = new HashMap<String, SearchCriteriaState.Slot>();
        fields.forEach((key, change) -> {
            if (explicitOnly && change.provenance() != Provenance.EXPLICIT_USER) throw new IllegalArgumentException("Unconfirmed model proposal");
            var origin = change.provenance() == Provenance.MODEL_PROPOSED ? SearchCriteriaState.Origin.MODEL_PROPOSED
                    : Set.of("adults", "children", "rooms").contains(key) ? SearchCriteriaState.Origin.USER_CONFIRMED : SearchCriteriaState.Origin.USER_PROVIDED;
            result.put(key, new SearchCriteriaState.Slot(change.operation() == Operation.CLEAR ? JSON.nullNode() : change.value(), origin));
        });
        return result;
    }
}
