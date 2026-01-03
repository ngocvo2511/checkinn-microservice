package com.example.revenueservice.messaging.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingStatusEvent(
        String bookingId,
        String hotelId,
        String roomTypeId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        int rooms,
        String bookingStatus,
        LocalDateTime eventAt
) {}
