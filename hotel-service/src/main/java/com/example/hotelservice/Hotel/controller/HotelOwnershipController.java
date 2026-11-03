package com.example.hotelservice.Hotel.controller;

import com.example.hotelservice.Hotel.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

/** Minimal service-only identity lookup, independent of catalog publication. */
@RestController
@RequestMapping("/internal/hotels")
@RequiredArgsConstructor
public class HotelOwnershipController {
    private final HotelService hotels;
    @GetMapping("/{id}/owner")
    public org.springframework.http.ResponseEntity<Map<String, UUID>> owner(@PathVariable UUID id) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("ownerId", hotels.getById(id).getOwnerId()));
    }
}
