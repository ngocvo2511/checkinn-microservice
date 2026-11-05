package com.example.bookingservice.booking.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.bookingservice.booking.enums.BookingStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "bookings_onsite_due", columnList = "onsite_reservation_status,onsite_next_attempt_at"),
        @Index(name = "bookings_loyalty_due", columnList = "loyalty_status,loyalty_next_attempt_at"),
        @Index(name = "bookings_idempotency_key_hash_unique", columnList = "idempotency_key_hash", unique = true)})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    private String id;

    @Column(length = 64, updatable = false)
    private String idempotencyKeyHash;

    @Column(length = 64, updatable = false)
    private String idempotencyRequestHash;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String hotelId;

    @Column(nullable = false)
    private String hotelName;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Column(nullable = false)
    private Integer adults;

    @Column(nullable = false)
    private Integer children;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private BigDecimal paidAmount;

    private String voucherCode;

    private BigDecimal voucherDiscount;

    private Long earnedPoints;  // Điểm tích lũy từ booking này

    private Long usedPoints;    // Điểm đã sử dụng để giảm giá

    private BigDecimal pointsDiscountAmount;  // Số tiền giảm từ điểm

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String contactEmail;

    @Column(nullable = false)
    private String contactPhone;

    private String specialRequests;

    @Column(name = "hold_id")
    private String holdId;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "cancellation_policy_version", length = 64)
    private String cancellationPolicyVersion;
    @Column(name = "check_in_at")
    private java.time.Instant checkInAt;
    @Column(name = "free_cancellation_until")
    private java.time.Instant freeCancellationUntil;

    // Durable intent, committed atomically with the payment/cancellation outcome.
    // NULL identifies historical rows; no automatic financial backfill.
    @Column(length = 24)
    private String loyaltyStatus;
    private LocalDateTime loyaltyNextAttemptAt;
    private Integer loyaltyAttempts;
    @Column(length = 64)
    private String loyaltyLastError;

    // NULL is a legacy booking; never infer/backfill a historical reservation policy.
    @Column(length = 24)
    private String onsiteReservationStatus;
    private LocalDateTime onsiteNextAttemptAt;
    private Integer onsiteAttempts;
    @Column(length = 64)
    private String onsiteLastError;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingItem> items;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = BookingStatus.PENDING;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
