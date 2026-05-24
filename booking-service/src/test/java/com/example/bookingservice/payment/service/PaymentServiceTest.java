package com.example.bookingservice.payment.service;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.booking.service.BookingService;
import com.example.bookingservice.integration.loyalty.LoyaltyPointsClient;
import com.example.bookingservice.messaging.HotelEventPublisher;
import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import com.example.bookingservice.payment.service.factory.HotelPaymentCreationPolicy;
import com.example.bookingservice.payment.service.factory.PaymentCreationPolicyFactory;
import com.example.bookingservice.payment.service.factory.VnPayPaymentCreationPolicy;
import com.example.bookingservice.payment.vnpay.VnPayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private HotelEventPublisher eventPublisher;
    private LoyaltyPointsClient loyaltyPointsClient;
    private VnPayProperties vnPayProperties;
    private PaymentCreationPolicyFactory paymentCreationPolicyFactory;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        eventPublisher = mock(HotelEventPublisher.class);
        loyaltyPointsClient = mock(LoyaltyPointsClient.class);
        vnPayProperties = mock(VnPayProperties.class);
        paymentCreationPolicyFactory = mock(PaymentCreationPolicyFactory.class);

        paymentService = new PaymentService(
                paymentRepository,
                bookingRepository,
                vnPayProperties,
                bookingService,
                eventPublisher,
                loyaltyPointsClient,
                paymentCreationPolicyFactory
        );
    }

    @Test
    void createPaymentCreatesHotelPaymentOnly() {
        Booking booking = Booking.builder()
                .id("booking-1")
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1_000_000))
                .build();
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .bookingId("booking-1")
                .amount(BigDecimal.valueOf(1_000_000))
                .method(PaymentMethod.HOTEL)
                .build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc("booking-1")).thenReturn(Optional.empty());
        when(bookingService.ensureActiveHold("booking-1")).thenReturn(booking);
        when(paymentCreationPolicyFactory.getPolicy(PaymentMethod.HOTEL)).thenReturn(new HotelPaymentCreationPolicy());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId("payment-1");
            return payment;
        });

        var response = paymentService.createPayment(request);

        assertThat(response.getMethod()).isEqualTo(PaymentMethod.HOTEL);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.ONSITE_PENDING);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void createPaymentRejectsVnPayBecauseVnPayUsesInitFlow() {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .bookingId("booking-1")
                .amount(BigDecimal.valueOf(1_000_000))
                .method(PaymentMethod.VNPAY)
                .build();

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initVnPayPayment");

        verify(bookingRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initVnPayPaymentCreatesPaymentThroughPolicyFactory() {
        Booking booking = Booking.builder()
                .id("booking-1")
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1_000_000))
                .build();

        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc("booking-1")).thenReturn(Optional.empty());
        when(bookingService.ensureActiveHold("booking-1")).thenReturn(booking);
        when(paymentCreationPolicyFactory.getPolicy(PaymentMethod.VNPAY)).thenReturn(new VnPayPaymentCreationPolicy());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId("payment-1");
            }
            return payment;
        });
        when(vnPayProperties.getVersion()).thenReturn("2.1.0");
        when(vnPayProperties.getTmnCode()).thenReturn("TEST");
        when(vnPayProperties.getReturnUrl()).thenReturn("http://localhost/vnpay/return");
        when(vnPayProperties.getPayUrl()).thenReturn("http://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        when(vnPayProperties.getHashSecret()).thenReturn("secret");

        var response = paymentService.initVnPayPayment("booking-1", "127.0.0.1");

        assertThat(response.getOrderId()).isEqualTo("payment-1");
        assertThat(response.getRedirectUrl()).contains("vnp_TxnRef=payment-1");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        verify(paymentCreationPolicyFactory).getPolicy(PaymentMethod.VNPAY);
    }

    @Test
    void processVNPayCallbackReturnsExistingPaymentOnSuccessfulReplay() {
        Booking booking = Booking.builder()
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
                .build();

        Payment payment = Payment.builder()
                .id("payment-1")
                .bookingId("booking-1")
                .amount(BigDecimal.valueOf(1_000_000))
                .method(PaymentMethod.VNPAY)
                .status(PaymentStatus.PENDING)
                .vnpayOrderId("payment-1")
                .build();

        when(paymentRepository.findByVnpayOrderId("payment-1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByVnpayTransactionNo("vnpay-txn-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookingRepository.findById("booking-1")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            booking.setEarnedPoints(1000L);
            return null;
        }).when(bookingService).confirmBookingHold("booking-1");

        paymentService.processVNPayCallback("payment-1", "00", "vnpay-txn-1");
        var replayResponse = paymentService.processVNPayCallback("payment-1", "00", "vnpay-txn-1");

        assertThat(replayResponse.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(replayResponse.getVnpayResponseCode()).isEqualTo("00");

        verify(bookingService, times(1)).confirmBookingHold("booking-1");
        verify(eventPublisher, times(1)).publishPaymentCompleted(any());
        verify(eventPublisher, times(1)).publishBookingStatus(any());
        verify(loyaltyPointsClient, never()).earnPoints(any(), any(), any());
    }
}
