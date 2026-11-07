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
    private final com.example.hotelservice.security.HotelServiceTokenProvider tokens;
    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    @org.springframework.beans.factory.annotation.Value("${clients.user-grpc-deadline-ms:5000}")
    private long deadlineMs = 5000;

    public UserResponse GetUserById(UUID ownerId) {

        GetUserRequest request =
                GetUserRequest.newBuilder()
                        .setId(ownerId.toString())
                        .build();

        if (deadlineMs <= 0) throw new IllegalStateException("User RPC deadline must be positive");
        try {
            io.grpc.Metadata headers=new io.grpc.Metadata();
            headers.put(io.grpc.Metadata.Key.of("authorization",io.grpc.Metadata.ASCII_STRING_MARSHALLER),tokens.authorization());
            return userStub.withInterceptors(io.grpc.stub.MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(deadlineMs, java.util.concurrent.TimeUnit.MILLISECONDS).getUserById(request);
        } catch (io.grpc.StatusRuntimeException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "User profile lookup unavailable");
        }
    }
}
