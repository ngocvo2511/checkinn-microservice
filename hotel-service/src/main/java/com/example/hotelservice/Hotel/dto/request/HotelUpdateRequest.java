package com.example.hotelservice.Hotel.dto.request;

import com.example.hotelservice.Amenity.dto.request.AmenityRequest;
import com.example.hotelservice.Policy.dto.request.PolicyRequest;

import java.util.List;
import java.util.UUID;

public record HotelUpdateRequest(
        String name,
        String description,
        Short starRating,
        UUID cityId,
        HotelAddressDto address,
        Boolean isActive,
        List <PolicyRequest> policies,
        List <AmenityRequest> amenityCategories
) {}

