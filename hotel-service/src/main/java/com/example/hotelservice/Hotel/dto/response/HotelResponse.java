package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HotelResponse(
        UUID id,
        UUID ownerId,
        String name,
        String description,
        Short starRating,
        HotelAddressDto address,
        Boolean isActive,
        String approvedStatus,
        Instant createdAt,
        Instant updatedAt,
        List<RoomTypeResponse> roomTypes,
        List<MediaAssetResponse> mediaAssets
) {}

