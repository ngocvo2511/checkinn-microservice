package com.example.placeservice.service.osrm;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.OsrmTableResponse;
import com.example.placeservice.dto.osrm.RoadMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoadDistanceServiceTest {

    private OsrmRoutingClient osrmClient;
    private ValueOperations<String, String> valueOperations;
    private TravelEstimateModel estimateModel;
    private RoadDistanceService roadDistanceService;

    private final Coordinate origin = new Coordinate(21.0285, 105.8542);
    private final Coordinate dest = new Coordinate(21.0350, 105.8600);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        osrmClient = mock(OsrmRoutingClient.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        estimateModel = new TravelEstimateModel(new TravelEstimateProperties());

        roadDistanceService = new RoadDistanceService(osrmClient, redisTemplate, new ObjectMapper(), estimateModel);
    }

    @Test
    void getRoadMetrics_WhenRawLegCached_CalibratesWithoutCallingOsrm() {
        when(valueOperations.get(anyString())).thenReturn("{\"meters\":2300.0,\"seconds\":300.0}");

        RoadMetrics metrics = roadDistanceService.getRoadMetrics(origin, List.of(dest)).get(dest);

        assertThat(metrics.getDistanceKm()).isEqualTo(2.3);
        assertThat(metrics.getDurationMinutes()).isEqualTo(estimateModel.routedMinutes(300.0, 2.3));
        assertThat(metrics.isFallback()).isFalse();
        verifyNoInteractions(osrmClient);
    }

    @Test
    void getRoadMetrics_WhenOsrmSucceeds_CachesRawLegAndCalibrates() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(osrmClient.isEnabled()).thenReturn(true);
        OsrmTableResponse response = OsrmTableResponse.builder()
                .code("Ok")
                .distances(List.of(List.of(0.0, 3200.0)))
                .durations(List.of(List.of(0.0, 480.0)))
                .build();
        when(osrmClient.computeTable(eq(origin), eq(List.of(dest)))).thenReturn(Optional.of(response));

        RoadMetrics metrics = roadDistanceService.getRoadMetrics(origin, List.of(dest)).get(dest);

        assertThat(metrics.getDistanceKm()).isEqualTo(3.2);
        assertThat(metrics.getDurationMinutes()).isEqualTo(estimateModel.routedMinutes(480.0, 3.2));
        assertThat(metrics.isFallback()).isFalse();
        verify(valueOperations).set(startsWith("distance:osrm:v1:"),
                eq("{\"meters\":3200.0,\"seconds\":480.0}"), eq(Duration.ofDays(7)));
    }

    @Test
    void getRoadMetrics_WhenOsrmFails_FallsBackWithoutCaching() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(osrmClient.isEnabled()).thenReturn(true);
        when(osrmClient.computeTable(any(), any())).thenReturn(Optional.empty());

        RoadMetrics metrics = roadDistanceService.getRoadMetrics(origin, List.of(dest)).get(dest);

        assertThat(metrics.isFallback()).isTrue();
        assertThat(metrics.getDistanceKm()).isGreaterThan(0.0);
        assertThat(metrics.getDurationMinutes()).isGreaterThanOrEqualTo(1);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getRoadMetrics_WhenDestinationRepeats_QueriesOsrmOncePerCoordinate() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(osrmClient.isEnabled()).thenReturn(true);
        when(osrmClient.computeTable(any(), any())).thenReturn(Optional.empty());

        roadDistanceService.getRoadMetrics(origin, List.of(dest, dest));

        verify(osrmClient).computeTable(eq(origin), eq(List.of(dest)));
    }

    @Test
    void calculateCircuityFallback_IsNeverShorterThanStraightLine() {
        RoadMetrics metrics = roadDistanceService.calculateCircuityFallback(origin, dest);
        double straightKm = RoadDistanceService.haversineKm(
                origin.latitude(), origin.longitude(), dest.latitude(), dest.longitude());

        assertThat(metrics.getDistanceKm()).isGreaterThanOrEqualTo(Math.floor(straightKm * 10) / 10);
        assertThat(metrics.isFallback()).isTrue();
    }

    @Test
    void haversineKm_CalculatesAccurateDistance() {
        // Hanoi Ho Guom (21.0285, 105.8542) to Hanoi Opera House (21.0244, 105.8576) ~ 0.6 km
        double distance = RoadDistanceService.haversineKm(21.0285, 105.8542, 21.0244, 105.8576);
        assertThat(distance).isBetween(0.4, 0.8);
    }
}
