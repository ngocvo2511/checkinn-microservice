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
    private final TokenRevocationService revocations;
    private static final java.util.Set<String> USER_ROLES=java.util.Set.of("USER","CUSTOMER","OWNER","ADMIN");

    @Override
    public void login(AuthLoginRequest request,
                      StreamObserver<AuthLoginResponse> responseObserver) {

        try {
            log.info("[GRPC_LOGIN] Received login request");
            // 1. Gọi user-service để verify password
            UserResponse user = userGrpcClient.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            // 2. Sinh token
            String token = jwtService.generateToken(UUID.fromString(user.getId()), user.getRole(), user.getSessionGeneration());
            log.info("[GRPC_LOGIN] User authenticated successfully - userId: {}", user.getId());

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
            log.error("[GRPC_LOGIN] Login failed - errorType: {}", e.getClass().getSimpleName());
            responseObserver.onError(e);
        }
    }

    @Override
    public void validateToken(ValidateTokenRequest request,
                              StreamObserver<ValidateTokenResponse> responseObserver) {

        try {
            log.info("[GRPC_VALIDATE_TOKEN] Received token validation request");
            var claims = jwtService.parseToken(request.getToken());
            if(claims.getBody().getSubject()==null || claims.getBody().getExpiration()==null
                    || !claims.getBody().getExpiration().after(new java.util.Date()))throw new IllegalArgumentException("Invalid user token");
            UUID userId = UUID.fromString(claims.getBody().getSubject());
            String role = claims.getBody().get("role", String.class);
            if(role==null || !USER_ROLES.contains(role))throw new IllegalArgumentException("Invalid user token");
            String generation=claims.getBody().get("sessionGeneration",String.class);
            if(generation!=null && !generation.isEmpty())UUID.fromString(generation);
            if(revocations.isRevoked(request.getToken(),userId,generation))throw new io.jsonwebtoken.JwtException("Revoked user token");

            ValidateTokenResponse response = ValidateTokenResponse.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setRole(role)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("[GRPC_VALIDATE_TOKEN] Token validated successfully - userId: {}, role: {}", userId, role);

        } catch (SessionStoreUnavailableException error) {
            log.warn("[GRPC_VALIDATE_TOKEN] Session store unavailable");
            responseObserver.onError(io.grpc.Status.UNAVAILABLE.withDescription("Session validation temporarily unavailable").asRuntimeException());
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException error) {
            log.warn("[GRPC_VALIDATE_TOKEN] User token rejected");
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED.withDescription("Invalid or revoked user token").asRuntimeException());
        } catch (Exception error) {
            log.error("[GRPC_VALIDATE_TOKEN] Token validation failed - errorType: {}", error.getClass().getSimpleName());
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Token validation failed").asRuntimeException());
        }
    }
}
