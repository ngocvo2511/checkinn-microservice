package com.example.hotelservice.Room.dto.response;

import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.Room.dto.request.CapacityDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomTypeResponse(
        UUID id,
        UUID hotelId,
        String name,
        BigDecimal basePrice,
        CapacityDto capacity,
        List<String> amenities,
        Boolean isActive,
        Integer totalRooms,
        Instant createdAt,
        Instant updatedAt,
        List<MediaAssetResponse> mediaAssets,
        String description
) {
    @com.fasterxml.jackson.annotation.JsonProperty("textEncoding")
    public String textEncoding() { return "PLAIN_V1"; }

    public RoomTypeResponse(UUID id, UUID hotelId, String name, BigDecimal basePrice, CapacityDto capacity,
                            List<String> amenities, Boolean isActive, Integer totalRooms, Instant createdAt,
                            Instant updatedAt, List<MediaAssetResponse> mediaAssets) {
        this(id, hotelId, name, basePrice, capacity, amenities, isActive, totalRooms, createdAt, updatedAt, mediaAssets, null);
    }
}

