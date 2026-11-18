package com.example.travelsocialservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
public class ServiceTokenProvider {

    public static final String SUBJECT = "travel-social-service";
    public static final String ROLE = "TRAVEL_SOCIAL_SERVICE";

    private final JwtEncoder encoder;

    public ServiceTokenProvider(@Value("${jwt.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing or blank");
        }
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
    }

    public String authorization() {
        Instant now = Instant.now();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(SUBJECT).claim("role", ROLE)
                        .issuedAt(now).expiresAt(now.plusSeconds(300)).build())).getTokenValue();
    }
}
