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
            logger.info("📨 Nhận được event thanh toán thành công từ RabbitMQ: {}", event);
            logger.info("   - Booking ID: {}", event.getBookingId());
            logger.info("   - User Email: {}", event.getUserEmail());
            logger.info("   - Hotel: {}", event.getHotelName());
            logger.info("   - Total Amount: {}", event.getAmount());

            emailNotificationService.sendPaymentSuccessNotification(event);

            logger.info("✓ Đã xử lý thành công event thanh toán cho booking #{}", event.getBookingId());

        } catch (Exception e) {
            logger.error("✗ Lỗi khi xử lý event thanh toán cho booking #{}: {}", 
                    event.getBookingId(), e.getMessage(), e);
            // Có thể implement retry logic hoặc dead letter queue ở đây
        }
    }
}
