package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerSummaryResponse(
        BigDecimal totalRevenue,
        BigDecimal totalNetRevenue,
        BigDecimal totalCommission,
        List<HotelSummaryItem> hotels,
        BigDecimal commissionRate
) {
    
    public OwnerSummaryResponse(BigDecimal totalRevenue, List<HotelSummaryItem> hotels) {
        this(totalRevenue, null, null, hotels,null);
    }
    public OwnerSummaryResponse(BigDecimal totalRevenue,BigDecimal totalNetRevenue,BigDecimal totalCommission,List<HotelSummaryItem> hotels) {
        this(totalRevenue,totalNetRevenue,totalCommission,hotels,null);
    }
}
