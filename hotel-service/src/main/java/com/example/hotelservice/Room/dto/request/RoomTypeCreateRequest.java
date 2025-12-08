package com.example.hotelservice.Room.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RoomTypeCreateRequest(
        @NotNull UUID hotelId,
        @NotBlank String name,
        @NotNull BigDecimal basePrice,
        @NotNull @Valid CapacityDto capacity,
        List<String> amenities
) {}

