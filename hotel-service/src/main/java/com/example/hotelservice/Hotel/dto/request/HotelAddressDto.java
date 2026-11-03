package com.example.hotelservice.Hotel.dto.request;

import jakarta.validation.constraints.NotBlank;

public record HotelAddressDto(@NotBlank String street, @NotBlank String city, @NotBlank String country,
                              Double latitude, Double longitude, String coordinateSource) {
    public HotelAddressDto(String street, String city, String country, Double latitude, Double longitude) {
        this(street, city, country, latitude, longitude, null);
    }
    /** Old JSON has no verified provenance; do not advertise its coordinates as a hotel pin. */
    public HotelAddressDto forRead() {
        if (!"OWNER_PROVIDED".equals(coordinateSource) && !"GEOCODED".equals(coordinateSource))
            return new HotelAddressDto(street, city, country, null, null,
                    coordinateSource == null ? "LEGACY_UNVERIFIED" : coordinateSource);
        return this;
    }
}
