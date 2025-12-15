package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.dto.request.HotelApproveRequest;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    private UUID getOwnerId(String header) {
        return UUID.fromString(header);
    }

    // -------------------------------------------------------
    // 1. Tạo khách sạn
    // -------------------------------------------------------
    @PostMapping
    public ResponseEntity<HotelResponse> createHotel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody HotelCreateRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());

        var saved = hotelService.createHotel(request, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(saved));
    }

    // -------------------------------------------------------
    // 2. Update khách sạn
    // -------------------------------------------------------
    @PutMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> updateHotel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody HotelUpdateRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());

        var updated = hotelService.updateHotel(hotelId, request, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    // -------------------------------------------------------
    // 3. Lấy chi tiết khách sạn
    // -------------------------------------------------------
    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> getHotel(@PathVariable UUID hotelId) {
        var hotel = hotelService.getById(hotelId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(hotel));
    }

    // -------------------------------------------------------
    // 4. Lấy danh sách khách sạn của owner
    // -------------------------------------------------------
    @GetMapping("/owner")
    public ResponseEntity<?> getHotelsByOwner(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());

        var hotels = hotelService.getByOwner(ownerId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 5. Tìm khách sạn theo thành phố
    // -------------------------------------------------------
    @GetMapping("/city/{cityId}")
    public ResponseEntity<?> getHotelsByCity(@PathVariable UUID cityId) {
        var hotels = hotelService.getByCity(cityId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 6. Tìm khách sạn của owner theo thành phố
    // -------------------------------------------------------
    @GetMapping("/owner/{cityId}")
    public ResponseEntity<?> getHotelsByOwnerAndCity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cityId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());

        var hotels = hotelService.getByOwnerAndCity(ownerId, cityId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }
    @PutMapping("/{hotelId}/approve")
    public ResponseEntity<?> approveHotel(
            @PathVariable UUID hotelId
    ) {
        hotelService.approveHotel(hotelId);
        return ResponseEntity.ok("Hotel approved");
    }

    // ADMIN: Từ chối
    @PostMapping("/{hotelId}/reject")
    public ResponseEntity<?> rejectHotel(
            @PathVariable UUID hotelId,
            @RequestBody HotelApproveRequest request
    ) {
        hotelService.rejectHotel(hotelId);
        return ResponseEntity.ok("Hotel rejected: " + request.note());
    }

    // Admin activate
    @PatchMapping("/{hotelId}/activate")
    public ResponseEntity<?> activateHotel(@PathVariable UUID hotelId) {
        hotelService.activateHotel(hotelId);
        return ResponseEntity.ok("Hotel activated");
    }

    // Admin deactivate
    @PutMapping("/{hotelId}/deactivate")
    public ResponseEntity<?> deactivateHotel(
            @PathVariable UUID hotelId
    ) {
        hotelService.deactivateHotel(hotelId);
        return ResponseEntity.ok("Hotel deactivated");
    }
}
