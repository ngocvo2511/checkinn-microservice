package com.example.revenueservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "payment_records")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Setter
    @Column(nullable = false)
    private String bookingId;

    @Setter
    @Column(nullable = false)
    private String hotelId;

    @Setter
    private String roomTypeId;

    @Setter
    @Column(nullable = false)
    private LocalDate checkInDate;

    @Setter
    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Setter
    @Column(nullable = false)
    private Integer nights;

    @Setter
    @Column(nullable = false)
    private Integer rooms;

    @Setter
    @Column(nullable = false)
    private BigDecimal amount;

    @Setter
    @Column(nullable = false)
    private String paymentStatus;

    @Setter
    @Column(nullable = false)
    private String paymentMethod;

    @Setter
    private LocalDateTime paidAt;

    @Setter
    @Column(nullable = false)
    private LocalDateTime eventAt;

    @Setter
    @Column(nullable = false)
    private LocalDateTime createdAt;

}
