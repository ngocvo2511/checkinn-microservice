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
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private RoadDistanceService roadDistanceService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        osrmClient = mock(OsrmRoutingClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper();

        roadDistanceService = new RoadDistanceService(osrmClient, redisTemplate, objectMapper);
    }

    @Test
    void getRoadMetrics_WhenCacheHit_ReturnsCachedMetricsWithoutCallingOsrm() {
        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest = new Coordinate(21.0350, 105.8600);

        String cachedJson = "{\"distanceKm\":2.3,\"durationMinutes\":7,\"fallback\":false}";
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        Map<Coordinate, RoadMetrics> result = roadDistanceService.getRoadMetrics(origin, List.of(dest));

        assertThat(result).containsKey(dest);
        RoadMetrics metrics = result.get(dest);
        assertThat(metrics.getDistanceKm()).isEqualTo(2.3);
        assertThat(metrics.getDurationMinutes()).isEqualTo(7);
        assertThat(metrics.isFallback()).isFalse();

        verifyNoInteractions(osrmClient);
    }

    @Test
    void getRoadMetrics_WhenUncachedAndOsrmSuccess_ComputesMetricsAndCaches() {
        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest = new Coordinate(21.0350, 105.8600);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(osrmClient.isEnabled()).thenReturn(true);

        OsrmTableResponse response = OsrmTableResponse.builder()
                .code("Ok")
                .distances(List.of(List.of(0.0, 3200.0))) // 3.2 km
                .durations(List.of(List.of(0.0, 480.0)))  // 8 minutes
                .build();
        when(osrmClient.computeTable(eq(origin), eq(List.of(dest)))).thenReturn(Optional.of(response));

        Map<Coordinate, RoadMetrics> result = roadDistanceService.getRoadMetrics(origin, List.of(dest));

        assertThat(result).containsKey(dest);
        RoadMetrics metrics = result.get(dest);
        assertThat(metrics.getDistanceKm()).isEqualTo(3.2);
        assertThat(metrics.getDurationMinutes()).isEqualTo(15);
        assertThat(metrics.isFallback()).isFalse();

        verify(valueOperations).set(contains("distance:road:"), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void getRoadMetrics_WhenOsrmFailsOrEmpty_FallsBackToCircuityFactor() {
        Coordinate origin = new Coordinate(21.0285, 105.8542);
        Coordinate dest = new Coordinate(21.0350, 105.8600);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(osrmClient.isEnabled()).thenReturn(true);
        when(osrmClient.computeTable(any(), any())).thenReturn(Optional.empty());

        Map<Coordinate, RoadMetrics> result = roadDistanceService.getRoadMetrics(origin, List.of(dest));

        assertThat(result).containsKey(dest);
        RoadMetrics metrics = result.get(dest);
        assertThat(metrics.isFallback()).isTrue();
        assertThat(metrics.getDistanceKm()).isGreaterThan(0.0);
        assertThat(metrics.getDurationMinutes()).isGreaterThanOrEqualTo(1);

        verify(valueOperations).set(contains("distance:road:"), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    void haversineKm_CalculatesAccurateDistance() {
        // Hanoi Ho Guom (21.0285, 105.8542) to Hanoi Opera House (21.0244, 105.8576) ~ 0.6 km
        double distance = RoadDistanceService.haversineKm(21.0285, 105.8542, 21.0244, 105.8576);
        assertThat(distance).isBetween(0.4, 0.8);
    }
}
