package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record GrowthMetrics(
        BigDecimal currentPeriod,
        BigDecimal previousPeriod,
        double growthRate,
        BigDecimal growthAmount
) {}
