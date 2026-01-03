package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomTypeCapacityResponse(
        UUID id,
        UUID hotelId,
        String name,
        BigDecimal basePrice,
        Object capacity,
        List<String> amenities,
        Boolean isActive,
        Integer totalRooms,
        Instant createdAt,
        Instant updatedAt,
        Object mediaAssets
) {}
