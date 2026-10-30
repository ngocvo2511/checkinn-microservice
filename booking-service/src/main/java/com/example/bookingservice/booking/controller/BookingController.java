package com.example.bookingservice.booking.controller;

import com.example.bookingservice.booking.dto.BookingResponse;
import com.example.bookingservice.booking.dto.CreateBookingRequest;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private final com.example.bookingservice.booking.service.CancellationPolicy cancellationPolicy;

    @GetMapping("/cancellation-policy")
    public com.example.bookingservice.booking.service.CancellationPolicy.Details cancellationPolicy() {
        return cancellationPolicy.details();
    }

    public record CancellationReview(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 1000) String reason) {}
    @PostMapping("/{id}/cancel-reviewed")
    @PreAuthorize("@bookingAccess.isAdmin(authentication)")
    public ResponseEntity<Void> cancelReviewed(@PathVariable String id, @jakarta.validation.Valid @RequestBody CancellationReview review, Authentication actor) {
        try {
            bookingService.cancelBookingReviewed(id, actor.getName(), review.reason());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    @PreAuthorize("@bookingAccess.canCreate(authentication, #request.userId)")
    public ResponseEntity<?> createBooking(@RequestBody CreateBookingRequest request, Authentication actor) {
        request.setUserId(actor.getName());
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
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
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
    @PreAuthorize("@bookingAccess.canRead(authentication, #id)")
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
    @PreAuthorize("@bookingAccess.canListUser(authentication, #userId)")
    public ResponseEntity<List<BookingResponse>> getUserBookings(@PathVariable String userId) {
        log.info("[GET_USER_BOOKINGS] Request received - userId: {}", userId);
        List<BookingResponse> bookings = bookingService.getUserBookings(userId);
        log.info("[GET_USER_BOOKINGS] Returning {} bookings for userId: {}", bookings.size(), userId);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/hotel/{hotelId}")
    @PreAuthorize("@bookingAccess.canManageHotel(authentication, #hotelId)")
    public ResponseEntity<List<BookingResponse>> getHotelBookings(@PathVariable String hotelId) {
        log.info("[GET_HOTEL_BOOKINGS] Request received - hotelId: {}", hotelId);
        List<BookingResponse> bookings = bookingService.getHotelBookings(hotelId);
        log.info("[GET_HOTEL_BOOKINGS] Returning {} bookings for hotelId: {}", bookings.size(), hotelId);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@bookingAccess.canManage(authentication, #id)")
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
    @PreAuthorize("@bookingAccess.canRead(authentication, #id)")
    public ResponseEntity<Void> cancelBooking(@PathVariable String id, Authentication actor) {
        try {
            log.info("[CANCEL_BOOKING] Request received - bookingId: {}", id);
            bookingService.cancelBooking(id, actor.getName());
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
