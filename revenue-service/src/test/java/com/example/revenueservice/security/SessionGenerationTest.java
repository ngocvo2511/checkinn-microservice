package com.example.revenueservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionGenerationTest {
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final TokenRevocationService service=new TokenRevocationService(redis);
    private final UUID user=UUID.randomUUID();
    private String signed(String subject,Object generation) throws Exception {
        var claims=new LinkedHashMap<String,Object>();claims.put("sub",subject);claims.put("role","CUSTOMER");
        if(generation!=null)claims.put("sessionGeneration",generation);
        var encoder=Base64.getUrlEncoder().withoutPadding();
        String data=encoder.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))+"."+encoder.encodeToString(new ObjectMapper().writeValueAsBytes(claims));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("unit-only-generation-key-not-production-01234567890123456789".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        return data+"."+encoder.encodeToString(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test void legacyAndMatchingFamilyWorkButResetInvalidatesEveryOlderFamily() throws Exception {
        when(redis.opsForValue()).thenReturn(values);String current=UUID.randomUUID().toString();
        assertFalse(service.isRevoked(signed(user.toString(),null)));
        when(values.get("auth:session-generation:"+user)).thenReturn(current);
        assertTrue(service.isRevoked(signed(user.toString(),null)));
        assertTrue(service.isRevoked(signed(user.toString(),UUID.randomUUID().toString())));
        assertFalse(service.isRevoked(signed(user.toString(),current)));
    }
    @Test void explicitFamilyIsRejectedIfItsSecurityStateIsMissing() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        assertTrue(service.isRevoked(signed(user.toString(),UUID.randomUUID().toString())));
    }
    @Test void individualRevocationStillWinsOverMatchingFamily() throws Exception {
        when(redis.hasKey(anyString())).thenReturn(true);
        assertTrue(service.isRevoked(signed(user.toString(),UUID.randomUUID().toString())));verifyNoInteractions(values);
    }
    @Test void userSessionNeverFailsOpenWhenStoreCannotBeRead() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service,"failOpen",true);
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("synthetic outage"));
        String token=signed(user.toString(),null);
        assertThrows(SessionStoreUnavailableException.class,()->service.isRevoked(token));
    }
    @Test void machineIdentityDoesNotBecomeAUserSessionAndMalformedMetadataCannotPass() throws Exception {
        assertFalse(service.isRevoked(signed("hotel-service",null)));verifyNoInteractions(values);
        reset(redis);assertTrue(service.isRevoked("not-a-jwt"));assertTrue(service.isRevoked(signed(user.toString(),42)));
        verifyNoInteractions(redis);
    }
}
