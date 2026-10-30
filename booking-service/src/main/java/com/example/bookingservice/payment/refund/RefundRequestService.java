package com.example.bookingservice.payment.refund;

import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.booking.repository.BookingRepository;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentStatus;
import com.example.bookingservice.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefundRequestService {
    private final RefundRequestRepository requests;
    private final PaymentRepository payments;
    private final BookingRepository bookings;

    @Transactional
    public RefundRequest request(String paymentId, String actorId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund reason must contain 1 to 1000 characters");
        }
        Payment payment = lockPayment(paymentId);
        requireCompleted(payment);
        return requests.findByPaymentId(paymentId).orElseGet(() -> {
            RefundRequest request = new RefundRequest();
            request.setPaymentId(paymentId); request.setBookingId(payment.getBookingId()); request.setAmount(payment.getAmount());
            request.setReason(reason.strip()); request.setRequestedBy(actorId); request.setRequestedAt(Instant.now());
            request.setStatus(RefundRequest.Status.REQUESTED);
            return requests.save(request);
        });
    }

    public RefundRequest get(String paymentId) {
        return requests.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found"));
    }

    @Transactional
    public RefundRequest approve(String paymentId, String administratorId) {
        Payment payment = lockPayment(paymentId);
        requireCompleted(payment);
        var booking = bookings.findByIdForUpdate(payment.getBookingId()).orElseThrow();
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancel the reservation through the reviewed workflow before approving refund");
        }
        RefundRequest request = get(paymentId);
        if (request.getAmount().compareTo(payment.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request amount requires reconciliation");
        }
        if (request.getStatus() == RefundRequest.Status.APPROVED_PENDING_EXECUTION) return request;
        request.setApprovedBy(administratorId); request.setApprovedAt(Instant.now());
        request.setStatus(RefundRequest.Status.APPROVED_PENDING_EXECUTION);
        return requests.save(request); // approval is not provider settlement; paidAmount stays unchanged
    }

    private Payment lockPayment(String paymentId) {
        String bookingId = payments.findBookingIdById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        var booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        var payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getAmount() == null || booking.getPaidAmount() == null || booking.getPaidAmount().compareTo(payment.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Received payment amount requires reconciliation");
        }
        return payment;
    }
    private void requireCompleted(Payment payment) {
        if (payment.getStatus() != PaymentStatus.COMPLETED || payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a completed positive payment can be refunded");
        }
    }
}
