package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Source-row work queue: remote idempotent reservation starts only after local payment commit. */
@Service
@RequiredArgsConstructor
public class OnsiteReservationService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final HotelAvailabilityClient inventory;

    public record Requested(String bookingId) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingStatusEvent process(String id) {
        Booking b = bookings.findByIdForUpdate(id).orElse(null);
        if (b == null || !"V1_PENDING".equals(b.getOnsiteReservationStatus())
                || b.getOnsiteNextAttemptAt() == null || b.getOnsiteNextAttemptAt().isAfter(LocalDateTime.now())) return null;
        b.setOnsiteAttempts(Math.addExact(b.getOnsiteAttempts() == null ? 0 : b.getOnsiteAttempts(), 1));
        if (b.getHoldId() == null || b.getHoldId().isBlank()) { review(b, "MISSING_HOLD"); return null; }
        try {
            if (b.getStatus() == BookingStatus.CANCELLED || b.getStatus() == BookingStatus.NO_SHOW) {
                inventory.cancelReservation(b.getHoldId());
                b.setOnsiteReservationStatus("V1_RELEASED");
            } else {
                var payment = payments.findFirstByBookingIdOrderByCreatedAtDesc(id).orElse(null);
                if ((b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.PENDING_PAYMENT)
                        || payment == null || payment.getMethod() != PaymentMethod.HOTEL
                        || payment.getStatus() != PaymentStatus.ONSITE_PENDING
                        || b.getPaidAmount() == null || b.getPaidAmount().signum() != 0
                        || b.getTotalAmount() == null || payment.getAmount() == null
                        || payment.getAmount().compareTo(b.getTotalAmount()) != 0) {
                    review(b, "SOURCE_RECONCILIATION_REQUIRED"); return null;
                }
                // A remote CONFIRMED hold replays successfully even after its original HELD deadline.
                inventory.confirmHold(b.getHoldId());
                b.setStatus(BookingStatus.CONFIRMED);
                b.setOnsiteReservationStatus("V1_CONFIRMED");
            }
            b.setOnsiteNextAttemptAt(null);
            b.setOnsiteLastError(null);
            return event(b);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 409) review(b, "INVENTORY_RECONCILIATION_REQUIRED");
            else retry(b);
        } catch (RuntimeException ex) { retry(b); }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(String id) {
        bookings.findByIdForUpdate(id).filter(b -> "V1_PENDING".equals(b.getOnsiteReservationStatus())).ifPresent(b -> { retry(b); b.setOnsiteLastError("LOCAL_TRANSACTION_FAILED"); });
    }

    private void retry(Booking b) {
        b.setOnsiteLastError("INVENTORY_UNAVAILABLE");
        b.setOnsiteNextAttemptAt(LocalDateTime.now().plusSeconds(5));
    }
    private void review(Booking b, String code) {
        b.setOnsiteReservationStatus("V1_REVIEW_REQUIRED"); b.setOnsiteLastError(code); b.setOnsiteNextAttemptAt(null);
    }
    private BookingStatusEvent event(Booking b) {
        return BookingStatusEvent.builder().bookingId(b.getId()).hotelId(b.getHotelId())
                .roomTypeId(b.getItems() == null || b.getItems().isEmpty() ? null : b.getItems().get(0).getRoomTypeId())
                .rooms(b.getItems() == null ? 0 : b.getItems().stream().mapToInt(i -> i.getQuantity()).sum())
                .checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate())
                .nights((int) ChronoUnit.DAYS.between(b.getCheckInDate(), b.getCheckOutDate()))
                .bookingStatus(b.getStatus().name()).eventAt(LocalDateTime.now()).build();
    }
}
