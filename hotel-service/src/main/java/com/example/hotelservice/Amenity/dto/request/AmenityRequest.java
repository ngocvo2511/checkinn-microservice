package com.example.hotelservice.Amenity.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AmenityRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    String title;
    @com.fasterxml.jackson.annotation.JsonSetter("items")
    public void rejectResponseItems(com.fasterxml.jackson.databind.JsonNode ignored) {
        throw new IllegalArgumentException("Use amenities for amenity writes");
    }

    @jakarta.validation.Valid
    List<@jakarta.validation.constraints.NotNull AmenityItemRequest> amenities;
}
