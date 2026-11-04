package com.example.placeservice.dto.osrm;

import java.util.Locale;

public record Coordinate(Double latitude, Double longitude) {

    public String toOsrmString() {
        return String.format(Locale.US, "%.6f,%.6f", longitude, latitude);
    }

    public String toCacheKeyPart() {
        return String.format(Locale.US, "%.5f,%.5f", latitude, longitude);
    }
}
