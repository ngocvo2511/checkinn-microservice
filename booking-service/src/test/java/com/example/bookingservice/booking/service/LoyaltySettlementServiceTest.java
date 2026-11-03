package com.example.bookingservice.booking.service;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsDTO;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LoyaltySettlementServiceTest {
    BookingRepository bookings = mock(BookingRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    LoyaltyPointsClient points = mock(LoyaltyPointsClient.class);
    LoyaltySettlementService service = new LoyaltySettlementService(bookings, payments, points);
    Booking booking;

    @BeforeEach void fixture() {
        booking = Booking.builder().id("booking").userId("guest").status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("95000")).paidAmount(new BigDecimal("95000"))
                .usedPoints(10L).pointsDiscountAmount(new BigDecimal("5000"))
                .loyaltyStatus("PENDING").loyaltyNextAttemptAt(LocalDateTime.now().minusSeconds(1)).build();
        when(bookings.findByIdForUpdate("booking")).thenReturn(Optional.of(booking));
        when(payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc("booking", PaymentStatus.COMPLETED))
                .thenReturn(Optional.of(Payment.builder().amount(new BigDecimal("95000")).build()));
        when(points.usePoints(any())).thenReturn(new BigDecimal("5000"));
        var award = new LoyaltyPointsDTO(); award.setAwardedPoints(9L);
        when(points.earnPoints("guest", "booking", new BigDecimal("95000"))).thenReturn(award);
    }

    @Test void committedSourceSettlesUseBeforeEarnAndSkipsCompletedWork() {
        service.process("booking");
        var order = inOrder(points);
        order.verify(points).usePoints(argThat(r -> r.getPointsToUse() == 10 && r.getExpectedDiscountAmount().compareTo(new BigDecimal("5000")) == 0));
        order.verify(points).earnPoints("guest", "booking", new BigDecimal("95000"));
        assertThat(booking.getLoyaltyStatus()).isEqualTo("SETTLED");
        assertThat(booking.getEarnedPoints()).isEqualTo(9L);
        service.process("booking");
        verify(points, times(1)).usePoints(any());
    }
    @Test void useOutageRetainsDurableIntentAndDoesNotEarn() {
        when(points.usePoints(any())).thenThrow(new IllegalStateException("timeout"));
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("PENDING");
        assertThat(booking.getLoyaltyNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(booking.getEarnedPoints()).isNull();
        verify(points, never()).earnPoints(any(), any(), any());
    }
    @Test void earnUnknownOutcomeRetriesBothReceiptsAfterBackoff() {
        when(points.earnPoints(any(), any(), any())).thenThrow(new IllegalStateException("lost response"));
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("PENDING");
        service.process("booking"); // not due
        verify(points, times(1)).usePoints(any());
        var award = new LoyaltyPointsDTO(); award.setAwardedPoints(9L);
        doReturn(award).when(points).earnPoints(any(), any(), any());
        booking.setLoyaltyNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("SETTLED");
        verify(points, times(2)).usePoints(any());
    }
    @Test void staleSettlementTaskOnCancelledBookingReversesInsteadOfEarning() {
        booking.setStatus(BookingStatus.CANCELLED);
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("REVERSED");
        verify(points).refundPoints("guest", "booking");
        verify(points, never()).usePoints(any());
        verify(points, never()).earnPoints(any(), any(), any());
    }
    @Test void cancellationUnknownOutcomeRemainsRetryable() {
        booking.setStatus(BookingStatus.CANCELLED);
        doThrow(new IllegalStateException("lost reversal response")).when(points).refundPoints(any(), any());
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("PENDING");
        assertThat(booking.getLoyaltyAttempts()).isEqualTo(1);
    }
    @Test void invalidUseReceiptNeedsReviewAndNeverEarns() {
        when(points.usePoints(any())).thenReturn(BigDecimal.ONE);
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("REVIEW_REQUIRED");
        verify(points, never()).earnPoints(any(), any(), any());
    }
    @Test void missingCommittedPaymentCannotGrantPoints() {
        when(payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("REVIEW_REQUIRED");
        verify(points, never()).usePoints(any());
    }
    @Test void historicalRowsWithoutIntentAreNotBackfilled() {
        booking.setLoyaltyStatus(null);
        service.process("booking");
        verify(points, never()).usePoints(any());
    }
    @Test void conflictRequiresReviewInsteadOfRetryingOrGrantingAnEarning() {
        var conflict = mock(feign.FeignException.class);
        when(conflict.status()).thenReturn(409);
        when(points.usePoints(any())).thenThrow(conflict);
        service.process("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(booking.getLoyaltyNextAttemptAt()).isNull();
        verify(points, never()).earnPoints(any(), any(), any());
    }
    @Test void failedLocalCompletionIsBackedOffButCannotOverwriteConcurrentCompletion() {
        service.deferAfterFailure("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("PENDING");
        assertThat(booking.getLoyaltyNextAttemptAt()).isAfter(LocalDateTime.now());
        assertThat(booking.getLoyaltyLastError()).isEqualTo("LOCAL_TRANSACTION_FAILED");
        booking.setLoyaltyStatus("SETTLED");
        booking.setLoyaltyLastError(null);
        service.deferAfterFailure("booking");
        assertThat(booking.getLoyaltyStatus()).isEqualTo("SETTLED");
        assertThat(booking.getLoyaltyLastError()).isNull();
    }
}
