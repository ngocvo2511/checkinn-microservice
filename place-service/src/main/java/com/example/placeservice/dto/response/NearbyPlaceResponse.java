package com.example.placeservice.dto.response;

import lombok.Builder;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Builder
public record NearbyPlaceResponse(
        UUID id,
        String googlePlaceId,
        String name,
        String category,
        String address,
        Double latitude,
        Double longitude,
        Double rating,
        Integer userRatingsTotal,
        String coverImageUrl,
        String detailUrl,
        String provinceName,
        Double distanceKm,
        Integer estimatedTravelTimeMinutes,
        List<PlacePhotoDto> photos,
        List<PlaceReviewDto> reviews
) implements Serializable {

    public static Integer calculateTravelTimeMinutes(Double distanceKm) {
        if (distanceKm == null || distanceKm <= 0) {
            return 1;
        }
        // Assuming average urban transit speed of 25 km/h
        int minutes = (int) Math.round((distanceKm / 25.0) * 60.0);
        return Math.max(1, minutes);
    }
}
