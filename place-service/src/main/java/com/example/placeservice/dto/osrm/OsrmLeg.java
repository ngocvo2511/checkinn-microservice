package com.example.placeservice.dto.osrm;

/** Raw OSRM answer for one origin/destination pair, cached before calibration so coefficient changes apply immediately. */
public record OsrmLeg(double meters, double seconds) {
}
