package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyPointsRequest {
    private UUID userId;
    private Long pointsToUse;  // Số điểm muốn sử dụng
    private @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.example.userservice.contract.UuidContract.Deserializer.class) UUID bookingId;  // ID của booking
    private String description;  // Mô tả sử dụng
    private java.math.BigDecimal expectedDiscountAmount; // Trusted booking quote snapshot; optional for legacy callers.

    public ApplyPointsRequest(UUID userId, Long pointsToUse, @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=com.example.userservice.contract.UuidContract.Deserializer.class) UUID bookingId, String description) {
        this(userId, pointsToUse, bookingId, description, null);
    }
}
