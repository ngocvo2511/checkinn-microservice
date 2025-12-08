package com.example.hotelservice.Hotel.dto.response;

import java.util.UUID;

public record HotelListItemResponse(
        UUID id,
        String name,
        Short starRating,
        Boolean isActive,
        String approvedStatus,
        String city,
        String country,
        String thumbnailUrl
) {}

