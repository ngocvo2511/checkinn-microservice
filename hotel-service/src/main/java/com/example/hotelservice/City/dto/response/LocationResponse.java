package com.example.hotelservice.City.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
        UUID id,
        String name,
        String code,
        Double latitude,
        Double longitude,
        Integer hotelCount,
        Instant createdAt,
        String parentName,
        String parentCode,
        String type  // "PROVINCE" hoặc "CITY"
) {}
