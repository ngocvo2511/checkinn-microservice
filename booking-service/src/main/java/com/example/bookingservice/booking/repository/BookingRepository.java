package com.example.bookingservice.booking.repository;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {
    @Query("SELECT b.id FROM Booking b WHERE b.onsiteReservationStatus = 'V1_PENDING' AND b.onsiteNextAttemptAt <= ?1 ORDER BY b.onsiteNextAttemptAt, b.id")
    List<String> findDueOnsite(LocalDateTime now, org.springframework.data.domain.Pageable page);
    java.util.Optional<Booking> findByIdempotencyKeyHash(String keyHash);

    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(cast(?1 as text), 0)) as text)", nativeQuery = true)
    String lockCreationCommand(String keyHash);
    @Query("SELECT b.id FROM Booking b WHERE b.loyaltyStatus = 'PENDING' AND b.loyaltyNextAttemptAt <= ?1 ORDER BY b.loyaltyNextAttemptAt, b.id")
    List<String> findDueLoyalty(LocalDateTime now, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = ?1")
    java.util.Optional<Booking> findByIdForUpdate(String id);
    @Query("SELECT b.id FROM Booking b WHERE b.status = ?1")
    List<String> findIdsByStatus(BookingStatus status);
    List<Booking> findByUserId(String userId);
    List<Booking> findByHotelId(String hotelId);
    List<Booking> findByStatus(BookingStatus status);
    List<Booking> findByCheckInDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("""
    SELECT COUNT(b)
    FROM Booking b
    WHERE b.createdAt >= :startOfDay
      AND b.createdAt < :startOfNextDay
""")
    long countBookingsCreatedToday(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("startOfNextDay") LocalDateTime startOfNextDay
    );

}
