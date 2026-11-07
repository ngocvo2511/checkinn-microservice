package com.example.hotelservice.Hotel.service;

import com.checkinn.user.grpc.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class UserGrpcDeadlineTest {
    @Test void realStalledRpcHasConfiguredDeadlineAndMapsToDependencyFailure() throws Exception {
        AtomicBoolean stalled=new AtomicBoolean(false);
        Server server=ServerBuilder.forPort(0).addService(new UserServiceGrpc.UserServiceImplBase(){
            @Override public void getUserById(GetUserRequest request,StreamObserver<UserResponse> observer){
                if(stalled.get()) return;
                observer.onNext(UserResponse.newBuilder().setId(request.getId()).build());observer.onCompleted();
            }
        }).build().start();
        ManagedChannel channel=ManagedChannelBuilder.forAddress("127.0.0.1",server.getPort()).usePlaintext().build();
        try {
            var tokens=org.mockito.Mockito.mock(com.example.hotelservice.security.HotelServiceTokenProvider.class);
            org.mockito.Mockito.when(tokens.authorization()).thenReturn("Bearer synthetic-profile-token");
            UserGrpcClient client=new UserGrpcClient(tokens);ReflectionTestUtils.setField(client,"userStub",UserServiceGrpc.newBlockingStub(channel));
            UUID id=UUID.randomUUID();assertEquals(id.toString(),client.GetUserById(id).getId());
            ReflectionTestUtils.setField(client,"deadlineMs",150L);stalled.set(true);
            long before=System.nanoTime();
            var failure=assertThrows(ResponseStatusException.class,()->client.GetUserById(id));
            assertEquals(503,failure.getStatusCode().value());assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<3000);
            ReflectionTestUtils.setField(client,"deadlineMs",0L);assertThrows(IllegalStateException.class,()->client.GetUserById(id));
        } finally {channel.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);server.shutdownNow().awaitTermination(5,TimeUnit.SECONDS);}
    }
}
