package com.example.userservice.controller;

import com.example.userservice.dto.VerifyOtpRequest;
import com.example.userservice.dto.OtpVerificationResponse;
import com.example.userservice.service.OtpService;
import com.example.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/otp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OtpController {

    private final OtpService otpService;
    private final UserService userService;

    @PostMapping("/verify")
    public ResponseEntity<OtpVerificationResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            if(request==null || blank(request.getEmail()) || blank(request.getOtpCode()))
                throw new IllegalArgumentException("Missing OTP input");
            boolean isValid = otpService.verifyOtp(request.getEmail(), request.getOtpCode());
            
            if (isValid) {
                // Mark user email as verified
                userService.verifyUserEmail(request.getEmail());
                
                return ResponseEntity.ok(OtpVerificationResponse.builder()
                        .verified(true)
                        .message("Xác thực email thành công")
                        .email(request.getEmail())
                        .build());
            } else {
                return ResponseEntity.ok(OtpVerificationResponse.builder()
                        .verified(false)
                        .message("Mã OTP không hợp lệ hoặc đã hết hạn")
                        .email(request.getEmail())
                        .build());
            }
        } catch (Exception e) {
            var status=failureStatus(e);
            return ResponseEntity.status(status).body(OtpVerificationResponse.builder()
                    .verified(false)
                    .message(failureMessage(status))
                    .email(request==null?null:request.getEmail())
                    .build());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendOtp(@RequestParam String email) {
        try {
            if(blank(email))throw new IllegalArgumentException("Missing email");
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Mã OTP đã được gửi đến email của bạn"
            ));
        } catch (Exception e) {
            var status=failureStatus(e);
            return ResponseEntity.status(status).body(java.util.Map.of(
                    "success", false,
                    "message", failureMessage(status)
            ));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateAndSendOtp(@RequestParam String email) {
        try {
            if(blank(email))throw new IllegalArgumentException("Missing email");
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "Mã OTP đã được tạo và gửi đến email của bạn"
            ));
        } catch (Exception e) {
            var status=failureStatus(e);
            return ResponseEntity.status(status).body(java.util.Map.of(
                    "success", false,
                    "message", failureMessage(status)
            ));
        }
    }

    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static org.springframework.http.HttpStatus failureStatus(Exception failure) {
        Throwable cause=failure;
        for(int depth=0;cause!=null && depth<16;depth++,cause=cause.getCause()) {
            if(cause instanceof org.springframework.dao.DataAccessException
                    || cause instanceof org.springframework.transaction.TransactionException
                    || cause instanceof org.springframework.amqp.AmqpException
                    || cause instanceof java.sql.SQLException
                    || cause instanceof com.example.userservice.security.SessionStoreUnavailableException)
                return org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
        }
        return failure instanceof IllegalArgumentException ? org.springframework.http.HttpStatus.BAD_REQUEST
                : org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
    }
    private static String failureMessage(org.springframework.http.HttpStatus status) {
        return switch(status) {
            case BAD_REQUEST -> "Invalid OTP request";
            case SERVICE_UNAVAILABLE -> "OTP service temporarily unavailable";
            default -> "OTP request could not be completed";
        };
    }
}
