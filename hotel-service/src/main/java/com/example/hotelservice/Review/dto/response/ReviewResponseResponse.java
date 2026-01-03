package com.example.hotelservice.Review.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponseResponse {

    private UUID id;
    private UUID reviewId;
    private UUID ownerId;
    private String content;
    private String ownerName;
    private String ownerAvatar;
    private Instant createdAt;
    private Instant updatedAt;
}
