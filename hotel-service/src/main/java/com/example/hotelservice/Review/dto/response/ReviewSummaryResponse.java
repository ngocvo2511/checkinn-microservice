package com.example.hotelservice.Review.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Null average with zero count means known no reviews, not a zero guest score. */
public record ReviewSummaryResponse(UUID hotelId, long totalReviews, BigDecimal averageRating) {}
