package com.example.placeservice.controller;

import com.example.placeservice.dto.response.NearbyPlaceResponse;
import com.example.placeservice.enums.PlaceCategory;
import com.example.placeservice.service.PlaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService placeService;

    /**
     * Endpoint to retrieve tourist attractions and dining spots surrounding geographic coordinates.
     * Consumed by frontend, Hotel Service, and AI Service.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyPlaceResponse>> getNearbyPlaces(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false, defaultValue = "ALL") PlaceCategory category,
            @RequestParam(required = false, defaultValue = "10.0") Double radiusKm,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        log.info("[PLACE_CONTROLLER] getNearbyPlaces lat: {}, lng: {}, category: {}, radiusKm: {}, limit: {}",
                latitude, longitude, category, radiusKm, limit);
        List<NearbyPlaceResponse> result = placeService.getNearbyPlaces(latitude, longitude, category, radiusKm, limit);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve full details of a specific place by UUID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<NearbyPlaceResponse> getPlaceById(@PathVariable UUID id) {
        log.info("[PLACE_CONTROLLER] getPlaceById: {}", id);
        NearbyPlaceResponse result = placeService.getPlaceById(id);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve places located within a specific city.
     */
    @GetMapping("/by-city/{cityId}")
    public ResponseEntity<List<NearbyPlaceResponse>> getPlacesByCityId(
            @PathVariable UUID cityId,
            @RequestParam(required = false, defaultValue = "ALL") PlaceCategory category,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        log.info("[PLACE_CONTROLLER] getPlacesByCityId: {}, category: {}, limit: {}", cityId, category, limit);
        List<NearbyPlaceResponse> result = placeService.getPlacesByCityId(cityId, category, limit);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve places located within a specific province (e.g. Hà Nội, Đà Nẵng, Lâm Đồng).
     * Eliminates cross-service database lookups.
     */
    @GetMapping("/by-province")
    public ResponseEntity<List<NearbyPlaceResponse>> getPlacesByProvince(
            @RequestParam String provinceName,
            @RequestParam(required = false, defaultValue = "ALL") PlaceCategory category,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        log.info("[PLACE_CONTROLLER] getPlacesByProvince: {}, category: {}, limit: {}", provinceName, category, limit);
        List<NearbyPlaceResponse> result = placeService.getPlacesByProvince(provinceName, category, limit);
        return ResponseEntity.ok(result);
    }
}
