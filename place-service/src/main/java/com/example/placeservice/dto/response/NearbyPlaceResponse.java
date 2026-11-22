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
        /** True when distance/time come from the straight-line circuity estimate instead of OSRM routing. */
        Boolean distanceEstimated,
        List<PlacePhotoDto> photos,
        List<PlaceReviewDto> reviews
) implements Serializable {
}
