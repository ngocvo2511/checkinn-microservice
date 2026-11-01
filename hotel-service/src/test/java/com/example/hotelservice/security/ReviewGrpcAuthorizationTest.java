package com.example.hotelservice.security;

import com.example.hotelservice.Review.service.ReviewService;
import com.example.hotelservice.grpc.DeleteReviewRequest;
import com.example.hotelservice.grpc.ReviewGrpcServiceImpl;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewGrpcAuthorizationTest {
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
