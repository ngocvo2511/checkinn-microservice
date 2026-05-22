package com.example.hotelservice.Room.controller;

import com.example.hotelservice.Room.dto.request.RoomTypeCreateRequest;
import com.example.hotelservice.Room.dto.request.RoomTypeUpdateRequest;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Room.mapper.RoomTypeMapper;
import com.example.hotelservice.Room.service.RoomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
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
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RoomTypeCreateRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[ROOM_TYPE_CONTROLLER_CREATE] Create room type request received - hotelId: {}, ownerId: {}, name: {}",
            request.hotelId(), ownerId, request.name());

        var saved = roomTypeService.createRoomType(request, ownerId);
        log.info("[ROOM_TYPE_CONTROLLER_CREATE] Create room type success - roomTypeId: {}, ownerId: {}", saved.getId(), ownerId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(saved));
    }

    // -------------------------------------------------------
    // 2. Update RoomType
    // -------------------------------------------------------
    @PutMapping("/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> updateRoomType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomTypeId,
            @RequestBody RoomTypeUpdateRequest request
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[ROOM_TYPE_CONTROLLER_UPDATE] Update room type request received - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);

        var updated = roomTypeService.updateRoomType(roomTypeId, request, ownerId);
        log.info("[ROOM_TYPE_CONTROLLER_UPDATE] Update room type success - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(updated));
    }



    // -------------------------------------------------------
    // 3. Get RoomType By Id
    // -------------------------------------------------------
    @GetMapping("/{roomTypeId}")
    public ResponseEntity<RoomTypeResponse> getById(@PathVariable UUID roomTypeId) {
        log.info("[ROOM_TYPE_CONTROLLER_GET_BY_ID] Fetch room type request received - roomTypeId: {}", roomTypeId);
        var entity = roomTypeService.getById(roomTypeId);
        log.info("[ROOM_TYPE_CONTROLLER_GET_BY_ID] Fetch room type success - roomTypeId: {}", roomTypeId);
        return ResponseEntity.ok(roomTypeMapper.toRoomTypeResponse(entity));
    }

    // -------------------------------------------------------
    // 4. Get RoomTypes by HotelId
    // -------------------------------------------------------
    @GetMapping("/hotel/{hotelId}")
    public ResponseEntity<?> getByHotel(@PathVariable UUID hotelId) {
        log.info("[ROOM_TYPE_CONTROLLER_GET_BY_HOTEL] Fetch room types by hotel request received - hotelId: {}", hotelId);

        var list = roomTypeService.getByHotel(hotelId)
                .stream()
                .map(roomTypeMapper::toRoomTypeResponse)
                .toList();

        log.info("[ROOM_TYPE_CONTROLLER_GET_BY_HOTEL] Fetch room types by hotel success - hotelId: {}, count: {}", hotelId, list.size());
        return ResponseEntity.ok(list);
    }

    // Activate / deactivate
    @PutMapping("/{roomTypeId}/activate")
    public ResponseEntity<?> activate(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID roomTypeId
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);
        log.info("[ROOM_TYPE_CONTROLLER_ACTIVATE] Activate room type request received - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);
        roomTypeService.activateRoomType(roomTypeId, ownerId);
        log.info("[ROOM_TYPE_CONTROLLER_ACTIVATE] Activate room type success - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);
        return ResponseEntity.ok("Activated");
    }

    @PutMapping("/{roomTypeId}/deactivate")
    public ResponseEntity<?> deactivate(
            @RequestHeader("X-OWNER-ID") String ownerIdHeader,
            @PathVariable UUID roomTypeId
    ) {
        UUID ownerId = getOwnerId(ownerIdHeader);
        log.info("[ROOM_TYPE_CONTROLLER_DEACTIVATE] Deactivate room type request received - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);
        roomTypeService.deactivateRoomType(roomTypeId, ownerId);
        log.info("[ROOM_TYPE_CONTROLLER_DEACTIVATE] Deactivate room type success - roomTypeId: {}, ownerId: {}", roomTypeId, ownerId);
        return ResponseEntity.ok("Deactivated");
    }
}
