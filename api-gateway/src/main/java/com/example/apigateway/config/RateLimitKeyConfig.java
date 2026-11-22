package com.example.apigateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Rate-limit key for social mutations: the verified JWT subject, or the client IP when the token is
 * missing or invalid. The gateway only keys on the token; travel-social-service still authenticates.
 * RedisRateLimiter builds its bucket from this key alone, so the route id is part of the key to keep
 * each action's limit separate.
 */
@Configuration
public class RateLimitKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitKeyConfig.class);

    @Bean
    public KeyResolver socialUserKeyResolver(@Value("${jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("[RATE_LIMIT] jwt.secret is not set; social rate limits fall back to client IP keys");
            return exchange -> Mono.just(routeScoped(exchange, ipKey(exchange)));
        }
        ReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        return resolver(decoder);
    }

    static KeyResolver resolver(ReactiveJwtDecoder decoder) {
        return exchange -> callerKey(decoder, exchange).map(key -> routeScoped(exchange, key));
    }

    private static Mono<String> callerKey(ReactiveJwtDecoder decoder, ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return Mono.just(ipKey(exchange));
        }
        return decoder.decode(header.substring(7))
                .map(jwt -> jwt.getSubject() == null || jwt.getSubject().isBlank() ? ipKey(exchange) : "user:" + jwt.getSubject())
                .onErrorResume(ex -> Mono.just(ipKey(exchange)));
    }

    static String routeScoped(ServerWebExchange exchange, String key) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        return (route == null ? "unrouted" : route.getId()) + ":" + key;
    }

    static String ipKey(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return "ip:" + (remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress());
    }
}
