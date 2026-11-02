package com.example.hotelservice.Review.service;

import com.example.bookingservice.grpc.BookingGrpcServiceGrpc;
import com.example.bookingservice.grpc.ReviewEligibilityRequest;
import com.example.hotelservice.security.HotelServiceTokenProvider;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ReviewEligibilityClient {
    private final HotelServiceTokenProvider tokens;

    @GrpcClient("booking-service")
    private BookingGrpcServiceGrpc.BookingGrpcServiceBlockingStub bookings;

    public boolean isEligible(UUID bookingId, UUID guestId, UUID hotelId) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), tokens.authorization());
        try {
            return bookings.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .checkReviewEligibility(ReviewEligibilityRequest.newBuilder()
                            .setBookingId(bookingId.toString()).setGuestId(guestId.toString())
                            .setHotelId(hotelId.toString()).build()).getEligible();
        } catch (StatusRuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Booking review eligibility unavailable", ex);
        }
    }
}
