package com.example.bookingservice.payment.service.factory;

import com.example.bookingservice.booking.entity.Booking;
import com.example.bookingservice.payment.dto.CreatePaymentRequest;
import com.example.bookingservice.payment.entity.Payment;
import com.example.bookingservice.payment.enums.PaymentMethod;

public interface PaymentCreationPolicy {

    PaymentMethod getMethod();

    Payment createPayment(CreatePaymentRequest request);

    default void updateBookingAfterPaymentCreated(Booking booking) {
    }

    default boolean shouldPersistBooking() {
        return false;
    }
}
