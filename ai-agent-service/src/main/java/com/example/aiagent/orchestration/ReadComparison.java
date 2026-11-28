package com.example.aiagent.orchestration;

import com.example.aiagent.tools.ToolId;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Bounded same-turn comparison of two public A01 offers; no fetching or summary-derived evidence. */
public final class ReadComparison {
    public record Item(String hotelId, JsonNode baseUnitPrice, JsonNode reviews) {
        public Item { baseUnitPrice = baseUnitPrice.deepCopy(); reviews = reviews.deepCopy(); }
        @Override public JsonNode baseUnitPrice() { return baseUnitPrice.deepCopy(); }
        @Override public JsonNode reviews() { return reviews.deepCopy(); }
    }
    public record Comparison(String evidenceId, long criteriaRevision, List<Item> items, int basePriceOrder) {
        public Comparison { items = List.copyOf(items); }
    }
    public Optional<Comparison> compare(EvidenceLedger ledger, String evidenceId, int first, int second) {
        if (first < 0 || second < 0 || first >= 5 || second >= 5 || first == second) return Optional.empty();
        var record = ledger.find(evidenceId).orElse(null);
        if (record == null || record.toolId() != ToolId.A01) return Optional.empty();
        var items = record.facts().path("items");
        if (first >= items.size() || second >= items.size()) return Optional.empty();
        var left = items.get(first); var right = items.get(second);
        if (left.path("hotelId").equals(right.path("hotelId"))) return Optional.empty();
        // A single validated search envelope has one criteria revision and price unit. Stay/quantity still match exactly.
        for (String field : List.of("checkIn", "checkOut", "quantity"))
            if (!left.path("offer").path(field).equals(right.path("offer").path(field))) return Optional.empty();
        var a = left.path("offer").path("unitPrice"); var b = right.path("offer").path("unitPrice");
        if (!a.isNumber() || !b.isNumber()) return Optional.empty();
        var values = new ArrayList<Item>();
        for (var item : List.of(left, right)) {
            var reviews = item.path("reviews").deepCopy();
            if (!reviews.isObject() || !reviews.path("totalReviews").isIntegralNumber()) return Optional.empty();
            if (reviews.path("totalReviews").asLong() == 0)
                ((com.fasterxml.jackson.databind.node.ObjectNode) reviews).putNull("averageRating");
            values.add(new Item(item.path("hotelId").asText(), item.path("offer").path("unitPrice"), reviews));
        }
        return Optional.of(new Comparison(record.id(), record.criteriaRevision(), values, a.decimalValue().compareTo(b.decimalValue())));
    }
}
