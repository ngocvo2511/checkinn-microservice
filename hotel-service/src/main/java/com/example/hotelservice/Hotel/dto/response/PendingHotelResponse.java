package com.example.hotelservice.Hotel.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PendingHotelResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private Instant createdAt;
}
