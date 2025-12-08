package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import com.example.authservice.dto.AuthResponseDto;
import com.example.authservice.dto.LoginRequestDto;
import com.example.authservice.dto.RegisterRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;

    public AuthResponseDto register(RegisterRequestDto request) {
        try {
            // Gọi user-service để tạo user
            UserResponse user = userGrpcClient.register(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName()
            );

            // Sinh token
            String token = jwtService.generateToken(user.getId(), user.getRole());

            // Trả về response
            return AuthResponseDto.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();

        } catch (Exception e) {
            String msg = extractGrpcMessage(e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registration failed: " + msg, e);
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
            String token = jwtService.generateToken(user.getId(), user.getRole());

            // Trả về response
            return AuthResponseDto.builder()
                    .token(token)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .fullName(user.getFullName())
                    .role(user.getRole())
                    .build();

        } catch (Exception e) {
            String msg = extractGrpcMessage(e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login failed: " + msg, e);
        }
    }

    private String extractGrpcMessage(Exception e) {
        if (e instanceof StatusRuntimeException sre) {
            String desc = sre.getStatus().getDescription();
            if (desc != null && !desc.isBlank()) {
                return desc;
            }
            return sre.getStatus().getCode().name();
        }
        return e.getMessage();
    }
}
