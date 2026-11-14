package com.example.bookingservice.booking.service;
import static com.example.bookingservice.contract.TestIds.*;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.integration.hotel.HotelAvailabilityClient;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.*;
import com.example.bookingservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaidReservationServiceTest {
    final BookingRepository bookings=mock(BookingRepository.class);
    final PaymentRepository payments=mock(PaymentRepository.class);
    final HotelAvailabilityClient inventory=mock(HotelAvailabilityClient.class);
    final BookingService bookingService=mock(BookingService.class);
    final PaidReservationService service=new PaidReservationService(bookings,payments,inventory,mock(com.example.bookingservice.messaging.HotelEventPublisher.class),bookingService);
    Booking b; Payment p;
    @BeforeEach void setup() {
        b=Booking.builder().id(id("booking")).holdId(id("hold")).status(BookingStatus.PENDING_PAYMENT)
            .paidAmount(BigDecimal.TEN).totalAmount(BigDecimal.TEN).paidReservationStatus("V1_PENDING").paidReservationPaymentId(id("receipt"))
            .paidReservationNextAttemptAt(LocalDateTime.now().minusSeconds(1)).checkInDate(LocalDate.now().plusDays(1))
            .checkOutDate(LocalDate.now().plusDays(2)).items(List.of()).build();
        p=Payment.builder().id(id("receipt")).bookingId(id("booking")).method(PaymentMethod.VNPAY).status(PaymentStatus.COMPLETED)
            .paidAt(LocalDateTime.now()).amount(BigDecimal.TEN).build();
        when(bookings.findByIdForUpdate(id("booking"))).thenReturn(Optional.of(b));when(payments.findById(id("receipt"))).thenReturn(Optional.of(p));
    }
    @Test void confirmsOriginalStockAndQueuesPointsWithoutMutatingCashReceipt() {
        var event=service.process(id("booking"));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);assertThat(b.getPaidReservationStatus()).isEqualTo("V1_CONFIRMED");
        assertThat(event.getBookingStatus()).isEqualTo("CONFIRMED");assertThat(b.getPaidAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);verify(bookingService).settleLoyaltyPoints(b);
        service.process(id("booking"));verify(inventory,times(1)).confirmHold(id("hold"));verify(payments,never()).save(any());
    }
    @Test void transportUnknownPreservesMoneyAndDurableIntent() {
        doThrow(new org.springframework.web.client.ResourceAccessException("synthetic timeout")).when(inventory).confirmHold(id("hold"));
        assertThat(service.process(id("booking"))).isNull();assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(b.getPaidAmount()).isEqualByComparingTo(BigDecimal.TEN);assertThat(b.getPaidReservationStatus()).isEqualTo("V1_PENDING");
        assertThat(b.getPaidReservationNextAttemptAt()).isAfter(LocalDateTime.now());verifyNoInteractions(bookingService);
    }
    @Test void expiredOrMissingHoldNeedsReviewWithoutDiscardingMoneyOrBuyingReplacementRooms() {
        doThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.CONFLICT)).when(inventory).confirmHold(id("hold"));
        service.process(id("booking"));assertThat(b.getPaidReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");
        assertThat(b.getPaidAmount()).isEqualByComparingTo(BigDecimal.TEN);verify(inventory,never()).holdRooms(any());verifyNoInteractions(bookingService);
    }
    @Test void cancelledSourceWinsAndReleaseIsIdempotentWithoutInventingRefund() {
        b.setStatus(BookingStatus.CANCELLED);assertThat(service.process(id("booking"))).isNull();service.process(id("booking"));
        verify(inventory,times(1)).cancelReservation(id("hold"));verify(inventory,never()).confirmHold(any());
        assertThat(b.getPaidReservationStatus()).isEqualTo("V1_RELEASED");assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(b.getPaidAmount()).isEqualByComparingTo(BigDecimal.TEN);verifyNoInteractions(bookingService);
    }
    @Test void historicalRowsAreNotPromotedAutomatically() {
        b.setPaidReservationStatus(null);assertThat(service.process(id("booking"))).isNull();verifyNoInteractions(inventory,payments,bookingService);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"0","20"})
    void ambiguousReceivedMoneyNeedsReviewBeforeInventoryMutation(String amount) {
        b.setPaidAmount(new BigDecimal(amount));service.process(id("booking"));assertThat(b.getPaidReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");verifyNoInteractions(inventory,bookingService);
    }
    @Test void receiptMustBelongToThisBooking() {
        p.setBookingId(id("other-booking"));service.process(id("booking"));assertThat(b.getPaidReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");verifyNoInteractions(inventory);
    }
    @Test void missingHoldNeverAllocatesNewStock() {
        b.setHoldId(null);service.process(id("booking"));assertThat(b.getPaidReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");verifyNoInteractions(inventory);
    }
    @Test void localCompletionFailureIsNotSwallowedAsRemoteSuccess() {
        doThrow(new IllegalStateException("synthetic local failure")).when(bookingService).settleLoyaltyPoints(b);
        assertThatThrownBy(()->service.process(id("booking"))).isInstanceOf(IllegalStateException.class);
    }
}
