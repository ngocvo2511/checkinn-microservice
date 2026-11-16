package com.example.bookingservice.booking.service;

import static com.example.bookingservice.contract.UuidContract.*;
import java.util.UUID;
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

/** Reconcile committed verified payment receipts with their original idempotent inventory hold. */
@Service
@RequiredArgsConstructor
public class PaidReservationService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final HotelAvailabilityClient inventory;
    private final com.example.bookingservice.messaging.HotelEventPublisher events;
    private final BookingService bookingService;
    public record Requested(UUID bookingId) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BookingStatusEvent process(UUID id) {
        Booking b=bookings.findByIdForUpdate(id).orElse(null);
        if (b==null || !"V1_PENDING".equals(b.getPaidReservationStatus()) || b.getPaidReservationNextAttemptAt()==null
                || b.getPaidReservationNextAttemptAt().isAfter(LocalDateTime.now())) return null;
        b.setPaidReservationAttempts(Math.addExact(b.getPaidReservationAttempts()==null?0:b.getPaidReservationAttempts(),1));
        if (b.getHoldId()==null) {review(b,"MISSING_HOLD");return null;}
        boolean cancelled=b.getStatus()==BookingStatus.CANCELLED;
        if (!cancelled) {
            var payment=b.getPaidReservationPaymentId()==null?null:payments.findById(b.getPaidReservationPaymentId()).orElse(null);
            if ((b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.PENDING_PAYMENT)
                    || payment==null || !id.equals(payment.getBookingId()) || payment.getMethod()!=PaymentMethod.VNPAY
                    || payment.getStatus()!=PaymentStatus.COMPLETED || payment.getPaidAt()==null
                    || b.getPaidAmount()==null || b.getPaidAmount().signum()<=0 || b.getTotalAmount()==null || payment.getAmount()==null
                    || payment.getAmount().compareTo(b.getTotalAmount())!=0 || payment.getAmount().compareTo(b.getPaidAmount())!=0) {
                review(b,"SOURCE_RECONCILIATION_REQUIRED");return null;
            }
        }
        try {
            if (cancelled) inventory.cancelReservation(b.getHoldId());
            else inventory.confirmHold(b.getHoldId());
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            if (ex.getStatusCode().value()==400 || ex.getStatusCode().value()==404 || ex.getStatusCode().value()==409) review(b,"INVENTORY_RECONCILIATION_REQUIRED");
            else retry(b);
            return null;
        } catch (RuntimeException ex) {retry(b);return null;}
        // Local completion failures propagate and roll back the processor transaction.
        // The coordinator then defers the still-durable intent; remote effects replay idempotently.
        if (cancelled) b.setPaidReservationStatus("V1_RELEASED");
        else {
            b.setStatus(BookingStatus.CONFIRMED);
            b.setPaidReservationStatus("V1_CONFIRMED");
            bookingService.settleLoyaltyPoints(b);
        }
        b.setPaidReservationNextAttemptAt(null);b.setPaidReservationLastError(null);
        // Cancellation was already published when its source decision committed.
        if(cancelled)return null;
        var snapshot=event(b);events.publishBookingStatus(snapshot);return snapshot;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void defer(UUID id) {
        bookings.findByIdForUpdate(id).filter(b -> "V1_PENDING".equals(b.getPaidReservationStatus())).ifPresent(b -> {
            retry(b);b.setPaidReservationLastError("LOCAL_TRANSACTION_FAILED");
        });
    }
    private void retry(Booking b) {b.setPaidReservationNextAttemptAt(LocalDateTime.now().plusSeconds(5));b.setPaidReservationLastError("INVENTORY_UNAVAILABLE");}
    private void review(Booking b,String code) {b.setPaidReservationStatus("V1_REVIEW_REQUIRED");b.setPaidReservationNextAttemptAt(null);b.setPaidReservationLastError(code);}
    private BookingStatusEvent event(Booking b) {
        return BookingStatusEvent.builder().bookingId(text(b.getId())).hotelId(text(b.getHotelId()))
            .roomTypeId(b.getItems()==null || b.getItems().isEmpty()?null:text(b.getItems().get(0).getRoomTypeId()))
            .rooms(b.getItems()==null?0:b.getItems().stream().mapToInt(i -> i.getQuantity()).sum())
            .checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate())
            .nights((int)ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()))
            .bookingStatus(b.getStatus().name()).eventAt(LocalDateTime.now()).build();
    }
}
