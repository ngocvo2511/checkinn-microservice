package com.example.travelsocialservice.client;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.security.ServiceTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public author display data from user-service. Lookups never fail a request:
 * an author that cannot be resolved is rendered with a null display name.
 */
@Slf4j
@Component
public class AuthorClient {

    static final int BATCH_SIZE = 100;
    static final String CACHE_PREFIX = "social:author:";
    private static final ParameterizedTypeReference<List<Author>> AUTHOR_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;
    private final StringRedisTemplate redis;
    private final Duration cacheTtl;

    public AuthorClient(RestClient.Builder builder, SocialProperties properties, ServiceTokenProvider tokens,
                        StringRedisTemplate redis) {
        this.restClient = builder.baseUrl(properties.userServiceUrl()).build();
        this.tokens = tokens;
        this.redis = redis;
        this.cacheTtl = properties.client().authorCacheTtl();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(UUID id, String displayName, String avatarUrl) {
        public static Author placeholder(UUID id) {
            return new Author(id, null, null);
        }
    }

    public Map<UUID, Author> findAll(Collection<UUID> authorIds) {
        List<UUID> ids = authorIds.stream().distinct().toList();
        Map<UUID, Author> authors = new HashMap<>();
        List<UUID> misses = readCache(ids, authors);
        for (int from = 0; from < misses.size(); from += BATCH_SIZE) {
            List<Author> fetched = fetch(misses.subList(from, Math.min(from + BATCH_SIZE, misses.size())));
            fetched.forEach(author -> authors.put(author.id(), author));
            writeCache(fetched);
        }
        ids.forEach(id -> authors.putIfAbsent(id, Author.placeholder(id)));
        return authors;
    }

    private List<Author> fetch(List<UUID> ids) {
        try {
            List<Author> authors = restClient.post().uri("/internal/users/public-profiles")
                    .header(HttpHeaders.AUTHORIZATION, tokens.authorization())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", ids))
                    .retrieve().body(AUTHOR_LIST);
            return authors == null ? List.of() : authors;
        } catch (RuntimeException ex) {
            log.warn("[AUTHOR_CLIENT] Public profile lookup failed; rendering placeholders: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<UUID> readCache(List<UUID> ids, Map<UUID, Author> authors) {
        if (ids.isEmpty()) {
            return List.of();
        }
        try {
            List<String> cached = redis.opsForValue().multiGet(ids.stream().map(id -> CACHE_PREFIX + id).toList());
            List<UUID> misses = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                String displayName = cached == null ? null : cached.get(i);
                if (displayName == null) {
                    misses.add(ids.get(i));
                } else {
                    authors.put(ids.get(i), new Author(ids.get(i), displayName.isEmpty() ? null : displayName, null));
                }
            }
            return misses;
        } catch (RuntimeException ex) {
            log.warn("[AUTHOR_CLIENT] Author cache read failed: {}", ex.getMessage());
            return ids;
        }
    }

    private void writeCache(List<Author> authors) {
        try {
            for (Author author : authors) {
                String displayName = author.displayName() == null ? "" : author.displayName();
                redis.opsForValue().set(CACHE_PREFIX + author.id(), displayName, cacheTtl);
            }
        } catch (RuntimeException ex) {
            log.warn("[AUTHOR_CLIENT] Author cache write failed: {}", ex.getMessage());
        }
    }
}
