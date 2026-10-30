package com.example.bookingservice.payment.refund;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundRequestServiceTest {
    private final RefundRequestRepository requests = mock(RefundRequestRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final RefundRequestService service = new RefundRequestService(requests, payments, bookings);
    private Booking booking;
    private Payment payment;
    @BeforeEach void setup() {
        booking = Booking.builder().id("booking-1").status(BookingStatus.CANCELLED).paidAmount(new BigDecimal("1000000")).build();
        payment = Payment.builder().id("payment-1").bookingId("booking-1").status(PaymentStatus.COMPLETED).amount(new BigDecimal("1000000")).build();
        when(payments.findBookingIdById("payment-1")).thenReturn(Optional.of("booking-1"));
        when(payments.findByIdForUpdate("payment-1")).thenReturn(Optional.of(payment));
        when(bookings.findByIdForUpdate("booking-1")).thenReturn(Optional.of(booking));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void requestTakesAmountFromPaymentAndPreservesFinancialState() {
        var request = service.request("payment-1", "customer-1", "Customer request");
        assertThat(request.getAmount()).isEqualByComparingTo("1000000");
        assertThat(request.getRequestedBy()).isEqualTo("customer-1");
        assertThat(request.getStatus()).isEqualTo(RefundRequest.Status.REQUESTED);
        assertFinancialStateUnchanged();
        var order = inOrder(bookings, payments);
        order.verify(bookings).findByIdForUpdate("booking-1");
        order.verify(payments).findByIdForUpdate("payment-1");
    }
    @Test void repeatedRequestReturnsSameRequestWithoutDuplicating() {
        RefundRequest existing = new RefundRequest(); existing.setId("request-1");
        when(requests.findByPaymentId("payment-1")).thenReturn(Optional.of(existing));
        assertThat(service.request("payment-1", "customer-1", "Repeated request")).isSameAs(existing);
        verify(requests, never()).save(any());
    }
    @Test void unpaidPaymentCannotCreateRefundRequest() {
        payment.setStatus(PaymentStatus.ONSITE_PENDING);
        assertThatThrownBy(() -> service.request("payment-1", "customer-1", "Request"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void approvalIsAuditedButDoesNotPretendProviderSettlement() {
        RefundRequest request = new RefundRequest(); request.setStatus(RefundRequest.Status.REQUESTED); request.setAmount(payment.getAmount());
        when(requests.findByPaymentId("payment-1")).thenReturn(Optional.of(request));
        service.approve("payment-1", "administrator-1");
        assertThat(request.getApprovedBy()).isEqualTo("administrator-1");
        assertThat(request.getApprovedAt()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
        assertFinancialStateUnchanged();
    }
    @Test void activeReservationMustBeCancelledBeforeRefundApproval() {
        booking.setStatus(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> service.approve("payment-1", "administrator-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void emptyReasonIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.request("payment-1", "customer-1", " "))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(payments, bookings, requests);
    }
    @Test void missingMoneyReceiptRequiresReconciliationBeforeRefundRequest() {
        booking.setPaidAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.request("payment-1", "customer-1", "Request"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void changedRequestAmountCannotBeApproved() {
        RefundRequest request = new RefundRequest(); request.setStatus(RefundRequest.Status.REQUESTED); request.setAmount(BigDecimal.ONE);
        when(requests.findByPaymentId("payment-1")).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.approve("payment-1", "administrator-1"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    private void assertFinancialStateUnchanged() {
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(booking.getPaidAmount()).isEqualByComparingTo("1000000");
        verify(payments, never()).save(any());
        verify(bookings, never()).save(any());
    }
}
