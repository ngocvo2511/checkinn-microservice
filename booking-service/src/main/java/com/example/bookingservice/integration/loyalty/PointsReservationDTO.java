package com.example.bookingservice.integration.loyalty;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

public record PointsReservationDTO(UUID bookingId, long pointsToUse, BigDecimal discountAmount,
                                   BigDecimal redemptionRate, Instant holdExpiresAt, String state) {}
