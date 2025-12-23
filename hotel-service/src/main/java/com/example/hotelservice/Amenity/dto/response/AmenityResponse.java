package com.example.hotelservice.Amenity.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AmenityResponse {
    String id;
    String title;
    List<AmenityItemResponse> items;
}
