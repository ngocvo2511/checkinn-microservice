package com.example.travelsocialservice.web.dto;

import com.example.travelsocialservice.domain.ContentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PostDtos {

    private PostDtos() {
    }

    public record PostRequest(String body, UUID placeId, Integer rating, List<UUID> mediaIds) {
    }

    public record AuthorView(UUID id, String displayName, String avatarUrl) {
    }

    public record PlaceView(UUID id, String name, String category, String provinceName) {
    }

    public record MediaView(UUID id, String url, int order) {
    }

    /**
     * {@code source} and {@code verified} mark this as community content, never a booking-verified
     * hotel review. {@code status} is present only for the author and administrators.
     */
    public record PostView(
            UUID id,
            AuthorView author,
            String body,
            Integer rating,
            String source,
            boolean verified,
            UUID placeId,
            PlaceView place,
            List<MediaView> media,
            long likeCount,
            Boolean likedByMe,
            long commentCount,
            Instant createdAt,
            Instant updatedAt,
            @JsonInclude(JsonInclude.Include.NON_NULL) ContentStatus status
    ) {
    }

    public record RatingView(UUID placeId, long count, Double average) {
    }

    public record LikeView(long likeCount, boolean likedByMe) {
    }
}
