package com.example.hotelservice.City.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CityCreateRequest(
        @NotBlank String name,
        Double latitude,
        Double longitude,
        UUID provinceId,
        Integer hotelCount
) {}
