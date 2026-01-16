package com.example.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyPointsResponse {
    private UUID id;
    private UUID userId;
    private Long totalPoints;      // Tổng điểm đã tích lũy
    private Long usedPoints;       // Tổng điểm đã sử dụng
    private Long availablePoints;  // Điểm sẵn sàng để dùng
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
