package com.example.hotelservice.City.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String name,
        Double latitude,
        Double longitude,
        Integer hotelCount,
        Instant createdAt,
        String parentName,
        String type  // "PROVINCE" hoặc "CITY"
) {}
