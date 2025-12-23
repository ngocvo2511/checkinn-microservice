package com.example.hotelservice.Amenity.dto.request;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AmenityRequest {
    String title;
    List<AmenityItemRequest> amenities;
}
