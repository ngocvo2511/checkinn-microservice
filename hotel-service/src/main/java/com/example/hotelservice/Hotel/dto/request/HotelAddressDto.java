package com.example.hotelservice.Hotel.dto.request;

import jakarta.validation.constraints.NotBlank;

public record HotelAddressDto(
        @NotBlank String street,
        @NotBlank String city,
        @NotBlank String country,
        Double latitude,
        Double longitude
) {}

