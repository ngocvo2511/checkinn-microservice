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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        log.info("[REVIEW_CONTROLLER_CREATE] Create review request received - hotelId: {}, guestId: {}, bookingId: {}",
            request.getHotelId(), guestId, request.getBookingId());
        HotelReviewResponse response = reviewService.createReview(request, guestId);
        log.info("[REVIEW_CONTROLLER_CREATE] Create review success - reviewId: {}, hotelId: {}, guestId: {}",
            response.getId(), request.getHotelId(), guestId);
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
        log.info("[REVIEW_CONTROLLER_GET_HOTEL] Fetch hotel reviews request received - hotelId: {}, userId: {}", hotelId, userId);
        Page<HotelReviewResponse> reviews = reviewService.getHotelReviews(hotelId, pageable, userId);
        log.info("[REVIEW_CONTROLLER_GET_HOTEL] Fetch hotel reviews success - hotelId: {}, returned: {}", hotelId, reviews.getTotalElements());
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
        log.info("[REVIEW_CONTROLLER_GET_BY_ID] Fetch review request received - reviewId: {}, userId: {}", reviewId, userId);
        HotelReviewResponse review = reviewService.getReviewById(reviewId, userId);
        log.info("[REVIEW_CONTROLLER_GET_BY_ID] Fetch review success - reviewId: {}", reviewId);
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
        log.info("[REVIEW_CONTROLLER_UPDATE] Update review request received - reviewId: {}, guestId: {}", reviewId, guestId);
        HotelReviewResponse response = reviewService.updateReview(reviewId, request, guestId);
        log.info("[REVIEW_CONTROLLER_UPDATE] Update review success - reviewId: {}, guestId: {}", reviewId, guestId);
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
        log.info("[REVIEW_CONTROLLER_DELETE] Delete review request received - reviewId: {}, userId: {}, isAdmin: {}", reviewId, userId, isAdmin);
        reviewService.deleteReview(reviewId, userId, isAdmin);
        log.info("[REVIEW_CONTROLLER_DELETE] Delete review success - reviewId: {}, userId: {}", reviewId, userId);
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
        log.info("[REVIEW_CONTROLLER_STATS] Fetch review stats request received - hotelId: {}, userId: {}", hotelId, userId);
        HotelReviewStatsResponse stats = reviewService.getHotelReviewStats(hotelId, userId);
        log.info("[REVIEW_CONTROLLER_STATS] Fetch review stats success - hotelId: {}", hotelId);
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
        log.info("[REVIEW_CONTROLLER_HELPFUL] Mark review helpful request received - reviewId: {}, userId: {}", reviewId, userId);
        reviewService.markReviewHelpful(reviewId, userId);
        log.info("[REVIEW_CONTROLLER_HELPFUL] Mark review helpful success - reviewId: {}, userId: {}", reviewId, userId);
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
        log.info("[REVIEW_CONTROLLER_UNHELPFUL] Mark review unhelpful request received - reviewId: {}, userId: {}", reviewId, userId);
        reviewService.markReviewUnhelpful(reviewId, userId);
        log.info("[REVIEW_CONTROLLER_UNHELPFUL] Mark review unhelpful success - reviewId: {}, userId: {}", reviewId, userId);
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
        log.info("[REVIEW_CONTROLLER_ADD_RESPONSE] Add review response request received - reviewId: {}, ownerId: {}", reviewId, ownerId);
        ReviewResponseResponse response = reviewService.addReviewResponse(reviewId, request, ownerId);
        log.info("[REVIEW_CONTROLLER_ADD_RESPONSE] Add review response success - reviewId: {}, responseId: {}, ownerId: {}",
            reviewId, response.getId(), ownerId);
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
        log.info("[REVIEW_CONTROLLER_UPDATE_RESPONSE] Update review response request received - responseId: {}, ownerId: {}", responseId, ownerId);
        ReviewResponseResponse response = reviewService.updateReviewResponse(responseId, request, ownerId);
        log.info("[REVIEW_CONTROLLER_UPDATE_RESPONSE] Update review response success - responseId: {}, ownerId: {}", responseId, ownerId);
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
        log.info("[REVIEW_CONTROLLER_DELETE_RESPONSE] Delete review response request received - responseId: {}, ownerId: {}", responseId, ownerId);
        reviewService.deleteReviewResponse(responseId, ownerId);
        log.info("[REVIEW_CONTROLLER_DELETE_RESPONSE] Delete review response success - responseId: {}, ownerId: {}", responseId, ownerId);
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
        log.info("[REVIEW_CONTROLLER_CHECK_BOOKING] Check booking reviewed request received - bookingId: {}, userId: {}", bookingId, userId);
        Optional<HotelReviewResponse> review = reviewService.getReviewByBookingId(bookingId, userId);
        if (review.isPresent()) {
            log.info("[REVIEW_CONTROLLER_CHECK_BOOKING] Booking reviewed - bookingId: {}, userId: {}", bookingId, userId);
            return ResponseEntity.ok(review.get());
        }
        log.info("[REVIEW_CONTROLLER_CHECK_BOOKING] Booking not reviewed - bookingId: {}, userId: {}", bookingId, userId);
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
        log.info("[REVIEW_CONTROLLER_GET_RESPONSE] Fetch review response request received - reviewId: {}", reviewId);
        Optional<ReviewResponseResponse> response = reviewService.getReviewResponse(reviewId);
        log.info("[REVIEW_CONTROLLER_GET_RESPONSE] Fetch review response success - reviewId: {}, found: {}", reviewId, response.isPresent());
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // HOTEL OWNER ENDPOINTS
    // ============================================================

    /**
     * Get all reviews for hotels owned by the authenticated owner
     * GET /api/v1/reviews/owner/all
     * Header: X-User-Id (owner ID)
     */
    @GetMapping("/owner/all")
    public ResponseEntity<Page<HotelReviewResponse>> getOwnerReviews(
            @RequestHeader("X-User-Id") UUID ownerId,
            Pageable pageable
    ) {
        log.info("[REVIEW_CONTROLLER_OWNER_ALL] Fetch owner reviews request received - ownerId: {}", ownerId);
        Page<HotelReviewResponse> reviews = reviewService.getReviewsByOwner(ownerId, pageable);
        log.info("[REVIEW_CONTROLLER_OWNER_ALL] Fetch owner reviews success - ownerId: {}, returned: {}", ownerId, reviews.getTotalElements());
        return ResponseEntity.ok(reviews);
    }

    /**
     * Get reviews for a specific hotel owned by the authenticated owner
     * GET /api/v1/reviews/owner/hotel/{hotelId}
     * Header: X-User-Id (owner ID)
     */
    @GetMapping("/owner/hotel/{hotelId}")
    public ResponseEntity<Page<HotelReviewResponse>> getOwnerHotelReviews(
            @PathVariable UUID hotelId,
            @RequestHeader("X-User-Id") UUID ownerId,
            Pageable pageable
    ) {
        log.info("[REVIEW_CONTROLLER_OWNER_HOTEL] Fetch owner hotel reviews request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
        Page<HotelReviewResponse> reviews = reviewService.getReviewsByOwnerAndHotel(ownerId, hotelId, pageable);
        log.info("[REVIEW_CONTROLLER_OWNER_HOTEL] Fetch owner hotel reviews success - hotelId: {}, ownerId: {}, returned: {}",
            hotelId, ownerId, reviews.getTotalElements());
        return ResponseEntity.ok(reviews);
    }
}
