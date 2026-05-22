package com.example.authservice.service;

import com.checkinn.auth.grpc.*;
import com.checkinn.user.grpc.UserResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;


@GrpcService
@RequiredArgsConstructor
@Slf4j
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;

    @Override
    public void login(AuthLoginRequest request,
                      StreamObserver<AuthLoginResponse> responseObserver) {

        try {
            log.info("[GRPC_LOGIN] Received login request - usernameOrEmail: {}", request.getUsernameOrEmail());
            // 1. Gọi user-service để verify password
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            // 2. Sinh token
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());
            log.info("[GRPC_LOGIN] User authenticated successfully - userId: {}, email: {}, role: {}", user.getId(), user.getEmail(), user.getRole());

            // 3. Trả về
            AuthLoginResponse response = AuthLoginResponse.newBuilder()
                    .setToken(token)
                    .setUserId(user.getId())
                    .setEmail(user.getEmail())
                    .setFullName(user.getFullName())
                    .setRole(user.getRole())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[GRPC_LOGIN] Login response completed - userId: {}", user.getId());

        } catch (Exception e) {
            log.error("[GRPC_LOGIN] Login failed - usernameOrEmail: {}, error: {}", request.getUsernameOrEmail(), e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request,
                              StreamObserver<ValidateTokenResponse> responseObserver) {

        try {
            log.info("[GRPC_VALIDATE_TOKEN] Received token validation request");
            var claims = jwtService.parseToken(request.getToken());

            UUID userId = UUID.fromString(claims.getBody().getSubject());
            String role = claims.getBody().get("role", String.class);

            ValidateTokenResponse response = ValidateTokenResponse.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setRole(role)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[GRPC_VALIDATE_TOKEN] Token validated successfully - userId: {}, role: {}", userId, role);

        } catch (Exception e) {
            log.error("[GRPC_VALIDATE_TOKEN] Token validation failed - error: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}