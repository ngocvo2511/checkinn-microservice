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

    public UserResponse register(String username, String email, String password, String fullName) {

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setEmail(email)
                .setPassword(password)
                .setFullName(fullName)
                .build();

        return userStub.registerUser(request);
    }
}