package com.example.bookingservice.messaging.event;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class BookingStatusEvent {
    String bookingId;
    String hotelId;
    String roomTypeId;
    LocalDate checkInDate;
    LocalDate checkOutDate;
    int nights;
    int rooms;
    String bookingStatus;
    LocalDateTime eventAt;
}
