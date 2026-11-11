package com.example.hotelservice.security;

import com.example.hotelservice.Review.service.ReviewService;
import com.example.hotelservice.grpc.*;
import com.example.hotelservice.grpc.interceptor.ReviewAuthenticationInterceptor;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ReviewRpcBoundaryTest {
    static final jakarta.validation.ValidatorFactory validation=jakarta.validation.Validation.buildDefaultValidatorFactory();
    static final com.example.hotelservice.grpc.ReviewRpcPolicy policy=new com.example.hotelservice.grpc.ReviewRpcPolicy(validation.getValidator(),2000);
    @org.junit.jupiter.api.AfterAll static void closeValidation(){validation.close();}

    final ReviewService service=mock(ReviewService.class);
    final ReviewGrpcServiceImpl endpoint=new ReviewGrpcServiceImpl(service,policy);
    final String id=UUID.randomUUID().toString();
    void actor(Runnable call) {
        var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic").header("alg","HS256")
                .subject(UUID.randomUUID().toString()).claim("role","CUSTOMER").build();
        Context.current().withValue(ReviewAuthenticationInterceptor.ACTOR,jwt).run(call);
    }
    void error(StreamObserver<?> observer,Status.Code expected) {
        var failure=org.mockito.ArgumentCaptor.forClass(Throwable.class);verify(observer).onError(failure.capture());
        assertThat(Status.fromThrowable(failure.getValue()).getCode()).isEqualTo(expected);verify(observer,never()).onCompleted();
    }
    @Test void malformedReviewIdIsInvalidArgumentNotMissingResource() {
        StreamObserver<ReviewResponse> observer=mock(StreamObserver.class);
        endpoint.getReviewById(GetReviewByIdRequest.newBuilder().setReviewId("synthetic-invalid-uuid").build(),observer);
        error(observer,Status.Code.INVALID_ARGUMENT);verifyNoInteractions(service);
    }
    @Test void createAppliesTheSameRatingAndTextValidationAsRestBeforeBusinessMutation() {
        StreamObserver<ReviewResponse> observer=mock(StreamObserver.class);
        actor(()->endpoint.createReview(CreateReviewRequest.newBuilder().setHotelId(id).setBookingId(id)
                .setRating(12).setTitle("x").setContent("x").build(),observer));
        error(observer,Status.Code.INVALID_ARGUMENT);verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(doubles={-1,11,Double.NaN,Double.POSITIVE_INFINITY})
    void invalidUpdateRatingsCannotBeIgnoredOrReachTheService(double rating) {
        StreamObserver<ReviewResponse> observer=mock(StreamObserver.class);
        actor(()->endpoint.updateReview(UpdateReviewRequest.newBuilder().setReviewId(id).setRating(rating).build(),observer));
        error(observer,Status.Code.INVALID_ARGUMENT);verifyNoInteractions(service);
    }
    @Test void ownerReplyCannotBypassRestContentValidation() {
        StreamObserver<ReviewResponseDto> observer=mock(StreamObserver.class);
        actor(()->endpoint.addReviewResponse(AddReviewResponseRequest.newBuilder().setReviewId(id).setContent(" ").build(),observer));
        error(observer,Status.Code.INVALID_ARGUMENT);verifyNoInteractions(service);
    }
    @Test void dataOutageHasSafeStatusAndNeverPrintsThePrivateException(CapturedOutput output) {
        String marker="synthetic-private-review-db-error";
        when(service.getHotelReviews(any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException(marker));
        StreamObserver<GetHotelReviewsResponse> observer=mock(StreamObserver.class);
        endpoint.getHotelReviews(GetHotelReviewsRequest.newBuilder().setHotelId(id).build(),observer);
        var failure=org.mockito.ArgumentCaptor.forClass(Throwable.class);verify(observer).onError(failure.capture());
        assertThat(Status.fromThrowable(failure.getValue()).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(failure.getValue().getMessage()).doesNotContain(marker);assertThat(output.getAll()).doesNotContain(marker);
    }
    @Test void publicRpcPagingCannotRequestAnUnboundedDatabasePage() {
        when(service.getHotelReviews(any(),any())).thenReturn(org.springframework.data.domain.Page.empty());
        StreamObserver<GetHotelReviewsResponse> observer=mock(StreamObserver.class);
        endpoint.getHotelReviews(GetHotelReviewsRequest.newBuilder().setHotelId(id).setSize(Integer.MAX_VALUE).build(),observer);
        var page=org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);verify(service).getHotelReviews(any(),page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(2000);
    }
}
