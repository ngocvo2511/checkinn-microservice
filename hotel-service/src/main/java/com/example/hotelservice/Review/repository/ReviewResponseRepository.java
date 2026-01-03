package com.example.hotelservice.Review.repository;

import com.example.hotelservice.Review.entity.ReviewResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewResponseRepository extends JpaRepository<ReviewResponse, UUID> {

    /**
     * Get response for a specific review
     */
    Optional<ReviewResponse> findByReviewId(UUID reviewId);

    /**
     * Get all responses by an owner
     */
    List<ReviewResponse> findByOwnerId(UUID ownerId);

    /**
     * Check if a review already has a response
     */
    boolean existsByReviewId(UUID reviewId);
}
