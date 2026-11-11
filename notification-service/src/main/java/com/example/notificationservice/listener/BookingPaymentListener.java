package com.example.notificationservice.listener;

import com.example.notificationservice.model.BookingNotificationEvent;
import com.example.notificationservice.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingPaymentListener {

    private static final Logger logger = LoggerFactory.getLogger(BookingPaymentListener.class);

    private final EmailNotificationService emailNotificationService;

    public BookingPaymentListener(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @RabbitListener(queues = "notification.payment.queue")
    public void handlePaymentCompletedEvent(BookingNotificationEvent event) {
        try {
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED]");
            emailNotificationService.sendPaymentSuccessNotification(event);

            logger.info("[NOTIFICATION_PAYMENT_EVENT_PROCESSED]");

        } catch (Exception e) {
            logger.error("[NOTIFICATION_PAYMENT_EVENT_ERROR]");
            throw new IllegalStateException("Payment notification processing failed");
        }
    }
}
