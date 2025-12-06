package com.example.hotelservice.Hotel.dto.request;


public record HotelUpdateRequest(
        String name,
        String description,
        Short starRating,
        HotelAddressDto address,
        Boolean isActive
) {}

