package com.example.userservice.security;

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
    @Value("${jwt.secret}")
    private String secret; // 48+ chars

    private final Key key;
    private final TokenRevocationService tokenRevocationService;

    public JwtService(TokenRevocationService tokenRevocationService,
                      @Value("${jwt.secret}") String secret) {
        this.tokenRevocationService = tokenRevocationService;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing or blank");
        }
        this.secret = secret;
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UUID userId, String role) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 24h
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(UUID userId) {
        return generateToken(userId, "CUSTOMER");
    }

    public Jws<Claims> parseToken(String token) {
        if (tokenRevocationService.isRevoked(token)) {
            throw new IllegalStateException("Token has been revoked");
        }
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
        Jws<Claims> claims = parseToken(token);
        String role = claims.getBody().get("role", String.class);
        return role != null ? role : "CUSTOMER";
    }

    public boolean isAdmin(String token) {
        try {
            String role = extractRole(token);
            return "ADMIN".equalsIgnoreCase(role);
        } catch (Exception e) {
            return false;
        }
    }
}
