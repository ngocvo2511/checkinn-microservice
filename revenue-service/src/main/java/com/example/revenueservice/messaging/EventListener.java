package com.example.revenueservice.messaging;

import com.example.revenueservice.messaging.event.BookingStatusEvent;
import com.example.revenueservice.messaging.event.PaymentEvent;
import com.example.revenueservice.service.EventIngestionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EventListener {

    private final EventIngestionService ingestionService;

    public EventListener(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(queues = "${app.rabbit.payment-queue:revenue.payment.queue}")
    public void handlePaymentEvent(PaymentEvent event) {
        ingestionService.savePaymentEvent(event);
    }

    @RabbitListener(queues = "${app.rabbit.booking-queue:revenue.booking.queue}")
    public void handleBookingStatusEvent(BookingStatusEvent event) {
        ingestionService.saveBookingStatusEvent(event);
    }
}
