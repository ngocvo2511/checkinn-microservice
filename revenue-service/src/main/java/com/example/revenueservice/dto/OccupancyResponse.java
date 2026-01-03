package com.example.revenueservice.dto;

public record OccupancyResponse(
        double occupancyRate,
        long roomNights,
        long capacityNights
) {}
