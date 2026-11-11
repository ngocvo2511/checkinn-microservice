package com.example.hotelservice.Amenity.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AmenityItemRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    String title;
}
