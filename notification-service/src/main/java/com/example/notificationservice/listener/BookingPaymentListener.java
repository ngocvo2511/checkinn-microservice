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
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED] Received payment event from RabbitMQ: {}", event);
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED] Booking ID: {}", event.getBookingId());
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED] User Email: {}", event.getUserEmail());
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED] Hotel: {}", event.getHotelName());
            logger.info("[NOTIFICATION_PAYMENT_EVENT_RECEIVED] Total Amount: {}", event.getAmount());

            logger.info("[NOTIFICATION_PAYMENT_EMAIL_SEND] Start sending payment notification email for booking #{}", event.getBookingId());
            emailNotificationService.sendPaymentSuccessNotification(event);

            logger.info("[NOTIFICATION_PAYMENT_EVENT_PROCESSED] Successfully processed payment event for booking #{}", event.getBookingId());

        } catch (Exception e) {
            logger.error("[NOTIFICATION_PAYMENT_EVENT_ERROR] Error processing payment event for booking #{}: {}", 
                    event.getBookingId(), e.getMessage(), e);
            // Có thể implement retry logic hoặc dead letter queue ở đây
        }
    }
}
