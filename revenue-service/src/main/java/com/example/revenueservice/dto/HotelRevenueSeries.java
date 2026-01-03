package com.example.revenueservice.dto;

public record HotelRevenueSeries(
        String hotelId,
        String hotelName,
        RevenueResponse revenue
) {}
