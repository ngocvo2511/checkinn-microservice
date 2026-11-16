package com.example.userservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservePointsRequest(UUID userId, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.example.userservice.contract.UuidContract.Deserializer.class) UUID bookingId, Long pointsToUse, BigDecimal totalAmount, Instant holdExpiresAt) {}
