package com.example.bookingservice.integration.loyalty;

import java.math.BigDecimal;
import java.time.Instant;

public record PointsReservationDTO(String bookingId, long pointsToUse, BigDecimal discountAmount,
                                   BigDecimal redemptionRate, Instant holdExpiresAt, String state) {}
