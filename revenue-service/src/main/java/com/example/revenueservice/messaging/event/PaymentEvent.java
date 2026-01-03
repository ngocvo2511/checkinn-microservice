package com.example.revenueservice.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentEvent(
        String bookingId,
        String hotelId,
        String roomTypeId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        int rooms,
        BigDecimal amount,
        String paymentStatus,
        String paymentMethod,
        LocalDateTime paidAt,
        LocalDateTime eventAt
) {}
