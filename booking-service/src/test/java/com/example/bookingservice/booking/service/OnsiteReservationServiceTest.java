package com.example.bookingservice.booking.service;

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

class OnsiteReservationServiceTest {
    final BookingRepository bookings = mock(BookingRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final HotelAvailabilityClient inventory = mock(HotelAvailabilityClient.class);
    final OnsiteReservationService service = new OnsiteReservationService(bookings, payments, inventory, mock(com.example.bookingservice.messaging.HotelEventPublisher.class));
    Booking b;
    @BeforeEach void setup() {
        b = Booking.builder().id("booking").holdId("hold").status(BookingStatus.PENDING_PAYMENT)
                .paidAmount(BigDecimal.ZERO).totalAmount(BigDecimal.TEN).onsiteReservationStatus("V1_PENDING")
                .onsiteNextAttemptAt(LocalDateTime.now().minusSeconds(1)).checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2)).items(List.of()).build();
        when(bookings.findByIdForUpdate("booking")).thenReturn(Optional.of(b));
        when(payments.findFirstByBookingIdOrderByCreatedAtDesc("booking")).thenReturn(Optional.of(Payment.builder()
                .method(PaymentMethod.HOTEL).status(PaymentStatus.ONSITE_PENDING).amount(BigDecimal.TEN).build()));
    }
    @Test void confirmsStockWithoutReceivingMoneyOrSettlingLoyalty() {
        var event = service.process("booking");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getOnsiteReservationStatus()).isEqualTo("V1_CONFIRMED");
        assertThat(b.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.getLoyaltyStatus()).isNull();
        assertThat(event.getBookingStatus()).isEqualTo("CONFIRMED");
        service.process("booking"); verify(inventory, times(1)).confirmHold("hold");
    }
    @Test void unknownOutcomeKeepsDurableIntentAndNeverFalselyConfirmsOrCancels() {
        doThrow(new org.springframework.web.client.ResourceAccessException("timeout")).when(inventory).confirmHold("hold");
        assertThat(service.process("booking")).isNull();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(b.getOnsiteReservationStatus()).isEqualTo("V1_PENDING");
        assertThat(b.getOnsiteNextAttemptAt()).isAfter(LocalDateTime.now());
    }
    @Test void terminalHoldRequiresReviewWithoutDiscardingAcceptedPaymentChoice() {
        doThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.CONFLICT)).when(inventory).confirmHold("hold");
        service.process("booking");
        assertThat(b.getOnsiteReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=BookingStatus.class,names={"CANCELLED","NO_SHOW"})
    void cancellationWinsOverQueuedConfirmationAndReleaseIsIdempotent(BookingStatus terminal) {
        b.setStatus(terminal); assertThat(service.process("booking")).isNull(); service.process("booking");
        verify(inventory, times(1)).cancelReservation("hold"); verify(inventory, never()).confirmHold(any());
        assertThat(b.getOnsiteReservationStatus()).isEqualTo("V1_RELEASED");
    }
    @Test void legacyRowsAreNeverAutomaticallyPromoted() {
        b.setOnsiteReservationStatus(null); assertThat(service.process("booking")).isNull();
        verifyNoInteractions(inventory, payments);
    }
    @Test void missingHoldOrInconsistentAmountCannotClaimReservation() {
        b.setHoldId(null); service.process("booking"); assertThat(b.getOnsiteReservationStatus()).isEqualTo("V1_REVIEW_REQUIRED");
        verifyNoInteractions(inventory);
    }
}
