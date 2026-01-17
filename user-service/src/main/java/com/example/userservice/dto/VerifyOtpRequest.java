package com.example.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VerifyOtpRequest {
    private String email;
    private String otpCode;
}
