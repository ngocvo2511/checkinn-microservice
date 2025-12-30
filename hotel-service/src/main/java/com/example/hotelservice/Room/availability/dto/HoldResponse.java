package com.example.hotelservice.Room.availability.dto;

import com.example.hotelservice.Room.availability.enums.HoldStatus;

import java.time.Instant;
import java.util.UUID;

public record HoldResponse(UUID holdId, HoldStatus status, Instant expiresAt) {}
