package com.example.aiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.example.aiagent.tools.ToolError.Category.*;

/** Whitelist construction only; backend graphs are never copied wholesale. */
final class ToolProjection {
    record Projected(ObjectNode data, ToolResult.Status status, Instant checkedAt, boolean truncated) {}
    private boolean truncated;
    Projected project(ToolId id, JsonNode source, ObjectNode args, ToolExecutionContext context) {
        truncated = false;
        ObjectNode data = switch (id) {
            case A01 -> search(source, args);
            case A02 -> hotel(source, args);
            case A03 -> rooms(source, args);
            case A04 -> {
                ObjectNode room = room(source);
                equal(room.get("roomTypeId"), args.get("roomTypeId"));
                String known = context.publicHotelFor(args.get("roomTypeId").textValue());
                if (known != null) check(known.equals(room.get("hotelId").textValue()));
                yield room;
            }
            case A05 -> availability(source, args);
            case A06 -> locations(source);
            case A07 -> catalog(source, false);
            case A08 -> catalog(source, true);
            case A09 -> summaries(source, args);
            case A10 -> policy(source);
        };
        Instant checkedAt = id == ToolId.A05 ? instant(source, "checkedAt") : null;
        ToolResult.Status status = ToolResult.Status.OK;
        String collection = switch (id) {
            case A01 -> "items"; case A03 -> "rooms"; case A06 -> "locations";
            case A07 -> "types"; case A08 -> "amenities"; case A09 -> "summaries"; default -> null;
        };
        if (collection != null && data.get(collection).isEmpty()) status = ToolResult.Status.EMPTY;
        if (data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 12 * 1024)
            throw new ToolFailure(OUTPUT_TOO_LARGE);
        return new Projected(data, status, checkedAt, truncated);
    }
    private ObjectNode search(JsonNode source, ObjectNode args) {
        object(source); var content = array(source, "content"); check(content.size() <= 5);
        long page = integer(source, "page", 0, Integer.MAX_VALUE), size = integer(source, "size", 1, 5);
        long total = integer(source, "totalElements", 0, Long.MAX_VALUE);
        long pages = integer(source, "totalPages", 0, Integer.MAX_VALUE);
        check(page == args.path("page").asInt(0) && size == args.path("size").asInt(5));
        check(pages == total / size + (total % size == 0 ? 0 : 1));
        long offset = page * size;
        check(content.size() == (offset >= total ? 0 : Math.min(size, total - offset)));
        String sort = text(source, "sort"); check(sort.equals(args.path("sort").asText("DEFAULT")));
        var out = node(); var items = out.putArray("items"); Set<String> ids = new HashSet<>();
        for (var entry : content) {
            var hotel = required(entry, "hotel"); published(hotel);
            String hotelId = uuid(hotel, "id"); check(ids.add(hotelId));
            var item = node().put("hotelId", hotelId).put("name", text(hotel, "name"));
            if (hotel.has("starRating")) item.set("starRating", nullableInt(hotel, "starRating", 1, 5));
            if (hotel.has("accommodationType")) item.set("accommodationType", nullableText(hotel, "accommodationType"));
            if (hotel.hasNonNull("city")) item.set("cityName", nullableText(hotel.get("city"), "name"));
            var offer = required(entry, "offer"); var o = node().put("roomTypeId", uuid(offer, "roomTypeId"));
            long quantity = integer(offer, "quantity", 1, 20); check(quantity == args.path("rooms").asInt(1));
            o.put("quantity", quantity);
            BigDecimal unit = decimal(offer, "unitPrice"), totalNight = decimal(offer, "totalPerNight");
            check(unit.multiply(BigDecimal.valueOf(quantity)).compareTo(totalNight) == 0);
            o.put("unitPrice", unit).put("totalPerNight", totalNight);
            o.set("availableRooms", nullableInt(offer, "availableRooms", 0, Integer.MAX_VALUE));
            if (args.has("checkIn")) {
                equal(required(offer, "checkIn"), args.get("checkIn")); equal(required(offer, "checkOut"), args.get("checkOut"));
                check(!offer.get("availableRooms").isNull() && offer.get("availableRooms").longValue() >= quantity);
                instant(offer, "checkedAt");
            } else {
                for (String key : List.of("checkIn", "checkOut", "checkedAt", "availableRooms")) check(required(offer, key).isNull());
            }
            for (String key : List.of("checkIn", "checkOut", "checkedAt")) o.set(key, required(offer, key));
            item.set("offer", o);
            var review = summary(required(entry, "reviews")); check(hotelId.equals(review.get("hotelId").textValue()));
            item.set("reviews", review); items.add(item);
        }
        out.put("page", page).put("size", size).put("totalElements", total).put("totalPages", pages).put("sort", sort);
        out.put("occupancyBasis", args.has("checkIn") ? "USER_CONFIRMED" : "BACKEND_DEFAULT_BROWSE");
        return out;
    }
    private ObjectNode hotel(JsonNode source, JsonNode args) {
        published(source); equal(required(source, "id"), args.get("hotelId"));
        var out = node().put("hotelId", uuid(source, "id")).put("name", text(source, "name"));
        optionalText(source, out, "description", 2000);
        if (source.has("starRating")) out.set("starRating", nullableInt(source, "starRating", 1, 5));
        if (source.has("accommodationTypeLabel")) out.set("accommodationTypeLabel", nullableText(source, "accommodationTypeLabel"));
        out.set("lowestBasePrice", nullableDecimal(source, "lowestPrice"));
        if (source.has("address")) {
            var address = source.get("address");
            if (address.isNull()) out.putNull("address");
            else {
                object(address); var a = node();
                for (String key : List.of("street", "city", "country")) if (address.has(key)) a.set(key, nullableText(address, key));
                out.set("address", a); // no coordinates, arbitrary nested data or contacts
            }
        }
        out.set("policies", textPairs(array(source, "policies"), "category", "content"));
        out.set("faqs", textPairs(array(source, "faqs"), "question", "answer"));
        var amenities = out.putArray("amenities");
        for (var category : array(source, "amenityCategories")) for (var item : array(category, "items")) {
            String label = text(item, "title");
            if (amenities.size() >= 10 || label.length() > 1000) { truncated = true; continue; }
            var codes = array(item, "verifiedCodes"); var a = node().put("label", label); var retained = a.putArray("verifiedCodes");
            for (var code : codes) retained.add(codeLabel(code));
            amenities.add(a);
        }
        return out;
    }
    private ArrayNode textPairs(ArrayNode source, String first, String second) {
        var out = list();
        for (var item : source) {
            String a = text(item, first), b = text(item, second);
            if (out.size() >= 10 || a.length() > 1000 || b.length() > 1000) { truncated = true; continue; }
            out.add(node().put(first, a).put(second, b));
        }
        return out;
    }
    private ObjectNode room(JsonNode source) {
        object(source); check(required(source, "isActive").isBoolean() && source.get("isActive").booleanValue());
        var out = node().put("roomTypeId", uuid(source, "id")).put("hotelId", uuid(source, "hotelId"))
                .put("name", text(source, "name"));
        out.set("basePrice", nullableDecimal(source, "basePrice"));
        var capacity = required(source, "capacity"); var c = node();
        // A null capacity means both unknown, not zero; output shape explicitly supports nullable slots.
        for (String key : List.of("adults", "children")) {
            if (capacity.isNull()) c.putNull(key); else c.set(key, nullableInt(capacity, key, 0, Integer.MAX_VALUE));
        }
        out.set("capacity", c); var amenities = out.putArray("amenities");
        for (var label : array(source, "amenities")) { check(label.isTextual()); amenities.add(label.textValue()); }
        optionalText(source, out, "description", 2000); return out;
    }
    private ObjectNode rooms(JsonNode source, JsonNode args) {
        check(source != null && source.isArray()); var out = node().put("hotelId", args.get("hotelId").textValue());
        var rooms = out.putArray("rooms"); Set<String> ids = new HashSet<>();
        for (var entry : source) {
            var room = room(entry); equal(room.get("hotelId"), args.get("hotelId"));
            check(ids.add(room.get("roomTypeId").textValue()));
            if (rooms.size() < 10) rooms.add(room); else truncated = true;
        }
        return out.put("hasMore", source.size() > 10);
    }
    private ObjectNode availability(JsonNode source, JsonNode args) {
        object(source); var available = required(source, "available"); check(available.isBoolean());
        long count = integer(source, "availableRooms", 0, Integer.MAX_VALUE);
        check(available.booleanValue() == (count >= args.get("quantity").intValue())); instant(source, "checkedAt");
        var out = node();
        for (String key : List.of("roomTypeId", "checkIn", "checkOut", "quantity")) out.set(key, args.get(key));
        out.put("available", available.booleanValue()).put("availableRooms", count); return out;
    }
    private ObjectNode locations(JsonNode source) {
        check(source != null && source.isArray()); var out = node(); var locations = out.putArray("locations");
        Set<String> ids = new HashSet<>();
        for (var entry : source) {
            String id = uuid(entry, "id"); check(ids.add(id)); String type = text(entry, "type");
            check(Set.of("CITY", "PROVINCE").contains(type));
            var item = node().put("id", id).put("name", text(entry, "name")).put("type", type);
            item.set("parentName", nullableText(entry, "parentName"));
            var parent = required(entry, "parentId"); if (!parent.isNull()) uuid(entry, "parentId"); item.set("parentId", parent);
            if (locations.size() < 10) locations.add(item); else truncated = true;
        }
        return out.put("hasMore", source.size() > 10);
    }
    private ObjectNode catalog(JsonNode source, boolean amenities) {
        check(source != null && source.isArray() && source.size() <= (amenities ? 200 : 50));
        var out = node(); var items = out.putArray(amenities ? "amenities" : "types"); Set<String> seen = new HashSet<>();
        for (var entry : source) {
            if (amenities) check(required(entry, "active").isBoolean() && entry.get("active").booleanValue());
            var item = codeLabel(entry); check(seen.add(item.get("code").textValue())); items.add(item);
        }
        return out;
    }
    private ObjectNode summaries(JsonNode source, JsonNode args) {
        check(source != null && source.isArray() && source.size() <= 3);
        Set<String> requested = new LinkedHashSet<>(), seen = new HashSet<>();
        args.get("hotelIds").forEach(id -> requested.add(id.textValue())); var out = node(); var summaries = out.putArray("summaries");
        for (var entry : source) {
            var item = summary(entry); String id = item.get("hotelId").textValue();
            check(requested.contains(id) && seen.add(id)); summaries.add(item);
        }
        var missing = out.putArray("missingHotelIds"); for (String id : requested) if (!seen.contains(id)) missing.add(id);
        return out;
    }
    private ObjectNode summary(JsonNode source) {
        long count = integer(source, "totalReviews", 0, Long.MAX_VALUE); var rating = required(source, "averageRating");
        if (count == 0) check(rating.isNull());
        else check(rating.isNumber() && rating.decimalValue().signum() >= 0 && rating.decimalValue().compareTo(BigDecimal.TEN) <= 0);
        var out = node().put("hotelId", uuid(source, "hotelId")).put("totalReviews", count); out.set("averageRating", rating); return out;
    }
    private ObjectNode policy(JsonNode source) {
        String version = text(source, "version"), time = text(source, "checkInTime"), zone = text(source, "timeZone");
        try { LocalTime.parse(time); ZoneId.of(zone); } catch (DateTimeException ex) { throw new ToolFailure(INVALID_UPSTREAM_RESPONSE); }
        return node().put("version", version).put("checkInTime", time).put("timeZone", zone)
                .put("freeCancellationHours", integer(source, "freeCancellationHours", 0, Integer.MAX_VALUE));
    }
    private static ObjectNode codeLabel(JsonNode entry) { return node().put("code", text(entry, "code")).put("label", text(entry, "label")); }
    private void optionalText(JsonNode source, ObjectNode out, String key, int max) {
        if (!source.has(key)) return;
        var value = nullableText(source, key);
        if (value.isNull() || value.textValue().length() <= max) out.set(key, value); else truncated = true;
    }
    private static void published(JsonNode source) {
        object(source); check(required(source, "isActive").isBoolean() && source.get("isActive").booleanValue());
        check("APPROVED".equals(text(source, "approvedStatus")));
    }
    static ObjectNode node() { return ToolArguments.JSON.createObjectNode(); }
    static ArrayNode list() { return ToolArguments.JSON.createArrayNode(); }
    private static void check(boolean value) { ToolArguments.require(value, INVALID_UPSTREAM_RESPONSE); }
    private static void object(JsonNode value) { check(value != null && value.isObject()); }
    private static JsonNode required(JsonNode object, String key) { object(object); var value = object.get(key); check(value != null); return value; }
    private static ArrayNode array(JsonNode object, String key) { var value = required(object, key); check(value.isArray()); return (ArrayNode) value; }
    private static String text(JsonNode object, String key) { var value = required(object, key); check(value.isTextual() && !value.textValue().isBlank()); return value.textValue(); }
    private static JsonNode nullableText(JsonNode object, String key) { var value = required(object, key); check(value.isNull() || value.isTextual()); return value; }
    private static String uuid(JsonNode object, String key) { String id = text(object, key); check(ToolArguments.uuid(id)); return id; }
    private static long integer(JsonNode object, String key, long min, long max) {
        var value = required(object, key); check(value.isIntegralNumber() && value.canConvertToLong());
        long n = value.longValue(); check(n >= min && n <= max); return n;
    }
    private static JsonNode nullableInt(JsonNode object, String key, long min, long max) {
        var value = required(object, key); if (!value.isNull()) integer(object, key, min, max); return value;
    }
    private static BigDecimal decimal(JsonNode object, String key) { var value = required(object, key); check(value.isNumber() && value.decimalValue().signum() >= 0); return value.decimalValue(); }
    private static JsonNode nullableDecimal(JsonNode object, String key) { var value = required(object, key); if (!value.isNull()) decimal(object, key); return value; }
    private static Instant instant(JsonNode object, String key) {
        try { return Instant.parse(text(object, key)); } catch (DateTimeException ex) { throw new ToolFailure(INVALID_UPSTREAM_RESPONSE); }
    }
    private static void equal(JsonNode first, JsonNode second) { check(Objects.equals(first, second)); }
}
