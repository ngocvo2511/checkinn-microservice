package com.example.placeservice.service.osrm;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.OsrmTableResponse;
import com.example.placeservice.dto.osrm.RoadMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final double CIRCUITY_FACTOR = 1.35;
    private static final double FALLBACK_URBAN_SPEED_KMH = 22.0;

    /**
     * Calculates pairwise road distance and travel duration for a list of destinations from an origin.
     * Uses Redis pairwise caching and falls back gracefully to empirical circuity factor if OSRM is unavailable.
     */
    public Map<Coordinate, RoadMetrics> getRoadMetrics(Coordinate origin, List<Coordinate> destinations) {
        if (origin == null || destinations == null || destinations.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Coordinate, RoadMetrics> results = new HashMap<>(destinations.size());
        List<Coordinate> uncachedDestinations = new ArrayList<>();

        // 1. Check Redis cache for each destination
        for (Coordinate dest : destinations) {
            String cacheKey = buildCacheKey(origin, dest);
            RoadMetrics cached = getFromCache(cacheKey);
            if (cached != null) {
                results.put(dest, cached);
            } else {
                uncachedDestinations.add(dest);
            }
        }

        if (uncachedDestinations.isEmpty()) {
            return results;
        }

        // 2. Query OSRM for uncached destinations if client is enabled
        Optional<OsrmTableResponse> osrmResponse = Optional.empty();
        if (osrmClient.isEnabled()) {
            osrmResponse = osrmClient.computeTable(origin, uncachedDestinations);
        }

        Map<String, String> cacheEntriesToSave = new HashMap<>();

        if (osrmResponse.isPresent() && isValidResponse(osrmResponse.get(), uncachedDestinations.size())) {
            List<Double> distRow = osrmResponse.get().getDistances().getFirst();
            List<Double> durRow = osrmResponse.get().getDurations().getFirst();

            for (int i = 0; i < uncachedDestinations.size(); i++) {
                Coordinate dest = uncachedDestinations.get(i);
                Double distMeters = distRow.get(i + 1);
                Double durSeconds = durRow.get(i + 1);

                RoadMetrics metrics;
                if (distMeters != null && durSeconds != null && distMeters >= 0 && durSeconds >= 0) {
                    double distKm = Math.round((distMeters / 1000.0) * 10.0) / 10.0;
                    int durMinutes = calculateCalibratedDurationMinutes(durSeconds, distKm);
                    metrics = RoadMetrics.builder()
                            .distanceKm(distKm)
                            .durationMinutes(durMinutes)
                            .fallback(false)
                            .build();
                } else {
                    metrics = calculateCircuityFallback(origin, dest);
                }

                results.put(dest, metrics);
                cacheMetrics(origin, dest, metrics);
            }
        } else {
            // Fallback for all uncached destinations
            log.info("[ROAD_DISTANCE] Using circuity factor fallback ({}x) for {} destinations",
                    CIRCUITY_FACTOR, uncachedDestinations.size());
            for (Coordinate dest : uncachedDestinations) {
                RoadMetrics fallbackMetrics = calculateCircuityFallback(origin, dest);
                results.put(dest, fallbackMetrics);
                cacheMetrics(origin, dest, fallbackMetrics);
            }
        }

        return results;
    }

    public RoadMetrics getRoadMetrics(Coordinate origin, Coordinate destination) {
        Map<Coordinate, RoadMetrics> map = getRoadMetrics(origin, List.of(destination));
        return map.getOrDefault(destination, calculateCircuityFallback(origin, destination));
    }

    public static int calculateCalibratedDurationMinutes(Double durSeconds, Double distKm) {
        if (durSeconds == null || durSeconds <= 0 || distKm == null || distKm <= 0) {
            return 1;
        }
        // OSRM raw duration represents free-flow highway/open-road speed without intersection/traffic delays.
        // Calibrate with an urban delay multiplier (1.8x) for Vietnamese city centers (traffic, signals, turns)
        // plus an initial 1-minute departure buffer.
        double calibratedMinutes = (durSeconds / 60.0) * 1.8 + 1.0;
        return Math.max(1, (int) Math.round(calibratedMinutes));
    }

    public RoadMetrics calculateCircuityFallback(Coordinate origin, Coordinate destination) {
        double haversine = haversineKm(
                origin.latitude(), origin.longitude(),
                destination.latitude(), destination.longitude()
        );
        double roadDistKm = Math.round(haversine * CIRCUITY_FACTOR * 10.0) / 10.0;
        int durationMinutes = Math.max(1, (int) Math.round((roadDistKm / FALLBACK_URBAN_SPEED_KMH) * 60.0 + 1.0));

        return RoadMetrics.builder()
                .distanceKm(roadDistKm)
                .durationMinutes(durationMinutes)
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

    private boolean isValidResponse(OsrmTableResponse response, int expectedCount) {
        return response.getDistances() != null
                && !response.getDistances().isEmpty()
                && response.getDistances().getFirst().size() >= expectedCount + 1
                && response.getDurations() != null
                && !response.getDurations().isEmpty()
                && response.getDurations().getFirst().size() >= expectedCount + 1;
    }

    private String buildCacheKey(Coordinate origin, Coordinate dest) {
        return String.format(Locale.US, "distance:road:%s:%s",
                origin.toCacheKeyPart(), dest.toCacheKeyPart());
    }

    private RoadMetrics getFromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, RoadMetrics.class);
            }
        } catch (Exception ex) {
            log.warn("[ROAD_DISTANCE] Redis cache read failed for key {}: {}", key, ex.getMessage());
        }
        return null;
    }

    private void cacheMetrics(Coordinate origin, Coordinate dest, RoadMetrics metrics) {
        String key = buildCacheKey(origin, dest);
        try {
            String json = objectMapper.writeValueAsString(metrics);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("[ROAD_DISTANCE] Redis cache serialize failed for key {}: {}", key, ex.getMessage());
        } catch (Exception ex) {
            log.warn("[ROAD_DISTANCE] Redis cache write failed for key {}: {}", key, ex.getMessage());
        }
    }
}
