package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import com.example.authservice.dto.AuthResponseDto;
import com.example.authservice.dto.LoginRequestDto;
import com.example.authservice.dto.RegisterRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;

    public AuthResponseDto register(RegisterRequestDto request, UserRole userRole) {
        logger.info("[REGISTER] User registration attempt - username: {}, email: {}, role: {}", 
                request.getUsername(), request.getEmail(), userRole);
        try {
            // Gọi user-service để tạo user
            logger.debug("[REGISTER] Calling user-service to create user account");
            UserResponse user = userGrpcClient.register(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    userRole
            );

            logger.info("[REGISTER] User account created successfully - userId: {}, email: {}", 
                    user.getId(), user.getEmail());
                MDC.put("userId", user.getId());

            // Sinh token
            logger.debug("[REGISTER] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

            logger.info("[REGISTER] Registration successful - userId: {}, email: {}", 
                    user.getId(), user.getEmail());

            // Trả về response
            return AuthResponseDto.builder()
                    .token(token)
                    .userId(UUID.fromString(user.getId()))
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .emailVerified(false)
                    .build();

        } catch (StatusRuntimeException e) {
            String msg = extractGrpcMessage(e);
            logger.error("[REGISTER] gRPC error during registration - username: {}, email: {}, error: {}", 
                    request.getUsername(), request.getEmail(), msg, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đăng ký thất bại: " + msg, e);
        } catch (Exception e) {
            logger.error("[REGISTER] Exception during registration - username: {}, email: {}, error: {}", 
                    request.getUsername(), request.getEmail(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } finally {
            MDC.remove("userId");
        }
    }

    public AuthResponseDto login(LoginRequestDto request) {
        logger.info("[LOGIN] Login attempt - usernameOrEmail: {}", request.getUsernameOrEmail());
        try {
            // Gọi user-service để verify password
            logger.debug("[LOGIN] Calling user-service to verify credentials for: {}", request.getUsernameOrEmail());
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            logger.info("[LOGIN] User authenticated successfully - userId: {}, username: {}, role: {}", 
                    user.getId(), user.getUsername(), user.getRole());
                MDC.put("userId", user.getId());

            // Sinh token
            logger.debug("[LOGIN] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

            logger.info("[LOGIN] Login successful - userId: {}, email: {}", user.getId(), user.getEmail());

            // Trả về response
            return AuthResponseDto.builder()
                    .token(token)
                    .userId(UUID.fromString(user.getId()))
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();

        } catch (StatusRuntimeException e) {
            String msg = extractGrpcMessage(e);
            logger.warn("[LOGIN] Authentication failed - usernameOrEmail: {}, gRPC code: {}, message: {}", 
                    request.getUsernameOrEmail(), e.getStatus().getCode(), msg, e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg, e);
        } catch (Exception e) {
            logger.error("[LOGIN] Exception during login - usernameOrEmail: {}, error: {}", 
                    request.getUsernameOrEmail(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        } finally {
            MDC.remove("userId");
        }
    }

    private String extractGrpcMessage(StatusRuntimeException e) {
        String desc = e.getStatus().getDescription();
        if (desc != null && !desc.isBlank()) {
            return desc;
        }
        // Lấy root cause từ metadata nếu có
        return e.getStatus().getCode().name();
    }

    public void resetPassword(String email, String newPassword) {
        logger.info("[RESET_PASSWORD] Password reset attempt - email: {}", email);
        try {
            if (newPassword == null || newPassword.length() < 6) {
                logger.warn("[RESET_PASSWORD] Invalid password format - email: {}, passwordLength: {}", 
                        email, newPassword != null ? newPassword.length() : 0);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải có ít nhất 6 ký tự");
            }

            logger.debug("[RESET_PASSWORD] Calling user-service to reset password for email: {}", email);
            // Gọi user-service để reset password
            userGrpcClient.resetPassword(email, newPassword);

            logger.info("[RESET_PASSWORD] Password reset successful - email: {}", email);

        } catch (StatusRuntimeException e) {
            String msg = extractGrpcMessage(e);
            logger.error("[RESET_PASSWORD] gRPC error during password reset - email: {}, message: {}", 
                    email, msg, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đặt lại mật khẩu thất bại: " + msg, e);
        } catch (Exception e) {
            logger.error("[RESET_PASSWORD] Exception during password reset - email: {}, error: {}", 
                    email, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
