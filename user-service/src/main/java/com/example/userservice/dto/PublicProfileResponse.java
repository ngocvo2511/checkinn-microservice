package com.example.userservice.dto;

import java.util.UUID;

/** The only user fields that may be shown publicly. Never add email, phone, address or username here. */
public record PublicProfileResponse(UUID id, String displayName, String avatarUrl) {
}
