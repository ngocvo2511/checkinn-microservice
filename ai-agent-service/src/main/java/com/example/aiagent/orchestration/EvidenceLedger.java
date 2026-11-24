package com.example.aiagent.orchestration;

import com.example.aiagent.tools.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Conversation-local verified projections. No raw responses or failed observations. */
public final class EvidenceLedger {
    public enum Validity { CURRENT, OLD_REVISION, EXPIRED, PREVIOUS_TURN_INVENTORY }
    public record Record(String id, ToolId toolId, String fingerprint, String sourceRoute, String requestId,
                         Instant retrievedAt, Instant checkedAt, long criteriaRevision, Set<String> hotelIds,
                         Map<String, String> roomHotels, JsonNode facts, ToolResult.Status status, String turnId) {
        public Record { hotelIds = Set.copyOf(hotelIds); roomHotels = Map.copyOf(roomHotels); facts = facts.deepCopy(); }
        @Override public JsonNode facts() { return facts.deepCopy(); }
        @Override public String toString() { return "Evidence[tool=" + toolId + ", revision=" + criteriaRevision + "]"; }
    }
    private final List<Record> records;
    private final long revision;
    private final String turn;
    private final Clock clock;
    public EvidenceLedger(List<Record> previous, long revision, String turn, Clock clock) {
        this.records = new ArrayList<>(previous); this.revision = revision; this.turn = turn; this.clock = clock;
    }
    public Validity validity(Record record) {
        if (record.criteriaRevision() != revision) return Validity.OLD_REVISION;
        if (record.retrievedAt().isAfter(clock.instant()) || !clock.instant().isBefore(record.retrievedAt().plusSeconds(30))) return Validity.EXPIRED;
        if (record.checkedAt() != null && (record.checkedAt().isAfter(clock.instant())
                || !clock.instant().isBefore(record.checkedAt().plusSeconds(30)))) return Validity.EXPIRED;
        if ((record.toolId() == ToolId.A01 || record.toolId() == ToolId.A05) && !turn.equals(record.turnId()))
            return Validity.PREVIOUS_TURN_INVENTORY;
        // A01 stores checkedAt per offer, not in the envelope.
        if (record.toolId() == ToolId.A01) for (var item : record.facts().path("items")) {
            var checked = item.path("offer").get("checkedAt");
            if (checked != null && !checked.isNull()) {
                Instant at = Instant.parse(checked.textValue());
                if (at.isAfter(clock.instant()) || !clock.instant().isBefore(at.plusSeconds(30))) return Validity.EXPIRED;
            }
        }
        return Validity.CURRENT;
    }
    public List<Record> current() { return records.stream().filter(r -> validity(r) == Validity.CURRENT).toList(); }
    public Optional<Record> find(String id) { return current().stream().filter(r -> r.id().equals(id)).findFirst(); }
    public List<Record> snapshot() { return List.copyOf(records.subList(Math.max(0, records.size() - 24), records.size())); }
    public Optional<Record> add(ToolResult result, JsonNode args) {
        if (result.status() == ToolResult.Status.ERROR || result.data() == null || result.criteriaRevision() == null
                || result.criteriaRevision() != revision) return Optional.empty();
        var hotels = new HashSet<String>(); var rooms = new HashMap<String, String>();
        var data = result.data();
        if (data.hasNonNull("hotelId")) hotels.add(data.get("hotelId").textValue());
        if (result.toolId() == ToolId.A01) for (var item : data.path("items")) {
            String hotel = item.path("hotelId").textValue(); hotels.add(hotel);
            rooms.put(item.path("offer").path("roomTypeId").textValue(), hotel);
        }
        if (result.toolId() == ToolId.A03) for (var room : data.path("rooms")) rooms.put(room.path("roomTypeId").textValue(), room.path("hotelId").textValue());
        if (result.toolId() == ToolId.A04) rooms.put(data.path("roomTypeId").textValue(), data.path("hotelId").textValue());
        var e = result.evidence();
        var record = new Record(UUID.randomUUID().toString(), result.toolId(), fingerprint(args), e.sourceRoute(), e.requestId(),
                e.retrievedAt(), e.checkedAt(), revision, hotels, rooms, data, result.status(), turn);
        records.add(record); if (records.size() > 24) records.removeFirst(); return Optional.of(record);
    }
    public Map<String, String> roomHotels() {
        var result = new HashMap<String, String>(); current().forEach(r -> result.putAll(r.roomHotels())); return Map.copyOf(result);
    }
    public Set<String> hotelIds() { var ids = new HashSet<String>(); current().forEach(r -> ids.addAll(r.hotelIds())); return Set.copyOf(ids); }
    public Set<String> codes(ToolId tool, String field) {
        var codes = new HashSet<String>(); current().stream().filter(r -> r.toolId() == tool)
                .forEach(r -> r.facts().path(field).forEach(item -> codes.add(item.path("code").asText()))); return Set.copyOf(codes);
    }
    public static String fingerprint(JsonNode args) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(args).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static String canonical(JsonNode node) {
        if (node.isObject()) {
            var sorted = new TreeMap<String, String>(); node.fields().forEachRemaining(e -> sorted.put(e.getKey(), canonical(e.getValue())));
            return sorted.toString();
        }
        if (node.isArray()) { var values = new TreeSet<String>(); node.forEach(n -> values.add(canonical(n))); return values.toString(); }
        if (node.isNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        return node.toString();
    }
}
