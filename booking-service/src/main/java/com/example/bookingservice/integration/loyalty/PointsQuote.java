package com.example.bookingservice.integration.loyalty;

import java.math.BigDecimal;

public record PointsQuote(long pointsToUse, BigDecimal discountAmount, BigDecimal redemptionRate) {}
