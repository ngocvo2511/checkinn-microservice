package com.example.authservice.service;

import com.checkinn.auth.grpc.*;
import com.checkinn.user.grpc.UserResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;


@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final UserGrpcClient userGrpcClient;
    private final JwtService jwtService;

    @Override
    public void login(AuthLoginRequest request,
                      StreamObserver<AuthLoginResponse> responseObserver) {

        try {
            // 1. Gọi user-service để verify password
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            // 2. Sinh token
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole());

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

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request,
                              StreamObserver<ValidateTokenResponse> responseObserver) {

        try {
            var claims = jwtService.parseToken(request.getToken());

            UUID userId = UUID.fromString(claims.getBody().getSubject());
            String role = claims.getBody().get("role", String.class);

            ValidateTokenResponse response = ValidateTokenResponse.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setRole(role)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}