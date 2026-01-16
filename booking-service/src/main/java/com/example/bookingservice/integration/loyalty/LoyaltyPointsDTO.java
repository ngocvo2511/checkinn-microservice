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
public class LoyaltyPointsDTO {
    private Long totalPoints;
    private Long usedPoints;
    private Long availablePoints;
}
