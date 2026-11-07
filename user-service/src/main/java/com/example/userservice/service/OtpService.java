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
import java.security.SecureRandom;

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

        log.info("[OTP_GENERATE_AND_SEND] OTP generated and sent to notification service");
        return otpCode;
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
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

            log.info("[OTP_RABBITMQ_PUBLISH] OTP email event published to RabbitMQ");
        } catch (Exception e) {
            log.error("[OTP_RABBITMQ_PUBLISH_ERROR] OTP delivery failed - errorType: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Failed to send OTP notification", e);
        }
    }

    @Transactional
    public boolean verifyOtp(String email, String otpCode) {
        var rows=otpRepository.findAllForUpdate(email);
        if(rows.size()!=1) return false; // Preserve ambiguous data; request a fresh challenge.
        Otp otp=rows.getFirst();
        if(otp.getExpiryTime()==null || !otp.getExpiryTime().isAfter(LocalDateTime.now()) || otp.getAttemptCount()>=MAX_ATTEMPTS) {
            otpRepository.delete(otp);
            return false;
        }
        if(!otp.getOtpCode().equals(otpCode)) {
            otp.setAttemptCount(otp.getAttemptCount()+1);
            otpRepository.save(otp);
            return false;
        }
        // A successful intermediate verification remains usable for the final reset.
        // Incorrect guesses still consume the same budget after verification.
        if(!otp.isVerified()) {
            otp.setVerified(true);
            otpRepository.save(otp);
        }
        return true;
    }

    /** Preserve verify-then-reset UI; consume the verified proof in the password transaction. */
    @Transactional
    public void consumeVerifiedForReset(String email) {
        var rows=otpRepository.findAllForUpdate(email);
        var now=LocalDateTime.now();
        long valid=rows.stream().filter(o->o.isVerified() && o.getAttemptCount()<MAX_ATTEMPTS && o.getExpiryTime()!=null && o.getExpiryTime().isAfter(now)).count();
        if(rows.size()!=1 || valid!=1) throw new IllegalArgumentException("Verified OTP required for password reset");
        otpRepository.deleteAll(rows);
        otpRepository.flush();
    }

    public boolean isOtpVerified(String email) {
        Optional<Otp> otpOptional = otpRepository.findByEmail(email);
        return otpOptional.map(Otp::isVerified).orElse(false);
    }
}
