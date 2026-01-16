package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loyalty_points")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyPoints {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Long totalPoints;  // Tổng điểm đã tích lũy

    @Column(nullable = false)
    private Long usedPoints;   // Tổng điểm đã sử dụng

    @Column(nullable = false)
    private Long availablePoints;  // Điểm sẵn sàng để dùng

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalPoints == null) {
            totalPoints = 0L;
        }
        if (usedPoints == null) {
            usedPoints = 0L;
        }
        if (availablePoints == null) {
            availablePoints = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // availablePoints = totalPoints - usedPoints
        this.availablePoints = this.totalPoints - this.usedPoints;
    }
}
