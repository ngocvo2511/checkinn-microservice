package com.example.bookingservice.booking.service;

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

    private BookingRepository bookingRepository;
    private PaymentRepository paymentRepository;
    private BookingScheduledTask bookingScheduledTask;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        bookingScheduledTask = new BookingScheduledTask(bookingRepository, paymentRepository);
    }

    @Test
    void processExpiredPaymentHoldsCancelsExpiredBookingAndActivePayment() {
        Booking expiredBooking = Booking.builder()
                .id("booking-1")
                .userId("user-1")
                .hotelId("hotel-1")
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
                .holdId("hold-1")
                .holdExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Payment activePayment = Payment.builder()
                .id("payment-1")
                .bookingId("booking-1")
                .amount(BigDecimal.valueOf(1_000_000))
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .build();

        when(bookingRepository.findByStatus(BookingStatus.PENDING)).thenReturn(List.of());
        when(bookingRepository.findByStatus(BookingStatus.PENDING_PAYMENT)).thenReturn(List.of(expiredBooking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc("booking-1")).thenReturn(Optional.of(activePayment));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingScheduledTask.processExpiredPaymentHolds();

        assertThat(expiredBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(activePayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(bookingRepository).save(expiredBooking);
        verify(paymentRepository).save(activePayment);
    }
}
