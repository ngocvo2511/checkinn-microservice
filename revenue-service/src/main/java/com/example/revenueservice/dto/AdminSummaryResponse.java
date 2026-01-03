package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminSummaryResponse(
        BigDecimal totalRevenue,
        BigDecimal totalCommission,
        long totalBookings,
        double systemCancellationRate,
        List<TopHotelItem> topHotels,
        List<RegionalRevenue> regionalBreakdown,
        GrowthMetrics monthlyGrowth,
        GrowthMetrics yearlyGrowth,
        CustomerAnalytics customerAnalytics
) {}
