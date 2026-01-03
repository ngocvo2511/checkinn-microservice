package com.example.hotelservice.Review.service;

import com.example.hotelservice.Review.dto.request.CreateReviewRequest;
import com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest;
import com.example.hotelservice.Review.dto.request.UpdateReviewRequest;
import com.example.hotelservice.Review.dto.response.HotelReviewResponse;
import com.example.hotelservice.Review.dto.response.HotelReviewStatsResponse;
import com.example.hotelservice.Review.dto.response.ReviewResponseResponse;
import com.example.hotelservice.Review.entity.HotelReview;
import com.example.hotelservice.Review.entity.ReviewResponse;
import com.example.hotelservice.Review.enums.ReviewStatus;
import com.example.hotelservice.Review.mapper.ReviewMapper;
import com.example.hotelservice.Review.repository.HotelReviewRepository;
import com.example.hotelservice.Review.repository.ReviewResponseRepository;
import com.example.hotelservice.Review.repository.ReviewFeedbackRepository;
import com.example.hotelservice.Review.entity.ReviewFeedback;
import com.example.hotelservice.Review.enums.FeedbackType;
import com.example.hotelservice.Hotel.service.UserGrpcClient;
import com.checkinn.user.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final HotelReviewRepository hotelReviewRepository;
    private final ReviewResponseRepository reviewResponseRepository;
    private final ReviewFeedbackRepository reviewFeedbackRepository;
    private final ReviewMapper reviewMapper;
    private final UserGrpcClient userGrpcClient;

    /**
     * Create a new review
     */
    public HotelReviewResponse createReview(CreateReviewRequest request, UUID guestId) {
        // Check if guest already reviewed this hotel for this booking
        if (request.getBookingId() != null) {
            Optional<HotelReview> existing = hotelReviewRepository
                    .findByHotelIdAndGuestIdAndBookingId(
                            request.getHotelId(),
                            guestId,
                            request.getBookingId()
                    );
            if (existing.isPresent()) {
                throw new IllegalStateException("Bạn đã đánh giá khách sạn này cho booking này rồi");
            }
        } else {
            // Check if guest already reviewed this hotel (without specific booking)
            if (hotelReviewRepository.existsByHotelIdAndGuestId(request.getHotelId(), guestId)) {
                throw new IllegalStateException("Bạn chỉ có thể đánh giá mỗi khách sạn một lần");
            }
        }

        HotelReview review = reviewMapper.createRequestToEntity(request);
        review.setGuestId(guestId);
        review.setStatus(ReviewStatus.PUBLISHED);

        HotelReview saved = hotelReviewRepository.save(review);
        HotelReviewResponse response = reviewMapper.entityToResponse(saved);
        enrichGuestInfo(response);
        return response;
    }

    /**
     * Get all reviews for a hotel with pagination
     */
    @Transactional(readOnly = true)
    public Page<HotelReviewResponse> getHotelReviews(UUID hotelId, Pageable pageable) {
        return getHotelReviews(hotelId, pageable, null);
    }

    /**
     * Get all reviews for a hotel with pagination (with user feedback)
     */
    @Transactional(readOnly = true)
    public Page<HotelReviewResponse> getHotelReviews(UUID hotelId, Pageable pageable, UUID userId) {
        Page<HotelReview> reviews = hotelReviewRepository
                .findByHotelIdAndStatusOrderByCreatedAtDesc(hotelId, ReviewStatus.PUBLISHED, pageable);

        return reviews.map(r -> {
            HotelReviewResponse resp = reviewMapper.entityToResponse(r);
            enrichGuestInfo(resp);
            enrichUserFeedback(resp, userId);
            return resp;
        });
    }

    /**
     * Get review by ID
     */
    @Transactional(readOnly = true)
    public HotelReviewResponse getReviewById(UUID reviewId) {
        return getReviewById(reviewId, null);
    }

    /**
     * Get review by ID (with user feedback)
     */
    @Transactional(readOnly = true)
    public HotelReviewResponse getReviewById(UUID reviewId, UUID userId) {
        HotelReview review = hotelReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Đánh giá không tồn tại"));
        HotelReviewResponse response = reviewMapper.entityToResponse(review);
        enrichGuestInfo(response);
        enrichUserFeedback(response, userId);
        return response;
    }

    /**
     * Update review (only by review author)
     */
    public HotelReviewResponse updateReview(UUID reviewId, UpdateReviewRequest request, UUID guestId) {
        HotelReview review = hotelReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Đánh giá không tồn tại"));

        if (!review.getGuestId().equals(guestId)) {
            throw new IllegalAccessError("Bạn không có quyền sửa đánh giá này");
        }

        if (request.getRating() != null) {
            review.setRating(request.getRating());
        }
        if (request.getStaffRating() != null) {
            review.setStaffRating(request.getStaffRating());
        }
        if (request.getAmenitiesRating() != null) {
            review.setAmenitiesRating(request.getAmenitiesRating());
        }
        if (request.getCleanlinessRating() != null) {
            review.setCleanlinessRating(request.getCleanlinessRating());
        }
        if (request.getComfortRating() != null) {
            review.setComfortRating(request.getComfortRating());
        }
        if (request.getValueForMoneyRating() != null) {
            review.setValueForMoneyRating(request.getValueForMoneyRating());
        }
        if (request.getLocationRating() != null) {
            review.setLocationRating(request.getLocationRating());
        }
        if (request.getTitle() != null) {
            review.setTitle(request.getTitle());
        }
        if (request.getContent() != null) {
            review.setContent(request.getContent());
        }

        HotelReview updated = hotelReviewRepository.save(review);
        HotelReviewResponse response = reviewMapper.entityToResponse(updated);
        enrichGuestInfo(response);
        return response;
    }

    /**
     * Delete review (only by review author or admin)
     */
    public void deleteReview(UUID reviewId, UUID userId, boolean isAdmin) {
        HotelReview review = hotelReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Đánh giá không tồn tại"));

        if (!isAdmin && !review.getGuestId().equals(userId)) {
            throw new IllegalAccessError("Bạn không có quyền xóa đánh giá này");
        }

        hotelReviewRepository.deleteById(reviewId);
    }

    /**
     * Get review statistics for a hotel
     */
    @Transactional(readOnly = true)
    public HotelReviewStatsResponse getHotelReviewStats(UUID hotelId) {
        return getHotelReviewStats(hotelId, null);
    }

    /**
     * Get review statistics for a hotel (with user feedback)
     */
    @Transactional(readOnly = true)
    public HotelReviewStatsResponse getHotelReviewStats(UUID hotelId, UUID userId) {
        long total = hotelReviewRepository.countByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED);
        Optional<Double> avgRating = hotelReviewRepository.getAverageRating(hotelId, ReviewStatus.PUBLISHED);

        BigDecimal avgRatingBD = avgRating
                .map(d -> BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);

        // Get rating distribution
        List<HotelReview> allReviews = hotelReviewRepository
                .findByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED, Pageable.unpaged())
                .getContent();

        // Rating distribution for 10-point scale
        long rating1 = allReviews.stream().filter(r -> r.getRating().intValue() == 1).count();
        long rating2 = allReviews.stream().filter(r -> r.getRating().intValue() == 2).count();
        long rating3 = allReviews.stream().filter(r -> r.getRating().intValue() == 3).count();
        long rating4 = allReviews.stream().filter(r -> r.getRating().intValue() == 4).count();
        long rating5 = allReviews.stream().filter(r -> r.getRating().intValue() == 5).count();
        long rating6 = allReviews.stream().filter(r -> r.getRating().intValue() == 6).count();
        long rating7 = allReviews.stream().filter(r -> r.getRating().intValue() == 7).count();
        long rating8 = allReviews.stream().filter(r -> r.getRating().intValue() == 8).count();
        long rating9 = allReviews.stream().filter(r -> r.getRating().intValue() == 9).count();
        long rating10 = allReviews.stream().filter(r -> r.getRating().intValue() == 10).count();

        // Calculate average ratings for each criteria
        Double avgStaff = allReviews.stream()
                .filter(r -> r.getStaffRating() != null)
                .mapToDouble(HotelReview::getStaffRating)
                .average()
                .orElse(0.0);

        Double avgAmenities = allReviews.stream()
                .filter(r -> r.getAmenitiesRating() != null)
                .mapToDouble(HotelReview::getAmenitiesRating)
                .average()
                .orElse(0.0);

        Double avgCleanliness = allReviews.stream()
                .filter(r -> r.getCleanlinessRating() != null)
                .mapToDouble(HotelReview::getCleanlinessRating)
                .average()
                .orElse(0.0);

        Double avgComfort = allReviews.stream()
                .filter(r -> r.getComfortRating() != null)
                .mapToDouble(HotelReview::getComfortRating)
                .average()
                .orElse(0.0);

        Double avgValueForMoney = allReviews.stream()
                .filter(r -> r.getValueForMoneyRating() != null)
                .mapToDouble(HotelReview::getValueForMoneyRating)
                .average()
                .orElse(0.0);

        Double avgLocation = allReviews.stream()
                .filter(r -> r.getLocationRating() != null)
                .mapToDouble(HotelReview::getLocationRating)
                .average()
                .orElse(0.0);

        // Get recent reviews
        List<HotelReviewResponse> recentReviews = allReviews.stream()
                .sorted(Comparator.comparing(HotelReview::getCreatedAt).reversed())
                .limit(5)
                .map(r -> {
                    HotelReviewResponse resp = reviewMapper.entityToResponse(r);
                    enrichGuestInfo(resp);
                    enrichUserFeedback(resp, userId);
                    return resp;
                })
                .collect(Collectors.toList());

        return HotelReviewStatsResponse.builder()
                .hotelId(hotelId)
                .averageRating(avgRatingBD)
                .totalReviews(total)
                .ratingDistribution1(rating1)
                .ratingDistribution2(rating2)
                .ratingDistribution3(rating3)
                .ratingDistribution4(rating4)
                .ratingDistribution5(rating5)
                .ratingDistribution6(rating6)
                .ratingDistribution7(rating7)
                .ratingDistribution8(rating8)
                .ratingDistribution9(rating9)
                .ratingDistribution10(rating10)
                .averageStaffRating(avgStaff > 0 ? avgStaff : null)
                .averageAmenitiesRating(avgAmenities > 0 ? avgAmenities : null)
                .averageCleanlinessRating(avgCleanliness > 0 ? avgCleanliness : null)
                .averageComfortRating(avgComfort > 0 ? avgComfort : null)
                .averageValueForMoneyRating(avgValueForMoney > 0 ? avgValueForMoney : null)
                .averageLocationRating(avgLocation > 0 ? avgLocation : null)
                .recentReviews(recentReviews)
                .build();
    }

    /**
     * Add helpful count to review
     */
    public void markReviewHelpful(UUID reviewId) {
        markReviewFeedback(reviewId, null, FeedbackType.HELPFUL);
    }

    /**
     * Add helpful count to review (with user tracking)
     */
    public void markReviewHelpful(UUID reviewId, UUID userId) {
        markReviewFeedback(reviewId, userId, FeedbackType.HELPFUL);
    }

    /**
     * Add unhelpful count to review
     */
    public void markReviewUnhelpful(UUID reviewId) {
        markReviewFeedback(reviewId, null, FeedbackType.UNHELPFUL);
    }

    /**
     * Add unhelpful count to review (with user tracking)
     */
    public void markReviewUnhelpful(UUID reviewId, UUID userId) {
        markReviewFeedback(reviewId, userId, FeedbackType.UNHELPFUL);
    }

    /**
     * Mark review feedback (with optional user tracking)
     */
    private void markReviewFeedback(UUID reviewId, UUID userId, FeedbackType feedbackType) {
        HotelReview review = hotelReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Đánh giá không tồn tại"));

        // If userId is provided, track user's vote
        if (userId != null) {
            Optional<ReviewFeedback> existingFeedback = reviewFeedbackRepository.findByReviewIdAndUserId(reviewId, userId);
            
            if (existingFeedback.isPresent()) {
                ReviewFeedback feedback = existingFeedback.get();
                FeedbackType oldType = feedback.getFeedbackType();
                
                // If same type, do nothing
                if (oldType == feedbackType) {
                    return;
                }
                
                // Update counts: decrement old type, increment new type
                if (oldType == FeedbackType.HELPFUL) {
                    review.setHelpfulCount(Math.max(0, review.getHelpfulCount() - 1));
                    review.setUnhelpfulCount(review.getUnhelpfulCount() + 1);
                } else {
                    review.setUnhelpfulCount(Math.max(0, review.getUnhelpfulCount() - 1));
                    review.setHelpfulCount(review.getHelpfulCount() + 1);
                }
                
                // Update feedback type
                feedback.setFeedbackType(feedbackType);
                reviewFeedbackRepository.save(feedback);
            } else {
                // Create new feedback
                ReviewFeedback newFeedback = ReviewFeedback.builder()
                        .reviewId(reviewId)
                        .userId(userId)
                        .feedbackType(feedbackType)
                        .build();
                reviewFeedbackRepository.save(newFeedback);
                
                // Increment count
                if (feedbackType == FeedbackType.HELPFUL) {
                    review.setHelpfulCount(review.getHelpfulCount() + 1);
                } else {
                    review.setUnhelpfulCount(review.getUnhelpfulCount() + 1);
                }
            }
        } else {
            // Anonymous vote (no user tracking)
            if (feedbackType == FeedbackType.HELPFUL) {
                review.setHelpfulCount(review.getHelpfulCount() + 1);
            } else {
                review.setUnhelpfulCount(review.getUnhelpfulCount() + 1);
            }
        }
        
        hotelReviewRepository.save(review);
    }

    /**
     * Add owner response to review
     */
    public ReviewResponseResponse addReviewResponse(UUID reviewId, CreateReviewResponseRequest request, UUID ownerId) {
        HotelReview review = hotelReviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Đánh giá không tồn tại"));

        // Check if response already exists
        if (reviewResponseRepository.existsByReviewId(reviewId)) {
            throw new IllegalStateException("Đánh giá này đã có câu trả lời rồi");
        }

        ReviewResponse response = ReviewResponse.builder()
                .reviewId(reviewId)
                .ownerId(ownerId)
                .content(request.getContent())
                .build();

        ReviewResponse saved = reviewResponseRepository.save(response);
        return mapResponseToDto(saved);
    }

    /**
     * Update owner response
     */
    public ReviewResponseResponse updateReviewResponse(UUID responseId, CreateReviewResponseRequest request, UUID ownerId) {
        ReviewResponse response = reviewResponseRepository.findById(responseId)
                .orElseThrow(() -> new IllegalArgumentException("Trả lời không tồn tại"));

        if (!response.getOwnerId().equals(ownerId)) {
            throw new IllegalAccessError("Bạn không có quyền sửa trả lời này");
        }

        response.setContent(request.getContent());
        ReviewResponse updated = reviewResponseRepository.save(response);
        return mapResponseToDto(updated);
    }

    /**
     * Delete owner response
     */
    public void deleteReviewResponse(UUID responseId, UUID ownerId) {
        ReviewResponse response = reviewResponseRepository.findById(responseId)
                .orElseThrow(() -> new IllegalArgumentException("Trả lời không tồn tại"));

        if (!response.getOwnerId().equals(ownerId)) {
            throw new IllegalAccessError("Bạn không có quyền xóa trả lời này");
        }

        reviewResponseRepository.deleteById(responseId);
    }

    /**
     * Get response for a review
     */
    @Transactional(readOnly = true)
    public Optional<ReviewResponseResponse> getReviewResponse(UUID reviewId) {
        return reviewResponseRepository.findByReviewId(reviewId)
                .map(this::mapResponseToDto);
    }

    /**
     * Get review by hotel, guest, and booking (for gRPC validation)
     */
    @Transactional(readOnly = true)
    public Optional<HotelReviewResponse> getReviewByHotelGuestBooking(UUID hotelId, UUID guestId, UUID bookingId) {
        return hotelReviewRepository.findByHotelIdAndGuestIdAndBookingId(hotelId, guestId, bookingId)
                .map(reviewMapper::entityToResponse);
    }

    /**
     * Get review by booking ID and user ID
     */
    @Transactional(readOnly = true)
    public Optional<HotelReviewResponse> getReviewByBookingId(UUID bookingId, UUID userId) {
        return hotelReviewRepository.findAll().stream()
                .filter(review -> review.getBookingId() != null &&
                                  review.getBookingId().equals(bookingId) &&
                                  review.getGuestId().equals(userId))
                .findFirst()
                .map(reviewMapper::entityToResponse);
    }

    /**
     * Check if user already reviewed hotel (for gRPC validation)
     */
    @Transactional(readOnly = true)
    public boolean checkUserReviewExists(UUID hotelId, UUID guestId) {
        return hotelReviewRepository.existsByHotelIdAndGuestId(hotelId, guestId);
    }

    private ReviewResponseResponse mapResponseToDto(ReviewResponse entity) {
        return ReviewResponseResponse.builder()
                .id(entity.getId())
                .reviewId(entity.getReviewId())
                .ownerId(entity.getOwnerId())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private void enrichGuestInfo(HotelReviewResponse response) {
        if (response == null || response.getGuestId() == null) {
            return;
        }
        try {
            UserResponse user = userGrpcClient.GetUserById(response.getGuestId());
            if (user != null) {
                String fullName = user.getFullName();
                String username = user.getUsername();
                if (fullName != null && !fullName.isBlank()) {
                    response.setGuestName(fullName);
                } else if (username != null && !username.isBlank()) {
                    response.setGuestName(username);
                }
            }
        } catch (Exception e) {
            // swallow errors to avoid breaking review fetch when user-service is unavailable
        }
    }

    private void enrichUserFeedback(HotelReviewResponse response, UUID userId) {
        if (response == null || response.getId() == null || userId == null) {
            return;
        }
        try {
            Optional<ReviewFeedback> feedback = reviewFeedbackRepository.findByReviewIdAndUserId(response.getId(), userId);
            if (feedback.isPresent()) {
                response.setUserFeedback(feedback.get().getFeedbackType().name());
            }
        } catch (Exception e) {
            // swallow errors
        }
    }
}
