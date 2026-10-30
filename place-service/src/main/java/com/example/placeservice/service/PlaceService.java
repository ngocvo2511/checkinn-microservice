package com.example.placeservice.service;

import com.example.placeservice.dto.response.NearbyPlaceResponse;
import com.example.placeservice.enums.PlaceCategory;

import java.util.List;
import java.util.UUID;

public interface PlaceService {

    /**
     * Retrieves nearby points of interest (attractions, dining, cafes, historical landmarks)
     * surrounding geographic coordinates.
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param category POI category filter (or ALL)
     * @param radiusKm Radius in kilometers (default e.g. 10km)
     * @param limit Maximum places to return (default e.g. 10)
     * @return List of nearby places with distance, travel time, photos, and reviews
     */
    List<NearbyPlaceResponse> getNearbyPlaces(Double latitude, Double longitude, PlaceCategory category, Double radiusKm, Integer limit);

    /**
     * Retrieves full place details by unique identifier.
     *
     * @param id Place UUID
     * @return Full place details with photos and reviews
     */
    NearbyPlaceResponse getPlaceById(UUID id);

    /**
     * Retrieves places located within a specific city.
     *
     * @param cityId City UUID
     * @param category POI category filter (or ALL)
     * @param limit Maximum places to return
     * @return List of places for that city
     */
    List<NearbyPlaceResponse> getPlacesByCityId(UUID cityId, PlaceCategory category, Integer limit);

    /**
     * Retrieves places located within a specific province (e.g. Hà Nội, Đà Nẵng, Lâm Đồng)
     * without requiring cross-service database lookups.
     *
     * @param provinceName Province or centrally-governed city name
     * @param category POI category filter (or ALL)
     * @param limit Maximum places to return
     * @return List of places for that province
     */
    List<NearbyPlaceResponse> getPlacesByProvince(String provinceName, PlaceCategory category, Integer limit);
}
