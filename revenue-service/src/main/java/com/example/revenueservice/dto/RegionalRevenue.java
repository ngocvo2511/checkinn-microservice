package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record RegionalRevenue(
        String region,
        BigDecimal totalRevenue,
        long hotelCount,
        BigDecimal averageRevenue
) {}
