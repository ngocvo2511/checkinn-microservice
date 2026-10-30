package com.example.bookingservice.payment.refund;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund_requests", uniqueConstraints = @UniqueConstraint(name = "uk_refund_requests_payment_id", columnNames = "payment_id"))
@Getter @Setter
public class RefundRequest {
    public enum Status { REQUESTED, APPROVED_PENDING_EXECUTION }
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(name = "payment_id", nullable = false) private String paymentId;
    @Column(name = "booking_id", nullable = false) private String bookingId;
    @Column(nullable = false, precision = 38, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "approved_by") private String approvedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 64) private Status status;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "approved_at") private Instant approvedAt;
    @Version private long version;
}
