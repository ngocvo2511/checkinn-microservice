package com.example.userservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpEmailEvent {
    @JsonProperty("email")
    private String email;

    @JsonProperty("otp_code")
    private String otpCode;

    @JsonProperty("event_type")
    private String eventType; // "otp_verification"

    @JsonProperty("timestamp")
    private long timestamp;
}
