package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.dto.CreateBookingRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

/** Stable, scoped command identity. Never persist the contact-bearing request JSON. */
public final class BookingIdempotency {
    private BookingIdempotency() {}

    public static String keyHash(CreateBookingRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) return null;
        if (request.getUserId() == null || request.getUserId().isBlank()
                || !request.getIdempotencyKey().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Idempotency key must be a UUID and requires an authenticated customer");
        }
        return hash("booking-create-v1:" + request.getUserId() + ":" + UUID.fromString(request.getIdempotencyKey()));
    }

    public static UUID holdCommand(String keyHash) {
        return UUID.nameUUIDFromBytes(("booking-hold-v1:" + keyHash).getBytes(StandardCharsets.UTF_8));
    }

    public static String bookingId(String keyHash) {
        return UUID.nameUUIDFromBytes(("booking-id-v1:" + keyHash).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String requestHash(CreateBookingRequest r) {
        try {
            // Explicit V1 sequence avoids hash changes when a DTO gains unrelated fields.
            var items = r.getItems() == null ? null : r.getItems().stream().map(i -> i == null ? null :
                    Arrays.asList(i.getRoomTypeId(), i.getCheckInDate(), i.getCheckOutDate(), i.getQuantity(),
                            i.getUnitPrice() == null ? null : i.getUnitPrice().stripTrailingZeros().toPlainString(),
                            i.getGuestName())).toList();
            var input = Arrays.asList("V1", r.getUserId(), r.getHotelId(), r.getCheckInDate(), r.getCheckOutDate(),
                    r.getAdults(), r.getChildren(), r.getContactName(), r.getContactEmail(), r.getContactPhone(),
                    r.getSpecialRequests(), r.getVoucherCode(), r.getPointsToUse(), r.getAcceptedCancellationPolicyVersion(), items);
            return hash(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(input));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("Booking command could not be encoded");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
