package com.example.bookingservice.messaging;

import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotelEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.exchange:hotel.events}")
    private String exchange;

    public void publishPaymentCompleted(PaymentEvent event) {
        try {
            log.info("[RABBIT_PUBLISH] Publishing payment.completed - bookingId: {}, paymentId: {}", event.getBookingId(), event.getBookingId());
            rabbitTemplate.convertAndSend(exchange, "payment.completed", event);
            log.info("[RABBIT_PUBLISH] Published payment.completed - bookingId: {}, paymentId: {}", event.getBookingId(), event.getBookingId());
        } catch (Exception e) {
            log.warn("Failed to publish payment.completed event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }

    public void publishPaymentRefunded(PaymentEvent event) {
        try {
            log.info("[RABBIT_PUBLISH] Publishing payment.refunded - bookingId: {}, paymentId: {}", event.getBookingId(), event.getBookingId());
            rabbitTemplate.convertAndSend(exchange, "payment.refunded", event);
            log.info("[RABBIT_PUBLISH] Published payment.refunded - bookingId: {}, paymentId: {}", event.getBookingId(), event.getBookingId());
        } catch (Exception e) {
            log.warn("Failed to publish payment.refunded event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }

    public void publishBookingStatus(BookingStatusEvent event) {
        try {
            log.info("[RABBIT_PUBLISH] Publishing booking.status.changed - bookingId: {}, status: {}", event.getBookingId(), event.getBookingStatus());
            rabbitTemplate.convertAndSend(exchange, "booking.status.changed", event);
            log.info("[RABBIT_PUBLISH] Published booking.status.changed - bookingId: {}, status: {}", event.getBookingId(), event.getBookingStatus());
        } catch (Exception e) {
            log.warn("Failed to publish booking.status.changed event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }
}
