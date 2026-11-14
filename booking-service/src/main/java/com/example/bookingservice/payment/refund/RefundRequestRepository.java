package com.example.bookingservice.payment.refund;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {
    Optional<RefundRequest> findByPaymentId(UUID paymentId);
}
