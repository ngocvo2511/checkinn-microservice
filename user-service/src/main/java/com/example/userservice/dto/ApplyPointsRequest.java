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
    private String bookingId;  // ID của booking
    private String description;  // Mô tả sử dụng
}
