package com.example.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenRevocationService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.token-revocation.key-prefix:auth:revoked-token:}")
    private String keyPrefix="auth:revoked-token:";

    @Value("${app.token-revocation.user-generation-prefix:auth:session-generation:}")
    private String userGenerationPrefix="auth:session-generation:";

    /** Signature, expiry, subject and claim types must be validated by the caller first. */
    public boolean isRevoked(String token, java.util.UUID userId, String generation) {
        try {
            if(Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix+sha256(token))))return true;
            String current=redisTemplate.opsForValue().get(userGenerationPrefix+userId);
            if(current==null)current=""; // Legacy is valid only before this account's first rotation.
            else java.util.UUID.fromString(current);
            return !current.equals(generation==null?"":generation);
        } catch(Exception error) {
            // User-token validation never fails open, including for a legacy token.
            throw new SessionStoreUnavailableException();
        }
    }

    public void revoke(String token, Date expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt.toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            log.debug("[LOGOUT] Token already expired, skipping blacklist write");
            return;
        }

        String key = keyPrefix + sha256(token);
        redisTemplate.opsForValue().set(key, "revoked", ttl);
        log.info("[LOGOUT] Token revoked until {}", expiresAt);
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
