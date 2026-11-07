package com.example.hotelservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtMdcFilterTest {
    @Test void malformedTokenIs401WithoutDecoderMessageOrContinuation() throws Exception {
        var decoder=mock(JwtDecoder.class);var revoked=mock(TokenRevocationService.class);
        when(decoder.decode("synthetic-bad")).thenThrow(new BadJwtException("synthetic decoder detail"));
        var request=new MockHttpServletRequest("GET","/amenity-catalog");request.addHeader("Authorization","Bearer synthetic-bad");
        var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        new JwtMdcFilter(decoder,revoked).doFilter(request,response,chain);
        assertEquals(401,response.getStatus());assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        assertFalse(response.getContentAsString().contains("synthetic decoder detail"));verifyNoInteractions(chain);
        assertNull(org.slf4j.MDC.get("userId"));
    }
    @Test void revocationOutageIs503AndNeverAuthenticates() throws Exception {
        var decoder=mock(JwtDecoder.class);var revoked=mock(TokenRevocationService.class);
        when(revoked.isRevoked(anyString())).thenThrow(new IllegalStateException("synthetic storage detail"));
        var request=new MockHttpServletRequest("GET","/amenity-catalog");request.addHeader("Authorization","Bearer synthetic-bad");
        var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        new JwtMdcFilter(decoder,revoked).doFilter(request,response,chain);
        assertEquals(503,response.getStatus());assertTrue(response.getContentAsString().contains("DEPENDENCY_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains("synthetic storage detail"));verifyNoInteractions(decoder,chain);
    }
    @Test void revokedTokenIs401WithoutDecodingOrContinuing() throws Exception {
        var decoder=mock(JwtDecoder.class);var revoked=mock(TokenRevocationService.class);
        when(revoked.isRevoked(anyString())).thenReturn(true);
        var request=new MockHttpServletRequest("GET","/amenity-catalog");request.addHeader("Authorization","Bearer synthetic-revoked");
        var response=new MockHttpServletResponse();var chain=mock(jakarta.servlet.FilterChain.class);
        new JwtMdcFilter(decoder,revoked).doFilter(request,response,chain);
        assertEquals(401,response.getStatus());verifyNoInteractions(decoder,chain);
    }
}
