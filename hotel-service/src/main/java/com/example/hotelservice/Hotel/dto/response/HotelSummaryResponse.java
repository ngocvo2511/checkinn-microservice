package com.example.hotelservice.Hotel.dto.response;

import java.util.UUID;

public record HotelSummaryResponse(
        UUID id,
        String name,
        String cityName,
        String provinceName,
        Short starRating,
        String coverImageUrl
) {}
