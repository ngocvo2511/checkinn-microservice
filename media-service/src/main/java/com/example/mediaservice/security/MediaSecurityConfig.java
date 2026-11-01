package com.example.mediaservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class MediaSecurityConfig {
    @Bean JwtDecoder mediaJwtDecoder(@Value("${jwt.secret:${SECRET_KEY}}") String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("JWT secret required");
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).build();
    }
}
