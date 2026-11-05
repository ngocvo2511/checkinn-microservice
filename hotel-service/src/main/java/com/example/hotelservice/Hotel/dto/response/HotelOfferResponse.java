package com.example.hotelservice.Hotel.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One room type satisfies all constraints; prices are base prices per room/night. */
public record HotelOfferResponse(HotelResponse hotel, RoomOffer offer) {
    public record RoomOffer(UUID roomTypeId, int quantity, BigDecimal unitPrice, BigDecimal totalPerNight,
            Integer availableRooms, LocalDate checkIn, LocalDate checkOut, Instant checkedAt) {}
}
