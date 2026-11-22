package com.example.placeservice.dto.response;

import com.example.placeservice.entity.Place;
import com.example.placeservice.enums.PlaceCategory;

import java.util.UUID;

/** Public catalog fields only: no provider identifiers, photos, or reviews. */
public record PlaceSummary(
        UUID id,
        String name,
        PlaceCategory category,
        String address,
        Double latitude,
        Double longitude,
        String provinceName,
        Double rating,
        Integer userRatingsTotal,
        String coverImageUrl
) {
    public static PlaceSummary from(Place place) {
        return new PlaceSummary(place.getId(), place.getName(), place.getCategory(), place.getAddress(),
                place.getLatitude(), place.getLongitude(), place.getProvinceName(), place.getRating(),
                place.getUserRatingsTotal(), place.getCoverImageUrl());
    }
}
