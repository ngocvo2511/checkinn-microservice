package com.example.bookingservice.messaging;

import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HotelEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.exchange:hotel.events}")
    private String exchange;

    public void publishPaymentCompleted(PaymentEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, "payment.completed", event);
        } catch (Exception e) {
            log.warn("Failed to publish payment.completed event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }

    public void publishPaymentRefunded(PaymentEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, "payment.refunded", event);
        } catch (Exception e) {
            log.warn("Failed to publish payment.refunded event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }

    public void publishBookingStatus(BookingStatusEvent event) {
        try {
            rabbitTemplate.convertAndSend(exchange, "booking.status.changed", event);
        } catch (Exception e) {
            log.warn("Failed to publish booking.status.changed event - RabbitMQ may be unavailable: {}", e.getMessage());
        }
    }
}
