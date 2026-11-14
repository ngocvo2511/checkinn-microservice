package com.example.bookingservice.integration.loyalty;

import java.util.UUID;
import java.math.BigDecimal;

public record PointsQuoteRequest(UUID userId, Long pointsToUse, BigDecimal totalAmount) {}
