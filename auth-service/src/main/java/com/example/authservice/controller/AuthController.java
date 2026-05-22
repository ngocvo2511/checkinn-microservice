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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@RequestBody RegisterRequestDto request) {
        log.info("[REGISTER] Received user registration request - username: {}, email: {}", request.getUsername(), request.getEmail());
        try {
            AuthResponseDto response = authService.register(request, UserRole.USER);
            log.info("[REGISTER] User registration completed - email: {}, role: {}", request.getEmail(), response.getRole());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[REGISTER] User registration failed - username: {}, email: {}, error: {}", request.getUsername(), request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/register-owner")
    public ResponseEntity<?> registerOwner(@RequestBody RegisterRequestDto request) {
        log.info("[REGISTER_OWNER] Received owner registration request - username: {}, email: {}", request.getUsername(), request.getEmail());
        try {
            AuthResponseDto response = authService.register(request, UserRole.OWNER);
            log.info("[REGISTER_OWNER] Owner registration completed - email: {}, role: {}", request.getEmail(), response.getRole());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[REGISTER_OWNER] Owner registration failed - username: {}, email: {}, error: {}", request.getUsername(), request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto request) {
        log.info("[LOGIN] Received login request - usernameOrEmail: {}", request.getUsernameOrEmail());
        try {
            AuthResponseDto response = authService.login(request);
            log.info("[LOGIN] Login completed - email: {}, role: {}", response.getEmail(), response.getRole());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[LOGIN] Login failed - usernameOrEmail: {}, error: {}", request.getUsernameOrEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<OtpVerificationResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        log.info("[OTP_VERIFY] Received OTP verification request - email: {}", request.getEmail());
        try {
            OtpVerificationResponse response = otpService.verifyOtp(request.getEmail(), request.getOtpCode());
            log.info("[OTP_VERIFY] OTP verification completed - email: {}, verified: {}", request.getEmail(), response != null && response.isVerified());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[OTP_VERIFY] OTP verification failed - email: {}, error: {}", request.getEmail(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestParam String email) {
        log.info("[OTP_RESEND] Received OTP resend request - email: {}", email);
        try {
            Map<String, Object> response = otpService.resendOtp(email);
            log.info("[OTP_RESEND] OTP resend completed - email: {}, response: {}", email, response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[OTP_RESEND] OTP resend failed - email: {}, error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestParam String email) {
        log.info("[FORGOT_PASSWORD] Received forgot password request - email: {}", email);
        try {
            otpService.generateAndSendOtp(email);
            log.info("[FORGOT_PASSWORD] OTP generated and sent successfully - email: {}", email);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mã xác thực đã được gửi đến email của bạn"
            ));
        } catch (Exception e) {
            log.error("[FORGOT_PASSWORD] Forgot password failed - email: {}, error: {}", email, e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        log.info("[RESET_PASSWORD] Received reset password request - email: {}", request.get("email"));
        try {
            String email = request.get("email");
            String otpCode = request.get("otpCode");
            String newPassword = request.get("newPassword");

            if (email == null || otpCode == null || newPassword == null) {
                log.error("[RESET_PASSWORD] Reset password failed due to missing fields - email: {}, hasOtp: {}, hasNewPassword: {}",
                    email, otpCode != null, newPassword != null);
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Email, mã OTP và mật khẩu mới là bắt buộc"
                ));
            }

            // Verify OTP
            OtpVerificationResponse otpResponse = otpService.verifyOtp(email, otpCode);
            if (!otpResponse.isVerified()) {
                log.error("[RESET_PASSWORD] Reset password failed because OTP is invalid - email: {}, message: {}", email, otpResponse.getMessage());
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", otpResponse.getMessage()
                ));
            }

            // Reset password
            authService.resetPassword(email, newPassword);
            log.info("[RESET_PASSWORD] Password reset completed successfully - email: {}", email);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mật khẩu đã được thay đổi thành công"
            ));
        } catch (Exception e) {
            log.error("[RESET_PASSWORD] Reset password failed - email: {}, error: {}", request.get("email"), e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}
