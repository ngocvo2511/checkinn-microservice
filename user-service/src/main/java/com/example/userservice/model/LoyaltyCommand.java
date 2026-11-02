package com.example.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loyalty_commands")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LoyaltyCommand {
    @Id @Column(length = 160) private String commandKey;
    @Column(nullable = false, length = 128) private String bookingId;
    @Column(nullable = false, length = 16) private String commandType;
    @Column(nullable = false) private UUID userId;
    private BigDecimal inputAmount;
    private Long inputPoints;
    private BigDecimal resultAmount;
    @Column(nullable = false) private Long resultPoints;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void created() { createdAt = LocalDateTime.now(); }
}
