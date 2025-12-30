package com.example.bookingservice.booking.repository;

import com.example.bookingservice.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, String> {
    List<BookingItem> findByBookingId(String bookingId);
    List<BookingItem> findByRoomTypeId(String roomTypeId);
}
