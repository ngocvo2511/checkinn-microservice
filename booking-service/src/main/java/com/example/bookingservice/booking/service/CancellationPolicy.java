package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
public class CancellationPolicy {
    public static final String VERSION = "STANDARD_24H_V1";
    // V1 makes the existing 14:00 check-in convention explicit for Vietnamese properties.
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Clock clock;
    public CancellationPolicy() { this(Clock.systemUTC()); }
    public CancellationPolicy(Clock clock) { this.clock = clock; }
    public record Details(String version, String checkInTime, String timeZone, int freeCancellationHours) {}
    public Details details() { return new Details(VERSION, "14:00", ZONE.getId(), 24); }
    public LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    public Instant checkInAt(LocalDate date) { return date.atTime(14, 0).atZone(ZONE).toInstant(); }
    public void snapshot(Booking booking, String acceptedVersion) {
        if (acceptedVersion == null || acceptedVersion.isBlank()) return; // legacy caller: manual review only
        if (!VERSION.equals(acceptedVersion)) throw new IllegalArgumentException("Unsupported cancellation policy version");
        booking.setCancellationPolicyVersion(VERSION);
        booking.setCheckInAt(checkInAt(booking.getCheckInDate()));
        booking.setFreeCancellationUntil(booking.getCheckInAt().minus(Duration.ofHours(24)));
    }
    public boolean freeCancellationAllowed(Booking booking) {
        return VERSION.equals(booking.getCancellationPolicyVersion()) && booking.getFreeCancellationUntil() != null
                && !clock.instant().isAfter(booking.getFreeCancellationUntil());
    }
    public boolean beforeCheckIn(Booking booking) {
        Instant checkIn = booking.getCheckInAt() != null ? booking.getCheckInAt() : checkInAt(booking.getCheckInDate());
        return clock.instant().isBefore(checkIn);
    }
}
