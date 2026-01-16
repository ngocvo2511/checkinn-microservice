package com.example.bookingservice.integration.loyalty;

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
    private String userId;
    private Long pointsToUse;
    private String bookingId;
    private String description;
}
