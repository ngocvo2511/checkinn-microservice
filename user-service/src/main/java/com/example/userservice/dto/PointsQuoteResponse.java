package com.example.userservice.dto;

import java.math.BigDecimal;

public record PointsQuoteResponse(long pointsToUse, BigDecimal discountAmount, BigDecimal redemptionRate) {}
