package com.example.hotelservice.MediaAsset.dto.request;

import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MediaUploadRequest(
        UUID targetId,
        MediaTargetType targetType,
        String fileName,
        String mimeType,
        byte[] fileData,
        Boolean isThumbnail,
        Integer sortOrder
) {}

