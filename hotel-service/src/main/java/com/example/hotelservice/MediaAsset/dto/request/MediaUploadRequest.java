package com.example.hotelservice.MediaAsset.dto.request;

import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MediaUploadRequest(
        @NotNull UUID targetId,
        @NotNull MediaTargetType targetType,
        @NotBlank String url,
        boolean thumbnail,
        Integer sortOrder
) {}

