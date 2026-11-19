package com.example.hotelservice.validation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Public search/availability limits (F-9B-3, product decision 18/11/2026).
 * "Today" is the civil date in the business time zone; a stay is counted in calendar nights.
 * Applies to public reads only; hold/booking admission rules are unchanged.
 */
public final class StayLimits {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int MAX_NIGHTS = 30;
    public static final int MAX_ROOMS = 20;
    public static final int MAX_ADULTS = 40;
    public static final int MAX_CHILDREN = 40;

    private StayLimits() {}

    public static LocalDate today() { return today(Clock.systemUTC()); }
    public static LocalDate today(Clock clock) { return LocalDate.now(clock.withZone(BUSINESS_ZONE)); }

    /** Both dates present: check-in not before today, check-out after check-in, at most {@link #MAX_NIGHTS} nights. */
    public static void requireStay(LocalDate checkIn, LocalDate checkOut, LocalDate today) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) throw new IllegalArgumentException("Invalid stay dates");
        if (checkIn.isBefore(today)) throw new IllegalArgumentException("Check-in date cannot be in the past");
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS)
            throw new IllegalArgumentException("Stay cannot exceed " + MAX_NIGHTS + " nights");
    }

    public static void requireRooms(int rooms) {
        if (rooms < 1 || rooms > MAX_ROOMS) throw new IllegalArgumentException("Rooms must be between 1 and " + MAX_ROOMS);
    }

    public static void requireGuests(int adults, int children) {
        if (adults < 1 || adults > MAX_ADULTS) throw new IllegalArgumentException("Adults must be between 1 and " + MAX_ADULTS);
        if (children < 0 || children > MAX_CHILDREN) throw new IllegalArgumentException("Children must be between 0 and " + MAX_CHILDREN);
    }
}
