package com.example.userservice.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
