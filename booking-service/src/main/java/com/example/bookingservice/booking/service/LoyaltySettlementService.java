package com.example.bookingservice.booking.service;

import java.util.UUID;
import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.loyalty.ApplyPointsRequestDTO;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoyaltySettlementService {
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final LoyaltyPointsClient points;
    @Value("${loyalty.settlement.retry-seconds:30}")
    private long retrySeconds = 30;

    /** Runs only against committed source state. All writers lock booking before points. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID bookingId) {
        Booking booking = bookings.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !"PENDING".equals(booking.getLoyaltyStatus())
                || booking.getLoyaltyNextAttemptAt() == null || booking.getLoyaltyNextAttemptAt().isAfter(LocalDateTime.now())) return;
        booking.setLoyaltyAttempts(Math.addExact(booking.getLoyaltyAttempts() == null ? 0 : booking.getLoyaltyAttempts(), 1));
        try {
            if (booking.getStatus() == BookingStatus.CANCELLED
                    || (booking.getStatus() == BookingStatus.NO_SHOW && booking.getOnsiteReservationStatus() != null
                        && booking.getPaidAmount() != null && booking.getPaidAmount().signum() == 0)) {
                points.refundPoints(booking.getUserId(), bookingId);
                booking.setLoyaltyStatus("REVERSED");
            } else {
                if (!settleable(booking)) {
                    review(booking, "SOURCE_RECONCILIATION_REQUIRED");
                    return;
                }
                if (booking.getUsedPoints() != null && booking.getUsedPoints() > 0) {
                    var discount = points.usePoints(new ApplyPointsRequestDTO(booking.getUserId(), booking.getUsedPoints(),
                            bookingId, "Points for booking " + bookingId, booking.getPointsDiscountAmount()));
                    if (discount == null || booking.getPointsDiscountAmount() == null
                            || discount.compareTo(booking.getPointsDiscountAmount()) != 0) {
                        review(booking, "INVALID_USE_RECEIPT");
                        return;
                    }
                }
                // Always replay EARN: its durable receipt is authoritative even if a local marker was lost.
                var receipt = points.earnPoints(booking.getUserId(), bookingId, booking.getTotalAmount());
                if (receipt == null || receipt.getAwardedPoints() == null || receipt.getAwardedPoints() < 0) {
                    review(booking, "INVALID_EARN_RECEIPT");
                    return;
                }
                booking.setEarnedPoints(receipt.getAwardedPoints());
                booking.setLoyaltyStatus("SETTLED");
            }
            booking.setLoyaltyLastError(null);
            booking.setLoyaltyNextAttemptAt(null);
        } catch (feign.FeignException ex) {
            if (ex.status() == 409 || ex.status() == 400) review(booking, "LEDGER_RECONCILIATION_REQUIRED");
            else retry(booking, "LOYALTY_UNAVAILABLE");
        } catch (RuntimeException ex) {
            retry(booking, "LOYALTY_UNAVAILABLE");
        }
        // Dirty-checking commits markers with earnedPoints; a commit failure keeps the old PENDING intent.
    }

    private boolean settleable(Booking booking) {
        if (booking.getStatus() == null || !Set.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN,
                BookingStatus.CHECKED_OUT, BookingStatus.NO_SHOW).contains(booking.getStatus())
                || booking.getTotalAmount() == null || booking.getTotalAmount().signum() <= 0
                || booking.getPaidAmount() == null || booking.getPaidAmount().compareTo(booking.getTotalAmount()) != 0) return false;
        return payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(booking.getId(), PaymentStatus.COMPLETED)
                .filter(payment -> payment.getAmount() != null && payment.getAmount().compareTo(booking.getTotalAmount()) == 0).isPresent();
    }

    /** The previous processing transaction may have failed at commit, after its method returned. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferAfterFailure(UUID bookingId) {
        bookings.findByIdForUpdate(bookingId).filter(booking -> "PENDING".equals(booking.getLoyaltyStatus()))
                .ifPresent(booking -> {
                    booking.setLoyaltyAttempts(Math.addExact(booking.getLoyaltyAttempts() == null ? 0 : booking.getLoyaltyAttempts(), 1));
                    retry(booking, "LOCAL_TRANSACTION_FAILED");
                });
    }

    private void retry(Booking booking, String code) {
        booking.setLoyaltyLastError(code);
        booking.setLoyaltyNextAttemptAt(LocalDateTime.now().plusSeconds(Math.max(1, retrySeconds)));
    }
    private void review(Booking booking, String code) {
        booking.setLoyaltyStatus("REVIEW_REQUIRED");
        booking.setLoyaltyLastError(code);
        booking.setLoyaltyNextAttemptAt(null);
    }
}
