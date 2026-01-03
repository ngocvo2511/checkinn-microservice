package com.example.revenueservice.dto;

import java.math.BigDecimal;

public record HotelSummaryItem(
        String hotelId,
        String hotelName,
        BigDecimal totalRevenue,
        BigDecimal averageRevenue,
        double occupancyRate,
        double cancellationRate,
        BigDecimal platformCommission,
        BigDecimal netRevenue,
        BookingStatusBreakdown bookingStatusBreakdown,
        java.util.List<RoomTypeRevenue> roomTypeRevenue
) {

    public HotelSummaryItem(String hotelId, String hotelName, BigDecimal totalRevenue, 
                            BigDecimal averageRevenue, double occupancyRate, double cancellationRate) {
        this(hotelId, hotelName, totalRevenue, averageRevenue, occupancyRate, cancellationRate,
             null, null, null, null);
    }
}
