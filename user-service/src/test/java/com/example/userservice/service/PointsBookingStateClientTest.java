package com.example.userservice.service;

import com.example.bookingservice.grpc.*;
import com.example.userservice.security.JwtService;
import io.grpc.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointsBookingStateClientTest {
    @Test void scopedCredentialTupleAndDeadlineAreSentWithoutExposingBookingData() {
        var tokens = mock(JwtService.class); when(tokens.generatePointsRecoveryToken()).thenReturn("test-service-token");
        var channel = new StateChannel(Status.OK, "MISSING");
        var client = new PointsBookingStateClient(tokens, "localhost:9093");
        try {
            ReflectionTestUtils.setField(client, "bookings", BookingGrpcServiceGrpc.newBlockingStub(channel));
            UUID user = UUID.randomUUID();
            assertThat(client.getState("booking", user)).isEqualTo("MISSING");
            assertThat(channel.request).isEqualTo(LoyaltyBookingStateRequest.newBuilder().setBookingId("booking").setUserId(user.toString()).build());
            assertThat(channel.authorization).isEqualTo("Bearer test-service-token");
            assertThat(channel.options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)).isBetween(1L, 3000L);
        } finally { client.close(); }
    }

    @Test void outageAndUnrecognizedStateNeverMeanMissing() {
        var tokens = mock(JwtService.class); when(tokens.generatePointsRecoveryToken()).thenReturn("test-service-token");
        var client = new PointsBookingStateClient(tokens, "localhost:9093");
        try {
            for (Status status : new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.UNAUTHENTICATED, Status.UNIMPLEMENTED}) {
                ReflectionTestUtils.setField(client, "bookings", BookingGrpcServiceGrpc.newBlockingStub(new StateChannel(status, "")));
                assertThat(client.getState("booking", UUID.randomUUID())).isEqualTo("UNKNOWN");
            }
            ReflectionTestUtils.setField(client, "bookings", BookingGrpcServiceGrpc.newBlockingStub(new StateChannel(Status.OK, "unexpected")));
            assertThat(client.getState("booking", UUID.randomUUID())).isEqualTo("UNKNOWN");
        } finally { client.close(); }
    }

    @Test void recoveryTokenHasOnlyServiceIdentityAndShortLifetime() {
        var jwt = new JwtService(mock(com.example.userservice.security.TokenRevocationService.class), "test-key-at-least-thirty-two-bytes-long-123456");
        var claims = jwt.parseToken(jwt.generatePointsRecoveryToken()).getBody();
        assertThat(claims.getSubject()).isEqualTo("user-service");
        assertThat(claims.get("role", String.class)).isEqualTo("USER_SERVICE");
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(300000L);
    }

    private static class StateChannel extends Channel {
        final Status status; final String state;
        Object request; String authorization; CallOptions options;
        StateChannel(Status status, String state) { this.status = status; this.state = state; }
        @Override public String authority() { return "test"; }
        @Override public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions options) {
            this.options = options;
            return new ClientCall<>() {
                Listener<RespT> listener;
                @Override public void start(Listener<RespT> listener, Metadata headers) {
                    this.listener = listener; authorization = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
                }
                @Override public void request(int count) {}
                @Override public void cancel(String message, Throwable cause) {}
                @Override public void sendMessage(ReqT message) { request = message; }
                @SuppressWarnings("unchecked") @Override public void halfClose() {
                    if (status.isOk()) listener.onMessage((RespT) LoyaltyBookingStateResponse.newBuilder().setState(state).build());
                    listener.onClose(status, new Metadata());
                }
            };
        }
    }
}
