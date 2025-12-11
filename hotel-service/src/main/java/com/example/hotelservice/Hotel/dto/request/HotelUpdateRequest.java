package com.example.hotelservice.Hotel.dto.request;

import java.util.UUID;

public record HotelUpdateRequest(
        String name,
        String description,
        Short starRating,
        UUID cityId,
        HotelAddressDto address,
        Boolean isActive
) {}

