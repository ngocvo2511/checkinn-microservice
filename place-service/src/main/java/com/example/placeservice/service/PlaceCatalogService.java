package com.example.placeservice.service;

import com.example.placeservice.dto.response.PlaceSearchPage;
import com.example.placeservice.dto.response.PlaceSummary;
import com.example.placeservice.entity.Place;
import com.example.placeservice.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Keyword search and batch display lookups over the public place catalog. */
@Service
@RequiredArgsConstructor
public class PlaceCatalogService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 50;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_BATCH_IDS = 50;

    private final PlaceRepository placeRepository;

    @Transactional(readOnly = true)
    public PlaceSearchPage search(String query, String cursor, Integer limit) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty() || q.length() > MAX_QUERY_LENGTH) {
            throw badRequest("q must be 1-" + MAX_QUERY_LENGTH + " characters");
        }
        int pageSize = limit == null ? DEFAULT_LIMIT : limit;
        if (pageSize < 1 || pageSize > MAX_LIMIT) {
            throw badRequest("limit must be 1-" + MAX_LIMIT);
        }
        String pattern = "%" + escapeLike(q) + "%";

        // Fetch one extra row to know whether another page exists.
        List<Place> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = placeRepository.searchByName(pattern, pageSize + 1);
        } else {
            Cursor after = Cursor.decode(cursor);
            rows = placeRepository.searchByNameAfter(pattern, after.name(), after.id(), pageSize + 1);
        }

        boolean hasMore = rows.size() > pageSize;
        List<Place> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore ? Cursor.of(page.get(page.size() - 1)).encode() : null;
        return new PlaceSearchPage(page.stream().map(PlaceSummary::from).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public List<PlaceSummary> batch(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw badRequest("ids must contain at least one id");
        }
        Set<UUID> distinct = new LinkedHashSet<>(ids);
        if (distinct.size() > MAX_BATCH_IDS) {
            throw badRequest("ids must contain at most " + MAX_BATCH_IDS + " distinct ids");
        }
        return placeRepository.findAllById(distinct).stream().map(PlaceSummary::from).toList();
    }

    static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Opaque keyset cursor: base64url of "{name}|{id}". Names may contain '|', so split on the last one. */
    record Cursor(String name, UUID id) {
        static Cursor of(Place place) {
            return new Cursor(place.getName(), place.getId());
        }

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((name + "|" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String raw) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                int separator = decoded.lastIndexOf('|');
                if (separator < 0) {
                    throw new IllegalArgumentException("missing separator");
                }
                return new Cursor(decoded.substring(0, separator), UUID.fromString(decoded.substring(separator + 1)));
            } catch (IllegalArgumentException ex) {
                throw badRequest("cursor is malformed");
            }
        }
    }
}
