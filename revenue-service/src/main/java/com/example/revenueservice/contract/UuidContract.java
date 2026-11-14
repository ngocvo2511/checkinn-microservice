package com.example.revenueservice.contract;

import java.util.UUID;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** Strict transport parser. Domain UUIDs may be v3 or v4; provider/business keys never use this. */
public final class UuidContract {
    private UuidContract() {}
    public static UUID required(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("A canonical UUID is required");
        return UUID.fromString(value);
    }
    public static UUID optional(String value) { return value == null ? null : required(value); }
    public static String text(UUID value) { return value == null ? null : value.toString(); }
    public static boolean valid(String value) {
        try { required(value); return true; } catch (IllegalArgumentException invalid) { return false; }
    }
    public static final class Deserializer extends JsonDeserializer<UUID> {
        @Override public UUID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            try { return required(parser.getValueAsString()); }
            catch (IllegalArgumentException invalid) { return (UUID) context.handleWeirdStringValue(UUID.class, parser.getValueAsString(), invalid.getMessage()); }
        }
    }
}
