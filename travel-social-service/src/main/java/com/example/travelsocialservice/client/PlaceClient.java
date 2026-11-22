package com.example.travelsocialservice.client;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.web.ApiException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reads the public place catalog. place-service stays the source of truth for place data. */
@Slf4j
@Component
public class PlaceClient {

    static final int BATCH_SIZE = 50;
    private static final ParameterizedTypeReference<List<PlaceRef>> PLACE_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public PlaceClient(RestClient.Builder builder, SocialProperties properties) {
        this.restClient = builder.baseUrl(properties.placeServiceUrl()).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaceRef(UUID id, String name, String category, String provinceName) {
    }

    /**
     * Used when publishing: an unknown place must be rejected, so an unreachable catalog
     * fails the request instead of being treated as "not found".
     */
    public Optional<PlaceRef> require(UUID placeId) {
        try {
            return fetch(List.of(placeId)).stream().findFirst();
        } catch (RestClientException ex) {
            log.warn("[PLACE_CLIENT] Place lookup failed: {}", ex.getMessage());
            throw ApiException.unavailable("PLACE_SERVICE_UNAVAILABLE", "Places cannot be verified right now. Try again later.");
        }
    }

    /** Used when rendering: missing or unreachable places simply have no entry. */
    public Map<UUID, PlaceRef> findAll(Collection<UUID> placeIds) {
        List<UUID> ids = new ArrayList<>(placeIds.stream().distinct().toList());
        Map<UUID, PlaceRef> found = new HashMap<>();
        try {
            for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
                fetch(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size())))
                        .forEach(place -> found.put(place.id(), place));
            }
        } catch (RestClientException ex) {
            log.warn("[PLACE_CLIENT] Place display lookup failed; rendering without place context: {}", ex.getMessage());
        }
        return found;
    }

    private List<PlaceRef> fetch(List<UUID> ids) {
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<PlaceRef> places = restClient.get()
                .uri(uri -> uri.path("/places/batch").queryParam("ids", joined).build())
                .retrieve().body(PLACE_LIST);
        return places == null ? List.of() : places;
    }
}
