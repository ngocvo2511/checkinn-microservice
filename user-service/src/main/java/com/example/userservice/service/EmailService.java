//package com.example.userservice.service;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.mail.SimpleMailMessage;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.stereotype.Service;
//import java.util.Random;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class EmailService {
//
//    private final JavaMailSender mailSender;
//    private static final String FROM_EMAIL = "noreply@checkinn.com";
//
//    public String generateOtp() {
//        Random random = new Random();
//        int otp = 100000 + random.nextInt(900000);
//        return String.valueOf(otp);
//    }
//
//    public void sendOtpEmail(String toEmail, String otpCode) {
//        try {
//            SimpleMailMessage message = new SimpleMailMessage();
//            message.setFrom(FROM_EMAIL);
//            message.setTo(toEmail);
//            message.setSubject("CheckInn - Email Verification Code");
//            message.setText(buildOtpEmailBody(otpCode));
//
//            mailSender.send(message);
//            log.info("OTP email sent successfully to: {}", toEmail);
//        } catch (Exception e) {
//            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
//            throw new RuntimeException("Failed to send OTP email", e);
//        }
//    }
//
//    private String buildOtpEmailBody(String otpCode) {
//        return "Welcome to CheckInn!\n\n" +
//                "Your verification code is: " + otpCode + "\n\n" +
//                "This code will expire in 10 minutes.\n\n" +
//                "If you didn't request this code, please ignore this email.\n\n" +
//                "Best regards,\n" +
//                "CheckInn Team";
//    }
//}
