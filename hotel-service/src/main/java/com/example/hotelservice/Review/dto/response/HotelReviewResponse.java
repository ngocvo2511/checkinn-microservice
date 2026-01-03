package com.example.hotelservice.Review.dto.response;

import com.example.hotelservice.Review.enums.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelReviewResponse {

    private UUID id;
    private UUID hotelId;
    private UUID guestId;
    private UUID bookingId;
    private BigDecimal rating;
    
    // Detailed ratings (1-10 scale)
    private Double staffRating;
    private Double amenitiesRating;
    private Double cleanlinessRating;
    private Double comfortRating;
    private Double valueForMoneyRating;
    private Double locationRating;
    
    private String title;
    private String content;
    private Integer helpfulCount;
    private Integer unhelpfulCount;
    private ReviewStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    
    // Guest info (if available)
    private String guestName;
    private String guestAvatar;
    
    // Current user's feedback on this review (if authenticated)
    private String userFeedback; // "HELPFUL", "UNHELPFUL", or null
}
