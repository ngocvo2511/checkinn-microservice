package com.example.placeservice.service.osrm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelEstimateModel {

    private final TravelEstimateProperties props;

    public int routedMinutes(double osrmSeconds, double roadKm) {
        if (osrmSeconds <= 0) {
            return 1;
        }
        double congestion = props.getCongestionBase()
                + props.getCongestionUrbanExtra() * Math.exp(-Math.max(roadKm, 0) / props.getCongestionDecayKm());
        return toMinutes(osrmSeconds / 60.0 * congestion);
    }

    public double circuityFactor(double straightLineKm) {
        return props.getCircuityBase()
                + props.getCircuityShortTripExtra() * Math.exp(-Math.max(straightLineKm, 0) / props.getCircuityDecayKm());
    }

    public int estimatedMinutes(double roadKm) {
        if (roadKm <= 0) {
            return 1;
        }
        double speedKmh = props.getFallbackUrbanSpeedKmh()
                + (props.getFallbackOpenRoadSpeedKmh() - props.getFallbackUrbanSpeedKmh())
                * (1 - Math.exp(-roadKm / props.getFallbackSpeedRampKm()));
        return toMinutes(roadKm / speedKmh * 60.0);
    }

    private int toMinutes(double drivingMinutes) {
        return Math.max(1, (int) Math.round(drivingMinutes + props.getDepartureBufferMinutes()));
    }
}
