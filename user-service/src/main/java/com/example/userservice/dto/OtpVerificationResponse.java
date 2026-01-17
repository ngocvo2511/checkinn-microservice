package com.example.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OtpVerificationResponse {
    private boolean verified;
    private String message;
    private String email;
}
