package com.example.hotelservice.Review.repository;

import com.example.hotelservice.Review.entity.HotelReview;
import com.example.hotelservice.Review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HotelReviewRepository extends JpaRepository<HotelReview, UUID> {

    /**
     * Get all reviews for a specific hotel with pagination
     */
    Page<HotelReview> findByHotelIdAndStatus(UUID hotelId, ReviewStatus status, Pageable pageable);

    /**
     * Get all published reviews for a hotel
     */
    Page<HotelReview> findByHotelIdAndStatusOrderByCreatedAtDesc(
            UUID hotelId,
            ReviewStatus status,
            Pageable pageable
    );

    /**
     * Get reviews by guest
     */
    List<HotelReview> findByGuestId(UUID guestId);

    /**
     * Check if guest already reviewed this hotel for this booking
     */
    Optional<HotelReview> findByHotelIdAndGuestIdAndBookingId(UUID hotelId, UUID guestId, UUID bookingId);

    /**
     * Check if guest already reviewed this hotel (any booking)
     */
    boolean existsByHotelIdAndGuestId(UUID hotelId, UUID guestId);

    /**
     * Get reviews count for a hotel (published only)
     */
    long countByHotelIdAndStatus(UUID hotelId, ReviewStatus status);

    /**
     * Get average rating for a hotel (published only)
     */
    @Query("SELECT AVG(r.rating) FROM HotelReview r WHERE r.hotelId = :hotelId AND r.status = :status")
    Optional<Double> getAverageRating(@Param("hotelId") UUID hotelId, @Param("status") ReviewStatus status);

    /**
     * Find all pending reviews (for admin)
     */
    List<HotelReview> findByStatus(ReviewStatus status);
}
