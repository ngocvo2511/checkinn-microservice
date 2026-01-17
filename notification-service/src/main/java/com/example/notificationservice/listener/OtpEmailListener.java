package com.example.notificationservice.listener;

import com.example.notificationservice.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEmailListener {

    private final EmailNotificationService emailNotificationService;

    @RabbitListener(queues = "notification.otp.queue")
    public void handleOtpEmail(Map<String, Object> event) {
        try {
            String email = (String) event.get("email");
            String otpCode = (String) event.get("otp_code");
            String eventType = (String) event.get("event_type");

            log.info("Received OTP event for email: {} (type: {})", email, eventType);

            if ("otp_verification".equals(eventType)) {
                sendOtpEmail(email, otpCode);
            }

        } catch (Exception e) {
            log.error("Error processing OTP email event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process OTP email event", e);
        }
    }

    private void sendOtpEmail(String email, String otpCode) {
        try {
            emailNotificationService.sendOtpVerificationEmail(email, otpCode);
            log.info("OTP email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }
}
