package com.example.bookingservice.booking.controller;

import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Slf4j
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<?> createBooking(@RequestBody CreateBookingRequest request) {
        try {
            log.info("[CREATE_BOOKING] Request received - userId: {}, hotelId: {}, checkIn: {}, checkOut: {}",
                request.getUserId(), request.getHotelId(), request.getCheckInDate(), request.getCheckOutDate());
            BookingResponse booking = bookingService.createBooking(request);
            log.info("[CREATE_BOOKING] Booking created - bookingId: {}, userId: {}, hotelId: {}",
                booking.getId(), booking.getUserId(), booking.getHotelId());
            return ResponseEntity.status(HttpStatus.CREATED).body(booking);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating booking: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Validation Error");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error creating booking: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal Server Error");
            error.put("message", e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable String id) {
        try {
            log.info("[GET_BOOKING] Request received - bookingId: {}", id);
            BookingResponse booking = bookingService.getBooking(id);
            log.info("[GET_BOOKING] Booking fetched - bookingId: {}, status: {}", booking.getId(), booking.getStatus());
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            log.warn("[GET_BOOKING] Booking not found - bookingId: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable String userId) {
        log.info("[GET_USER_BOOKINGS] Request received - userId: {}", userId);
        List<BookingResponse> bookings = bookingService.getUserBookings(userId);
        log.info("[GET_USER_BOOKINGS] Returning {} bookings for userId: {}", bookings.size(), userId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<List<BookingResponse>> getHotelBookings(@PathVariable String hotelId) {
        log.info("[GET_HOTEL_BOOKINGS] Request received - hotelId: {}", hotelId);
        List<BookingResponse> bookings = bookingService.getHotelBookings(hotelId);
        log.info("[GET_HOTEL_BOOKINGS] Returning {} bookings for hotelId: {}", bookings.size(), hotelId);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(
            @PathVariable String id,
            @RequestParam BookingStatus status) {
        try {
            log.info("[UPDATE_BOOKING_STATUS] Request received - bookingId: {}, status: {}", id, status);
            BookingResponse booking = bookingService.updateBookingStatus(id, status);
            log.info("[UPDATE_BOOKING_STATUS] Updated - bookingId: {}, status: {}", booking.getId(), booking.getStatus());
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            log.warn("[UPDATE_BOOKING_STATUS] Bad request - bookingId: {}, status: {}", id, status);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable String id) {
        try {
            log.info("[CANCEL_BOOKING] Request received - bookingId: {}", id);
            bookingService.cancelBooking(id);
            log.info("[CANCEL_BOOKING] Cancelled - bookingId: {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("[CANCEL_BOOKING] Bad request - bookingId: {}", id);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/count/total")
    public ResponseEntity<Long> getTotalBookingsCount() {
        log.info("[COUNT_TOTAL] Request received");
        long count = bookingService.getTotalBookingsCount();
        log.info("[COUNT_TOTAL] Total bookings: {}", count);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/count/today")
    public ResponseEntity<Long> getTodayBookingsCount() {
        log.info("[COUNT_TODAY] Request received");
        long count = bookingService.getTodayBookingsCount();
        log.info("[COUNT_TODAY] Today's bookings: {}", count);
        return ResponseEntity.ok(count);
    }
}
