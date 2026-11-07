package com.example.authservice.service;

import com.checkinn.user.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UserRpcClientTest {
    @Test void allAccountRpcsCarrySignedShortLivedAuthIdentity() throws Exception {
        JwtService jwt=new JwtService();ReflectionTestUtils.setField(jwt,"secret","unit-only-auth-rpc-signing-key-not-production-01234567890123456789");jwt.init();
        AtomicInteger verified=new AtomicInteger();String id=UUID.randomUUID().toString();
        ServerInterceptor authenticate=new ServerInterceptor(){
            @Override public <ReqT,RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT,RespT> call,Metadata headers,ServerCallHandler<ReqT,RespT> next){
                String header=headers.get(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER));
                assertNotNull(header);var claims=jwt.parseToken(header.substring(7)).getBody();
                assertEquals("auth-service",claims.getSubject());assertEquals("AUTH_SERVICE",claims.get("role",String.class));
                assertTrue(claims.getExpiration().getTime()-System.currentTimeMillis()<=300000);verified.incrementAndGet();
                return next.startCall(call,headers);
            }
        };
        var service=new UserServiceGrpc.UserServiceImplBase(){
            private void user(StreamObserver<UserResponse> observer){observer.onNext(UserResponse.newBuilder().setId(id).setRole("CUSTOMER").build());observer.onCompleted();}
            @Override public void loginUser(LoginRequest request,StreamObserver<UserResponse> observer){user(observer);}
            @Override public void registerUser(RegisterRequest request,StreamObserver<UserResponse> observer){user(observer);}
            @Override public void resetPassword(ResetPasswordRequest request,StreamObserver<com.google.protobuf.Empty> observer){observer.onNext(com.google.protobuf.Empty.getDefaultInstance());observer.onCompleted();}
        };
        Server server=ServerBuilder.forPort(0).addService(ServerInterceptors.intercept(service,authenticate)).build().start();
        ManagedChannel channel=ManagedChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().build();
        try {
            var client=new UserGrpcClient(jwt);ReflectionTestUtils.setField(client,"userStub",UserServiceGrpc.newBlockingStub(channel));
            assertEquals(id,client.login("synthetic-user","synthetic-password").getId());
            assertEquals(id,client.register("synthetic-user","synthetic@example.invalid","synthetic-password","Synthetic",UserRole.USER).getId());
            client.resetPassword("synthetic@example.invalid","synthetic-password");assertEquals(3,verified.get());
            ReflectionTestUtils.setField(client,"deadlineMs",0L);assertThrows(IllegalStateException.class,()->client.login("synthetic-user","synthetic-password"));
        } finally {channel.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);server.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);}
    }
}
