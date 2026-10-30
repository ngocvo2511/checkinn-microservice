package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class CancellationPolicyTest {
    private final Instant deadline = Instant.parse("2027-01-09T07:00:00Z");
    private Booking booking() {
        Booking booking = Booking.builder().checkInDate(LocalDate.of(2027, 1, 10)).build();
        new CancellationPolicy().snapshot(booking, CancellationPolicy.VERSION);
        return booking;
    }
    @Test void checkInAndDeadlineUseVietnamTimezoneAndTwentyFourHours() {
        Booking booking = booking();
        assertThat(booking.getCheckInAt()).isEqualTo(Instant.parse("2027-01-10T07:00:00Z"));
        assertThat(booking.getFreeCancellationUntil()).isEqualTo(deadline);
    }
    @Test void exactlyTwentyFourHoursBeforeCheckInIsEligible() {
        assertThat(new CancellationPolicy(Clock.fixed(deadline, ZoneOffset.UTC)).freeCancellationAllowed(booking())).isTrue();
    }
    @Test void oneSecondAfterDeadlineRequiresReview() {
        assertThat(new CancellationPolicy(Clock.fixed(deadline.plusSeconds(1), ZoneOffset.UTC)).freeCancellationAllowed(booking())).isFalse();
    }
    @Test void legacyBookingDoesNotAcquirePolicyRetroactively() {
        Booking booking = Booking.builder().checkInDate(LocalDate.of(2027, 1, 10)).build();
        var policy = new CancellationPolicy(Clock.fixed(deadline.minusSeconds(3600), ZoneOffset.UTC));
        policy.snapshot(booking, null);
        assertThat(booking.getCancellationPolicyVersion()).isNull();
        assertThat(policy.freeCancellationAllowed(booking)).isFalse();
    }
    @Test void unknownVersionIsRejected() {
        assertThatThrownBy(() -> new CancellationPolicy().snapshot(booking(), "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void businessDateUsesVietnamTimezoneWhenUtcIsStillPreviousDay() {
        var policy = new CancellationPolicy(Clock.fixed(Instant.parse("2027-01-09T18:00:00Z"), ZoneOffset.UTC));
        assertThat(policy.today()).isEqualTo(LocalDate.of(2027, 1, 10));
    }
}
