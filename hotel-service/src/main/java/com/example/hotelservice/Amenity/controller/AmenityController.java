package com.example.hotelservice.Amenity.controller;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Amenity.dto.request.AmenityUpdateRequest;
import com.example.hotelservice.Amenity.dto.response.CategoryResponse;
import com.example.hotelservice.Amenity.service.AmenityCategoryService;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
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
        return ResponseEntity.ok(amenityCategoryService.getAvailableCategories());
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
        var updated = hotelService.updateAmenities(hotelId, request.amenityCategories(), ownerId);
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
        var updated = hotelService.addAmenityCategory(hotelId, request, ownerId);
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
        var updated = hotelService.clearAmenities(hotelId, ownerId);
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
        var updated = hotelService.removeAmenityCategory(hotelId, categoryTitle, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }


}
