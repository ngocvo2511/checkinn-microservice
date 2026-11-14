package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.dto.CreateBookingRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Constants independently computed with SHA-256/MD5 UUIDv3, then verified before conversion. */
class UuidLegacyGoldenTest {
    @Test void preservesLegacyBytesAndFingerprints() {
        var request = CreateBookingRequest.builder()
            .userId("11111111-1111-4111-8111-111111111111")
            .hotelId("22222222-2222-4222-8222-222222222222")
            .idempotencyKey("ABCDEFAB-CDEF-4ABC-8DEF-ABCDEFABCDEF").build();
        var hash = BookingIdempotency.keyHash(request);
        assertThat(hash).isEqualTo("768586b8602ad6f6316db9120e2295c7851bdea2054b75dc4876db264b8de242");
        assertThat(BookingIdempotency.bookingId(hash).toString()).isEqualTo("d3616870-e674-310a-b1ee-6a282806ec94");
        assertThat(BookingIdempotency.holdCommand(hash).toString()).isEqualTo("de0936d6-9eec-3ef7-a53c-91cb97898ab0");
        assertThat(BookingIdempotency.requestHash(request)).isEqualTo("34c9ca60917ecf61fcea5ce75c922562ec8c14217233bb91b009b66184afd6f4");
    }
}
