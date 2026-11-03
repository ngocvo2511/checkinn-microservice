package com.example.hotelservice.security;

import com.example.hotelservice.Review.service.ReviewService;
import com.example.hotelservice.grpc.DeleteReviewRequest;
import com.example.hotelservice.grpc.CreateReviewRequest;
import com.example.hotelservice.grpc.ReviewResponse;
import com.example.hotelservice.grpc.ReviewGrpcServiceImpl;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewGrpcAuthorizationTest {
    @Test void suppliedBookingIsCheckedForSignedGuestInsteadOfAnyEarlierStay() {
        var reviews = mock(ReviewService.class); var endpoint = new ReviewGrpcServiceImpl(reviews);
        UUID actor = UUID.randomUUID(), hotel = UUID.randomUUID(), booking = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg", "HS256")
                .subject(actor.toString()).claim("role", "CUSTOMER").build();
        @SuppressWarnings("unchecked") StreamObserver<com.example.hotelservice.grpc.CheckUserReviewExistsResponse> observer = mock(StreamObserver.class);
        io.grpc.Context.current().withValue(com.example.hotelservice.grpc.interceptor.ReviewAuthenticationInterceptor.ACTOR, jwt).run(() ->
                endpoint.checkUserReviewExists(com.example.hotelservice.grpc.CheckUserReviewExistsRequest.newBuilder()
                        .setHotelId(hotel.toString()).setGuestId(UUID.randomUUID().toString()).setBookingId(booking.toString()).build(), observer));
        verify(reviews).getReviewByHotelGuestBooking(hotel, actor, booking);
        verify(reviews, never()).checkUserReviewExists(any(), any());
        verify(observer).onNext(argThat(result -> !result.getExists()));
    }

    @Test void unpublishedDetailAndResponseMapToNotFound() {
        var reviews = mock(ReviewService.class); var endpoint = new ReviewGrpcServiceImpl(reviews);
        when(reviews.getReviewById(any())).thenThrow(new jakarta.persistence.EntityNotFoundException("Review not found"));
        when(reviews.getReviewResponse(any())).thenThrow(new jakarta.persistence.EntityNotFoundException("Review not found"));
        @SuppressWarnings("unchecked") StreamObserver<ReviewResponse> detail = mock(StreamObserver.class);
        @SuppressWarnings("unchecked") StreamObserver<com.example.hotelservice.grpc.ReviewResponseDto> response = mock(StreamObserver.class);
        endpoint.getReviewById(com.example.hotelservice.grpc.GetReviewByIdRequest.newBuilder().setReviewId(UUID.randomUUID().toString()).build(), detail);
        endpoint.getReviewResponse(com.example.hotelservice.grpc.GetReviewResponseRequest.newBuilder().setReviewId(UUID.randomUUID().toString()).build(), response);
        verify(detail).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.NOT_FOUND));
        verify(response).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.NOT_FOUND));
        verify(detail, never()).onNext(any()); verify(response, never()).onNext(any());
    }
    @Test void createMapsEligibilityFailuresWithoutAcceptingBodyGuest() {
        ReviewService reviews = mock(ReviewService.class);
        var endpoint = new ReviewGrpcServiceImpl(reviews);
        UUID actor = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "HS256").subject(actor.toString()).claim("role", "CUSTOMER").build();
        var request = CreateReviewRequest.newBuilder().setHotelId(UUID.randomUUID().toString())
                .setGuestId(UUID.randomUUID().toString()).setBookingId(UUID.randomUUID().toString())
                .setRating(8).setTitle("Synthetic review").setContent("Synthetic regression content").build();
        RuntimeException[] failures = {new SecurityException("ineligible"), new IllegalArgumentException("missing booking"),
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)};
        Status.Code[] expected = {Status.Code.PERMISSION_DENIED, Status.Code.INVALID_ARGUMENT, Status.Code.UNAVAILABLE};
        for (int index = 0; index < failures.length; index++) {
            when(reviews.createReview(any(), eq(actor))).thenThrow(failures[index]);
            @SuppressWarnings("unchecked") StreamObserver<ReviewResponse> observer = mock(StreamObserver.class);
            io.grpc.Context.current().withValue(
                    com.example.hotelservice.grpc.interceptor.ReviewAuthenticationInterceptor.ACTOR, jwt)
                    .run(() -> endpoint.createReview(request, observer));
            Status.Code code = expected[index];
            verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == code));
            verify(observer, never()).onNext(any());
        }
    }
    @Test void anonymousCannotDeleteUsingForgedBodyAdminAndIdentity() {
        ReviewService reviews = mock(ReviewService.class);
        var endpoint = new ReviewGrpcServiceImpl(reviews);
        @SuppressWarnings("unchecked") StreamObserver<Empty> observer = mock(StreamObserver.class);
        endpoint.deleteReview(DeleteReviewRequest.newBuilder().setReviewId(UUID.randomUUID().toString())
                .setUserId(UUID.randomUUID().toString()).setIsAdmin(true).build(), observer);
        verifyNoInteractions(reviews);
        verify(observer).onError(argThat(error -> Status.fromThrowable(error).getCode() == Status.Code.UNAUTHENTICATED));
        verify(observer, never()).onNext(any());
    }
    @Test void verifiedCustomerCannotEscalateUsingBodyAdmin() {
        ReviewService reviews = mock(ReviewService.class);
        var endpoint = new ReviewGrpcServiceImpl(reviews);
        UUID actor = UUID.randomUUID(), review = UUID.randomUUID();
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test")
                .header("alg", "HS256").subject(actor.toString()).claim("role", "CUSTOMER").build();
        @SuppressWarnings("unchecked") StreamObserver<Empty> observer = mock(StreamObserver.class);
        io.grpc.Context.current().withValue(
                com.example.hotelservice.grpc.interceptor.ReviewAuthenticationInterceptor.ACTOR, jwt).run(() ->
                endpoint.deleteReview(DeleteReviewRequest.newBuilder().setReviewId(review.toString())
                        .setUserId(UUID.randomUUID().toString()).setIsAdmin(true).build(), observer));
        verify(reviews).deleteReview(review, actor, false);
        verify(observer).onCompleted();
    }
}
