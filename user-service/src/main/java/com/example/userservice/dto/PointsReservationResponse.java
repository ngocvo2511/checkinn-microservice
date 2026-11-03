package com.example.userservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PointsReservationResponse(String bookingId, long pointsToUse, BigDecimal discountAmount,
                                        BigDecimal redemptionRate, Instant holdExpiresAt, String state) {}
