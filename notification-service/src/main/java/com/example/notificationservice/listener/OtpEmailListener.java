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

            log.info("[NOTIFICATION_OTP_EVENT_RECEIVED]");

            if ("otp_verification".equals(eventType)) {
                emailNotificationService.sendOtpVerificationEmail(email, otpCode);
                log.info("[NOTIFICATION_OTP_EVENT_PROCESSED]");
            } else {
                log.info("[NOTIFICATION_OTP_EVENT_SKIPPED]");
            }

        } catch (Exception e) {
            log.error("[NOTIFICATION_OTP_EVENT_ERROR]");
            throw new IllegalStateException("OTP notification processing failed");
        }
    }

}
