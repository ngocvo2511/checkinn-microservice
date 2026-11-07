package com.example.hotelservice.Room.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomTypeCreateRequest(
        @NotNull UUID hotelId,
        @NotBlank @jakarta.validation.constraints.Size(max=100) String name,
        @NotNull @jakarta.validation.constraints.Positive BigDecimal basePrice,
        @NotNull @Valid CapacityDto capacity,
        @NotNull @jakarta.validation.constraints.Min(1) Integer roomAmount,
        List<String> amenities,
        String description
) {
    public RoomTypeCreateRequest(UUID hotelId, String name, BigDecimal basePrice, CapacityDto capacity,
                                 Integer roomAmount, List<String> amenities) {
        this(hotelId, name, basePrice, capacity, roomAmount, amenities, null);
    }
}

