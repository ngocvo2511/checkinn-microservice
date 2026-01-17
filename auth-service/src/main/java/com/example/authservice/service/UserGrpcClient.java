package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class UserGrpcClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse login(String usernameOrEmail, String password) {

        LoginRequest request = LoginRequest.newBuilder()
                .setUsernameOrEmail(usernameOrEmail)
                .setPassword(password)
                .build();

        return userStub.loginUser(request);
    }

    public UserResponse register(String username, String email, String password, String fullName, UserRole userRole) {

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setEmail(email)
                .setPassword(password)
                .setFullName(fullName)
                .setRole(userRole)
                .build();

        return userStub.registerUser(request);
    }

    public void resetPassword(String email, String newPassword) {
        ResetPasswordRequest request = ResetPasswordRequest.newBuilder()
                .setEmail(email)
                .setNewPassword(newPassword)
                .build();

        userStub.resetPassword(request);
    }
}