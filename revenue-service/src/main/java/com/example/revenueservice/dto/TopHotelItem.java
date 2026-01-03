package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record TopHotelItem(
        String hotelId,
        String hotelName,
        String city,
        BigDecimal totalRevenue,
        long bookingCount,
        double occupancyRate
) {}
