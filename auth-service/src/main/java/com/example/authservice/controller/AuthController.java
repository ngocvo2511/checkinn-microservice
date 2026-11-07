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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
        log.info("[REGISTER] Received user registration request");
        try {
            AuthResponseDto response = authService.register(request, UserRole.USER);
            log.info("[REGISTER] User registration completed - userId: {}", response.getUserId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[REGISTER] User registration failed - errorType: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/register-owner")
    public ResponseEntity<?> registerOwner(@RequestBody RegisterRequestDto request) {
        log.info("[REGISTER_OWNER] Received owner registration request");
        try {
            AuthResponseDto response = authService.register(request, UserRole.OWNER);
            log.info("[REGISTER_OWNER] Owner registration completed - userId: {}", response.getUserId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[REGISTER_OWNER] Owner registration failed - errorType: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@RequestBody LoginRequestDto request) {
        log.info("[LOGIN] Received login request");
        try {
            AuthResponseDto response = authService.login(request);
            log.info("[LOGIN] Login completed - userId: {}", response.getUserId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[LOGIN] Login failed - errorType: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("[LOGOUT] Received logout request");
        authService.logout(authHeader);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<OtpVerificationResponse> verifyOtp(@RequestBody VerifyOtpRequest request) {
        log.info("[OTP_VERIFY] Received OTP verification request");
        try {
            OtpVerificationResponse response = otpService.verifyOtp(request.getEmail(), request.getOtpCode());
            log.info("[OTP_VERIFY] OTP verification completed - verified: {}", response != null && response.isVerified());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[OTP_VERIFY] OTP verification failed - errorType: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/otp/resend")
    public ResponseEntity<Map<String, Object>> resendOtp(@RequestParam String email) {
        log.info("[OTP_RESEND] Received OTP resend request");
        try {
            Map<String, Object> response = otpService.resendOtp(email);
            log.info("[OTP_RESEND] OTP resend completed - success: {}", response != null && Boolean.TRUE.equals(response.get("success")));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[OTP_RESEND] OTP resend failed - errorType: {}", e.getClass().getSimpleName());
            throw e;
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestParam String email) {
        log.info("[FORGOT_PASSWORD] Received forgot password request");
        try {
            var result=otpService.generateAndSendOtp(email);
            if(result==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OTP service temporarily unavailable");
            if(!Boolean.TRUE.equals(result.get("success")))return ResponseEntity.ok(result);
            log.info("[FORGOT_PASSWORD] OTP notification request accepted");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mã xác thực đã được gửi đến email của bạn"
            ));
        } catch (Exception e) {
            log.error("[FORGOT_PASSWORD] Forgot password failed - errorType: {}", e.getClass().getSimpleName());
            return failure(e);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        log.info("[RESET_PASSWORD] Received reset password request");
        try {
            String email = request.get("email");
            String otpCode = request.get("otpCode");
            String newPassword = request.get("newPassword");

            if (email == null || otpCode == null || newPassword == null) {
                log.warn("[RESET_PASSWORD] Required fields are missing");
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Email, mã OTP và mật khẩu mới là bắt buộc"
                ));
            }

            // Verify OTP
            OtpVerificationResponse otpResponse = otpService.verifyOtp(email, otpCode);
            if(otpResponse==null)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OTP service temporarily unavailable");
            if (!otpResponse.isVerified()) {
                log.warn("[RESET_PASSWORD] OTP verification was rejected");
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", otpResponse.getMessage()
                ));
            }

            // Reset password
            authService.resetPassword(email, newPassword);
            log.info("[RESET_PASSWORD] Password reset completed successfully");

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Mật khẩu đã được thay đổi thành công"
            ));
        } catch (Exception e) {
            log.error("[RESET_PASSWORD] Reset password failed - errorType: {}", e.getClass().getSimpleName());
            return failure(e);
        }
    }

    private ResponseEntity<Map<String,Object>> failure(Exception error) {
        HttpStatus status=error instanceof ResponseStatusException typed ? HttpStatus.resolve(typed.getStatusCode().value()) : HttpStatus.INTERNAL_SERVER_ERROR;
        if(status==null)status=HttpStatus.INTERNAL_SERVER_ERROR;
        String message=status.getReasonPhrase();
        if(status.is4xxClientError() && error instanceof ResponseStatusException typed && typed.getReason()!=null && !typed.getReason().isBlank())
            message=typed.getReason();
        return ResponseEntity.status(status).body(Map.of("success",false,"message",message));
    }
}
