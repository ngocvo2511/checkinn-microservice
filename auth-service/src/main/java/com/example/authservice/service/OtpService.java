package com.example.authservice.service;

import com.example.authservice.dto.OtpVerificationResponse;
import com.example.authservice.dto.VerifyOtpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final RestTemplate restTemplate;

    @Value("${user.service.url:http://localhost:8082}")
    private String userServiceUrl;

    public OtpVerificationResponse verifyOtp(String email, String otpCode) {
        try {
            String url = userServiceUrl + "/api/otp/verify";
            
            VerifyOtpRequest request = VerifyOtpRequest.builder()
                    .email(email)
                    .otpCode(otpCode)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<VerifyOtpRequest> entity = new HttpEntity<>(request, headers);

            log.info("Verifying OTP for email: {}", email);
            OtpVerificationResponse response = restTemplate.postForObject(url, entity, OtpVerificationResponse.class);
            
            log.info("OTP verification result for {}: {}", email, response != null ? response.isVerified() : "null");
            return response;
            
        } catch (Exception e) {
            log.error("Error verifying OTP: {}", e.getMessage(), e);
            return OtpVerificationResponse.builder()
                    .verified(false)
                    .message("OTP verification failed: " + e.getMessage())
                    .email(email)
                    .build();
        }
    }

    public Map<String, Object> resendOtp(String email) {
        try {
            String url = userServiceUrl + "/api/otp/resend?email=" + email;
            
            log.info("Resending OTP for email: {}", email);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, null, Map.class);
            
            log.info("OTP resend result for {}: {}", email, response);
            return response != null ? response : Map.of("success", false, "message", "Failed to resend OTP");
            
        } catch (Exception e) {
            log.error("Error resending OTP: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to resend OTP: " + e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> generateAndSendOtp(String email) {
        try {
            String url = userServiceUrl + "/api/otp/generate?email=" + email;
            
            log.info("Generating and sending OTP for email: {}", email);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, null, Map.class);
            
            log.info("OTP generation result for {}: {}", email, response);
            return response != null ? response : Map.of("success", false, "message", "Failed to generate OTP");
            
        } catch (Exception e) {
            log.error("Error generating OTP: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to generate OTP: " + e.getMessage());
            return errorResponse;
        }
    }
}
