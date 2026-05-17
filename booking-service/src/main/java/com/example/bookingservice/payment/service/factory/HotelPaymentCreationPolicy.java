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
public class HotelPaymentCreationPolicy implements PaymentCreationPolicy {

    @Override
    public PaymentMethod getMethod() {
        return PaymentMethod.HOTEL;
    }

    @Override
    public Payment createPayment(CreatePaymentRequest request) {
        return Payment.builder()
            .bookingId(request.getBookingId())
            .amount(request.getAmount())
            .method(request.getMethod())
            .status(PaymentStatus.ONSITE_PENDING)
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
