package com.example.authservice.controller;

import com.checkinn.user.grpc.UserRole;
import com.example.authservice.dto.LoginRequestDto;
import com.example.authservice.dto.RegisterRequestDto;
import com.example.authservice.dto.AuthResponseDto;
import com.example.authservice.dto.VerifyOtpRequest;
import com.example.authservice.dto.OtpVerificationResponse;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody RegisterRequestDto request) {
        return ResponseEntity.ok(authService.register(request, UserRole.USER));
    }

    @PostMapping("/register-owner")
    public ResponseEntity<?> registerOwner(@RequestBody RegisterRequestDto request) {
        return ResponseEntity.ok(authService.register(request, UserRole.OWNER));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<OtpVerificationResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        OtpVerificationResponse response = otpService.verifyOtp(request.getEmail(), request.getOtpCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestParam String email) {
        Map<String, Object> response = otpService.resendOtp(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestParam String email) {
        try {
            otpService.generateAndSendOtp(email);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mã xác thực đã được gửi đến email của bạn"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String otpCode = request.get("otpCode");
            String newPassword = request.get("newPassword");

            if (email == null || otpCode == null || newPassword == null) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Email, OTP code và mật khẩu mới là bắt buộc"
                ));
            }

            // Verify OTP
            OtpVerificationResponse otpResponse = otpService.verifyOtp(email, otpCode);
            if (!otpResponse.isVerified()) {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", otpResponse.getMessage()
                ));
            }

            // Reset password
            authService.resetPassword(email, newPassword);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mật khẩu đã được thay đổi thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}
