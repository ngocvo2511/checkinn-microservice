package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "points_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointsTransaction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String transactionType;  // EARN (tích điểm từ booking), USE (sử dụng điểm)

    @Column(nullable = false)
    private Long points;  // Số điểm được cộng/trừ

    private String bookingId;  // ID của booking liên quan

    @Column(nullable = false, length = 500)
    private String description;  // Mô tả giao dịch

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
