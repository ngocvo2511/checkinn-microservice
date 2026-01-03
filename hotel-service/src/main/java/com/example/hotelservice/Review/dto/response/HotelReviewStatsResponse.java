package com.example.hotelservice.Review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelReviewStatsResponse {

    private UUID hotelId;
    private BigDecimal averageRating;
    private Long totalReviews;
    
    // Rating distribution for 10-point scale
    private Long ratingDistribution1;
    private Long ratingDistribution2;
    private Long ratingDistribution3;
    private Long ratingDistribution4;
    private Long ratingDistribution5;
    private Long ratingDistribution6;
    private Long ratingDistribution7;
    private Long ratingDistribution8;
    private Long ratingDistribution9;
    private Long ratingDistribution10;
    
    // Average ratings by criteria
    private Double averageStaffRating;
    private Double averageAmenitiesRating;
    private Double averageCleanlinessRating;
    private Double averageComfortRating;
    private Double averageValueForMoneyRating;
    private Double averageLocationRating;
    
    private List<HotelReviewResponse> recentReviews;
}
