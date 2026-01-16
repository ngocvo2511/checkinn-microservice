package com.example.hotelservice.Room.availability.controller;

import com.example.hotelservice.Room.availability.dto.AvailabilityCheckResponse;
import com.example.hotelservice.Room.availability.dto.HoldRequest;
import com.example.hotelservice.Room.availability.dto.HoldResponse;
import com.example.hotelservice.Room.availability.service.AvailabilityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/check")
    public ResponseEntity<AvailabilityCheckResponse> check(
            @RequestParam UUID roomTypeId,
            @RequestParam("checkIn") java.time.LocalDate checkIn,
            @RequestParam("checkOut") java.time.LocalDate checkOut,
            @RequestParam int quantity
    ) {
        boolean available = availabilityService.isAvailable(roomTypeId, checkIn, checkOut, quantity);
        int availableRooms = availabilityService.getAvailableRoomCount(roomTypeId, checkIn, checkOut);
        return ResponseEntity.ok(new AvailabilityCheckResponse(available, availableRooms));
    }

    @PostMapping("/hold")
    public ResponseEntity<?> hold(@RequestBody HoldRequest request) {
        try {
            HoldResponse response = availabilityService.checkAndHold(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (IllegalStateException | EntityNotFoundException ex) {
            return ResponseEntity.status(409).body(ex.getMessage());
        }
    }

    @PostMapping("/hold/{holdId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable UUID holdId) {
        try {
            HoldResponse response = availabilityService.confirmHold(holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(ex.getMessage());
        }
    }

    @PostMapping("/hold/{holdId}/release")
    public ResponseEntity<?> release(@PathVariable UUID holdId) {
        try {
            HoldResponse response = availabilityService.releaseHold(holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/hold/{holdId}")
    public ResponseEntity<?> getHold(@PathVariable UUID holdId) {
        try {
            HoldResponse response = availabilityService.getHold(holdId);
            return ResponseEntity.ok(response);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
