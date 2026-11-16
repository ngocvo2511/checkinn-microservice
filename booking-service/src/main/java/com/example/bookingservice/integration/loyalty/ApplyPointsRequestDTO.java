package com.example.bookingservice.integration.loyalty;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplyPointsRequestDTO {
    private UUID userId;
    private Long pointsToUse;
    private UUID bookingId;
    private String description;
    private BigDecimal expectedDiscountAmount;

    public ApplyPointsRequestDTO(UUID userId, Long pointsToUse, UUID bookingId, String description) {
        this(userId, pointsToUse, bookingId, description, null);
    }
}
