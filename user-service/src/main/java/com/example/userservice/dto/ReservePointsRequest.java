package com.example.userservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservePointsRequest(UUID userId, String bookingId, Long pointsToUse, BigDecimal totalAmount, Instant holdExpiresAt) {}
