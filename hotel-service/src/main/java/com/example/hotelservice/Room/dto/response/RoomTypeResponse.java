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
        List<MediaAssetResponse> mediaAssets
) {}

