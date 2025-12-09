package com.example.userservice.service;

import com.checkinn.user.grpc.*;
import com.example.userservice.model.Role;
import com.example.userservice.model.User;
import com.example.userservice.model.UserProfile;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;


@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final UserService userService;

    @Override
    public void registerUser(RegisterRequest request,
                             StreamObserver<UserResponse> responseObserver) {

        try {
            User user = userService.registerUser(
                    com.example.userservice.dto.RegisterRequest.builder()
                            .username(request.getUsername())
                            .email(request.getEmail())
                            .password(request.getPassword())
                            .fullName(request.getFullName())
                            .build(),
                    (request.getRole()==UserRole.OWNER) ? Role.OWNER : Role.CUSTOMER
            );

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(user.getProfile().getFullName())
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getUserById(GetUserRequest request,
                            StreamObserver<UserResponse> responseObserver) {

        try {
            User user = userService.getUserById(request.getId());

            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .setFullName(user.getProfile().getFullName())
                    .setRole(user.getRole().name())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void loginUser(LoginRequest request,
                          StreamObserver<UserResponse> responseObserver) {

        try {
            var result = userService.login(
                    request.getUsernameOrEmail(),
                    request.getPassword()
            );

            User user = result.getUser();
            UserProfile profile = result.getProfile();

            UserResponse.Builder res = UserResponse.newBuilder()
                    .setId(user.getId())
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
            responseObserver.onError(e);
        }
    }

}
