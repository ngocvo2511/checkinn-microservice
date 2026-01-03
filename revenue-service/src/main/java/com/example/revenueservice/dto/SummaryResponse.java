package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record SummaryResponse(
        BigDecimal totalRevenue,
        BigDecimal averageRevenue,
        double occupancyRate,
        double cancellationRate
) {}
