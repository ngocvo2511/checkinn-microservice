package com.example.hotelservice.Room.dto.request;

public record CapacityDto(
        int adults,
        int children,
        String bedType,
        Integer roomSize,
        Boolean breakfastIncluded,
        Integer breakfastQuantity
) {}

