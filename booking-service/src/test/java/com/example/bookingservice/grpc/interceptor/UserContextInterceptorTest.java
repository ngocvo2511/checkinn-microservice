package com.example.bookingservice.grpc.interceptor;

import com.example.bookingservice.security.JwtService;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserContextInterceptorTest {
    private final JwtService jwt = mock(JwtService.class);
    private final ServerCall<String, String> call = mock(ServerCall.class);
    private final ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
    private final Metadata headers = new Metadata();
    private UserContextInterceptor interceptor;

    @BeforeEach void setUp() {
        interceptor = new UserContextInterceptor(jwt);
        MethodDescriptor<String, String> method = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("booking.BookingGrpcService/GetBooking")
                .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class)).build();
        when(call.getMethodDescriptor()).thenReturn(method);
    }

    @Test void userIdHeaderIsNotAuthentication() {
        headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        interceptor.interceptCall(call, headers, next);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }

    @Test void missingCredentialsAreRejected() {
        interceptor.interceptCall(call, headers, next);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }

    @Test void invalidOrRevokedBearerIsRejected() {
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer revoked");
        when(jwt.validateToken("revoked")).thenReturn(false);
        interceptor.interceptCall(call, headers, next);
        verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }

    @Test void verifiedActorPropagatesToListenerAndDoesNotLeakAfterCall() {
        UUID principal = UUID.randomUUID();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer valid");
        headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        when(jwt.validateToken("valid")).thenReturn(true);
        when(jwt.extractUserId("valid")).thenReturn(principal);
        when(jwt.extractRole("valid")).thenReturn("CUSTOMER");
        when(next.startCall(any(), any())).thenAnswer(invocation -> {
            assertThat(UserContextInterceptor.ACTOR.get().getName()).isEqualTo(principal.toString());
            return new ServerCall.Listener<String>() {
                @Override public void onHalfClose() {
                    assertThat(UserContextInterceptor.ACTOR.get().getName()).isEqualTo(principal.toString());
                }
            };
        });
        ServerCall.Listener<String> listener = interceptor.interceptCall(call, headers, next);
        listener.onHalfClose();
        assertThat(UserContextInterceptor.ACTOR.get()).isNull();
        verify(call, never()).close(any(), any());
    }
}
