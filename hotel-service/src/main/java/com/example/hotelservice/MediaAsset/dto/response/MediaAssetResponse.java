package com.example.hotelservice.MediaAsset.dto.response;

import com.example.hotelservice.MediaAsset.enums.MediaTargetType;

import java.time.Instant;
import java.util.UUID;

public record MediaAssetResponse(
        UUID id,
        UUID targetId,
        MediaTargetType targetType,
        String url,
        boolean isThumbnail,
        Integer sortOrder,
        Instant createdAt
) {}
