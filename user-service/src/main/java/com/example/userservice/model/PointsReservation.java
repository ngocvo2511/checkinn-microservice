package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "points_reservations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PointsReservation {
    @Id @Column(length = 128) private String bookingId;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private Long requestedPoints;
    @Column(nullable = false) private BigDecimal totalAmount;
    @Column(nullable = false) private Long points;
    @Column(nullable = false) private BigDecimal discountAmount;
    @Column(nullable = false) private BigDecimal redemptionRate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PointsReservationState state;
    @Column(nullable = false) private Instant holdExpiresAt;
    @Column(nullable = false) private Instant nextCheckAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
}
