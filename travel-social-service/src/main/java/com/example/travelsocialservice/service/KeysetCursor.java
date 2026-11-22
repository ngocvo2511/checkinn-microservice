package com.example.travelsocialservice.service;

import com.example.travelsocialservice.web.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Opaque page position: base64url of "{createdAt}|{id}". */
public record KeysetCursor(Instant createdAt, UUID id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** Null or blank means the first page. */
    public static KeysetCursor decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("missing separator");
            }
            return new KeysetCursor(Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw ApiException.invalid("cursor", "cursor is malformed");
        }
    }
}
