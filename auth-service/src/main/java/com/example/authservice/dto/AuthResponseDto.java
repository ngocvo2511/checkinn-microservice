package com.example.authservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponseDto {
    private String token;
    private UUID userId;
    private String email;
    private String fullName;
    private String role;
}
