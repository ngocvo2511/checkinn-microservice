package com.example.hotelservice.Hotel.dto.response;

import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.example.hotelservice.City.dto.response.CityResponse;
import com.example.hotelservice.Hotel.dto.request.HotelAddressDto;
import com.example.hotelservice.Hotel.enums.HotelApprovalStatus;
import com.example.hotelservice.MediaAsset.dto.response.MediaAssetResponse;
import com.example.hotelservice.Policy.dto.response.PolicyResponse;
import com.example.hotelservice.Question.dto.QuestionResponse;
import com.example.hotelservice.Room.dto.response.RoomTypeResponse;
import com.example.hotelservice.Amenity.dto.response.AmenityResponse;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HotelResponse(
        UUID id,
        UUID ownerId,
        UUID cityId,
        String name,
        String description,
        Short starRating,
        HotelAddressDto address,
        String contactEmail,
        String contactPhone,
        List<PolicyResponse> policies,
        List<QuestionResponse> faqs,
        List<AmenityResponse> amenityCategories,
        Boolean isActive,
        HotelApprovalStatus approvedStatus,
        CityResponse city,
        Instant createdAt,
        Instant updatedAt,
        java.math.BigDecimal lowestPrice,
        List<RoomTypeResponse> roomTypes,
        List<MediaAssetResponse> mediaAssets
) {}

