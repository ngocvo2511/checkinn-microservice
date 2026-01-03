package com.example.revenueservice.dto;

public record BookingStatusBreakdown(
        long confirmed,
        long cancelled,
        long noShow,
        long total
) {}
