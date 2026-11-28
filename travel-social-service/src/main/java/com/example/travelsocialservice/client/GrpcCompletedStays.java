package com.example.travelsocialservice.client;

import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import com.example.bookingservice.grpc.CompletedStayRequest;
import com.example.bookingservice.grpc.CompletedStayResponse;
import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.security.ServiceTokenProvider;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcCompletedStays implements CompletedStays {

    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ServiceTokenProvider tokens;
    private final long deadlineMillis;

    @GrpcClient("booking-service")
    private BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub bookingStub;

    @Autowired
    public GrpcCompletedStays(ServiceTokenProvider tokens, SocialProperties properties) {
        this.tokens = tokens;
        this.deadlineMillis = properties.client().readTimeout().toMillis();
    }

    GrpcCompletedStays(ServiceTokenProvider tokens, SocialProperties properties,
                       BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub bookingStub) {
        this(tokens, properties);
        this.bookingStub = bookingStub;
    }

    @Override
    public Optional<CompletedStay> find(UUID bookingId) {
        CompletedStayResponse response;
        try {
            response = authorizedStub().getCompletedStay(
                    CompletedStayRequest.newBuilder().setBookingId(bookingId.toString()).build());
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new StayLookupUnavailableException("Completed-stay lookup failed: " + ex.getStatus().getCode(), ex);
        } catch (RuntimeException ex) {
            throw new StayLookupUnavailableException("Completed-stay lookup failed", ex);
        }
        try {
            return Optional.of(new CompletedStay(UUID.fromString(response.getUserId()), UUID.fromString(response.getHotelId()),
                    LocalDate.parse(response.getCheckInDate()), LocalDate.parse(response.getCheckOutDate())));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new StayLookupUnavailableException("booking-service returned a malformed completed stay", ex);
        }
    }

    private BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub authorizedStub() {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, tokens.authorization());
        return bookingStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(deadlineMillis, TimeUnit.MILLISECONDS);
    }
}
