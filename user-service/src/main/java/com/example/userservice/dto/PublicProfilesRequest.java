package com.example.userservice.dto;

import java.util.List;
import java.util.UUID;

public record PublicProfilesRequest(List<UUID> ids) {
}
