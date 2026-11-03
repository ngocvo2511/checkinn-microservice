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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"hotel-service,HOTEL_SERVICE,CheckReviewEligibility,true",
            "hotel-service,HOTEL_SERVICE,GetBooking,false", "hotel-service,HOTEL_SERVICE,CreateBooking,false",
            "user-service,USER_SERVICE,GetLoyaltyBookingState,true", "user-service,USER_SERVICE,GetBooking,false",
            "user-service,USER_SERVICE,CreateBooking,false"})
    void signedMachineSubjectsAreRestrictedToNarrowRpcs(String subject, String role, String method, boolean allowed) {
        var descriptor = call.getMethodDescriptor().toBuilder()
                .setFullMethodName("booking.BookingGrpcService/" + method).build();
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        String secret = "test-key-at-least-thirty-two-bytes-long-123456";
        var tokens = new JwtService(mock(com.example.bookingservice.security.TokenRevocationService.class), secret);
        String credential = io.jsonwebtoken.Jwts.builder().setSubject(subject).claim("role", role)
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        io.jsonwebtoken.SignatureAlgorithm.HS256).compact();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + credential);
        when(next.startCall(any(), any())).thenAnswer(invocation -> {
            assertThat(UserContextInterceptor.ACTOR.get().getName()).isEqualTo(subject);
            return new ServerCall.Listener<String>() {};
        });
        new UserContextInterceptor(tokens).interceptCall(call, headers, next);
        if (allowed) {
            verify(next).startCall(call, headers);
            verify(call, never()).close(any(), any());
        } else {
            verifyNoInteractions(next);
            verify(call).close(argThat(status -> status.getCode() == Status.Code.UNAUTHENTICATED), any());
        }
    }

    @Test void verifiedActorPropagatesToListenerAndDoesNotLeakAfterCall() {
        UUID principal = UUID.randomUUID();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer valid");
        headers.put(Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER), UUID.randomUUID().toString());
        when(jwt.validateToken("valid")).thenReturn(true);
        when(jwt.extractSubject("valid")).thenReturn(principal.toString());
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
