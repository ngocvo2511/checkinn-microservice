package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import com.example.authservice.dto.AuthResponseDto;
import com.example.authservice.dto.LoginRequestDto;
import com.example.authservice.dto.RegisterRequestDto;
import io.jsonwebtoken.JwtException;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    public AuthResponseDto register(RegisterRequestDto request, UserRole userRole) {
        logger.debug("[REGISTER] User registration attempt - username: {}, email: {}, role: {}",
                request.getUsername(), request.getEmail(), userRole);
        try {
            validatePassword(request.getPassword(), "REGISTER", request.getEmail());

            logger.debug("[REGISTER] Calling user-service to create user account");
            UserResponse user = userGrpcClient.register(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    userRole
            );

            logger.debug("[REGISTER] User account created successfully - userId: {}, email: {}",
                    user.getId(), user.getEmail());
            MDC.put("userId", user.getId());

            logger.debug("[REGISTER] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

            logger.debug("[REGISTER] Registration successful - userId: {}, email: {}",
                    user.getId(), user.getEmail());

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
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("[REGISTER] Exception during registration - username: {}, email: {}, error: {}",
                    request.getUsername(), request.getEmail(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } finally {
            MDC.remove("userId");
        }
    }

    public AuthResponseDto login(LoginRequestDto request) {
        logger.debug("[LOGIN] Login attempt - usernameOrEmail: {}", request.getUsernameOrEmail());
        try {
            logger.debug("[LOGIN] Calling user-service to verify credentials for: {}", request.getUsernameOrEmail());
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            logger.debug("[LOGIN] User authenticated successfully - userId: {}, username: {}, role: {}",
                    user.getId(), user.getUsername(), user.getRole());
            MDC.put("userId", user.getId());

            logger.debug("[LOGIN] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

            logger.debug("[LOGIN] Login successful - userId: {}, email: {}", user.getId(), user.getEmail());

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

    public void resetPassword(String email, String newPassword) {
        logger.debug("[RESET_PASSWORD] Password reset attempt - email: {}", email);
        try {
            validatePassword(newPassword, "RESET_PASSWORD", email);

            logger.debug("[RESET_PASSWORD] Calling user-service to reset password for email: {}", email);
            userGrpcClient.resetPassword(email, newPassword);

            logger.debug("[RESET_PASSWORD] Password reset successful - email: {}", email);

        } catch (StatusRuntimeException e) {
            String msg = extractGrpcMessage(e);
            logger.error("[RESET_PASSWORD] gRPC error during password reset - email: {}, message: {}",
                    email, msg, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đặt lại mật khẩu thất bại: " + msg, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("[RESET_PASSWORD] Exception during password reset - email: {}, error: {}",
                    email, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    public void logout(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        String token = authHeader.substring(7);
        java.util.Date expiresAt;
        try {
            expiresAt = jwtService.extractExpiration(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bearer token", e);
        }

        try {
            tokenRevocationService.revoke(token, expiresAt);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("[LOGOUT] Failed to revoke token: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Logout is temporarily unavailable because token revocation storage cannot be reached", e);
        }
    }

    private String extractGrpcMessage(StatusRuntimeException e) {
        String desc = e.getStatus().getDescription();
        if (desc != null && !desc.isBlank()) {
            return desc;
        }
        return e.getStatus().getCode().name();
    }

    private void validatePassword(String password, String action, String email) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            logger.warn("[{}] Invalid password format - email: {}, passwordLength: {}",
                    action, email, password != null ? password.length() : 0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }
}
