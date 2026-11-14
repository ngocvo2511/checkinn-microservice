package com.example.bookingservice.booking.service;
import static com.example.bookingservice.contract.TestIds.*;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingScheduledTaskTest {
    @Test void onsiteUnknownConfirmationMustNotExpireAsAnUnpaidHold() {
        var booking = Booking.builder().id(id("onsite")).status(BookingStatus.PENDING_PAYMENT).paidAmount(BigDecimal.ZERO)
                .holdExpiresAt(LocalDateTime.now().minusHours(1)).onsiteReservationStatus("V1_PENDING").build();
        var bookings = mock(BookingRepository.class); var payments = mock(PaymentRepository.class);
        when(bookings.findIdsByStatus(BookingStatus.PENDING_PAYMENT)).thenReturn(List.of(id("onsite")));
        when(bookings.findByIdForUpdate(id("onsite"))).thenReturn(Optional.of(booking));
        new BookingScheduledTask(bookings, payments, mock(com.example.bookingservice.messaging.HotelEventPublisher.class)).processExpiredPaymentHolds();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        org.mockito.Mockito.verifyNoInteractions(payments);
    }
    @Test void onsiteConfirmedBookingCannotBecomeNoShowOrCheckedOutByCalendar() {
        var booking = Booking.builder().id(id("onsite")).status(BookingStatus.CONFIRMED).paidAmount(BigDecimal.ZERO)
                .checkInDate(LocalDate.now().minusDays(3)).checkOutDate(LocalDate.now().minusDays(2))
                .onsiteReservationStatus("V1_CONFIRMED").build();
        var bookings = mock(BookingRepository.class); var payments = mock(PaymentRepository.class);
        when(bookings.findIdsByStatus(BookingStatus.CONFIRMED)).thenReturn(List.of(id("onsite")));
        when(bookings.findByIdForUpdate(id("onsite"))).thenReturn(Optional.of(booking));
        var jobs = new BookingScheduledTask(bookings, payments, mock(com.example.bookingservice.messaging.HotelEventPublisher.class));
        jobs.processAutoCheckOut(); jobs.processNoShow(); jobs.processAutoCheckIn();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        org.mockito.Mockito.verify(bookings, org.mockito.Mockito.never()).save(any());
    }

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private BookingScheduledTask bookingScheduledTask;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        bookingScheduledTask = new BookingScheduledTask(bookingRepository, paymentRepository, mock(com.example.bookingservice.messaging.HotelEventPublisher.class));
    }

    @Test
    void processExpiredPaymentHoldsCancelsExpiredBookingAndActivePayment() {
        Booking expiredBooking = Booking.builder()
                .id(id("booking-1"))
                .userId(id("user-1"))
                .hotelId(id("hotel-1"))
                .hotelName("Tripto Hotel")
                .checkInDate(LocalDate.now().plusDays(1))
                .checkOutDate(LocalDate.now().plusDays(2))
                .adults(2)
                .children(0)
                .status(BookingStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(1_000_000))
                .paidAmount(BigDecimal.ZERO)
                .contactName("Test User")
                .contactEmail("test@example.com")
                .contactPhone("0900000000")
                .holdId(id("hold-1"))
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Payment activePayment = Payment.builder()
                .id(id("payment-1"))
                .bookingId(id("booking-1"))
                .amount(BigDecimal.valueOf(1_000_000))
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .build();

        when(bookingRepository.findIdsByStatus(BookingStatus.PENDING)).thenReturn(List.of());
        when(bookingRepository.findIdsByStatus(BookingStatus.PENDING_PAYMENT)).thenReturn(List.of(id("booking-1")));
        when(bookingRepository.findByIdForUpdate(id("booking-1"))).thenReturn(Optional.of(expiredBooking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(id("booking-1"))).thenReturn(Optional.of(activePayment));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingScheduledTask.processExpiredPaymentHolds();

        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(activePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(bookingRepository).save(expiredBooking);
        verify(paymentRepository).save(activePayment);
    }
    @Test void receivedMoneyAwaitingInventoryReconciliationIsNotAutoCancelled() {
        Booking booking = Booking.builder().id(id("booking-1")).status(BookingStatus.PENDING_PAYMENT)
                .paidAmount(BigDecimal.TEN).holdExpiresAt(LocalDateTime.now().minusHours(1)).build();
        when(bookingRepository.findIdsByStatus(BookingStatus.PENDING)).thenReturn(List.of());
        when(bookingRepository.findIdsByStatus(BookingStatus.PENDING_PAYMENT)).thenReturn(List.of(id("booking-1")));
        when(bookingRepository.findByIdForUpdate(id("booking-1"))).thenReturn(Optional.of(booking));
        bookingScheduledTask.processExpiredPaymentHolds();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        org.mockito.Mockito.verifyNoInteractions(paymentRepository);
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test void bookingConfirmedWhileSchedulerWaitsForLockIsNotCancelled() {
        Booking booking = Booking.builder().id(id("booking-1")).status(BookingStatus.CONFIRMED)
                .paidAmount(BigDecimal.TEN).holdExpiresAt(LocalDateTime.now().minusHours(1)).build();
        when(bookingRepository.findIdsByStatus(BookingStatus.PENDING)).thenReturn(List.of(id("booking-1")));
        when(bookingRepository.findByIdForUpdate(id("booking-1"))).thenReturn(Optional.of(booking));

        bookingScheduledTask.processExpiredPaymentHolds();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        org.mockito.Mockito.verifyNoInteractions(paymentRepository);
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).findByStatus(any());
    }

    @Test void cancelledBookingIsNotOverwrittenByAutomaticCheckout() {
        Booking booking = Booking.builder().id(id("booking-1")).status(BookingStatus.CANCELLED)
                .checkOutDate(LocalDate.now().minusDays(1)).build();
        when(bookingRepository.findIdsByStatus(BookingStatus.CONFIRMED)).thenReturn(List.of(id("booking-1")));
        when(bookingRepository.findByIdForUpdate(id("booking-1"))).thenReturn(Optional.of(booking));

        bookingScheduledTask.processAutoCheckOut();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }
}
