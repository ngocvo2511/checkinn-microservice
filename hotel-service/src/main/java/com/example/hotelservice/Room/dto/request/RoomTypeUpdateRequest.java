package com.example.hotelservice.Room.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record RoomTypeUpdateRequest(
        String name,
        BigDecimal basePrice,
        CapacityDto capacity,
        List<String> amenities,
        Boolean isActive,
        Integer roomAmount,
        String description
) {
    public RoomTypeUpdateRequest(String name, BigDecimal basePrice, CapacityDto capacity, List<String> amenities,
                                 Boolean isActive, Integer roomAmount) {
        this(name, basePrice, capacity, amenities, isActive, roomAmount, null);
    }
}

