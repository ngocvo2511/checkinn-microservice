package com.example.userservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.token-revocation.key-prefix:auth:revoked-token:}")
    private String keyPrefix;

    @Value("${app.token-revocation.fail-open:false}")
    private boolean failOpen;

    public boolean isRevoked(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix + sha256(token)));
        } catch (Exception e) {
            log.error("Redis token revocation check failed: {}", e.getMessage(), e);
            if (failOpen) {
                return false;
            }
            throw new IllegalStateException("Token revocation storage is unavailable", e);
        }
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
