package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record RoomTypeRevenue(
        String roomTypeName,
        BigDecimal totalRevenue,
        long bookingCount,
        BigDecimal averagePrice
) {}
