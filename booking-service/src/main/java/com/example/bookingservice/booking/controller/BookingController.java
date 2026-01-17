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
            log.info("Creating booking with request: {}", request);
            BookingResponse booking = bookingService.createBooking(request);
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
            BookingResponse booking = bookingService.getBooking(id);
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable String userId) {
        List<BookingResponse> bookings = bookingService.getUserBookings(userId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<List<BookingResponse>> getHotelBookings(@PathVariable String hotelId) {
        List<BookingResponse> bookings = bookingService.getHotelBookings(hotelId);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<BookingResponse> updateBookingStatus(
            @PathVariable String id,
            @RequestParam BookingStatus status) {
        try {
            BookingResponse booking = bookingService.updateBookingStatus(id, status);
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable String id) {
        try {
            bookingService.cancelBooking(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/count/total")
    public ResponseEntity<Long> getTotalBookingsCount() {
        long count = bookingService.getTotalBookingsCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/count/today")
    public ResponseEntity<Long> getTodayBookingsCount() {
        long count = bookingService.getTodayBookingsCount();
        return ResponseEntity.ok(count);
    }
}
