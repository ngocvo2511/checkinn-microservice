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

    public JwtService(@Value("${app.jwt.secret:THIS_IS_SUPER_SECRET_KEY_12345678901234567890}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
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
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
