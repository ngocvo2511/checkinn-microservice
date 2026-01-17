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

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;

    public AuthResponseDto register(RegisterRequestDto request, UserRole userRole) {
        try {
            // Gọi user-service để tạo user
            UserResponse user = userGrpcClient.register(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    userRole
            );

            // Sinh token
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration failed: " + msg, e);
        } catch (Exception e) {
            System.err.println("[AuthService] Error during registration: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    public AuthResponseDto login(LoginRequestDto request) {
        try {
            // Gọi user-service để verify password
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            // Sinh token
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

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
            System.err.println("[AuthService] gRPC error: " + e.getStatus().getCode() + " - " + msg);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msg, e);
        } catch (Exception e) {
            System.err.println("[AuthService] Error during login: " + e.getMessage());
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
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
        try {
            if (newPassword == null || newPassword.length() < 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải có ít nhất 6 ký tự");
            }

            // Gọi user-service để reset password
            userGrpcClient.resetPassword(email, newPassword);

        } catch (StatusRuntimeException e) {
            String msg = extractGrpcMessage(e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password reset failed: " + msg, e);
        } catch (Exception e) {
            System.err.println("[AuthService] Error during password reset: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
