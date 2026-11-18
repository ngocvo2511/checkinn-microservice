package com.example.hotelservice.Room.availability.dto;

/** Point-in-time answer; {@code checkedAt} (UTC) tells callers how old it is. Not a hold. */
public record AvailabilityCheckResponse(boolean available, Integer availableRooms, java.time.Instant checkedAt) {}
