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
        logger.debug("[REGISTER] User registration attempt - role: {}", userRole);
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

            logger.debug("[REGISTER] User account created successfully - userId: {}", user.getId());
            MDC.put("userId", user.getId());

            logger.debug("[REGISTER] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole(), user.getSessionGeneration());

            logger.debug("[REGISTER] Registration successful - userId: {}", user.getId());

            return AuthResponseDto.builder()
                    .token(token)
                    .userId(UUID.fromString(user.getId()))
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .emailVerified(false)
                    .build();

        } catch (StatusRuntimeException e) {
            if(accountUnavailable(e))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"User account service temporarily unavailable");
            String msg = extractGrpcMessage(e);
            logger.error("[REGISTER] gRPC registration failed - status: {}", e.getStatus().getCode());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đăng ký thất bại: " + msg, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("[REGISTER] Registration failed - errorType: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable");
        } finally {
            MDC.remove("userId");
        }
    }

    public AuthResponseDto login(LoginRequestDto request) {
        if (request == null || request.getUsernameOrEmail() == null || request.getUsernameOrEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Login identity and password are required");
        logger.debug("[LOGIN] Login attempt");
        try {
            logger.debug("[LOGIN] Calling user-service to verify credentials");
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            logger.debug("[LOGIN] User authenticated successfully - userId: {}", user.getId());
            MDC.put("userId", user.getId());

            logger.debug("[LOGIN] Generating JWT token for userId: {}", user.getId());
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole(), user.getSessionGeneration());

            logger.debug("[LOGIN] Login successful - userId: {}", user.getId());

            return AuthResponseDto.builder()
                    .token(token)
                    .userId(UUID.fromString(user.getId()))
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();

        } catch (StatusRuntimeException e) {
            if(accountUnavailable(e))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"User account service temporarily unavailable");
            String msg = extractGrpcMessage(e);
            logger.warn("[LOGIN] Authentication failed - status: {}", e.getStatus().getCode());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg, e);
        } catch (Exception e) {
            logger.error("[LOGIN] Login failed - errorType: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable");
        } finally {
            MDC.remove("userId");
        }
    }

    public void resetPassword(String email, String newPassword) {
        logger.debug("[RESET_PASSWORD] Password reset attempt");
        try {
            validatePassword(newPassword, "RESET_PASSWORD", email);

            logger.debug("[RESET_PASSWORD] Calling user-service to reset password");
            userGrpcClient.resetPassword(email, newPassword);

            logger.debug("[RESET_PASSWORD] Password reset successful");

        } catch (StatusRuntimeException e) {
            if(accountUnavailable(e))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"User account service temporarily unavailable");
            String msg = extractGrpcMessage(e);
            logger.error("[RESET_PASSWORD] gRPC password reset failed - status: {}", e.getStatus().getCode());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đặt lại mật khẩu thất bại: " + msg, e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("[RESET_PASSWORD] Password reset failed - errorType: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Authentication temporarily unavailable");
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
            logger.error("[LOGOUT] Failed to revoke token - errorType: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Logout is temporarily unavailable because token revocation storage cannot be reached", e);
        }
    }

    private boolean accountUnavailable(StatusRuntimeException error) {
        return switch(error.getStatus().getCode()) {
            case UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, UNKNOWN -> true;
            default -> false;
        };
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
            logger.warn("[{}] Invalid password format", action);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự");
        }
    }
}
