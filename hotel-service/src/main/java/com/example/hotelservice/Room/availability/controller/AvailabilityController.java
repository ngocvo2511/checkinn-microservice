package com.example.hotelservice.Room.availability.controller;

import com.example.hotelservice.Room.availability.dto.AvailabilityCheckResponse;
import com.example.hotelservice.Room.availability.dto.HoldRequest;
import com.example.hotelservice.Room.availability.dto.HoldResponse;
import com.example.hotelservice.Room.availability.service.AvailabilityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
@Slf4j
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/check")
    public ResponseEntity<AvailabilityCheckResponse> check(
            @RequestParam UUID roomTypeId,
            @RequestParam("checkIn") java.time.LocalDate checkIn,
            @RequestParam("checkOut") java.time.LocalDate checkOut,
            @RequestParam int quantity
    ) {
        log.info("[AVAILABILITY_CONTROLLER_CHECK] Check availability request received - roomTypeId: {}, checkIn: {}, checkOut: {}, quantity: {}",
            roomTypeId, checkIn, checkOut, quantity);
        boolean available = availabilityService.isAvailable(roomTypeId, checkIn, checkOut, quantity);
        int availableRooms = availabilityService.getAvailableRoomCount(roomTypeId, checkIn, checkOut);
        log.info("[AVAILABILITY_CONTROLLER_CHECK] Check availability success - roomTypeId: {}, available: {}, availableRooms: {}",
            roomTypeId, available, availableRooms);
        return ResponseEntity.ok(new AvailabilityCheckResponse(available, availableRooms));
    }

    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody HoldRequest request) {
        try {
            log.info("[AVAILABILITY_CONTROLLER_HOLD] Hold request received - roomTypeId: {}, checkIn: {}, checkOut: {}, quantity: {}",
                    request.roomTypeId(), request.checkInDate(), request.checkOutDate(), request.quantity());
            HoldResponse response = availabilityService.checkAndHold(request);
            log.info("[AVAILABILITY_CONTROLLER_HOLD] Hold success - holdId: {}, roomTypeId: {}", response.holdId(), request.roomTypeId());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_HOLD] Hold rejected - roomTypeId: {}, reason: {}", request.roomTypeId(), ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (IllegalStateException | EntityNotFoundException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_HOLD] Hold conflict - roomTypeId: {}, reason: {}", request.roomTypeId(), ex.getMessage());
            return ResponseEntity.status(409).body(ex.getMessage());
        }
    }

    @PostMapping("/hold/{holdId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable UUID holdId) {
        try {
            log.info("[AVAILABILITY_CONTROLLER_CONFIRM] Confirm hold request received - holdId: {}", holdId);
            HoldResponse response = availabilityService.confirmHold(holdId);
            log.info("[AVAILABILITY_CONTROLLER_CONFIRM] Confirm hold success - holdId: {}", holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_CONFIRM] Confirm hold not found - holdId: {}, reason: {}", holdId, ex.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_CONFIRM] Confirm hold conflict - holdId: {}, reason: {}", holdId, ex.getMessage());
            return ResponseEntity.status(409).body(ex.getMessage());
        }
    }

    @PostMapping("/hold/{holdId}/release")
    public ResponseEntity<?> release(@PathVariable UUID holdId) {
        try {
            log.info("[AVAILABILITY_CONTROLLER_RELEASE] Release hold request received - holdId: {}", holdId);
            HoldResponse response = availabilityService.releaseHold(holdId);
            log.info("[AVAILABILITY_CONTROLLER_RELEASE] Release hold success - holdId: {}", holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_RELEASE] Release hold not found - holdId: {}, reason: {}", holdId, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/hold/{holdId}")
    public ResponseEntity<?> getHold(@PathVariable UUID holdId) {
        try {
            log.info("[AVAILABILITY_CONTROLLER_GET_HOLD] Get hold request received - holdId: {}", holdId);
            HoldResponse response = availabilityService.getHold(holdId);
            log.info("[AVAILABILITY_CONTROLLER_GET_HOLD] Get hold success - holdId: {}", holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            log.warn("[AVAILABILITY_CONTROLLER_GET_HOLD] Get hold not found - holdId: {}, reason: {}", holdId, ex.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
