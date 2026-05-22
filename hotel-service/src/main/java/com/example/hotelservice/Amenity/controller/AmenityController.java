package com.example.hotelservice.Amenity.controller;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Amenity.dto.request.AmenityUpdateRequest;
import com.example.hotelservice.Amenity.dto.response.CategoryResponse;
import com.example.hotelservice.Amenity.service.AmenityCategoryService;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
@Slf4j
public class AmenityController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;
    private final AmenityCategoryService amenityCategoryService;

    private UUID getOwnerId(String header) {
        return UUID.fromString(header);
    }

    // -------------------------------------------------------
    // 0. Danh sách danh mục tiện ích có sẵn
    // -------------------------------------------------------
    @GetMapping("/amenities/categories")
    public ResponseEntity<List<CategoryResponse>> getAvailableAmenityCategories() {
        log.info("[AMENITY_CONTROLLER_GET_CATEGORIES] Fetch available amenity categories request received");
        var categories = amenityCategoryService.getAvailableCategories();
        log.info("[AMENITY_CONTROLLER_GET_CATEGORIES] Fetch available amenity categories success - count: {}", categories.size());
        return ResponseEntity.ok(categories);
    }

    // -------------------------------------------------------
    // 1. Cập nhật amenities cho khách sạn
    // -------------------------------------------------------
    @PutMapping("/{hotelId}/amenities")
    public ResponseEntity<HotelResponse> updateAmenities(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody AmenityUpdateRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[AMENITY_CONTROLLER_UPDATE] Update amenities request received - hotelId: {}, ownerId: {}, categoriesCount: {}",
            hotelId, ownerId, request.amenityCategories() != null ? request.amenityCategories().size() : 0);
        var updated = hotelService.updateAmenities(hotelId, request.amenityCategories(), ownerId);
        log.info("[AMENITY_CONTROLLER_UPDATE] Update amenities success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    // -------------------------------------------------------
    // 2. Thêm amenity category vào khách sạn
    // -------------------------------------------------------
    @PostMapping("/{hotelId}/amenities")
    public ResponseEntity<HotelResponse> addAmenityCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody AmenityRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[AMENITY_CONTROLLER_ADD] Add amenity category request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
        var updated = hotelService.addAmenityCategory(hotelId, request, ownerId);
        log.info("[AMENITY_CONTROLLER_ADD] Add amenity category success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    // -------------------------------------------------------
    // 3. Xóa tất cả amenities của khách sạn
    // -------------------------------------------------------
    @DeleteMapping("/{hotelId}/amenities")
    public ResponseEntity<HotelResponse> clearAmenities(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[AMENITY_CONTROLLER_CLEAR] Clear amenities request received - hotelId: {}, ownerId: {}", hotelId, ownerId);
        var updated = hotelService.clearAmenities(hotelId, ownerId);
        log.info("[AMENITY_CONTROLLER_CLEAR] Clear amenities success - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    // -------------------------------------------------------
    // 4. Xóa amenity category theo tên
    // -------------------------------------------------------
    @DeleteMapping("/{hotelId}/amenities/{categoryTitle}")
    public ResponseEntity<HotelResponse> removeAmenityCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @PathVariable String categoryTitle
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[AMENITY_CONTROLLER_REMOVE] Remove amenity category request received - hotelId: {}, ownerId: {}, categoryTitle: {}",
            hotelId, ownerId, categoryTitle);
        var updated = hotelService.removeAmenityCategory(hotelId, categoryTitle, ownerId);
        log.info("[AMENITY_CONTROLLER_REMOVE] Remove amenity category success - hotelId: {}, ownerId: {}, categoryTitle: {}",
            hotelId, ownerId, categoryTitle);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }


}
