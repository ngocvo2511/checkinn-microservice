package com.example.placeservice.service.osrm;

import com.example.placeservice.dto.osrm.Coordinate;
import com.example.placeservice.dto.osrm.OsrmTableResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OsrmRoutingClient {

    private final String baseUrl;
    private final int timeoutMs;
    private final boolean enabled;
    private final RestTemplate restTemplate;

    private static final int DEFAULT_BATCH_SIZE = 50;

    public OsrmRoutingClient(
            @Value("${osrm.base-url:http://localhost:5000}") String baseUrl,
            @Value("${osrm.timeout-ms:1500}") int timeoutMs,
            @Value("${osrm.enabled:true}") boolean enabled,
            RestTemplateBuilder restTemplateBuilder
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Computes distance and duration matrix from one origin to multiple destinations.
     * Coordinate order sent to OSRM: origin, dest_0, dest_1, ...
     * sources=0 maps row 0 to distances/durations from origin to all points.
     */
    public Optional<OsrmTableResponse> computeTable(Coordinate origin, List<Coordinate> destinations) {
        if (!enabled) {
            log.debug("[OSRM] Service is disabled via configuration (osrm.enabled=false)");
            return Optional.empty();
        }

        if (origin == null || destinations == null || destinations.isEmpty()) {
            return Optional.empty();
        }

        if (destinations.size() <= DEFAULT_BATCH_SIZE) {
            return executeBatch(origin, destinations);
        }

        // Chunk large batches to respect URL length boundaries
        List<Double> mergedDistances = new ArrayList<>();
        List<Double> mergedDurations = new ArrayList<>();

        for (int i = 0; i < destinations.size(); i += DEFAULT_BATCH_SIZE) {
            int end = Math.min(i + DEFAULT_BATCH_SIZE, destinations.size());
            List<Coordinate> subList = destinations.subList(i, end);

            Optional<OsrmTableResponse> batchResponse = executeBatch(origin, subList);
            if (batchResponse.isEmpty() || batchResponse.get().getDistances() == null || batchResponse.get().getDistances().isEmpty()) {
                log.warn("[OSRM] Batch failed at index [{}, {}). Aborting batch table compute.", i, end);
                return Optional.empty();
            }

            List<Double> batchRowDist = batchResponse.get().getDistances().getFirst();
            List<Double> batchRowDur = batchResponse.get().getDurations().getFirst();

            // Index 0 in batchRow is distance to origin itself. Skip index 0 and append destinations.
            for (int k = 1; k < batchRowDist.size(); k++) {
                mergedDistances.add(batchRowDist.get(k));
                mergedDurations.add(batchRowDur.get(k));
            }
        }

        // Reconstruct full OsrmTableResponse: index 0 is origin
        List<Double> fullDistancesRow = new ArrayList<>();
        fullDistancesRow.add(0.0);
        fullDistancesRow.addAll(mergedDistances);

        List<Double> fullDurationsRow = new ArrayList<>();
        fullDurationsRow.add(0.0);
        fullDurationsRow.addAll(mergedDurations);

        OsrmTableResponse combined = OsrmTableResponse.builder()
                .code("Ok")
                .distances(List.of(fullDistancesRow))
                .durations(List.of(fullDurationsRow))
                .build();

        return Optional.of(combined);
    }

    private Optional<OsrmTableResponse> executeBatch(Coordinate origin, List<Coordinate> destinations) {
        List<String> coordStrings = new ArrayList<>(destinations.size() + 1);
        coordStrings.add(origin.toOsrmString());
        for (Coordinate dest : destinations) {
            coordStrings.add(dest.toOsrmString());
        }

        String coordsJoined = String.join(";", coordStrings);
        String url = String.format("%s/table/v1/driving/%s?sources=0&annotations=distance,duration", baseUrl, coordsJoined);

        try {
            long startMs = System.currentTimeMillis();
            ResponseEntity<OsrmTableResponse> response = restTemplate.getForEntity(url, OsrmTableResponse.class);
            long elapsedMs = System.currentTimeMillis() - startMs;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                OsrmTableResponse body = response.getBody();
                if ("Ok".equalsIgnoreCase(body.getCode())) {
                    log.debug("[OSRM] Matrix calculated for {} destinations in {} ms", destinations.size(), elapsedMs);
                    return Optional.of(body);
                } else {
                    log.warn("[OSRM] Non-OK code returned from OSRM: {}", body.getCode());
                }
            } else {
                log.warn("[OSRM] Unexpected HTTP status from OSRM: {}", response.getStatusCode());
            }
        } catch (RestClientException ex) {
            log.warn("[OSRM] Request failed to {}: {}", baseUrl, ex.getMessage());
        } catch (Exception ex) {
            log.warn("[OSRM] Unexpected error contacting OSRM: {}", ex.getMessage(), ex);
        }

        return Optional.empty();
    }
}
