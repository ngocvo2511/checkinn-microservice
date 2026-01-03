package com.example.hotelservice.Review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_reviews", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"hotel_id", "guest_id", "booking_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(nullable = false)
    private java.math.BigDecimal rating;

    // Detailed ratings (1-10 scale)
    @Column(name = "staff_rating")
    private Double staffRating;

    @Column(name = "amenities_rating")
    private Double amenitiesRating;

    @Column(name = "cleanliness_rating")
    private Double cleanlinessRating;

    @Column(name = "comfort_rating")
    private Double comfortRating;

    @Column(name = "value_for_money_rating")
    private Double valueForMoneyRating;

    @Column(name = "location_rating")
    private Double locationRating;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "helpful_count", nullable = false)
    @Builder.Default
    private Integer helpfulCount = 0;

    @Column(name = "unhelpful_count", nullable = false)
    @Builder.Default
    private Integer unhelpfulCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private com.example.hotelservice.Review.enums.ReviewStatus status = com.example.hotelservice.Review.enums.ReviewStatus.PUBLISHED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void prePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = Instant.now();
    }
}
