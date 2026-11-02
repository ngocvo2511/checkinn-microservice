package com.example.hotelservice.Review.service;

import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import com.example.bookingservice.grpc.ReviewEligibilityRequest;
import com.example.bookingservice.grpc.ReviewEligibilityResponse;
import com.example.hotelservice.security.HotelServiceTokenProvider;
import io.grpc.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class ReviewEligibilityClientTest {
    @Test void sendsExactTupleServiceCredentialAndBoundedDeadline() {
        UUID booking = UUID.randomUUID(), guest = UUID.randomUUID(), hotel = UUID.randomUUID();
        var channel = new EligibilityChannel(Status.OK, true);
        var client = client(channel);
        assertThat(client.isEligible(booking, guest, hotel)).isTrue();
        assertThat(channel.request).isEqualTo(ReviewEligibilityRequest.newBuilder().setBookingId(booking.toString())
                .setGuestId(guest.toString()).setHotelId(hotel.toString()).build());
        assertThat(channel.authorization).startsWith("Bearer ");
        assertThat(channel.options.getDeadline()).isNotNull();
        assertThat(channel.options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)).isBetween(1L, 5000L);
    }

    @Test void falseIsIneligibleAndEveryRpcFailureRemainsUnavailable() {
        assertThat(client(new EligibilityChannel(Status.OK, false))
                .isEligible(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())).isFalse();
        for (Status status : new Status[]{Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.UNAUTHENTICATED,
                Status.PERMISSION_DENIED, Status.UNIMPLEMENTED}) {
            assertThatThrownBy(() -> client(new EligibilityChannel(status, false))
                    .isEligible(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        }
    }

    private ReviewEligibilityClient client(EligibilityChannel channel) {
        var client = new ReviewEligibilityClient(new HotelServiceTokenProvider("test-key-with-at-least-thirty-two-bytes-123456"));
        ReflectionTestUtils.setField(client, "bookings", BookingGrpcServiceGrpc.newBlockingStub(channel));
        return client;
    }

    private static class EligibilityChannel extends Channel {
        final Status status;
        final boolean eligible;
        CallOptions options;
        Object request;
        String authorization;
        EligibilityChannel(Status status, boolean eligible) { this.status = status; this.eligible = eligible; }
        @Override public String authority() { return "test"; }
        @Override public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method, CallOptions options) {
            this.options = options;
            return new ClientCall<>() {
                Listener<RespT> listener;
                @Override public void start(Listener<RespT> listener, Metadata headers) {
                    this.listener = listener;
                    authorization = headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
                }
                @Override public void request(int count) {}
                @Override public void cancel(String message, Throwable cause) {}
                @Override public void sendMessage(ReqT message) { request = message; }
                @SuppressWarnings("unchecked")
                @Override public void halfClose() {
                    if (status.isOk()) listener.onMessage((RespT) ReviewEligibilityResponse.newBuilder().setEligible(eligible).build());
                    listener.onClose(status, new Metadata());
                }
            };
        }
    }
}
