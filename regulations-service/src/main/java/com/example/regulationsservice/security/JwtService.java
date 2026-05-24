package com.example.regulationsservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final Key key;
    private final TokenRevocationService tokenRevocationService;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      TokenRevocationService tokenRevocationService) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret is missing or blank");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
        this.tokenRevocationService = tokenRevocationService;
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public UUID extractUserId(String token) {
        Jws<Claims> claims = parseToken(token);
        return UUID.fromString(claims.getBody().getSubject());
    }

    public String extractRole(String token) {
        Claims claims = parseToken(token).getBody();
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            if (tokenRevocationService.isRevoked(token)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
