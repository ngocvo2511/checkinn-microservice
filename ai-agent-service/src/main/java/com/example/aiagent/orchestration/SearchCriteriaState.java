package com.example.aiagent.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Explicit internal updates only. Model proposals cannot promote themselves into confirmed criteria. */
public record SearchCriteriaState(Map<String, Slot> slots, long revision) {
    public enum Origin { USER_PROVIDED, USER_CONFIRMED, MODEL_PROPOSED, BACKEND_DERIVED }
    public record Slot(JsonNode value, Origin origin) {
        public Slot { Objects.requireNonNull(value); Objects.requireNonNull(origin); value = value.deepCopy(); }
        @Override public JsonNode value() { return value.deepCopy(); }
        @Override public String toString() { return "Slot[origin=" + origin + "]"; }
    }
    private static final Set<String> FIELDS = Set.of("cityId", "provinceId", "cityName", "hotelName", "checkIn", "checkOut",
            "rooms", "adults", "children", "minPrice", "maxPrice", "accommodationTypes", "amenityCodes", "starRatings", "guestRatings");
    public SearchCriteriaState { slots = Map.copyOf(slots); if (revision < 0) throw new IllegalArgumentException("Invalid revision"); }
    @Override public String toString() { return "SearchCriteriaState[revision=" + revision + ", fields=" + slots.keySet() + "]"; }
    public static SearchCriteriaState empty() { return new SearchCriteriaState(Map.of(), 0); }
    public SearchCriteriaState update(Map<String, Slot> patch, Clock clock) {
        var next = new HashMap<>(slots);
        if (!FIELDS.containsAll(patch.keySet())) throw new IllegalArgumentException("Unsupported criteria");
        // An occupancy edit replaces the whole group: no silent old adults/children/rooms.
        if (patch.keySet().stream().anyMatch(Set.of("rooms", "adults", "children")::contains))
            List.of("rooms", "adults", "children").forEach(next::remove);
        if (patch.keySet().stream().anyMatch(Set.of("cityId", "provinceId", "cityName")::contains))
            List.of("cityId", "provinceId", "cityName").forEach(next::remove);
        if (patch.containsKey("checkIn") || patch.containsKey("checkOut")) {
            next.remove("checkIn"); next.remove("checkOut");
        }
        patch.forEach((key, value) -> { if (value == null || value.value().isNull()) next.remove(key); else next.put(key, value); });
        validate(next, clock);
        return new SearchCriteriaState(next, next.equals(slots) ? revision : revision + 1);
    }
    private static void validate(Map<String, Slot> slots, Clock clock) {
        var mapper = new ObjectMapper();
        var schema = mapper.createObjectNode(); slots.forEach((k, v) -> schema.set(k, v.value()));
        if (schema.toString().length() > 8192) throw new IllegalArgumentException("Criteria too large");
        if (List.of("cityId", "provinceId", "cityName").stream().filter(schema::has).count() > 1) throw new IllegalArgumentException("Ambiguous location");
        for (var entry : slots.entrySet()) {
            String key = entry.getKey(); var value = entry.getValue().value();
            if (Set.of("rooms", "adults", "children").contains(key)) {
                int min = key.equals("children") ? 0 : 1, max = key.equals("rooms") ? 20 : 40;
                if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max)
                    throw new IllegalArgumentException("Invalid occupancy");
            } else if (Set.of("minPrice", "maxPrice").contains(key)) {
                if (!value.isNumber() || value.decimalValue().signum() < 0) throw new IllegalArgumentException("Invalid price");
            } else if (Set.of("accommodationTypes", "amenityCodes", "starRatings", "guestRatings").contains(key)) {
                int max = key.equals("starRatings") ? 5 : key.equals("amenityCodes") ? 10 : 20;
                if (!value.isArray() || value.size() > max) throw new IllegalArgumentException("Invalid filters");
                var unique = new HashSet<JsonNode>();
                for (var item : value) {
                    if (!unique.add(item)) throw new IllegalArgumentException("Duplicate filter");
                    boolean valid = switch (key) {
                        case "starRatings" -> item.isIntegralNumber() && item.canConvertToInt() && item.intValue() >= 1 && item.intValue() <= 5;
                        case "guestRatings" -> item.isNumber() && item.decimalValue().signum() >= 0 && item.decimalValue().compareTo(java.math.BigDecimal.TEN) <= 0;
                        case "amenityCodes" -> item.isTextual() && item.textValue().matches("[A-Z][A-Z0-9_]{0,63}");
                        default -> item.isTextual() && !item.textValue().isBlank() && item.textValue().length() <= 64;
                    };
                    if (!valid) throw new IllegalArgumentException("Invalid filter");
                }
            } else if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 200) {
                throw new IllegalArgumentException("Invalid criteria");
            }
            if (Set.of("cityId", "provinceId").contains(key) && !value.textValue().matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[34][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
                throw new IllegalArgumentException("Invalid location ID");
            if (key.equals("cityName") && value.textValue().length() > 120) throw new IllegalArgumentException("Invalid destination");
            if (Set.of("checkIn", "checkOut").contains(key) && !value.textValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
                throw new IllegalArgumentException("Invalid civil date");
        }
        if (schema.has("checkIn") || schema.has("checkOut")) {
            // Partial dates are retained for clarification, never admitted to a dated tool call.
            for (String key : List.of("checkIn", "checkOut")) if (schema.has(key)) LocalDate.parse(schema.get(key).textValue());
            if (schema.has("checkIn") && LocalDate.parse(schema.get("checkIn").textValue()).isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))))
                throw new IllegalArgumentException("Past stay");
            if (schema.has("checkIn") && schema.has("checkOut")) {
                long nights = ChronoUnit.DAYS.between(LocalDate.parse(schema.get("checkIn").textValue()), LocalDate.parse(schema.get("checkOut").textValue()));
                if (nights < 1 || nights > 30) throw new IllegalArgumentException("Invalid stay");
            }
        }
        if (schema.has("minPrice") && schema.has("maxPrice") && schema.get("minPrice").decimalValue().compareTo(schema.get("maxPrice").decimalValue()) > 0)
            throw new IllegalArgumentException("Invalid range");
    }
    public JsonNode confirmed(String key) {
        var slot = slots.get(key);
        return slot != null && (slot.origin() == Origin.USER_CONFIRMED || slot.origin() == Origin.USER_PROVIDED) ? slot.value() : null;
    }
    public Integer occupancy(String key) {
        var slot = slots.get(key);
        return slot != null && slot.origin() == Origin.USER_CONFIRMED ? slot.value().intValue() : null;
    }
    public boolean hasDestination() { return List.of("cityId", "provinceId", "cityName", "hotelName").stream().anyMatch(k -> confirmed(k) != null); }
    public boolean hasDates() { return confirmed("checkIn") != null && confirmed("checkOut") != null; }
    public boolean hasOccupancy() { return occupancy("rooms") != null && occupancy("adults") != null && occupancy("children") != null; }
    public JsonNode confirmedJson() {
        var result = new ObjectMapper().createObjectNode();
        slots.forEach((key, value) -> { if (confirmed(key) != null) result.set(key, confirmed(key)); }); return result;
    }
}
