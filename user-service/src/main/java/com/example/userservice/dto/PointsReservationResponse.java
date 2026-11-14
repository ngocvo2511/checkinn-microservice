package com.example.userservice.dto;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

public record PointsReservationResponse(@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.example.userservice.contract.UuidContract.Deserializer.class) UUID bookingId, long pointsToUse, BigDecimal discountAmount,
                                        BigDecimal redemptionRate, Instant holdExpiresAt, String state) {}
