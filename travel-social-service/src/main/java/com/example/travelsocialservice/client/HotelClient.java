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

/** Reads display data of published hotels. hotel-service omits drafts, unapproved and inactive hotels. */
@Slf4j
@Component
public class HotelClient {

    static final int BATCH_SIZE = 50;
    private static final ParameterizedTypeReference<List<HotelRef>> HOTEL_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public HotelClient(RestClient.Builder builder, SocialProperties properties) {
        this.restClient = builder.baseUrl(properties.hotelServiceUrl()).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HotelRef(UUID id, String name, String cityName, String provinceName, Short starRating, String coverImageUrl) {
    }

    /** Used when publishing: an unreachable catalog fails the request instead of being treated as "not published". */
    public Optional<HotelRef> require(UUID hotelId) {
        try {
            return fetch(List.of(hotelId)).stream().filter(hotel -> hotelId.equals(hotel.id())).findFirst();
        } catch (RestClientException ex) {
            log.warn("[HOTEL_CLIENT] Hotel lookup failed: {}", ex.getMessage());
            throw ApiException.unavailable("HOTEL_SERVICE_UNAVAILABLE", "Hotels cannot be verified right now. Try again later.");
        }
    }

    /** Used when rendering: unpublished or unreachable hotels simply have no entry. */
    public Map<UUID, HotelRef> findAll(Collection<UUID> hotelIds) {
        List<UUID> ids = new ArrayList<>(hotelIds.stream().distinct().toList());
        Map<UUID, HotelRef> found = new HashMap<>();
        try {
            for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
                fetch(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size())))
                        .forEach(hotel -> found.put(hotel.id(), hotel));
            }
        } catch (RestClientException ex) {
            log.warn("[HOTEL_CLIENT] Hotel display lookup failed; rendering without hotel context: {}", ex.getMessage());
        }
        return found;
    }

    private List<HotelRef> fetch(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String joined = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        List<HotelRef> hotels = restClient.get()
                .uri(uri -> uri.path("/hotels/batch").queryParam("ids", joined).build())
                .retrieve().body(HOTEL_LIST);
        return hotels == null ? List.of() : hotels;
    }
}
