package com.example.travelsocialservice.web.dto;

import com.example.travelsocialservice.domain.ContentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PostDtos {

    private PostDtos() {
    }

    /** {@code PUT} replaces every field, including {@code hotelId} and {@code stayBookingId}. */
    public record PostRequest(String body, UUID placeId, Integer rating, List<UUID> mediaIds,
                              UUID hotelId, UUID stayBookingId) {

        public PostRequest(String body, UUID placeId, Integer rating, List<UUID> mediaIds) {
            this(body, placeId, rating, mediaIds, null, null);
        }
    }

    public record HotelView(UUID id, String name, String cityName, String provinceName, Short starRating,
                            String coverImageUrl) {
    }

    public record AuthorView(UUID id, String displayName, String avatarUrl) {
    }

    public record PlaceView(UUID id, String name, String category, String provinceName) {
    }

    public record MediaView(UUID id, String url, int order) {
    }

    /**
     * {@code source} and {@code verified} mark this as community content, never a booking-verified
     * hotel review. {@code verifiedStay} only says the author completed a stay booked through Checkinn.
     * {@code status} is present only for the author and administrators; {@code stayBookingId} only in
     * the author's own views.
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
            UUID hotelId,
            HotelView hotel,
            boolean verifiedStay,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID stayBookingId,
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
