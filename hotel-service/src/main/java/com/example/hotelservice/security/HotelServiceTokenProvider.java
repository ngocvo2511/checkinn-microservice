package com.example.hotelservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class HotelServiceTokenProvider {
    private final JwtEncoder encoder;
    public HotelServiceTokenProvider(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("JWT secret required");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
    }
    public String authorization() {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject("hotel-service").claim("role","HOTEL_SERVICE")
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build())).getTokenValue();
    }
}
