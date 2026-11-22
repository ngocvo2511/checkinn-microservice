package com.example.placeservice.service.osrm;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.OsrmLeg;
import com.example.placeservice.dto.osrm.OsrmTableResponse;
import com.example.placeservice.dto.osrm.RoadMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoadDistanceService {

    private final OsrmRoutingClient osrmClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TravelEstimateModel estimateModel;

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * Road distance and travel time from an origin to each destination. Every destination gets an entry:
     * routed via OSRM when possible, otherwise a straight-line estimate flagged as fallback.
     */
    public Map<Coordinate, RoadMetrics> getRoadMetrics(Coordinate origin, List<Coordinate> destinations) {
        if (origin == null || destinations == null || destinations.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Coordinate, RoadMetrics> results = new HashMap<>(destinations.size());
        Set<Coordinate> pending = new LinkedHashSet<>();

        for (Coordinate dest : new LinkedHashSet<>(destinations)) {
            OsrmLeg cached = getFromCache(buildCacheKey(origin, dest));
            if (cached != null) {
                results.put(dest, toRoutedMetrics(cached));
            } else {
                pending.add(dest);
            }
        }

        if (pending.isEmpty()) {
            return results;
        }
        List<Coordinate> uncached = List.copyOf(pending);

        Optional<OsrmTableResponse> osrmResponse = osrmClient.isEnabled()
                ? osrmClient.computeTable(origin, uncached)
                : Optional.empty();

        if (osrmResponse.isPresent() && isValidResponse(osrmResponse.get(), uncached.size())) {
            List<Double> distRow = osrmResponse.get().getDistances().getFirst();
            List<Double> durRow = osrmResponse.get().getDurations().getFirst();

            for (int i = 0; i < uncached.size(); i++) {
                Coordinate dest = uncached.get(i);
                Double meters = distRow.get(i + 1);
                Double seconds = durRow.get(i + 1);
                if (meters != null && seconds != null && meters >= 0 && seconds >= 0) {
                    OsrmLeg leg = new OsrmLeg(meters, seconds);
                    results.put(dest, toRoutedMetrics(leg));
                    cacheLeg(origin, dest, leg);
                } else {
                    results.put(dest, calculateCircuityFallback(origin, dest));
                }
            }
        } else {
            log.info("[ROAD_DISTANCE] OSRM unavailable, estimating {} destinations from straight-line distance",
                    uncached.size());
            // Fallbacks are cheap to recompute and are not cached, so a brief OSRM outage does not pin estimates for days.
            for (Coordinate dest : uncached) {
                results.put(dest, calculateCircuityFallback(origin, dest));
            }
        }

        return results;
    }

    public RoadMetrics calculateCircuityFallback(Coordinate origin, Coordinate destination) {
        double straightKm = haversineKm(
                origin.latitude(), origin.longitude(),
                destination.latitude(), destination.longitude()
        );
        double roadKm = roundToTenth(straightKm * estimateModel.circuityFactor(straightKm));
        return RoadMetrics.builder()
                .distanceKm(roadKm)
                .durationMinutes(estimateModel.estimatedMinutes(roadKm))
                .fallback(true)
                .build();
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    private RoadMetrics toRoutedMetrics(OsrmLeg leg) {
        double roadKm = roundToTenth(leg.meters() / 1000.0);
        return RoadMetrics.builder()
                .distanceKm(roadKm)
                .durationMinutes(estimateModel.routedMinutes(leg.seconds(), leg.meters() / 1000.0))
                .fallback(false)
                .build();
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean isValidResponse(OsrmTableResponse response, int expectedCount) {
        return response.getDistances() != null
                && !response.getDistances().isEmpty()
                && response.getDistances().getFirst().size() >= expectedCount + 1
                && response.getDurations() != null
                && !response.getDurations().isEmpty()
                && response.getDurations().getFirst().size() >= expectedCount + 1;
    }

    private String buildCacheKey(Coordinate origin, Coordinate dest) {
        return String.format(Locale.US, "distance:osrm:v1:%s:%s",
                origin.toCacheKeyPart(), dest.toCacheKeyPart());
    }

    private OsrmLeg getFromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, OsrmLeg.class);
            }
        } catch (Exception ex) {
            log.warn("[ROAD_DISTANCE] Redis cache read failed for key {}: {}", key, ex.getMessage());
        }
        return null;
    }

    private void cacheLeg(Coordinate origin, Coordinate dest, OsrmLeg leg) {
        String key = buildCacheKey(origin, dest);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(leg), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("[ROAD_DISTANCE] Redis cache write failed for key {}: {}", key, ex.getMessage());
        }
    }
}
