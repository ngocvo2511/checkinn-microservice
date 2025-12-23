package com.example.hotelservice.Hotel.service;

import com.checkinn.user.grpc.GetUserRequest;
import com.checkinn.user.grpc.UserResponse;
import com.checkinn.user.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserGrpcClient {
    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserResponse GetUserById(UUID ownerId) {

        GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setId(ownerId.toString())
                        .build();

        return userStub.getUserById(request);
    }
}
