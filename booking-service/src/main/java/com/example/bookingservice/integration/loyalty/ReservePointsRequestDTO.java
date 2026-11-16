package com.example.bookingservice.integration.loyalty;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

public record ReservePointsRequestDTO(UUID userId, UUID bookingId, Long pointsToUse, BigDecimal totalAmount, Instant holdExpiresAt) {}
