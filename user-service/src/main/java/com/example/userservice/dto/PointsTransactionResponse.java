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
public class PointsTransactionResponse {
    private UUID id;
    private UUID userId;
    private String transactionType;  // EARN, USE
    private Long points;
    private String bookingId;
    private String description;
    private LocalDateTime createdAt;
}
