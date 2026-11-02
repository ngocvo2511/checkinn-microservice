package com.example.userservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PointsQuoteRequest(UUID userId, Long pointsToUse, BigDecimal totalAmount) {}
