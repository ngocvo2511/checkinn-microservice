package com.example.bookingservice.payment.repository;

import java.util.UUID;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @org.springframework.data.jpa.repository.Query("SELECT p.bookingId FROM Payment p WHERE p.vnpayOrderId = ?1")
    Optional<UUID> findBookingIdByVnpayOrderId(String orderId);
    @org.springframework.data.jpa.repository.Query("SELECT p.bookingId FROM Payment p WHERE p.id = ?1")
    Optional<UUID> findBookingIdById(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.id = ?1")
    Optional<Payment> findByIdForUpdate(UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.vnpayOrderId = ?1")
    Optional<Payment> findByVnpayOrderIdForUpdate(String orderId);
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);
    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(UUID bookingId, PaymentStatus status);
    List<Payment> findByStatus(PaymentStatus status);
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByVnpayOrderId(String vnpayOrderId);
    Optional<Payment> findByVnpayTransactionNo(String vnpayTransactionNo);
}
