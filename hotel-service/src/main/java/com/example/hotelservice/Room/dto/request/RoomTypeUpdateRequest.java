package com.example.hotelservice.Room.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record RoomTypeUpdateRequest(
        @jakarta.validation.constraints.Pattern(regexp="(?s).*\\S.*", message="must not be blank") @jakarta.validation.constraints.Size(max=100) String name,
        @jakarta.validation.constraints.Positive BigDecimal basePrice,
        @jakarta.validation.Valid CapacityDto capacity,
        List<String> amenities,
        Boolean isActive,
        @jakarta.validation.constraints.Min(1) Integer roomAmount,
        String description
) {
    public RoomTypeUpdateRequest(String name, BigDecimal basePrice, CapacityDto capacity, List<String> amenities,
                                 Boolean isActive, Integer roomAmount) {
        this(name, basePrice, capacity, amenities, isActive, roomAmount, null);
    }
}

