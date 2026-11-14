package com.example.bookingservice.booking.repository;

import java.util.UUID;
import com.example.bookingservice.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {
    List<BookingItem> findByBookingId(UUID bookingId);
    List<BookingItem> findByRoomTypeId(UUID roomTypeId);
}
