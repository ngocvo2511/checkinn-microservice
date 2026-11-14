package com.example.bookingservice.payment.refund;
import static com.example.bookingservice.contract.TestIds.*;

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
        booking = Booking.builder().id(id("booking-1")).status(BookingStatus.CANCELLED).paidAmount(new BigDecimal("1000000")).build();
        payment = Payment.builder().id(id("payment-1")).bookingId(id("booking-1")).status(PaymentStatus.COMPLETED).amount(new BigDecimal("1000000")).build();
        when(payments.findBookingIdById(id("payment-1"))).thenReturn(Optional.of(id("booking-1")));
        when(payments.findByIdForUpdate(id("payment-1"))).thenReturn(Optional.of(payment));
        when(bookings.findByIdForUpdate(id("booking-1"))).thenReturn(Optional.of(booking));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void requestTakesAmountFromPaymentAndPreservesFinancialState() {
        var request = service.request(id("payment-1"), id("customer-1"), "Customer request");
        assertThat(request.getAmount()).isEqualByComparingTo("1000000");
        assertThat(request.getRequestedBy()).isEqualTo(id("customer-1"));
        assertThat(request.getStatus()).isEqualTo(RefundRequest.Status.REQUESTED);
        assertFinancialStateUnchanged();
        var order = inOrder(bookings, payments);
        order.verify(bookings).findByIdForUpdate(id("booking-1"));
        order.verify(payments).findByIdForUpdate(id("payment-1"));
    }
    @Test void repeatedRequestReturnsSameRequestWithoutDuplicating() {
        RefundRequest existing = new RefundRequest(); existing.setId(id("request-1"));
        when(requests.findByPaymentId(id("payment-1"))).thenReturn(Optional.of(existing));
        assertThat(service.request(id("payment-1"), id("customer-1"), "Repeated request")).isSameAs(existing);
        verify(requests, never()).save(any());
    }
    @Test void unpaidPaymentCannotCreateRefundRequest() {
        payment.setStatus(PaymentStatus.ONSITE_PENDING);
        assertThatThrownBy(() -> service.request(id("payment-1"), id("customer-1"), "Request"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void approvalIsAuditedButDoesNotPretendProviderSettlement() {
        RefundRequest request = validRequest();
        when(requests.findByPaymentId(id("payment-1"))).thenReturn(Optional.of(request));
        service.approve(id("payment-1"), id("administrator-1"));
        assertThat(request.getApprovedBy()).isEqualTo(id("administrator-1"));
        assertThat(request.getApprovedAt()).isNotNull();
        assertThat(request.getStatus()).isEqualTo(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
        assertFinancialStateUnchanged();
    }
    @Test void activeReservationMustBeCancelledBeforeRefundApproval() {
        booking.setStatus(BookingStatus.CONFIRMED);
        assertThatThrownBy(() -> service.approve(id("payment-1"), id("administrator-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void emptyReasonIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.request(id("payment-1"), id("customer-1"), " "))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(payments, bookings, requests);
    }
    @Test void missingMoneyReceiptRequiresReconciliationBeforeRefundRequest() {
        booking.setPaidAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.request(id("payment-1"), id("customer-1"), "Request"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    @Test void changedRequestAmountCannotBeApproved() {
        RefundRequest request = validRequest(); request.setAmount(BigDecimal.ONE);
        when(requests.findByPaymentId(id("payment-1"))).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.approve(id("payment-1"), id("administrator-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any());
    }
    private RefundRequest validRequest() {
        var request = new RefundRequest();
        request.setId(id("request-1")); request.setPaymentId(payment.getId()); request.setBookingId(payment.getBookingId());
        request.setAmount(payment.getAmount()); request.setStatus(RefundRequest.Status.REQUESTED);
        return request;
    }
    private RefundRequest approvedRequest() {
        var request = validRequest(); request.setStatus(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
        request.setApprovedBy(id("administrator-1")); request.setApprovedAt(java.time.Instant.parse("2026-11-12T00:00:00Z"));
        when(requests.findByPaymentId(id("payment-1"))).thenReturn(Optional.of(request));
        return request;
    }
    @Test void approvedExecutionOnlyValidatesAndPreservesReceiptAndApproval() {
        var request = approvedRequest();
        service.validateExecution(id("payment-1")); service.validateExecution(id("payment-1"));
        assertThat(request.getApprovedAt()).isEqualTo(java.time.Instant.parse("2026-11-12T00:00:00Z"));
        assertThat(request.getStatus()).isEqualTo(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
        verify(requests, never()).save(any()); assertFinancialStateUnchanged();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unapproved", "actor", "time", "payment", "booking", "amount", "nullAmount", "active", "insufficient", "notCompleted"})
    void executionRejectsMissingApprovalOrConflictingFinancialFacts(String fault) {
        var request = approvedRequest();
        switch (fault) {
            case "unapproved" -> request.setStatus(RefundRequest.Status.REQUESTED);
            case "actor" -> request.setApprovedBy(null);
            case "time" -> request.setApprovedAt(null);
            case "payment" -> request.setPaymentId(id("other-payment"));
            case "booking" -> request.setBookingId(id("other-booking"));
            case "amount" -> request.setAmount(BigDecimal.ONE);
            case "nullAmount" -> request.setAmount(null);
            case "active" -> booking.setStatus(BookingStatus.CONFIRMED);
            case "insufficient" -> booking.setPaidAmount(BigDecimal.ZERO);
            case "notCompleted" -> payment.setStatus(PaymentStatus.PENDING);
        }
        assertThatThrownBy(() -> service.validateExecution(id("payment-1")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        verify(requests, never()).save(any()); verify(payments, never()).save(any()); verify(bookings, never()).save(any());
    }
    @Test void executionRequiresAnExistingRequest() {
        assertThatThrownBy(() -> service.validateExecution(id("payment-1")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
        assertFinancialStateUnchanged();
    }
    @Test void repeatedApprovalPreservesOriginalActorAndTime() {
        var request = approvedRequest();
        assertThat(service.approve(id("payment-1"), id("administrator-2"))).isSameAs(request);
        assertThat(request.getApprovedBy()).isEqualTo(id("administrator-1"));
        assertThat(request.getApprovedAt()).isEqualTo(java.time.Instant.parse("2026-11-12T00:00:00Z"));
        verify(requests, never()).save(any()); assertFinancialStateUnchanged();
    }
    @Test void approvalRejectsRequestBoundToAnotherBooking() {
        var request = validRequest(); request.setBookingId(id("other-booking"));
        when(requests.findByPaymentId(id("payment-1"))).thenReturn(Optional.of(request));
        assertThatThrownBy(() -> service.approve(id("payment-1"), id("administrator-1")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(requests, never()).save(any()); assertFinancialStateUnchanged();
    }
    private void assertFinancialStateUnchanged() {
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(booking.getPaidAmount()).isEqualByComparingTo("1000000");
        verify(payments, never()).save(any());
        verify(bookings, never()).save(any());
    }
}
