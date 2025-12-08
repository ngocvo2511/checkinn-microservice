package com.example.hotelservice.Room.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomTypeListItemResponse(
        UUID id,
        String name,
        BigDecimal basePrice,
        Boolean isActive,
        String thumbnailUrl
) {}

