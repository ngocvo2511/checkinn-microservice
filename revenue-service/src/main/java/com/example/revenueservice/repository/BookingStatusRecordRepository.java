package com.example.revenueservice.repository;

import com.example.revenueservice.entity.BookingStatusRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingStatusRecordRepository extends JpaRepository<BookingStatusRecord, UUID> {
    Optional<BookingStatusRecord> findByBookingId(String bookingId);
    List<BookingStatusRecord> findByHotelIdAndEventAtBetween(String hotelId, LocalDateTime from, LocalDateTime to);
    List<BookingStatusRecord> findByEventAtBetween(LocalDateTime from, LocalDateTime to);
    @Query("""
    SELECT b FROM BookingStatusRecord b
    WHERE b.checkOutDate > :from
      AND b.checkInDate < :to
      AND b.bookingStatus IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT')
    """)
    List<BookingStatusRecord> findActiveBookings(
            LocalDate from,
            LocalDate to
    );

    @Query("""
    SELECT b
    FROM BookingStatusRecord b
    WHERE b.hotelId = :hotelId
      AND b.checkOutDate > :from
      AND b.checkInDate < :to
      AND b.bookingStatus IN ('CONFIRMED','CHECKED_IN','CHECKED_OUT')
    """)
    List<BookingStatusRecord> findActiveBookingsByHotel(
            String hotelId,
            LocalDate from,
            LocalDate to
    );


}
