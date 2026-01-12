package com.example.bookingservice.messaging.event;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class PaymentEvent {
    String bookingId;
    String customerId;
    String hotelId;
    String hotelName;
    String roomTypeId;
    String roomType;
    String userId;
    String userName;
    String userEmail;
    LocalDate checkInDate;
    LocalDate checkOutDate;
    int nights;
    int rooms;
    int numberOfGuests;
    BigDecimal amount;
    String paymentStatus;
    String paymentMethod;
    LocalDateTime paidAt;
    LocalDateTime eventAt;
}
