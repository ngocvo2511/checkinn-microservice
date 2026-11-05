package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.dto.CreateBookingItemRequest;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BookingIdempotencyTest {
    @Test void commandIdentityIsStableAndScopedToTheSignedCustomer() {
        var request = CreateBookingRequest.builder().userId("customer-a").idempotencyKey(UUID.randomUUID().toString()).build();
        String first = BookingIdempotency.keyHash(request);
        assertThat(BookingIdempotency.keyHash(request)).isEqualTo(first);
        request.setIdempotencyKey(request.getIdempotencyKey().toUpperCase());
        assertThat(BookingIdempotency.keyHash(request)).isEqualTo(first);
        request.setUserId("customer-b");
        assertThat(BookingIdempotency.keyHash(request)).isNotEqualTo(first);
        assertThat(BookingIdempotency.holdCommand(first).toString()).isNotEqualTo(BookingIdempotency.bookingId(first));
    }

    @Test void malformedKeysCannotReachInventoryAndLegacyRequestsRemainUnkeyed() {
        var request = CreateBookingRequest.builder().userId("customer").build();
        assertThat(BookingIdempotency.keyHash(request)).isNull();
        for (String key : List.of("bad", "1-1-1-1-1", "a".repeat(1000))) {
            request.setIdempotencyKey(key);
            assertThatIllegalArgumentException().isThrownBy(() -> BookingIdempotency.keyHash(request));
        }
    }

    @Test void fingerprintUsesBusinessInputAndNormalizesDecimalRepresentation() {
        var item = CreateBookingItemRequest.builder().roomTypeId("room").quantity(1).unitPrice(new BigDecimal("100.0")).build();
        var request = CreateBookingRequest.builder().userId("customer").hotelId("hotel").contactPhone("0000000000").items(List.of(item)).build();
        String first = BookingIdempotency.requestHash(request);
        item.setUnitPrice(new BigDecimal("100.00"));
        request.setHotelName("ignored client label"); item.setRoomTypeName("ignored client label");
        assertThat(BookingIdempotency.requestHash(request)).isEqualTo(first);
        request.setContactPhone("0000000001");
        assertThat(BookingIdempotency.requestHash(request)).isNotEqualTo(first);
    }
}
