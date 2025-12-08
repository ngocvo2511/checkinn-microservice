package com.example.authservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponseDto {
    private String token;
    private Long userId;
    private String email;
    private String fullName;
    private String role;
}
