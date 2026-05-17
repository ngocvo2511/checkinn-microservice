package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.dto.request.HotelApproveRequest;
import com.example.hotelservice.Hotel.dto.request.HotelCreateRequest;
import com.example.hotelservice.Hotel.dto.request.HotelUpdateRequest;
import com.example.hotelservice.Hotel.dto.response.HotelResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelDetailResponse;
import com.example.hotelservice.Hotel.dto.response.PendingHotelResponse;
import com.example.hotelservice.Hotel.mapper.HotelMapper;
import com.example.hotelservice.Hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/hotels")
@RequiredArgsConstructor
@Slf4j
public class HotelController {

    private final HotelService hotelService;
    private final HotelMapper hotelMapper;

    private UUID getOwnerId(String header) {
        return UUID.fromString(header);
    }
    private Boolean verifyAdmin(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role != null && role.equals("ADMIN");
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
        log.info("[HOTEL_CONTROLLER_CREATE] Create hotel request received - ownerId: {}, cityId: {}, name: {}",
            ownerId, request.cityId(), request.name());

        var saved = hotelService.createHotel(request, ownerId);
        log.info("[HOTEL_CONTROLLER_CREATE] Hotel created successfully - ownerId: {}, hotelId: {}", ownerId, saved.getId());
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
        log.info("[HOTEL_CONTROLLER_UPDATE] Update hotel request received - hotelId: {}, ownerId: {}", hotelId, ownerId);

        var updated = hotelService.updateHotel(hotelId, request, ownerId);
        log.info("[HOTEL_CONTROLLER_UPDATE] Hotel updated successfully - hotelId: {}, ownerId: {}", hotelId, ownerId);
        return ResponseEntity.ok(hotelMapper.toHotelResponse(updated));
    }

    // -------------------------------------------------------
    // 3. Lấy chi tiết khách sạn
    // -------------------------------------------------------
    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelResponse> getHotel(@PathVariable UUID hotelId) {
        log.info("[HOTEL_CONTROLLER_GET] Get hotel request received - hotelId: {}", hotelId);
        var hotel = hotelService.getDetail(hotelId);
        return ResponseEntity.ok(hotel);
    }

    // -------------------------------------------------------
    // Lấy tất cả khách sạn đã duyệt
    // -------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<HotelResponse>> getAllApprovedHotels() {
        log.info("[HOTEL_CONTROLLER_GET_APPROVED] Fetch approved hotels request received");
        List<HotelResponse> hotels = hotelService.getAllApprovedHotels()
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();
        return ResponseEntity.ok(hotels);
    }

    //Lấy danh sách khách sạn chờ duyệt
    @GetMapping("/pending")
    public ResponseEntity<List<PendingHotelResponse>> getPendingHotels(
            @AuthenticationPrincipal Jwt jwt
    ) {
        if(!verifyAdmin(jwt)){
            return ResponseEntity.status(403).body(null);
        }
        log.info("[HOTEL_CONTROLLER_PENDING] Fetch pending hotels request received");
        List<PendingHotelResponse> pendingHotels = hotelService.getPendingHotels()
                .stream()
                .map(hotelMapper::toPendingHotelResponse)
                .toList();
        return ResponseEntity.ok(pendingHotels);
    }

    @GetMapping("/pending/{hotelId}")
    public ResponseEntity<PendingHotelDetailResponse> getPendingHotelById(
            @PathVariable UUID hotelId,
            @AuthenticationPrincipal Jwt jwt){
        if(!verifyAdmin(jwt)){
            return ResponseEntity.status(403).body(null);
        }
        log.info("[HOTEL_CONTROLLER_PENDING_DETAIL] Fetch pending hotel detail request received - hotelId: {}", hotelId);
        return ResponseEntity.ok(hotelService.getPendingHotelDetail(hotelId));
    }


    // -------------------------------------------------------
    // 4. Lấy danh sách khách sạn của owner
    // -------------------------------------------------------
    @GetMapping("/owner")
    public ResponseEntity<?> getHotelsByOwner(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[HOTEL_CONTROLLER_OWNER] Fetch owner hotels request received - ownerId: {}", ownerId);
        var hotels = hotelService.getByOwner(ownerId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 5. Tìm khách sạn theo tên
    // -------------------------------------------------------
    @GetMapping("/search")
    public ResponseEntity<?> searchHotelsByName(@RequestParam String name) {
        log.info("[HOTEL_CONTROLLER_SEARCH] Search hotels by name request received - name: {}", name);
        var hotels = hotelService.searchByName(name)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 6. Tìm khách sạn theo thành phố
    // -------------------------------------------------------
    @GetMapping("/city/{cityId}")
    public ResponseEntity<?> getHotelsByCity(@PathVariable UUID cityId) {
        log.info("[HOTEL_CONTROLLER_CITY] Fetch hotels by city request received - cityId: {}", cityId);
        var hotels = hotelService.getByCity(cityId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }

    // -------------------------------------------------------
    // 7. Tìm khách sạn của owner theo thành phố
    // -------------------------------------------------------
    @GetMapping("/owner/{cityId}")
    public ResponseEntity<?> getHotelsByOwnerAndCity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID cityId
    ) {
        UUID ownerId = getOwnerId(jwt.getSubject());
        log.info("[HOTEL_CONTROLLER_OWNER_CITY] Fetch owner hotels by city request received - ownerId: {}, cityId: {}",
            ownerId, cityId);

        var hotels = hotelService.getByOwnerAndCity(ownerId, cityId)
                .stream()
                .map(hotelMapper::toHotelResponse)
                .toList();

        return ResponseEntity.ok(hotels);
    }
    @PutMapping("/{hotelId}/approve")
    public ResponseEntity<?> approveHotel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId
    ) {
        if(!verifyAdmin(jwt)){
            return ResponseEntity.status(403).body("Access denied");
        }
        log.info("[HOTEL_CONTROLLER_APPROVE] Approve hotel request received - hotelId: {}", hotelId);
        hotelService.approveHotel(hotelId);
        return ResponseEntity.ok("Hotel approved");
    }

    // ADMIN: Từ chối
    @PostMapping("/{hotelId}/reject")
    public ResponseEntity<?> rejectHotel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID hotelId,
            @RequestBody HotelApproveRequest request
    ) {
        if(!verifyAdmin(jwt)){
            return ResponseEntity.status(403).body("Access denied");
        }
        log.info("[HOTEL_CONTROLLER_REJECT] Reject hotel request received - hotelId: {}, note: {}", hotelId, request.note());
        hotelService.rejectHotel(hotelId);
        return ResponseEntity.ok("Hotel rejected: " + request.note());
    }

    // Admin activate
    @PatchMapping("/{hotelId}/activate")
    public ResponseEntity<?> activateHotel(@PathVariable UUID hotelId) {
        log.info("[HOTEL_CONTROLLER_ACTIVATE] Activate hotel request received - hotelId: {}", hotelId);
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
