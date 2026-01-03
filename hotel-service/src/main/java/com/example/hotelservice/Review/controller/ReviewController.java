package com.example.hotelservice.Review.controller;

import com.example.hotelservice.Review.dto.request.CreateReviewRequest;
import com.example.hotelservice.Review.dto.request.CreateReviewResponseRequest;
import com.example.hotelservice.Review.dto.request.UpdateReviewRequest;
import com.example.hotelservice.Review.dto.response.HotelReviewResponse;
import com.example.hotelservice.Review.dto.response.HotelReviewStatsResponse;
import com.example.hotelservice.Review.dto.response.ReviewResponseResponse;
import com.example.hotelservice.Review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Create a new review
     * POST /api/v1/reviews
     * Header: Authorization: Bearer {token}
     */
    @PostMapping
    public ResponseEntity<HotelReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @RequestHeader("X-User-Id") UUID guestId
    ) {
        HotelReviewResponse response = reviewService.createReview(request, guestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all reviews for a hotel
     * GET /api/v1/reviews/hotel/{hotelId}
     * Optional Header: X-User-Id for tracking user feedback
     */
    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<Page<HotelReviewResponse>> getHotelReviews(
            @PathVariable UUID hotelId,
            Pageable pageable,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        Page<HotelReviewResponse> reviews = reviewService.getHotelReviews(hotelId, pageable, userId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get review by ID
     * GET /api/v1/reviews/{reviewId}
     * Optional Header: X-User-Id for tracking user feedback
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<HotelReviewResponse> getReviewById(
            @PathVariable UUID reviewId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        HotelReviewResponse review = reviewService.getReviewById(reviewId, userId);
        return ResponseEntity.ok(review);
    }

    /**
     * Update review
     * PUT /api/v1/reviews/{reviewId}
     * Header: Authorization: Bearer {token}
     */
    @PutMapping("/{reviewId}")
    public ResponseEntity<HotelReviewResponse> updateReview(
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request,
            @RequestHeader("X-User-Id") UUID guestId
    ) {
        HotelReviewResponse response = reviewService.updateReview(reviewId, request, guestId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete review
     * DELETE /api/v1/reviews/{reviewId}
     * Header: Authorization: Bearer {token}
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable UUID reviewId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader(value = "X-Is-Admin", defaultValue = "false") boolean isAdmin
    ) {
        reviewService.deleteReview(reviewId, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get review statistics for a hotel
     * GET /api/v1/reviews/stats/hotel/{hotelId}
     * Optional Header: X-User-Id for tracking user feedback in recent reviews
     */
    @GetMapping("/stats/hotel/{hotelId}")
    public ResponseEntity<HotelReviewStatsResponse> getHotelReviewStats(
            @PathVariable UUID hotelId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        HotelReviewStatsResponse stats = reviewService.getHotelReviewStats(hotelId, userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Mark review as helpful
     * POST /api/v1/reviews/{reviewId}/helpful
     * Optional Header: X-User-Id for tracking user feedback
     */
    @PostMapping("/{reviewId}/helpful")
    public ResponseEntity<Void> markReviewHelpful(
            @PathVariable UUID reviewId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        reviewService.markReviewHelpful(reviewId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark review as unhelpful
     * POST /api/v1/reviews/{reviewId}/unhelpful
     * Optional Header: X-User-Id for tracking user feedback
     */
    @PostMapping("/{reviewId}/unhelpful")
    public ResponseEntity<Void> markReviewUnhelpful(
            @PathVariable UUID reviewId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        reviewService.markReviewUnhelpful(reviewId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Add response to review (hotel owner)
     * POST /api/v1/reviews/{reviewId}/response
     * Header: Authorization: Bearer {token}
     */
    @PostMapping("/{reviewId}/response")
    public ResponseEntity<ReviewResponseResponse> addReviewResponse(
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateReviewResponseRequest request,
            @RequestHeader("X-User-Id") UUID ownerId
    ) {
        ReviewResponseResponse response = reviewService.addReviewResponse(reviewId, request, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Update response to review
     * PUT /api/v1/reviews/response/{responseId}
     * Header: Authorization: Bearer {token}
     */
    @PutMapping("/response/{responseId}")
    public ResponseEntity<ReviewResponseResponse> updateReviewResponse(
            @PathVariable UUID responseId,
            @Valid @RequestBody CreateReviewResponseRequest request,
            @RequestHeader("X-User-Id") UUID ownerId
    ) {
        ReviewResponseResponse response = reviewService.updateReviewResponse(responseId, request, ownerId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete response to review
     * DELETE /api/v1/reviews/response/{responseId}
     * Header: Authorization: Bearer {token}
     */
    @DeleteMapping("/response/{responseId}")
    public ResponseEntity<Void> deleteReviewResponse(
            @PathVariable UUID responseId,
            @RequestHeader("X-User-Id") UUID ownerId
    ) {
        reviewService.deleteReviewResponse(responseId, ownerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Check if booking has been reviewed
     * GET /api/v1/reviews/check-booking/{bookingId}
     */
    @GetMapping("/check-booking/{bookingId}")
    public ResponseEntity<HotelReviewResponse> checkBookingReviewed(
            @PathVariable UUID bookingId,
            @RequestParam UUID userId
    ) {
        Optional<HotelReviewResponse> review = reviewService.getReviewByBookingId(bookingId, userId);
        if (review.isPresent()) {
            return ResponseEntity.ok(review.get());
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Get response for a review
     * GET /api/v1/reviews/{reviewId}/response
     */
    @GetMapping("/{reviewId}/response")
    public ResponseEntity<Optional<ReviewResponseResponse>> getReviewResponse(
            @PathVariable UUID reviewId
    ) {
        Optional<ReviewResponseResponse> response = reviewService.getReviewResponse(reviewId);
        return ResponseEntity.ok(response);
    }
}
