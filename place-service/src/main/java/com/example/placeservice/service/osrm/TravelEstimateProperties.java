package com.example.placeservice.service.osrm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Coefficients for turning routed or straight-line distances into displayed travel estimates.
 * Defaults are starting values; recalibrate them against observed trip times rather than editing code.
 */
@Data
@Component
@ConfigurationProperties(prefix = "routing.estimate")
public class TravelEstimateProperties {

    /** OSRM durations are free-flow; the delay multiplier is congestionBase + congestionUrbanExtra * e^(-km / congestionDecayKm). */
    private double congestionBase = 1.25;
    private double congestionUrbanExtra = 0.55;
    private double congestionDecayKm = 6.0;

    /** Fixed time for getting going (parking, pickup) added to every estimate. */
    private double departureBufferMinutes = 2.0;

    /** Road/straight-line ratio when OSRM is unavailable: circuityBase + circuityShortTripExtra * e^(-km / circuityDecayKm). */
    private double circuityBase = 1.25;
    private double circuityShortTripExtra = 0.25;
    private double circuityDecayKm = 3.0;

    /** Fallback average speed ramps from urban to open-road as the trip gets longer. */
    private double fallbackUrbanSpeedKmh = 20.0;
    private double fallbackOpenRoadSpeedKmh = 35.0;
    private double fallbackSpeedRampKm = 10.0;
}
