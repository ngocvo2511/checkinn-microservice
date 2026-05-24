package com.example.bookingservice.payment.service.factory;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.booking.enums.BookingStatus;
import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentMethod;
import com.example.bookingservice.payment.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VnPayPaymentCreationPolicy implements PaymentCreationPolicy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public Payment createPayment(CreatePaymentRequest request) {
        return Payment.builder()
            .bookingId(request.getBookingId())
            .amount(request.getAmount())
            .method(PaymentMethod.VNPAY)
            .status(PaymentStatus.PENDING)
            .transactionId(UUID.randomUUID().toString())
            .build();
    }

    @Override
    public void updateBookingAfterPaymentCreated(Booking booking) {
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
    }

    @Override
    public boolean shouldPersistBooking() {
        return true;
    }
}
