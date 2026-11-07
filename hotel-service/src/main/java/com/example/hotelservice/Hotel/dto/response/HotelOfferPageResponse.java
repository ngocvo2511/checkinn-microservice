package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Review.dto.response.ReviewSummaryResponse;
import java.util.List;

public record HotelOfferPageResponse(List<RatedOffer> content,int page,int size,long totalElements,
        int totalPages,String sort,List<String> availableAmenities) {
    public record RatedOffer(HotelResponse hotel,HotelOfferResponse.RoomOffer offer,ReviewSummaryResponse reviews) {}
}
