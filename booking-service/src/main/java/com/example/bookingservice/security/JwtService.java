package com.example.bookingservice.security;

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
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public UUID extractUserId(String token) {
        Jws<Claims> claims = parseToken(token);
        return UUID.fromString(claims.getBody().getSubject());
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
    public String extractRole(String token) {
        Claims claims = parseToken(token).getBody();
        return claims.get("role", String.class);
    }
}
