package com.example.hotelservice.Review.mapper;

import com.example.hotelservice.Review.dto.request.CreateReviewRequest;
import com.example.hotelservice.Review.dto.response.HotelReviewResponse;
import com.example.hotelservice.Review.entity.HotelReview;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public HotelReview createRequestToEntity(CreateReviewRequest request) {
        return HotelReview.builder()
                .hotelId(request.getHotelId())
                .rating(request.getRating())
                .staffRating(request.getStaffRating())
                .amenitiesRating(request.getAmenitiesRating())
                .cleanlinessRating(request.getCleanlinessRating())
                .comfortRating(request.getComfortRating())
                .valueForMoneyRating(request.getValueForMoneyRating())
                .locationRating(request.getLocationRating())
                .title(request.getTitle())
                .content(request.getContent())
                .bookingId(request.getBookingId())
                .build();
    }

    public HotelReviewResponse entityToResponse(HotelReview entity) {
        return HotelReviewResponse.builder()
                .id(entity.getId())
                .hotelId(entity.getHotelId())
                .guestId(entity.getGuestId())
                .bookingId(entity.getBookingId())
                .rating(entity.getRating())
                .staffRating(entity.getStaffRating())
                .amenitiesRating(entity.getAmenitiesRating())
                .cleanlinessRating(entity.getCleanlinessRating())
                .comfortRating(entity.getComfortRating())
                .valueForMoneyRating(entity.getValueForMoneyRating())
                .locationRating(entity.getLocationRating())
                .title(entity.getTitle())
                .content(entity.getContent())
                .helpfulCount(entity.getHelpfulCount())
                .unhelpfulCount(entity.getUnhelpfulCount())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
