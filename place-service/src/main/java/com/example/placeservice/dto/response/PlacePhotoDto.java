package com.example.placeservice.dto.response;

import lombok.Builder;

import java.io.Serializable;
import java.util.UUID;

@Builder
public record PlacePhotoDto(
        UUID id,
        String photoUrl,
        Integer displayOrder
) implements Serializable {}
