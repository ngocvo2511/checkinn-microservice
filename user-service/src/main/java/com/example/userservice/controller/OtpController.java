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
            boolean isValid = otpService.verifyOtp(request.getEmail(), request.getOtpCode());
            
            if (isValid) {
                // Mark user email as verified
                userService.verifyUserEmail(request.getEmail());
                
                return ResponseEntity.ok(OtpVerificationResponse.builder()
                        .verified(true)
                        .message("Email verified successfully")
                        .email(request.getEmail())
                        .build());
            } else {
                return ResponseEntity.ok(OtpVerificationResponse.builder()
                        .verified(false)
                        .message("Invalid or expired OTP")
                        .email(request.getEmail())
                        .build());
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(OtpVerificationResponse.builder()
                    .verified(false)
                    .message(e.getMessage())
                    .email(request.getEmail())
                    .build());
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendOtp(@RequestParam String email) {
        try {
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "OTP sent to your email"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateAndSendOtp(@RequestParam String email) {
        try {
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(java.util.Map.of(
                    "success", true,
                    "message", "OTP generated and sent to your email"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
