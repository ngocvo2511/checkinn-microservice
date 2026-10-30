package com.example.bookingservice.security;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Resource checks shared by REST and gRPC entry points. Never trusts identity headers. */
@Component("bookingAccess")
@RequiredArgsConstructor
public class BookingAccess {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final RestTemplate restTemplate;

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public boolean isAdmin(Authentication actor) {
        return hasRole(actor, "ADMIN");
    }

    public boolean isSelf(Authentication actor, String userId) {
        return actor != null && actor.isAuthenticated() && actor.getName().equals(userId);
    }

    public boolean canCreate(Authentication actor, String requestedUserId) {
        return actor != null && actor.isAuthenticated()
                && (requestedUserId == null || requestedUserId.isBlank() || isSelf(actor, requestedUserId));
    }

    public boolean canListUser(Authentication actor, String userId) {
        return isAdmin(actor) || isSelf(actor, userId);
    }

    public boolean canManageHotel(Authentication actor, String hotelId) {
        if (isAdmin(actor)) return true;
        if (!hasRole(actor, "OWNER")) return false;
        try {
            Map<?, ?> hotel = restTemplate.getForObject(hotelServiceUrl + "/hotels/{id}", Map.class, hotelId);
            return hotel != null && isSelf(actor, String.valueOf(hotel.get("ownerId")));
        } catch (RestClientException ex) {
            // A dependency outage must never grant access or look like an empty booking list.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel ownership lookup unavailable");
        }
    }

    public boolean canRead(Authentication actor, String bookingId) {
        Booking booking = requireBooking(bookingId);
        return isSelf(actor, booking.getUserId()) || canManageHotel(actor, booking.getHotelId());
    }

    public boolean canManage(Authentication actor, String bookingId) {
        return canManageHotel(actor, requireBooking(bookingId).getHotelId());
    }

    public boolean canPay(Authentication actor, String bookingId) {
        return isSelf(actor, requireBooking(bookingId).getUserId());
    }

    public boolean canReadPayment(Authentication actor, String paymentId) {
        String bookingId = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                .getBookingId();
        return canRead(actor, bookingId);
    }

    private Booking requireBooking(String bookingId) {
        return bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private boolean hasRole(Authentication actor, String role) {
        return actor != null && actor.isAuthenticated() && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
