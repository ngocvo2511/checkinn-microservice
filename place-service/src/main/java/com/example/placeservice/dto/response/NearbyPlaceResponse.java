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
        // Assuming average urban transit velocity in Vietnam (~22 km/h) plus 1-minute departure buffer
        int minutes = (int) Math.round((distanceKm / 22.0) * 60.0 + 1.0);
        return Math.max(1, minutes);
    }
}
