package com.example.bookingservice.integration.loyalty;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservePointsRequestDTO(String userId, String bookingId, Long pointsToUse, BigDecimal totalAmount, Instant holdExpiresAt) {}
