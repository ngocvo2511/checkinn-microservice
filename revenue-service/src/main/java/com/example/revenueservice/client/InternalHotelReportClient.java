package com.example.revenueservice.client;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.time.Instant;
import java.util.List;

/** Credentials are scoped to these two requests; never a global HTTP interceptor. */
@Component
public class InternalHotelReportClient {
    private final RestTemplate rest;
    private final String base;
    private final JwtEncoder encoder;
    public record Room(String id, Integer totalRooms) {}
    public record Hotel(String id, String ownerId, String name, String city, List<Room> roomTypes) {}
    public InternalHotelReportClient(RestTemplate rest, @Value("${hotel.service.url:http://hotel-service}") String base,
                                     @Value("${jwt.secret:${SECRET_KEY}}") String secret) {
        this.rest = rest; this.base = base;
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    public Hotel hotel(String id) { return get("/internal/reports/hotels/{id}", id, Hotel.class); }
    public Room room(String id) { return get("/internal/reports/rooms/{id}", id, Room.class); }
    private <T> T get(String path, String id, Class<T> type) {
        Instant now = Instant.now();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("revenue-service").claim("role", "REVENUE_SERVICE")
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build())).getTokenValue();
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token);
        try {
            T result = rest.exchange(base + path, HttpMethod.GET, new HttpEntity<>(headers), type, id).getBody();
            if (result != null) return result;
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel report context unavailable");
        }
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel report context unavailable");
    }
}
