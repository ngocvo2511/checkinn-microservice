package com.example.placeservice.service.osrm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelEstimateModelTest {

    private final TravelEstimateModel model = new TravelEstimateModel(new TravelEstimateProperties());

    @Test
    void routedMinutes_AppliesHeavierCongestionToShortUrbanTrips() {
        // 4 km in 7 free-flow minutes: ~1.53x congestion + 2 min buffer
        assertThat(model.routedMinutes(420, 4.0)).isEqualTo(13);
    }

    @Test
    void routedMinutes_DoesNotInflateLongOpenRoadTripsLikeAFlatMultiplier() {
        // 25 km in 20 free-flow minutes: ~1.26x, where the old flat 1.8x + 1 gave 37 min
        assertThat(model.routedMinutes(1200, 25.0)).isEqualTo(27);
    }

    @Test
    void routedMinutes_WhenNoDuration_ReturnsOneMinute() {
        assertThat(model.routedMinutes(0, 0)).isEqualTo(1);
    }

    @Test
    void circuityFactor_DecreasesWithDistanceButStaysAboveBase() {
        assertThat(model.circuityFactor(0.5)).isGreaterThan(model.circuityFactor(10));
        assertThat(model.circuityFactor(30)).isGreaterThanOrEqualTo(1.25);
    }

    @Test
    void estimatedRoadKm_IsMonotonicInStraightLineDistance() {
        double previous = 0;
        for (double km = 0.1; km <= 30; km += 0.1) {
            double road = km * model.circuityFactor(km);
            assertThat(road).isGreaterThan(previous);
            previous = road;
        }
    }

    @Test
    void estimatedMinutes_UsesFasterAverageSpeedForLongerTrips() {
        double shortTripKmh = 2.0 / ((model.estimatedMinutes(2.0) - 2.0) / 60.0);
        double longTripKmh = 25.0 / ((model.estimatedMinutes(25.0) - 2.0) / 60.0);
        assertThat(longTripKmh).isGreaterThan(shortTripKmh);
    }
}
