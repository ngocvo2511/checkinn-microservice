package com.example.bookingservice.security;

import static com.example.bookingservice.contract.UuidContract.*;
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
    private final JwtService jwt;

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;

    public boolean isAdmin(Authentication actor) {
        return hasRole(actor, "ADMIN");
    }

    public boolean isSelf(Authentication actor, String userId) {
        return actor != null && actor.isAuthenticated() && valid(actor.getName()) && required(actor.getName()).equals(required(userId));
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
            var headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(jwt.generateInventoryServiceToken());
            Map<?, ?> hotel = restTemplate.exchange(hotelServiceUrl + "/internal/hotels/{id}/owner", org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers), Map.class, hotelId).getBody();
            return hotel != null && isSelf(actor, String.valueOf(hotel.get("ownerId")));
        } catch (RestClientException | IllegalArgumentException ex) {
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
        java.util.UUID bookingId = payments.findById(required(paymentId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"))
                .getBookingId();
        return canRead(actor, bookingId);
    }

    private Booking requireBooking(String bookingId) {
        return bookings.findById(required(bookingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    // REST typed values and String protobuf boundary share the same authorization decisions.
    public boolean isSelf(Authentication a, java.util.UUID id) { return isSelf(a, text(id)); }
    public boolean canListUser(Authentication a, java.util.UUID id) { return canListUser(a, text(id)); }
    public boolean canManageHotel(Authentication a, java.util.UUID id) { return canManageHotel(a, text(id)); }
    public boolean canRead(Authentication a, java.util.UUID id) { return canRead(a, text(id)); }
    public boolean canManage(Authentication a, java.util.UUID id) { return canManage(a, text(id)); }
    public boolean canPay(Authentication a, java.util.UUID id) { return canPay(a, text(id)); }
    public boolean canReadPayment(Authentication a, java.util.UUID id) { return canReadPayment(a, text(id)); }

    private boolean hasRole(Authentication actor, String role) {
        return actor != null && actor.isAuthenticated() && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }
}
