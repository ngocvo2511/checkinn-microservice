package com.example.bookingservice.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.enums.PaymentMethod;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(
    name = "payments",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_vnpay_order_id", columnNames = "vnpay_order_id"),
        @UniqueConstraint(name = "uk_payments_vnpay_transaction_no", columnNames = "vnpay_transaction_no")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String transactionId;

    @Column(name = "vnpay_order_id")
    private String vnpayOrderId;

    // Exact signed pay request date (GMT+7), required by querydr/refund.
    // Legacy rows remain NULL; paidAt/transactionOccurredAt are different facts.
    @Column(name = "vnpay_create_date", length = 14, updatable = false)
    private String vnpayCreateDate;

    @Column(name = "vnpay_response_code")
    private String vnpayResponseCode;

    @Column(name = "vnpay_transaction_no")
    private String vnpayTransactionNo;

    private LocalDateTime paidAt;

    // Proven transaction time; legacy paidAt is a receipt-processing clock and cannot be backfilled.
    @Column(name = "transaction_occurred_at")
    private java.time.Instant transactionOccurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
