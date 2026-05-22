package com.example.revenueservice.messaging;

import com.example.revenueservice.messaging.event.BookingStatusEvent;
import com.example.revenueservice.messaging.event.PaymentEvent;
import com.example.revenueservice.service.EventIngestionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);
    private final EventIngestionService ingestionService;

    public EventListener(EventIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @RabbitListener(queues = "${app.rabbit.payment-queue:revenue.payment.queue}")
    public void handlePaymentEvent(PaymentEvent event) {
        logger.info("[REVENUE_PAYMENT_EVENT_RECEIVED] Received PaymentEvent bookingId={}", event.bookingId());
        ingestionService.savePaymentEvent(event);
        logger.debug("[REVENUE_PAYMENT_EVENT_HANDLED] Finished handling PaymentEvent bookingId={}", event.bookingId());
    }

    @RabbitListener(queues = "${app.rabbit.booking-queue:revenue.booking.queue}")
    public void handleBookingStatusEvent(BookingStatusEvent event) {
        logger.info("[REVENUE_BOOKING_STATUS_EVENT_RECEIVED] Received BookingStatusEvent bookingId={}", event.bookingId());
        ingestionService.saveBookingStatusEvent(event);
        logger.debug("[REVENUE_BOOKING_STATUS_EVENT_HANDLED] Finished handling BookingStatusEvent bookingId={}", event.bookingId());
    }
}
