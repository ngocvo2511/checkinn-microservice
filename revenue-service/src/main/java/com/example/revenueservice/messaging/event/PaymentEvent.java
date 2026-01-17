package com.example.revenueservice.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentEvent(
        String bookingId,
        String customerId,
        String hotelId,
        String hotelName,
        String roomTypeId,
        String roomType,
        String userId,
        String userName,
        String userEmail,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int nights,
        int rooms,
        int numberOfGuests,
        BigDecimal amount,
        String paymentStatus,
        String paymentMethod,
        LocalDateTime paidAt,
        LocalDateTime eventAt
) {}
