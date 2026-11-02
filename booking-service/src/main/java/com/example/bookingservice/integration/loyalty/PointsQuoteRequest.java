package com.example.bookingservice.integration.loyalty;

import java.math.BigDecimal;

public record PointsQuoteRequest(String userId, Long pointsToUse, BigDecimal totalAmount) {}
