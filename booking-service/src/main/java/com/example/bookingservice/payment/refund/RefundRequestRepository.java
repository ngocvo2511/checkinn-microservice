package com.example.bookingservice.payment.refund;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {
    Optional<RefundRequest> findByPaymentId(String paymentId);
}
