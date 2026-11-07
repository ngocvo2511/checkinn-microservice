package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@lombok.RequiredArgsConstructor
public class UserGrpcClient {

    private final JwtService jwt;
    @org.springframework.beans.factory.annotation.Value("${clients.user-grpc-deadline-ms:5000}")
    private long deadlineMs=5000;

    private UserServiceGrpc.UserServiceBlockingStub authenticated() {
        if(deadlineMs<=0)throw new IllegalStateException("User RPC deadline must be positive");
        io.grpc.Metadata metadata=new io.grpc.Metadata();
        metadata.put(io.grpc.Metadata.Key.of("authorization",io.grpc.Metadata.ASCII_STRING_MARSHALLER),"Bearer "+jwt.generateUserServiceToken());
        return userStub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(metadata))
                .withDeadlineAfter(deadlineMs,java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse login(String usernameOrEmail, String password) {

        LoginRequest request = LoginRequest.newBuilder()
                .setUsernameOrEmail(usernameOrEmail)
                .setPassword(password)
                .build();

        return authenticated().loginUser(request);
    }

    public UserResponse register(String username, String email, String password, String fullName, UserRole userRole) {

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setEmail(email)
                .setPassword(password)
                .setFullName(fullName)
                .setRole(userRole)
                .build();

        return authenticated().registerUser(request);
    }

    public void resetPassword(String email, String newPassword) {
        ResetPasswordRequest request = ResetPasswordRequest.newBuilder()
                .setEmail(email)
                .setNewPassword(newPassword)
                .build();

        authenticated().resetPassword(request);
    }
}