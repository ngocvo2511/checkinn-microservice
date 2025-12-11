package com.example.hotelservice.City.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CityCreateRequest(
        @NotBlank String name,
        String code,
        Double latitude,
        Double longitude
) {}
