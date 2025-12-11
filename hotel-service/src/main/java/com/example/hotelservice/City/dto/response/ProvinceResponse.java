package com.example.hotelservice.City.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ProvinceResponse(
        UUID id,
        String name,
        String code,
        Double latitude,
        Double longitude,
        Instant createdAt
) {}
