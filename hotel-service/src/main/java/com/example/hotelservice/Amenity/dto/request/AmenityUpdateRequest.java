package com.example.hotelservice.Amenity.dto.request;

import java.util.List;

public record AmenityUpdateRequest(
        @jakarta.validation.constraints.NotNull @jakarta.validation.Valid List<@jakarta.validation.constraints.NotNull AmenityRequest> amenityCategories
) {}
