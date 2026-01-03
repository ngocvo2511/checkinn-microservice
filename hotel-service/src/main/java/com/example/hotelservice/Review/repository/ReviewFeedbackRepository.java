package com.example.hotelservice.Review.repository;

import com.example.hotelservice.Review.entity.ReviewFeedback;
import com.example.hotelservice.Review.enums.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback, UUID> {

    Optional<ReviewFeedback> findByReviewIdAndUserId(UUID reviewId, UUID userId);

    long countByReviewIdAndFeedbackType(UUID reviewId, FeedbackType feedbackType);
}
