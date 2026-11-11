package com.example.regulationsservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegulationSessionBoundaryTest {
    static final String KEY="synthetic-regulation-session-unit-key-012345678901234567890123456789";
    final TokenRevocationService revocations=mock(TokenRevocationService.class);
    final JwtService jwt=new JwtService(KEY,revocations);
    String token(String subject) {
        return Jwts.builder().setSubject(subject).claim("role","ADMIN").setExpiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8)),SignatureAlgorithm.HS256).compact();
    }
    @Test void unavailableSessionStoreCannotMasqueradeAsAnInvalidToken() {
        String token=token(UUID.randomUUID().toString());
        when(revocations.isRevoked(token)).thenThrow(new SessionStoreUnavailableException());
        assertThatThrownBy(()->jwt.validateToken(token)).isInstanceOf(SessionStoreUnavailableException.class);
    }
    @Test void invalidSignedPrincipalIsRejectedBeforeControllerExtraction() {
        assertThat(jwt.validateToken(token("not-a-user-uuid"))).isFalse();
    }
    @Test void validAndRevokedSessionsKeepTheirOriginalSecuritySemantics() {
        String token=token(UUID.randomUUID().toString());
        assertThat(jwt.validateToken(token)).isTrue();
        when(revocations.isRevoked(token)).thenReturn(true);
        assertThat(jwt.validateToken(token)).isFalse();assertThat(jwt.validateToken("malformed")).isFalse();
    }
}
