package com.example.userservice.service;

import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import com.example.bookingservice.grpc.LoyaltyBookingStateRequest;
import com.example.userservice.security.JwtService;
import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PointsBookingStateClient {
    private final JwtService tokens;
    private final ManagedChannel channel;
    private BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub bookings;

    public PointsBookingStateClient(JwtService tokens, @Value("${booking.grpc.address:localhost:9093}") String address) {
        this.tokens = tokens;
        channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
        bookings = BookingGrpcServiceGrpc.newBlockingStub(channel);
    }

    public String getState(String bookingId, UUID userId) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + tokens.generatePointsRecoveryToken());
        try {
            String state = bookings.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(3, TimeUnit.SECONDS).getLoyaltyBookingState(LoyaltyBookingStateRequest.newBuilder()
                            .setBookingId(bookingId).setUserId(userId.toString()).build()).getState();
            return Set.of("MISSING", "CANCELLED", "ACTIVE").contains(state) ? state : "UNKNOWN";
        } catch (StatusRuntimeException ex) {
            log.warn("Points booking lookup unavailable (grpc={})", ex.getStatus().getCode());
            return "UNKNOWN";
        }
    }

    @PreDestroy public void close() { channel.shutdownNow(); }
}
