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
                facts.add(new Fact(evidence.id(), evidence.toolId(), evidence.criteriaRevision(), render(evidence, criteria)));
            }
            if (facts.isEmpty()) return clarification(criteria);
            return new Answer("GROUNDED", scope.intent() == PolicyExecutor.Intent.BROWSE
                    ? "Danh sách và giá cơ bản (giá cơ sở/tham khảo) trong trang kết quả vừa đọc; chưa có ngày lưu trú và nhóm khách được xác nhận, chưa kiểm tra phòng trống theo ngày. Giá không phải giá cuối cùng được bảo đảm. Dữ liệu không giữ chỗ. Chưa giữ phòng hoặc tạo booking. Bạn cung cấp ngày nhận/trả phòng và số người lớn, trẻ em, phòng để kiểm tra tiếp."
                    : "Dữ liệu READ tại thời điểm kiểm tra; giá cơ bản không phải tổng thanh toán, kiểm tra phòng không giữ chỗ. Chính sách hủy là chính sách chung.", facts);
        } catch (Exception malformed) { return safe("GROUNDING_REJECTED"); }
    }
    public boolean relevant(EvidenceLedger.Record record, PolicyExecutor.Scope scope) {
        return switch (scope.intent()) {
            case SEARCH, BROWSE -> Set.of(ToolId.A01, ToolId.A07, ToolId.A08).contains(record.toolId());
            case DETAIL -> record.toolId() == ToolId.A02 && record.hotelIds().contains(scope.hotelId());
            case ROOM_DETAIL -> record.toolId() == ToolId.A04 && record.roomHotels().containsKey(scope.roomTypeId())
                    && Objects.equals(scope.hotelId(), record.roomHotels().get(scope.roomTypeId()));
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
    private JsonNode render(EvidenceLedger.Record record, SearchCriteriaState criteria) {
        var out = (ObjectNode) render(record);
        if (record.toolId() == ToolId.A01 && criteria.confirmed("referencePriceExclusive") != null) {
            var filtered = JSON.createArrayNode();
            for (var item : out.path("items")) if (item.at("/baseOfferObservation/unitPrice").decimalValue()
                    .compareTo(criteria.confirmed("maxPrice").decimalValue()) < 0) filtered.add(item);
            out.set("items", filtered); out.remove("totalElements"); out.put("filterScope", "RETURNED_PAGE_ONLY");
            out.put("outcome", filtered.isEmpty() ? "EMPTY" : "OK");
        }
        return out;
    }
    /** Every rendered fact is selected from this turn's refresh outcomes, never from remembered identity data. */
    Answer comparison(List<ResultSetReferences.Identity> identities, PolicyExecutor.Outcome search,
                      List<PolicyExecutor.Outcome> details, EvidenceLedger ledger, SearchCriteriaState criteria) {
        var facts = new ArrayList<Fact>(); var verified = new HashSet<String>();
        for (int i = 0; i < 2; i++) {
            var record = ledger.find(details.get(i).evidenceId()).orElse(null);
            if (record != null && record.toolId() == ToolId.A02 && record.hotelIds().contains(identities.get(i).hotelId())) {
                var values = (ObjectNode) render(record);
                // lowestBasePrice is a detail observation, not refreshed A01 matched-offer pricing.
                values.remove("lowestBasePrice");
                values.put("selectionOrdinal", identities.get(i).ordinal() + 1);
                facts.add(new Fact(record.id(), record.toolId(), record.criteriaRevision(), values));
                verified.add(identities.get(i).hotelId());
            }
        }
        var record = ledger.find(search.evidenceId()).orElse(null);
        String order = "Chưa đủ giá mới của cả hai lựa chọn để kết luận bên nào rẻ hơn.";
        boolean fullPrices = false;
        if (record != null && record.toolId() == ToolId.A01) {
            var values = (ObjectNode) render(record, criteria); var items = values.putArray("items");
            var prices = new ArrayList<JsonNode>();
            for (var identity : identities) for (var item : render(record, criteria).path("items")) {
                if (identity.hotelId().equals(item.path("hotelId").asText()) && verified.contains(identity.hotelId())) {
                    items.add(item); prices.add(item.path("baseOfferObservation").path("unitPrice"));
                }
            }
            // The bounded fresh search may no longer return either selected hotel. Do not invent its offer.
            values.remove("totalElements"); values.put("comparisonScope", "TWO_SELECTED_HOTELS_ONLY");
            if (!items.isEmpty()) facts.add(new Fact(record.id(), record.toolId(), record.criteriaRevision(), values));
            if (prices.size() == 2 && prices.stream().allMatch(JsonNode::isNumber)) {
                int cmp = prices.getFirst().decimalValue().compareTo(prices.getLast().decimalValue());
                order = cmp == 0 ? "Hai lựa chọn có cùng giá cơ sở trong lần đọc mới."
                        : "Lựa chọn thứ " + (cmp < 0 ? identities.getFirst().ordinal() + 1 : identities.getLast().ordinal() + 1) + " có giá cơ sở thấp hơn trong lần đọc mới.";
                fullPrices = true;
            }
        }
        if (facts.isEmpty()) return safe("READ_UNAVAILABLE");
        return new Answer(fullPrices && verified.size() == 2 ? "GROUNDED" : "COMPARISON_PARTIAL",
                order + " Chỉ so sánh giá cơ sở VND/phòng/đêm và đánh giá có evidence mới; không suy giá từ chi tiết khách sạn. "
                        + "Thuộc tính thiếu hoặc lựa chọn đọc lỗi chưa thể xác minh; không coi null hay chưa có đánh giá là điểm 0. "
                        + "Giá không phải tổng thanh toán được bảo đảm; chưa giữ phòng hoặc tạo booking. "
                        + "Khi chưa có ngày lưu trú: chưa kiểm tra phòng trống theo ngày. Chưa đủ tiêu chí để kết luận phù hợp hơn.", facts);
    }
    public Answer clarification(SearchCriteriaState criteria) {
        if (!criteria.hasDestination()) return new Answer("NEED_DESTINATION", "Bạn muốn tìm ở đâu hoặc khách sạn nào?", List.of());
        return stayClarification(criteria);
    }
    /** Selected-room requests already have a destination through trusted identity, not a location search slot. */
    public Answer stayClarification(SearchCriteriaState criteria) {
        if (!criteria.hasDates()) {
            String message = criteria.confirmed("checkIn") != null
                    ? "Đã ghi nhận ngày nhận phòng. Bạn xác nhận ngày trả phòng; gửi cả hai ngày để xác nhận cặp ngày."
                    : criteria.confirmed("checkOut") != null
                    ? "Đã ghi nhận ngày trả phòng. Bạn xác nhận ngày nhận phòng; gửi cả hai ngày để xác nhận cặp ngày."
                    : "Bạn muốn nhận và trả phòng ngày nào?";
            return new Answer("NEED_DATES", message, List.of());
        }
        if (!criteria.hasOccupancy()) {
            var missing = new ArrayList<String>();
            if (criteria.occupancy("adults") == null) missing.add("số người lớn");
            if (criteria.occupancy("children") == null) missing.add("số trẻ em");
            if (criteria.occupancy("rooms") == null) missing.add("số phòng");
            String message = missing.size() == 3 ? "Vui lòng xác nhận số phòng, người lớn và trẻ em."
                    : "Cần xác nhận " + String.join(" và ", missing) + ". Khi trả lời, gửi cùng các số đã nêu để xác nhận trọn nhóm khách và phòng.";
            return new Answer("NEED_OCCUPANCY", message, List.of());
        }
        return safe("INSUFFICIENT_EVIDENCE");
    }
    public Answer safe(String category) { return new Answer(category, "Chưa đủ dữ liệu được xác minh để trả lời yêu cầu này.", List.of()); }
    public Answer invalidCriteria(RuntimeException invalid) {
        // Only exact server-owned validator reasons select a fixed message; never echo exception text.
        String message = switch (Objects.toString(invalid.getMessage(), "")) {
            case "Past stay" -> "Ngày nhận phòng đã qua theo giờ Việt Nam. Bạn chọn lại cặp ngày nhận và trả phòng.";
            case "Invalid stay" -> "Thời gian lưu trú phải từ 1 đến 30 đêm. Bạn xác nhận lại cặp ngày nhận và trả phòng.";
            case "Invalid occupancy" -> "Số phòng phải từ 1 đến 20, người lớn từ 1 đến 40, trẻ em từ 0 đến 40. Bạn xác nhận lại nhóm khách và phòng.";
            case "Invalid range" -> "Giá tối thiểu không được lớn hơn giá tối đa. Bạn xác nhận lại khoảng giá cho một phòng mỗi đêm.";
            default -> "Tiêu chí chưa hợp lệ; chưa áp dụng thay đổi. Bạn kiểm tra lại các giá trị vừa cung cấp.";
        };
        return new Answer("INVALID_CRITERIA", message, List.of());
    }
}
