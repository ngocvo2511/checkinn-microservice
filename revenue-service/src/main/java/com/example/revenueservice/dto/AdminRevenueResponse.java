package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminRevenueResponse(
        BigDecimal totalRevenue,
        List<RevenuePoint> data,
        List<HotelRevenueSeries> hotelBreakdown
) {}
