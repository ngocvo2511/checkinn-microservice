package com.example.hotelservice.City.dto.request;

import java.util.UUID;

public record ProvinceCreateRequest(
        String name,
        Double latitude,
        Double longitude
) {}
