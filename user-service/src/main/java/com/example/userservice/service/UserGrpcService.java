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

        logger.info("[gRPC_REGISTER] Register request received");

        try {
            User user = userService.registerUser(
                    com.example.userservice.dto.RegisterRequest.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(request.getPassword())
                            .fullName(request.getFullName())
                            .build(),
                    (request.getRole()== UserRole.OWNER) ? Role.OWNER : Role.CUSTOMER
            );

            logger.info("[gRPC_REGISTER] User registered successfully via gRPC - userId: {}", user.getId());

            UserResponse response = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(fullName(user))
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (com.example.userservice.security.SessionStoreUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Session verification unavailable").asRuntimeException());
        } catch (Exception e) {
            if (dependencyUnavailable(e, responseObserver)) return;
            logger.warn("[gRPC_REGISTER] Registration rejected - errorType: {}", e.getClass().getSimpleName());
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        }
    }


    @Override
    public void getUserById(GetUserRequest request,
                            StreamObserver<UserResponse> responseObserver) {

        if (!com.example.userservice.security.UserRpcAccess.canRead(request.getId())) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("User profile access denied").asRuntimeException());
            return;
        }

        try {
            User user = userService.getUserById(UUID.fromString(request.getId()));

            UserResponse response = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(fullName(user))
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (com.example.userservice.security.SessionStoreUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Session verification unavailable").asRuntimeException());
        } catch (Exception e) {
            if (dependencyUnavailable(e, responseObserver)) return;
            logger.warn("[gRPC_GET_USER_BY_ID_ERROR] Profile request rejected - errorType: {}", e.getClass().getSimpleName());
            responseObserver.onError(
                Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    @Override
    public void loginUser(LoginRequest request,
                          StreamObserver<UserResponse> responseObserver) {

        logger.info("[gRPC_LOGIN] Login request received");

        try {
            var result = userService.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            User user = result.getUser();
            UserProfile profile = result.getProfile();

            logger.info("[gRPC_LOGIN] User authenticated successfully via gRPC - userId: {}, role: {}",
                    user.getId(), user.getRole().name());

            UserResponse.Builder res = UserResponse.newBuilder()
                    .setId(String.valueOf(user.getId()))
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setRole(user.getRole().name())
                    .setSessionGeneration(result.getSessionGeneration());

            if (profile != null && profile.getFullName() != null) {
                res.setFullName(profile.getFullName());
            } else {
                res.setFullName("");
            }

            responseObserver.onNext(res.build());
            responseObserver.onCompleted();

        } catch (com.example.userservice.security.SessionStoreUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Session verification unavailable").asRuntimeException());
        } catch (Exception e) {
            if (dependencyUnavailable(e, responseObserver)) return;
            logger.warn("[gRPC_LOGIN] Login rejected - errorType: {}", e.getClass().getSimpleName());
            responseObserver.onError(
                Status.UNAUTHENTICATED
                    .withDescription(e.getMessage())
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

        } catch (com.example.userservice.security.SessionStoreUnavailableException e) {
            responseObserver.onError(Status.UNAVAILABLE.withDescription("Session verification unavailable").asRuntimeException());
        } catch (Exception e) {
            if (dependencyUnavailable(e, responseObserver)) return;
            logger.warn("[gRPC_RESET_PASSWORD_ERROR] Reset rejected - errorType: {}",e.getClass().getSimpleName());
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Password reset could not be completed").asRuntimeException());
        }
    }

    private static String fullName(User user) {
        UserProfile profile = user.getProfile();
        return profile == null || profile.getFullName() == null ? "" : profile.getFullName();
    }

    private static boolean dependencyUnavailable(Exception failure, StreamObserver<?> observer) {
        // Delivery failures can be wrapped by the service; inspect causes without exposing payloads.
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof org.springframework.dao.DataAccessException
                    || cause instanceof org.springframework.transaction.TransactionException
                    || cause instanceof org.springframework.amqp.AmqpException
                    || cause instanceof java.sql.SQLException
                    || cause instanceof com.example.userservice.security.SessionStoreUnavailableException) {
                logger.error("[gRPC_DEPENDENCY_UNAVAILABLE] Account operation failed - errorType: {}",
                        cause.getClass().getSimpleName());
                observer.onError(Status.UNAVAILABLE.withDescription("Account service temporarily unavailable").asRuntimeException());
                return true;
            }
        }
        return false;
    }
}
