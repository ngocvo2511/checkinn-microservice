package com.example.authservice.service;

import com.example.authservice.dto.OtpVerificationResponse;
import com.example.authservice.dto.VerifyOtpRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
            var request=VerifyOtpRequest.builder().email(email).otpCode(otpCode).build();
            var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
            log.info("[OTP_VERIFY] Requesting verification");
            var response=restTemplate.postForObject(userServiceUrl+"/api/otp/verify",new HttpEntity<>(request,headers),OtpVerificationResponse.class);
            if(response==null)throw unavailable();
            log.info("[OTP_VERIFY] Verification completed - verified: {}",response.isVerified());
            return response;
        } catch(Exception error) {
            log.error("[OTP_VERIFY] Upstream request failed - errorType: {}",error.getClass().getSimpleName());
            throw upstreamFailure(error);
        }
    }

    public Map<String,Object> resendOtp(String email) {
        return command("resend",email);
    }

    public Map<String,Object> generateAndSendOtp(String email) {
        return command("generate",email);
    }

    private Map<String,Object> command(String operation,String email) {
        if(email==null || email.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"OTP email is required");
        try {
            // Strict template-variable encoding preserves '+' aliases and prevents query injection.
            URI uri=UriComponentsBuilder.fromHttpUrl(userServiceUrl+"/api/otp/"+operation)
                    .queryParam("email","{email}").encode().buildAndExpand(Map.of("email",email)).toUri();
            log.info("[OTP_COMMAND] Requesting {}",operation);
            @SuppressWarnings("unchecked")
            Map<String,Object> response=restTemplate.postForObject(uri,null,Map.class);
            if(response==null || !(response.get("success") instanceof Boolean))throw unavailable();
            log.info("[OTP_COMMAND] {} completed - success: {}",operation,response.get("success"));
            return response;
        } catch(Exception error) {
            log.error("[OTP_COMMAND] {} failed - errorType: {}",operation,error.getClass().getSimpleName());
            throw upstreamFailure(error);
        }
    }

    private ResponseStatusException upstreamFailure(Exception error) {
        if(error instanceof ResponseStatusException typed)return typed;
        if(error instanceof HttpStatusCodeException http && http.getStatusCode().value()==400)
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid OTP request");
        return unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OTP service temporarily unavailable");
    }
}
