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
    private String keyPrefix="auth:revoked-token:";

    @Value("${app.token-revocation.user-generation-prefix:auth:session-generation:}")
    private String userGenerationPrefix="auth:session-generation:";

    @Value("${app.token-revocation.fail-open:false}")
    private boolean failOpen;

    public boolean isRevoked(String token) {
        var metadata=SessionClaims.read(token);
        if(!metadata.valid())return true;
        try {
            if(Boolean.TRUE.equals(redisTemplate.hasKey(keyPrefix+sha256(token))))return true;
            if(metadata.user()==null)return false; // Machine credentials have their own role/subject checks.
            return !currentUserGeneration(metadata.user().id()).equals(metadata.user().generation());
        } catch (Exception ex) {
            // Never fail open for a user session, including after an account-wide reset.
            if(failOpen && metadata.user()==null)return false;
            throw new SessionStoreUnavailableException();
        }
    }

    public String currentUserGeneration(java.util.UUID userId) {
        try {
            String value=redisTemplate.opsForValue().get(userGenerationPrefix+userId);
            if(value==null)return ""; // Legacy sessions remain valid until this account's first rotation.
            java.util.UUID.fromString(value);
            return value;
        }catch(Exception ex){throw new SessionStoreUnavailableException();}
    }

    /** Security revocation is monotonic: do not restore an old generation on DB rollback. */
    public String rotateUserSessions(java.util.UUID userId) {
        java.util.Objects.requireNonNull(userId,"User identity required");
        String generation=java.util.UUID.randomUUID().toString();
        byte[] key=(userGenerationPrefix+userId).getBytes(StandardCharsets.UTF_8);
        byte[] value=generation.getBytes(StandardCharsets.UTF_8);
        try {
            String result=redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                if(!Boolean.TRUE.equals(connection.stringCommands().set(key,value)))throw new SessionStoreUnavailableException();
                // Same connection: acknowledge the security marker only after local AOF fsync.
                org.springframework.data.redis.connection.RedisConnection raw=connection;
                while(raw instanceof org.springframework.data.redis.connection.DecoratedRedisConnection decorated)raw=decorated.getDelegate();
                byte[][] args={"1".getBytes(StandardCharsets.UTF_8),"0".getBytes(StandardCharsets.UTF_8),"1500".getBytes(StandardCharsets.UTF_8)};
                Object ack=raw instanceof org.springframework.data.redis.connection.lettuce.LettuceConnection lettuce
                    ? lettuce.execute("WAITAOF",new io.lettuce.core.output.NestedMultiOutput<>(io.lettuce.core.codec.ByteArrayCodec.INSTANCE),args)
                    : raw.execute("WAITAOF",args);
                if(!(ack instanceof java.util.List<?> values) || values.isEmpty() || !(values.getFirst() instanceof Number n) || n.longValue()<1)
                    throw new SessionStoreUnavailableException();
                return generation;
            },true);
            if(result==null)throw new SessionStoreUnavailableException();
            return result;
        }catch(Exception ex){throw new SessionStoreUnavailableException();}
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
