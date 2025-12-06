package com.example.hotelservice.Room.controller;

import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoomTypeController {

    private final RoomTypeService roomTypeService;
    private final RoomTypeMapper roomTypeMapper;

    private UUID getOwnerId(String header) {
        return UUID.fromString(header);
    }

    // -------------------------------------------------------
    // 1. Tạo RoomType
    // -------------------------------------------------------
    @PostMapping
    public ResponseEntity<RoomTypeResponse> createRoomType(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @RequestBody RoomTypeCreateRequest request
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);

        var saved = roomTypeService.createRoomType(request, ownerId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(saved));
    }

    // -------------------------------------------------------
    // 2. Update RoomType
    // -------------------------------------------------------
    @PutMapping("/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID roomTypeId,
            @RequestBody RoomTypeUpdateRequest request
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);

        var updated = roomTypeService.updateRoomType(roomTypeId, request, ownerId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(updated));
    }



    // -------------------------------------------------------
    // 3. Get RoomType By Id
    // -------------------------------------------------------
    @GetMapping("/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> getById(@PathVariable UUID roomTypeId) {
        var entity = roomTypeService.getById(roomTypeId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(entity));
    }

    // -------------------------------------------------------
    // 4. Get RoomTypes by HotelId
    // -------------------------------------------------------
    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<?> getByHotel(@PathVariable UUID hotelId) {

        var list = roomTypeService.getByHotel(hotelId)
                .stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();

        return ResponseEntity.ok(list);
    }

    // Activate / deactivate
    @PutMapping("/{roomTypeId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID roomTypeId
    ) {
        roomTypeService.activateRoomType(roomTypeId, getOwnerId(ownerIdHeader));
        return ResponseEntity.ok("Activated");
    }

    @PutMapping("/{roomTypeId}/deactivate")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID roomTypeId
    ) {
        roomTypeService.deactivateRoomType(roomTypeId, getOwnerId(ownerIdHeader));
        return ResponseEntity.ok("Deactivated");
    }
}
