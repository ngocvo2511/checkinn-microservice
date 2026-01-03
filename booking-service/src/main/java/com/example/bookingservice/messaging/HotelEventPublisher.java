package com.example.bookingservice.messaging;

import com.example.bookingservice.messaging.event.BookingStatusEvent;
import com.example.bookingservice.messaging.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HotelEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.exchange:hotel.events}")
    private String exchange;

    public void publishPaymentCompleted(PaymentEvent event) {
        rabbitTemplate.convertAndSend(exchange, "payment.completed", event);
    }

    public void publishPaymentRefunded(PaymentEvent event) {
        rabbitTemplate.convertAndSend(exchange, "payment.refunded", event);
    }

    public void publishBookingStatus(BookingStatusEvent event) {
        rabbitTemplate.convertAndSend(exchange, "booking.status.changed", event);
    }
}
