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
    interface PublicSummary {
        UUID getHotelId();
        long getTotalReviews();
        Double getAverageRating();
    }

    /** One aggregate query; never hydrate review text, guest profiles or private hotels. */
    @Query("""
        SELECT h.id AS hotelId, COUNT(r.id) AS totalReviews, AVG(r.rating) AS averageRating
        FROM Hotel h LEFT JOIN HotelReview r ON r.hotelId=h.id AND r.status=:reviewStatus
        WHERE h.id IN :hotelIds AND h.isActive=true AND h.approvedStatus=:approval
        GROUP BY h.id
        """)
    List<PublicSummary> summarizePublished(@Param("hotelIds") List<UUID> hotelIds,
            @Param("reviewStatus") ReviewStatus reviewStatus,
            @Param("approval") com.example.hotelservice.Hotel.enums.HotelApprovalStatus approval);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM HotelReview r WHERE r.id = ?1")
    Optional<HotelReview> findByIdForUpdate(UUID id);

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
    Page<HotelReview> findByGuestId(UUID guestId, Pageable pageable);
    Optional<HotelReview> findByBookingIdAndGuestId(UUID bookingId, UUID guestId);

    /**
     * Check if guest already reviewed this hotel for this booking
     */
    Optional<HotelReview> findByHotelIdAndGuestIdAndBookingId(UUID hotelId, UUID guestId, UUID bookingId);

    /**
     * Check if guest already reviewed this hotel (any booking)
     */
    boolean existsByHotelIdAndGuestId(UUID hotelId, UUID guestId);

    /**
     * Get reviews for multiple hotels (for owner view)
     */
    Page<HotelReview> findByHotelIdInAndStatusOrderByCreatedAtDesc(
            List<UUID> hotelIds,
            ReviewStatus status,
            Pageable pageable
    );

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
