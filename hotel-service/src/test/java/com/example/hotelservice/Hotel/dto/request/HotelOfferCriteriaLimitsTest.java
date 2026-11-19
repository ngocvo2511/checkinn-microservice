package com.example.hotelservice.Hotel.dto.request;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.hotelservice.validation.StayLimits;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** F-9B-3 boundaries: check-in from today (business zone), <= 30 nights, rooms 1..20, adults 1..40, children 0..40. */
class HotelOfferCriteriaLimitsTest {
    static final LocalDate TODAY = LocalDate.of(2030, 1, 10);

    static HotelOfferCriteria criteria(LocalDate in, LocalDate out, Integer rooms, Integer adults, Integer children) {
        return new HotelOfferCriteria(null, null, null, "Fixture", in, out, rooms, adults, children, null, null, null, null);
    }

    static void ok(HotelOfferCriteria c) { assertThatCode(() -> c.validate(TODAY)).doesNotThrowAnyException(); }
    static void rejected(HotelOfferCriteria c, String message) {
        assertThatThrownBy(() -> c.validate(TODAY)).isInstanceOf(IllegalArgumentException.class).hasMessage(message);
    }

    @Test void stayDates() {
        ok(criteria(TODAY, TODAY.plusDays(1), null, null, null));
        ok(criteria(TODAY, TODAY.plusDays(30), null, null, null));
        ok(criteria(null, null, null, null, null)); // dates stay optional
        rejected(criteria(TODAY.minusDays(1), TODAY.plusDays(1), null, null, null), "Check-in date cannot be in the past");
        rejected(criteria(TODAY, TODAY.plusDays(31), null, null, null), "Stay cannot exceed 30 nights");
        rejected(criteria(TODAY, TODAY, null, null, null), "Invalid stay dates");
        rejected(criteria(TODAY, null, null, null, null), "Invalid stay dates");
    }

    @Test void roomAndGuestBounds() {
        ok(criteria(null, null, 20, 40, 40));
        ok(criteria(null, null, 1, 1, 0));
        rejected(criteria(null, null, 21, null, null), "Rooms must be between 1 and 20");
        rejected(criteria(null, null, 0, null, null), "Rooms must be between 1 and 20");
        rejected(criteria(null, null, null, 41, null), "Adults must be between 1 and 40");
        rejected(criteria(null, null, null, 0, null), "Adults must be between 1 and 40");
        rejected(criteria(null, null, null, null, 41), "Children must be between 0 and 40");
        rejected(criteria(null, null, null, null, -1), "Children must be between 0 and 40");
    }

    @Test void todayIsTheBusinessZoneCivilDate() {
        // 18:30 UTC on 9 Jan is already 10 Jan in Asia/Ho_Chi_Minh (UTC+7).
        var utc = ZoneId.of("UTC");
        var lateEvening = ZonedDateTime.of(2030, 1, 9, 18, 30, 0, 0, utc).toInstant();
        var justBefore = ZonedDateTime.of(2030, 1, 9, 16, 59, 59, 0, utc).toInstant();
        org.assertj.core.api.Assertions.assertThat(StayLimits.today(java.time.Clock.fixed(lateEvening, utc))).isEqualTo(TODAY);
        org.assertj.core.api.Assertions.assertThat(StayLimits.today(java.time.Clock.fixed(justBefore, utc))).isEqualTo(TODAY.minusDays(1));
    }
}
