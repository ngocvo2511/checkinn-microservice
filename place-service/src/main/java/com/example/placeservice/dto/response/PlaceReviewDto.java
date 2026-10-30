package com.example.placeservice.dto.response;

import lombok.Builder;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Builder
public record PlaceReviewDto(
        UUID id,
        String authorName,
        String authorAvatarUrl,
        Integer rating,
        String comment,
        String relativeTimeDescription,
        Instant publishTime
) implements Serializable {}
