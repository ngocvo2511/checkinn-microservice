package com.example.placeservice.repository;

import java.util.UUID;

public interface NearbyPlaceProjection {
    UUID getId();
    String getGooglePlaceId();
    String getName();
    String getCategory();
    String getAddress();
    Double getLatitude();
    Double getLongitude();
    Double getRating();
    Integer getUserRatingsTotal();
    String getCoverImageUrl();
    String getDetailUrl();
    String getProvinceName();
    Double getDistanceKm();
}
