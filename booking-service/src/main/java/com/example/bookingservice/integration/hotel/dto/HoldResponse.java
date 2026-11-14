package com.example.bookingservice.integration.hotel.dto;

import java.util.UUID;
public record HoldResponse(UUID holdId, String status, String expiresAt) {}
