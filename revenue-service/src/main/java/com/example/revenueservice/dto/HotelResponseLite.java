package com.example.revenueservice.dto;

import java.util.UUID;

public record HotelResponseLite(
        UUID id,
        UUID ownerId,
        String name
) {}
