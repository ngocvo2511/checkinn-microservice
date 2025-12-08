package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.dto.request.HotelApproveRequest;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @RequestBody HotelCreateRequest request
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);

        var saved = hotelService.createHotel(request, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(saved));
    }

    // -------------------------------------------------------
    // 2. Update khách sạn
    // -------------------------------------------------------
    @PutMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> updateHotel(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID hotelId,
            @RequestBody HotelUpdateRequest request
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);

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
            @RequestHeader("X-OWNER-ID") String ownerIdHeader
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);

        var hotels = hotelService.getByOwner(ownerId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 5. Admin: Duyệt khách sạn
    // -------------------------------------------------------
    @PutMapping("/{hotelId}/approve")
    public ResponseEntity<?> approveHotel(
            @RequestHeader("X-ADMIN") boolean isAdmin,
            @PathVariable UUID hotelId
    ) {
        if (!isAdmin) return ResponseEntity.status(403).build();

        hotelService.approveHotel(hotelId);
        return ResponseEntity.ok("Hotel approved");
    }

    // ADMIN: Từ chối
    @PostMapping("/{hotelId}/reject")
    public ResponseEntity<?> rejectHotel(
            @RequestHeader("X-ADMIN") boolean isAdmin,
            @PathVariable UUID hotelId,
            @RequestBody HotelApproveRequest request
    ) {
        if (!isAdmin) return ResponseEntity.status(403).build();

        hotelService.rejectHotel(hotelId);
        return ResponseEntity.ok("Hotel rejected: " + request.note());
    }

    // Admin activate
    @PatchMapping("/{hotelId}/activate")
    public ResponseEntity<?> activateHotel(@RequestHeader("X-ADMIN") boolean isAdmin,
                                           @PathVariable UUID hotelId) {
        if (!isAdmin) return ResponseEntity.status(403).build();

        hotelService.activateHotel(hotelId);
        return ResponseEntity.ok("Hotel activated");
    }

    // Admin deactivate
    @PutMapping("/{hotelId}/deactivate")
    public ResponseEntity<?> deactivateHotel(
            @RequestHeader("X-ADMIN") boolean isAdmin,
            @PathVariable UUID hotelId
    ) {
        if (!isAdmin) return ResponseEntity.status(403).build();

        hotelService.deactivateHotel(hotelId);
        return ResponseEntity.ok("Hotel deactivated");
    }
}
