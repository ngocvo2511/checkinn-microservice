package com.example.travelsocialservice.service;

import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.PageResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class Paging {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    private Paging() {
    }

    public static int limit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        if (requested < 1 || requested > MAX_LIMIT) {
            throw ApiException.invalid("limit", "limit must be between 1 and " + MAX_LIMIT);
        }
        return requested;
    }

    /**
     * Builds a page from rows fetched with {@code limit + 1}: the extra row only signals that
     * another page exists.
     */
    public static <R, V> PageResponse<V> page(List<R> rows, int limit, Function<List<R>, List<V>> toViews,
                                              Function<R, KeysetCursor> cursorOf) {
        boolean hasMore = rows.size() > limit;
        List<R> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore ? cursorOf.apply(page.get(page.size() - 1)).encode() : null;
        return new PageResponse<>(toViews.apply(page), nextCursor);
    }

    /** PostgreSQL keeps microseconds; truncating here makes a stored timestamp equal its cursor value. */
    public static Instant now(Clock clock) {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public static String likePattern(String term) {
        return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
