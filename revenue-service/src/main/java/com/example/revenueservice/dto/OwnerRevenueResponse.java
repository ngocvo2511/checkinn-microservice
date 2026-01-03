package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerRevenueResponse(
        BigDecimal totalRevenue,
        List<HotelRevenueSeries> hotels
) {}
