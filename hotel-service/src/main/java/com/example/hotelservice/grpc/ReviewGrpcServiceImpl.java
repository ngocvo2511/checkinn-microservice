package com.example.hotelservice.grpc;

import com.example.hotelservice.Review.dto.response.HotelReviewResponse;
import com.example.hotelservice.Review.dto.response.HotelReviewStatsResponse;
import com.example.hotelservice.Review.dto.response.ReviewResponseResponse;
import com.example.hotelservice.Review.service.ReviewService;
import com.example.hotelservice.grpc.interceptor.UserContextInterceptor;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class ReviewGrpcServiceImpl extends ReviewGrpcServiceGrpc.ReviewGrpcServiceImplBase {

    private final ReviewService reviewService;

    // ============================================================
    // GET HOTEL REVIEWS
    // ============================================================
    @Override
    public void getHotelReviews(GetHotelReviewsRequest request,
                                StreamObserver<GetHotelReviewsResponse> responseObserver) {
        try {
            UUID hotelId = UUID.fromString(request.getHotelId());
            int page = request.getPage() > 0 ? request.getPage() : 0;
            int size = request.getSize() > 0 ? request.getSize() : 10;

            Pageable pageable = PageRequest.of(page, size);
            Page<HotelReviewResponse> reviews = reviewService.getHotelReviews(hotelId, pageable);

            GetHotelReviewsResponse.Builder builder = GetHotelReviewsResponse.newBuilder()
                    .setTotalPages(reviews.getTotalPages())
                    .setTotalElements(reviews.getTotalElements());

            for (HotelReviewResponse review : reviews.getContent()) {
                builder.addReviews(mapReviewToProto(review));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // GET REVIEW BY ID
    // ============================================================
    @Override
    public void getReviewById(GetReviewByIdRequest request,
                             StreamObserver<ReviewResponse> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());
            HotelReviewResponse review = reviewService.getReviewById(reviewId);

            responseObserver.onNext(mapReviewToProto(review));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // GET REVIEW STATS
    // ============================================================
    @Override
    public void getReviewStats(GetReviewStatsRequest request,
                               StreamObserver<ReviewStatsResponse> responseObserver) {
        try {
            UUID hotelId = UUID.fromString(request.getHotelId());
            HotelReviewStatsResponse stats = reviewService.getHotelReviewStats(hotelId);

            ReviewStatsResponse.Builder builder = ReviewStatsResponse.newBuilder()
                    .setHotelId(stats.getHotelId().toString())
                    .setAverageRating(stats.getAverageRating().doubleValue())
                    .setTotalReviews(stats.getTotalReviews())
                    .setRatingDistribution1Star(stats.getRatingDistribution1())
                    .setRatingDistribution2Star(stats.getRatingDistribution2())
                    .setRatingDistribution3Star(stats.getRatingDistribution3())
                    .setRatingDistribution4Star(stats.getRatingDistribution4())
                    .setRatingDistribution5Star(stats.getRatingDistribution5());

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // CREATE REVIEW
    // ============================================================
    @Override
    public void createReview(com.example.hotelservice.grpc.CreateReviewRequest request,
                            StreamObserver<com.example.hotelservice.grpc.ReviewResponse> responseObserver) {
        try {
            UUID guestId = UserContextInterceptor.USER_ID_CONTEXT_KEY.get();
            if (guestId == null) {
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("User ID not found in context. Please provide x-user-id metadata.")
                        .asRuntimeException());
                return;
            }
            UUID hotelId = UUID.fromString(request.getHotelId());

            com.example.hotelservice.Review.dto.request.CreateReviewRequest reviewRequest =
                    com.example.hotelservice.Review.dto.request.CreateReviewRequest.builder()
                            .hotelId(hotelId)
                            .rating(BigDecimal.valueOf(request.getRating()))
                            // Note: Detailed ratings not supported in gRPC proto yet
                            // Use REST API for full detailed ratings support
                            .title(request.getTitle())
                            .content(request.getContent())
                            .bookingId(request.getBookingId().isEmpty() ? null : UUID.fromString(request.getBookingId()))
                            .build();

            HotelReviewResponse review = reviewService.createReview(reviewRequest, guestId);

            responseObserver.onNext(mapReviewToProto(review));
            responseObserver.onCompleted();

        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // UPDATE REVIEW
    // ============================================================
    @Override
    public void updateReview(com.example.hotelservice.grpc.UpdateReviewRequest request,
                            StreamObserver<com.example.hotelservice.grpc.ReviewResponse> responseObserver) {
        try {
            // Note: In the proto, the UpdateReviewRequest has reviewId field
            // But in our REST DTO, reviewId comes from the URL path
            // We'll use the proto's reviewId here
            UUID reviewId = UUID.fromString(request.getReviewId());
            UUID guestId = UserContextInterceptor.USER_ID_CONTEXT_KEY.get();
            if (guestId == null) {
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("User ID not found in context. Please provide x-user-id metadata.")
                        .asRuntimeException());
                return;
            }

            com.example.hotelservice.Review.dto.request.UpdateReviewRequest updateRequest =
                    com.example.hotelservice.Review.dto.request.UpdateReviewRequest.builder()
                            .rating(request.getRating() > 0 ? BigDecimal.valueOf(request.getRating()) : null)
                            // Note: Detailed ratings not supported in gRPC proto yet
                            // Use REST API for full detailed ratings support
                            .title(request.getTitle().isEmpty() ? null : request.getTitle())
                            .content(request.getContent().isEmpty() ? null : request.getContent())
                            .build();

            HotelReviewResponse review = reviewService.updateReview(reviewId, updateRequest, guestId);

            responseObserver.onNext(mapReviewToProto(review));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // DELETE REVIEW
    // ============================================================
    @Override
    public void deleteReview(DeleteReviewRequest request,
                            StreamObserver<Empty> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());
            UUID userId = UUID.fromString(request.getUserId());
            boolean isAdmin = request.getIsAdmin();

            reviewService.deleteReview(reviewId, userId, isAdmin);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // ADD REVIEW RESPONSE
    // ============================================================
    @Override
    public void addReviewResponse(AddReviewResponseRequest request,
                                 StreamObserver<ReviewResponseDto> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());
            UUID ownerId = UUID.fromString(request.getOwnerId());

            com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest responseRequest =
                    com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest.builder()
                            .content(request.getContent())
                            .build();

            ReviewResponseResponse response = reviewService.addReviewResponse(reviewId, responseRequest, ownerId);

            responseObserver.onNext(mapResponseToProto(response));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // UPDATE REVIEW RESPONSE
    // ============================================================
    @Override
    public void updateReviewResponse(UpdateReviewResponseRequest request,
                                    StreamObserver<ReviewResponseDto> responseObserver) {
        try {
            UUID responseId = UUID.fromString(request.getResponseId());
            UUID ownerId = UUID.fromString(request.getOwnerId());

            com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest updateRequest =
                    com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest.builder()
                            .content(request.getContent())
                            .build();

            ReviewResponseResponse response = reviewService.updateReviewResponse(responseId, updateRequest, ownerId);

            responseObserver.onNext(mapResponseToProto(response));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // DELETE REVIEW RESPONSE
    // ============================================================
    @Override
    public void deleteReviewResponse(DeleteReviewResponseRequest request,
                                    StreamObserver<Empty> responseObserver) {
        try {
            UUID responseId = UUID.fromString(request.getResponseId());
            UUID ownerId = UUID.fromString(request.getOwnerId());

            reviewService.deleteReviewResponse(responseId, ownerId);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (IllegalStateException ex) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // GET REVIEW RESPONSE
    // ============================================================
    @Override
    public void getReviewResponse(GetReviewResponseRequest request,
                                 StreamObserver<ReviewResponseDto> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());

            // reviewService.getReviewResponse returns Optional
            var responseOptional = reviewService.getReviewResponse(reviewId);
            if (responseOptional.isPresent()) {
                responseObserver.onNext(mapResponseToProto(responseOptional.get()));
            } else {
                responseObserver.onNext(ReviewResponseDto.getDefaultInstance());
            }
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // MARK REVIEW HELPFUL
    // ============================================================
    @Override
    public void markReviewHelpful(MarkReviewHelpfulRequest request,
                                 StreamObserver<Empty> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());
            reviewService.markReviewHelpful(reviewId);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // MARK REVIEW UNHELPFUL
    // ============================================================
    @Override
    public void markReviewUnhelpful(MarkReviewUnhelpfulRequest request,
                                   StreamObserver<Empty> responseObserver) {
        try {
            UUID reviewId = UUID.fromString(request.getReviewId());
            reviewService.markReviewUnhelpful(reviewId);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // CHECK USER REVIEW EXISTS
    // ============================================================
    @Override
    public void checkUserReviewExists(CheckUserReviewExistsRequest request,
                                     StreamObserver<CheckUserReviewExistsResponse> responseObserver) {
        try {
            UUID hotelId = UUID.fromString(request.getHotelId());
            UUID guestId = UUID.fromString(request.getGuestId());
            UUID bookingId = request.getBookingId().isEmpty() ? null : UUID.fromString(request.getBookingId());

            // reviewService.checkUserReviewExists only takes hotelId and guestId
            boolean exists = reviewService.checkUserReviewExists(hotelId, guestId);

            CheckUserReviewExistsResponse response = CheckUserReviewExistsResponse.newBuilder()
                    .setExists(exists)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception ex) {
            ex.printStackTrace();
            responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // ============================================================
    // MAPPER METHODS
    // ============================================================

    private com.example.hotelservice.grpc.ReviewResponse mapReviewToProto(HotelReviewResponse review) {
        com.example.hotelservice.grpc.ReviewResponse.Builder builder = com.example.hotelservice.grpc.ReviewResponse.newBuilder()
                .setId(review.getId().toString())
                .setHotelId(review.getHotelId().toString())
                .setGuestId(review.getGuestId().toString())
                .setGuestName(review.getGuestName() != null ? review.getGuestName() : "")
                .setRating(review.getRating().doubleValue())
                .setTitle(review.getTitle())
                .setContent(review.getContent())
                .setHelpfulCount(review.getHelpfulCount())
                .setUnhelpfulCount(review.getUnhelpfulCount())
                .setStatus(com.example.hotelservice.grpc.ReviewStatus.valueOf(review.getStatus().name()))
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(review.getCreatedAt().getEpochSecond())
                        .setNanos(review.getCreatedAt().getNano())
                        .build())
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(review.getUpdatedAt().getEpochSecond())
                        .setNanos(review.getUpdatedAt().getNano())
                        .build());

        return builder.build();
    }

    private com.example.hotelservice.grpc.ReviewResponseDto mapResponseToProto(ReviewResponseResponse response) {
        return com.example.hotelservice.grpc.ReviewResponseDto.newBuilder()
                .setId(response.getId().toString())
                .setReviewId(response.getReviewId().toString())
                .setOwnerId(response.getOwnerId().toString())
                .setOwnerName(response.getOwnerName() != null ? response.getOwnerName() : "")
                .setContent(response.getContent())
                .setCreatedAt(Timestamp.newBuilder()
                        .setSeconds(response.getCreatedAt().getEpochSecond())
                        .setNanos(response.getCreatedAt().getNano())
                        .build())
                .setUpdatedAt(Timestamp.newBuilder()
                        .setSeconds(response.getUpdatedAt().getEpochSecond())
                        .setNanos(response.getUpdatedAt().getNano())
                        .build())
                .build();
    }
}
