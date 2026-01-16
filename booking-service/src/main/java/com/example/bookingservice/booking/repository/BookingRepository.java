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
