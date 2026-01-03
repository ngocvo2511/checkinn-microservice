package com.example.revenueservice.repository;

import com.example.revenueservice.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, UUID> {
    Optional<PaymentRecord> findByBookingId(String bookingId);
    List<PaymentRecord> findByHotelIdAndEventAtBetween(String hotelId, LocalDateTime from, LocalDateTime to);
    List<PaymentRecord> findByEventAtBetween(LocalDateTime from, LocalDateTime to);
}
