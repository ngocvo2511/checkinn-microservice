package com.example.apigateway.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitKeyConfigTest {

    static final String SECRET = "test-only-secret-key-with-at-least-32-bytes!!";
    final RateLimitKeyConfig config = new RateLimitKeyConfig();

    static String token(String secret, String subject, long expiresInMillis) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject(subject).claim("role", "CUSTOMER")
                .expirationTime(new Date(System.currentTimeMillis() + expiresInMillis)).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    static MockServerWebExchange exchange(String routeId, String authorization) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.post("/api/social/posts")
                .remoteAddress(new InetSocketAddress("203.0.113.7", 50000));
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, Route.async()
                .id(routeId).uri(URI.create("lb://travel-social-service")).predicate(ignored -> true).build());
        return exchange;
    }

    String resolve(KeyResolver resolver, String authorization) {
        return resolver.resolve(exchange("travel-social-create-post", authorization)).block();
    }

    @Test
    void validTokenIsKeyedBySubject() throws Exception {
        KeyResolver resolver = config.socialUserKeyResolver(SECRET);
        assertThat(resolve(resolver, "Bearer " + token(SECRET, "user-123", 60_000))).isEqualTo("travel-social-create-post:user:user-123");
    }

    @Test
    void missingMalformedForgedOrExpiredTokensFallBackToClientIp() throws Exception {
        KeyResolver resolver = config.socialUserKeyResolver(SECRET);
        assertThat(resolve(resolver, null)).isEqualTo("travel-social-create-post:ip:203.0.113.7");
        assertThat(resolve(resolver, "Basic abc")).isEqualTo("travel-social-create-post:ip:203.0.113.7");
        assertThat(resolve(resolver, "Bearer not-a-jwt")).isEqualTo("travel-social-create-post:ip:203.0.113.7");
        String forged = token("another-secret-key-that-is-also-32-bytes-long", "victim", 60_000);
        assertThat(resolve(resolver, "Bearer " + forged)).isEqualTo("travel-social-create-post:ip:203.0.113.7");
        assertThat(resolve(resolver, "Bearer " + token(SECRET, "user-123", -60_000))).isEqualTo("travel-social-create-post:ip:203.0.113.7");
    }

    @Test
    void blankSecretKeysEverythingByClientIp() throws Exception {
        KeyResolver resolver = config.socialUserKeyResolver("");
        assertThat(resolve(resolver, "Bearer " + token(SECRET, "user-123", 60_000))).isEqualTo("travel-social-create-post:ip:203.0.113.7");
    }

    @Test
    void eachRouteHasItsOwnBucketForTheSameCaller() throws Exception {
        KeyResolver resolver = config.socialUserKeyResolver(SECRET);
        String bearer = "Bearer " + token(SECRET, "user-123", 60_000);
        String post = resolver.resolve(exchange("travel-social-create-post", bearer)).block();
        String like = resolver.resolve(exchange("travel-social-like", bearer)).block();
        assertThat(post).isNotEqualTo(like).endsWith("user:user-123");
        assertThat(like).isEqualTo("travel-social-like:user:user-123");
    }
}
