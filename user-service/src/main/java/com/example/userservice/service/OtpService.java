package com.example.userservice.service;

import com.example.userservice.config.RabbitMQConfig;
import com.example.userservice.model.Otp;
import com.example.userservice.model.OtpEmailEvent;
import com.example.userservice.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final RabbitTemplate rabbitTemplate;

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;

    @Transactional
    public String generateAndSendOtp(String email) {
        // Generate OTP
        String otpCode = generateOtp();

        // Delete old OTP for this email if exists
        otpRepository.deleteByEmail(email);

        // Create new OTP record
        Otp otp = Otp.builder()
                .email(email)
                .otpCode(otpCode)
                .createdAt(LocalDateTime.now())
                .expiryTime(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .isVerified(false)
                .attemptCount(0)
                .build();

        otpRepository.save(otp);

        // Send via RabbitMQ to notification service
        sendOtpViaRabbitMQ(email, otpCode);

        log.info("OTP generated and sent to notification service for email: {}", email);
        return otpCode;
    }

    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void sendOtpViaRabbitMQ(String email, String otpCode) {
        try {
            OtpEmailEvent event = OtpEmailEvent.builder()
                    .email(email)
                    .otpCode(otpCode)
                    .eventType("otp_verification")
                    .timestamp(System.currentTimeMillis())
                    .build();

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.OTP_EXCHANGE,
                    RabbitMQConfig.OTP_ROUTING_KEY,
                    event
            );

            log.info("OTP email event published to RabbitMQ for: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP event to RabbitMQ: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP notification", e);
        }
    }

    @Transactional
    public boolean verifyOtp(String email, String otpCode) {
        // First try to find unverified OTP
        Optional<Otp> otpOptional = otpRepository.findByEmailAndIsVerifiedFalse(email);
        
        // If not found, try to find verified OTP (for reset password flow)
        if (otpOptional.isEmpty()) {
            otpOptional = otpRepository.findByEmail(email).stream()
                    .filter(otp -> otp.getOtpCode().equals(otpCode) && otp.isVerified())
                    .findFirst();
            
            if (otpOptional.isPresent()) {
                Otp otp = otpOptional.get();
                // Check if already verified OTP is still valid (not expired)
                if (LocalDateTime.now().isBefore(otp.getExpiryTime())) {
                    log.info("Using already verified OTP for email: {}", email);
                    return true;
                } else {
                    log.warn("Verified OTP expired for email: {}", email);
                    otpRepository.delete(otp);
                    return false;
                }
            }
            
            log.warn("OTP not found for email: {}", email);
            return false;
        }

        Otp otp = otpOptional.get();

        // Check if OTP has expired
        if (LocalDateTime.now().isAfter(otp.getExpiryTime())) {
            log.warn("OTP expired for email: {}", email);
            otpRepository.delete(otp);
            return false;
        }

        // Check attempt count
        if (otp.getAttemptCount() >= MAX_ATTEMPTS) {
            log.warn("Max OTP attempts exceeded for email: {}", email);
            otpRepository.delete(otp);
            return false;
        }

        // Check if OTP matches
        if (!otp.getOtpCode().equals(otpCode)) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            otpRepository.save(otp);
            log.warn("Invalid OTP attempt for email: {}, attempt: {}", email, otp.getAttemptCount());
            return false;
        }

        // Mark as verified
        otp.setVerified(true);
        otpRepository.save(otp);
        log.info("OTP verified successfully for email: {}", email);
        return true;
    }

    public boolean isOtpVerified(String email) {
        Optional<Otp> otpOptional = otpRepository.findByEmail(email);
        return otpOptional.map(Otp::isVerified).orElse(false);
    }
}
