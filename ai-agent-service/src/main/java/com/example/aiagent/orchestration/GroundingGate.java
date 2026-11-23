package com.example.aiagent.orchestration;

import com.example.aiagent.tools.ToolId;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/** Model may select evidence, never supply factual claim text or values. Free text is never rendered. */
public final class GroundingGate {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public record Fact(String evidenceId, ToolId toolId, long criteriaRevision, JsonNode values) {
        public Fact { values = values.deepCopy(); }
        @Override public JsonNode values() { return values.deepCopy(); }
    }
    public record Answer(String category, String message, List<Fact> facts) { public Answer { facts = List.copyOf(facts); } }
    public Answer validate(String raw, EvidenceLedger ledger, SearchCriteriaState criteria, PolicyExecutor.Scope scope) {
        try {
            if (raw == null || raw.length() > 4096) return safe("GROUNDING_REJECTED");
            var root = JSON.readTree(raw);
            if (!root.isObject() || root.size() != 1 || !root.path("evidenceIds").isArray() || root.path("evidenceIds").size() > 6) return safe("GROUNDING_REJECTED");
            var facts = new ArrayList<Fact>(); var seen = new HashSet<String>();
            for (var ref : root.path("evidenceIds")) {
                if (!ref.isTextual() || !seen.add(ref.textValue())) return safe("GROUNDING_REJECTED");
                var evidence = ledger.find(ref.textValue()).orElse(null);
                if (evidence == null || evidence.criteriaRevision() != criteria.revision() || !relevant(evidence, scope)) return safe("GROUNDING_REJECTED");
                facts.add(new Fact(evidence.id(), evidence.toolId(), evidence.criteriaRevision(), render(evidence)));
            }
            if (facts.isEmpty()) return clarification(criteria);
            return new Answer("GROUNDED", "Dữ liệu READ tại thời điểm kiểm tra; giá cơ bản không phải tổng thanh toán, kiểm tra phòng không giữ chỗ. Chính sách hủy là chính sách chung.", facts);
        } catch (Exception malformed) { return safe("GROUNDING_REJECTED"); }
    }
    public boolean relevant(EvidenceLedger.Record record, PolicyExecutor.Scope scope) {
        return switch (scope.intent()) {
            case SEARCH, BROWSE -> Set.of(ToolId.A01, ToolId.A07, ToolId.A08).contains(record.toolId());
            case DETAIL -> record.toolId() == ToolId.A02 && record.hotelIds().contains(scope.hotelId());
            case ROOMS -> record.toolId() == ToolId.A03 && record.hotelIds().contains(scope.hotelId());
            case AVAILABILITY -> record.toolId() == ToolId.A04 && record.roomHotels().containsKey(scope.roomTypeId())
                    && (scope.hotelId() == null || Objects.equals(scope.hotelId(), record.roomHotels().get(scope.roomTypeId())))
                    || record.toolId() == ToolId.A05 && Objects.equals(scope.roomTypeId(), record.facts().path("roomTypeId").asText());
            case REVIEWS -> record.toolId() == ToolId.A09 && reviewTarget(record.facts(), scope.hotelId());
            case LOCATIONS -> record.toolId() == ToolId.A06;
            case CANCELLATION -> record.toolId() == ToolId.A10;
        };
    }
    private boolean reviewTarget(JsonNode facts, String hotelId) {
        for (var summary : facts.path("summaries")) if (Objects.equals(hotelId, summary.path("hotelId").asText())) return true;
        for (var missing : facts.path("missingHotelIds")) if (Objects.equals(hotelId, missing.asText())) return true;
        return false;
    }
    private JsonNode render(EvidenceLedger.Record record) {
        var source = record.facts(); ObjectNode out = JSON.createObjectNode();
        // Explicit scalar/structured facts; descriptions, FAQ/policy free text and arbitrary labels never become instructions or claims.
        switch (record.toolId()) {
            case A01 -> {
                var items = out.putArray("items");
                for (var item : source.path("items")) {
                    var card = JSON.createObjectNode();
                    copy(item, card, "hotelId", "name", "cityName", "starRating", "reviews");
                    var offer = JSON.createObjectNode(); copy(item.path("offer"), offer, "roomTypeId", "quantity", "unitPrice", "totalPerNight", "availableRooms", "checkIn", "checkOut", "checkedAt");
                    card.set("baseOfferObservation", offer); items.add(card);
                }
                copy(source, out, "totalElements", "page", "occupancyBasis");
            }
            case A02 -> copy(source, out, "hotelId", "name", "starRating", "lowestBasePrice");
            case A03 -> {
                var rooms = out.putArray("rooms");
                for (var room : source.path("rooms")) { var value = JSON.createObjectNode(); copy(room, value, "roomTypeId", "hotelId", "name", "basePrice", "capacity"); rooms.add(value); }
                copy(source, out, "hotelId", "hasMore");
            }
            case A04 -> copy(source, out, "roomTypeId", "hotelId", "name", "basePrice", "capacity");
            case A05 -> { copy(source, out, "roomTypeId", "checkIn", "checkOut", "quantity", "available", "availableRooms"); out.put("checkedAt", record.checkedAt().toString()); out.put("reservationHold", false); }
            case A06 -> copy(source, out, "locations", "hasMore");
            case A07 -> copy(source, out, "types");
            case A08 -> copy(source, out, "amenities");
            case A09 -> copy(source, out, "summaries", "missingHotelIds");
            case A10 -> { copy(source, out, "version", "checkInTime", "timeZone", "freeCancellationHours"); out.put("scope", "GENERAL_ONLY"); }
        }
        out.put("outcome", record.status().name()); out.put("retrievedAt", record.retrievedAt().toString()); return out;
    }
    private void copy(JsonNode from, ObjectNode to, String... keys) { for (String key : keys) if (from.has(key)) to.set(key, from.get(key).deepCopy()); }
    public Answer clarification(SearchCriteriaState criteria) {
        if (!criteria.hasDestination()) return new Answer("NEED_DESTINATION", "Bạn muốn tìm ở đâu hoặc khách sạn nào?", List.of());
        if (!criteria.hasDates()) return new Answer("NEED_DATES", "Bạn muốn nhận và trả phòng ngày nào?", List.of());
        if (!criteria.hasOccupancy()) return new Answer("NEED_OCCUPANCY", "Vui lòng xác nhận số phòng, người lớn và trẻ em.", List.of());
        return safe("INSUFFICIENT_EVIDENCE");
    }
    public Answer safe(String category) { return new Answer(category, "Chưa đủ dữ liệu được xác minh để trả lời yêu cầu này.", List.of()); }
}
