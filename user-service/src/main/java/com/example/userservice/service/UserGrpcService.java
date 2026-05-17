package com.example.userservice.service;

import com.checkinn.user.grpc.LoginRequest;
import com.checkinn.user.grpc.GetUserRequest;
import com.checkinn.user.grpc.UserResponse;
import com.checkinn.user.grpc.UserRole;
import com.checkinn.user.grpc.UserServiceGrpc;
import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(UserGrpcService.class);

    private final UserService userService;

    @Override
    public void registerUser(com.checkinn.user.grpc.RegisterRequest request,
                             StreamObserver<UserResponse> responseObserver) {

        logger.info("[gRPC_REGISTER] Register request received - username: {}, email: {}, role: {}",
                request.getUsername(), request.getEmail(), request.getRole());

        try {
            logger.debug("[gRPC_REGISTER] Processing registration for username: {}", request.getUsername());
            User user = userService.registerUser(
                    com.example.userservice.dto.RegisterRequest.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(request.getPassword())
                            .fullName(request.getFullName())
                            .build(),
                    (request.getRole()== UserRole.OWNER) ? Role.OWNER : Role.CUSTOMER
            );

            logger.info("[gRPC_REGISTER] User registered successfully via gRPC - userId: {}, username: {}",
                    user.getId(), user.getUsername());

            UserResponse response = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(user.getProfile().getFullName())
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("[gRPC_REGISTER] Registration error - username: {}, email: {}, error: {}",
                    request.getUsername(), request.getEmail(), e.getMessage(), e);
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }


    @Override
    public void getUserById(GetUserRequest request,
                            StreamObserver<UserResponse> responseObserver) {

        try {
            User user = userService.getUserById(UUID.fromString(request.getId()));

            UserResponse response = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(user.getProfile().getFullName())
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("[UserGrpcService] GetUserById error: " + e.getMessage());
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void loginUser(LoginRequest request,
                          StreamObserver<UserResponse> responseObserver) {

        logger.info("[gRPC_LOGIN] Login request received - usernameOrEmail: {}",
                request.getUsernameOrEmail());

        try {
            logger.debug("[gRPC_LOGIN] Processing login for: {}", request.getUsernameOrEmail());
            var result = userService.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            User user = result.getUser();
            UserProfile profile = result.getProfile();

            logger.info("[gRPC_LOGIN] User authenticated successfully via gRPC - userId: {}, username: {}, role: {}",
                    user.getId(), user.getUsername(), user.getRole().name());

            UserResponse.Builder res = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setRole(user.getRole().name());

            if (profile != null && profile.getFullName() != null) {
                res.setFullName(profile.getFullName());
            } else {
                res.setFullName("");
            }

            responseObserver.onNext(res.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error("[gRPC_LOGIN] Login error - usernameOrEmail: {}, error: {}",
                    request.getUsernameOrEmail(), e.getMessage(), e);
            responseObserver.onError(
                Status.UNAUTHENTICATED
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void resetPassword(com.checkinn.user.grpc.ResetPasswordRequest request,
                             StreamObserver<com.google.protobuf.Empty> responseObserver) {

        try {
            userService.resetPassword(request.getEmail(), request.getNewPassword());

            responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            System.err.println("[UserGrpcService] Reset password error: " + e.getMessage());
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .withCause(e)
                    .asRuntimeException()
            );
        }
    }
}
