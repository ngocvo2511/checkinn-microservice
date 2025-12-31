package com.example.hotelservice.Amenity.dto.request;

import java.util.List;

public record AmenityUpdateRequest(
        List<AmenityRequest> amenityCategories
) {}
