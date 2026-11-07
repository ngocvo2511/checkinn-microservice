package com.example.hotelservice.Room.dto.request;

public record CapacityDto(
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1) Integer adults,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer children,
        String bedType,
        @jakarta.validation.constraints.Min(0) Integer roomSize,
        Boolean breakfastIncluded,
        @jakarta.validation.constraints.Min(0) Integer breakfastQuantity
) {}

