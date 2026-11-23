package com.example.aiagent.tools;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Validator for the exact Draft 2020-12 keywords used by the approved ten schemas, not a general schema engine. */
final class ToolArguments {
    static final int MAX_ARGUMENT_BYTES = 16 * 1024;
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(262144).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final JsonNode schemas;
    private final Clock clock;
    ToolArguments(Clock clock) {
        this.clock = clock;
        try (var stream = getClass().getResourceAsStream("/tools/read-input-schemas.json")) {
            schemas = JSON.readTree(Objects.requireNonNull(stream));
        } catch (Exception ex) { throw new IllegalStateException("Missing READ schemas"); }
    }
    JsonNode schema(ToolId id) {
        var schema = (ObjectNode) dereference(schemas.path("tools").path(id.toolName()));
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        return schema;
    }
    private JsonNode dereference(JsonNode schema) {
        if (schema.has("$ref")) return dereference(schemas.at(schema.get("$ref").textValue().substring(1)));
        var copy = schema.deepCopy();
        if (copy.isObject()) {
            var object = (ObjectNode) copy;
            var keys = new ArrayList<String>(); object.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) object.set(key, dereference(object.get(key)));
        } else if (copy.isArray()) {
            for (int i = 0; i < copy.size(); i++) ((com.fasterxml.jackson.databind.node.ArrayNode) copy).set(i, dereference(copy.get(i)));
        }
        return copy;
    }
    JsonNode parse(String raw) {
        if (raw == null || raw.length() > MAX_ARGUMENT_BYTES || raw.getBytes(StandardCharsets.UTF_8).length > MAX_ARGUMENT_BYTES)
            throw new ToolFailure(ToolError.Category.INVALID_ARGUMENTS);
        try { return JSON.readTree(raw); }
        catch (Exception ex) { throw new ToolFailure(ToolError.Category.INVALID_ARGUMENTS); }
    }
    ObjectNode validate(ToolId id, JsonNode input, ToolExecutionContext context) {
        if (input == null) throw new ToolFailure(ToolError.Category.INVALID_ARGUMENTS);
        // Reparse enforces depth and byte limits also for direct JsonNode callers.
        JsonNode bounded = parse(input.toString());
        validateSchema(schema(id), bounded, null);
        ObjectNode args = (ObjectNode) bounded;
        if (id == ToolId.A01) {
            int selectors = 0;
            for (String key : List.of("cityId", "provinceId", "cityName")) if (args.has(key)) selectors++;
            require(selectors <= 1, ToolError.Category.INVALID_ARGUMENTS);
            if (args.has("checkIn")) {
                for (String key : List.of("rooms", "adults", "children")) require(args.has(key), ToolError.Category.MISSING_REQUIRED_CRITERIA);
                require(context.confirmedRooms() != null && context.confirmedAdults() != null && context.confirmedChildren() != null,
                        ToolError.Category.MISSING_REQUIRED_CRITERIA);
                require(context.confirmedRooms() == args.get("rooms").intValue()
                        && context.confirmedAdults() == args.get("adults").intValue()
                        && context.confirmedChildren() == args.get("children").intValue(), ToolError.Category.POLICY_DENIED);
            } else {
                for (String key : List.of("rooms", "adults", "children", "minPrice", "maxPrice"))
                    require(!args.has(key), ToolError.Category.MISSING_REQUIRED_CRITERIA);
            }
            if (args.has("minPrice") && args.has("maxPrice")) require(args.get("minPrice").decimalValue()
                    .compareTo(args.get("maxPrice").decimalValue()) <= 0, ToolError.Category.INVALID_ARGUMENTS);
            membership(args, "accommodationTypes", context.accommodationCodes());
            membership(args, "amenityCodes", context.amenityCodes());
        }
        if (args.has("checkIn")) {
            try {
                LocalDate start = LocalDate.parse(args.get("checkIn").textValue());
                LocalDate end = LocalDate.parse(args.get("checkOut").textValue());
                long nights = ChronoUnit.DAYS.between(start, end);
                require(!start.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))))
                        && nights > 0 && nights <= 30, ToolError.Category.INVALID_DATE_OCCUPANCY);
            } catch (DateTimeException ex) { throw new ToolFailure(ToolError.Category.INVALID_DATE_OCCUPANCY); }
        }
        if (id == ToolId.A05) {
            require(context.confirmedRooms() != null && context.confirmedRooms() == args.get("quantity").intValue(),
                    ToolError.Category.MISSING_REQUIRED_CRITERIA);
            require(context.publicHotelFor(args.get("roomTypeId").textValue()) != null, ToolError.Category.POLICY_DENIED);
        }
        return args;
    }
    private void membership(JsonNode args, String key, Set<String> known) {
        if (!args.has(key)) return;
        for (var code : args.get(key)) {
            require(!known.isEmpty(), ToolError.Category.MISSING_REQUIRED_CRITERIA);
            require(known.contains(code.textValue()), ToolError.Category.INVALID_ARGUMENTS);
        }
    }
    private void validateSchema(JsonNode schema, JsonNode value, String fieldName) {
        String type = schema.path("type").asText();
        boolean valid = switch (type) {
            case "object" -> value.isObject(); case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber(); case "number" -> value.isNumber();
            case "array" -> value.isArray(); case "" -> true; default -> false;
        };
        require(valid, ToolError.Category.INVALID_ARGUMENTS);
        if (schema.has("enum")) {
            boolean found = false; for (var option : schema.get("enum")) found |= option.equals(value);
            require(found, ToolError.Category.INVALID_ARGUMENTS);
        }
        if (value.isObject()) {
            for (var required : schema.path("required")) require(value.has(required.textValue()), ToolError.Category.MISSING_REQUIRED_CRITERIA);
            var fields = value.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next(); JsonNode property = schema.path("properties").get(field);
                require(property != null, ToolError.Category.INVALID_ARGUMENTS);
                validateSchema(property, value.get(field), field);
            }
            if (schema.has("anyOf")) {
                boolean match = false;
                for (var branch : schema.get("anyOf")) {
                    boolean present = true; for (var key : branch.path("required")) present &= value.has(key.textValue());
                    match |= present;
                }
                require(match, ToolError.Category.MISSING_REQUIRED_CRITERIA);
            }
            schema.path("dependentRequired").fields().forEachRemaining(entry -> {
                if (value.has(entry.getKey())) for (var key : entry.getValue())
                    require(value.has(key.textValue()), ToolError.Category.MISSING_REQUIRED_CRITERIA);
            });
        }
        if (value.isTextual()) {
            String text = value.textValue(); int length = text.codePointCount(0, text.length());
            require(!text.isBlank() && (!schema.has("minLength") || length >= schema.get("minLength").intValue())
                    && (!schema.has("maxLength") || length <= schema.get("maxLength").intValue()), ToolError.Category.INVALID_ARGUMENTS);
            if (schema.has("pattern")) require(text.matches(schema.get("pattern").textValue()), ToolError.Category.INVALID_ARGUMENTS);
            if ("uuid".equals(schema.path("format").asText())) require(uuid(text), ToolError.Category.INVALID_ARGUMENTS);
            if ("date".equals(schema.path("format").asText())) {
                try { LocalDate.parse(text); } catch (DateTimeException ex) { throw new ToolFailure(ToolError.Category.INVALID_DATE_OCCUPANCY); }
            }
        }
        if (value.isNumber()) {
            require((!schema.has("minimum") || value.decimalValue().compareTo(schema.get("minimum").decimalValue()) >= 0)
                    && (!schema.has("maximum") || value.decimalValue().compareTo(schema.get("maximum").decimalValue()) <= 0),
                    fieldName != null && Set.of("rooms", "adults", "children", "quantity").contains(fieldName)
                            ? ToolError.Category.INVALID_DATE_OCCUPANCY : ToolError.Category.INVALID_ARGUMENTS);
        }
        if (value.isArray()) {
            require((!schema.has("minItems") || value.size() >= schema.get("minItems").intValue())
                    && (!schema.has("maxItems") || value.size() <= schema.get("maxItems").intValue()), ToolError.Category.INVALID_ARGUMENTS);
            Set<JsonNode> seen = new HashSet<>();
            for (var item : value) {
                if (schema.path("uniqueItems").asBoolean()) require(seen.add(item), ToolError.Category.INVALID_ARGUMENTS);
                validateSchema(schema.path("items"), item, fieldName);
            }
        }
    }
    static boolean uuid(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[34][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }
    static void require(boolean condition, ToolError.Category error) { if (!condition) throw new ToolFailure(error); }
}
