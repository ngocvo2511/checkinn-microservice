package com.example.travelsocialservice.config;

import com.example.travelsocialservice.security.TokenRevocationService;
import com.example.travelsocialservice.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> adminOnly = (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwt
                        && "ADMIN".equals(jwt.getToken().getClaimAsString("role")));
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                write(response, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "A valid user token is required");
        AccessDeniedHandler forbidden = (request, response, ex) ->
                write(response, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this");

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/social/admin/**").access(adminOnly)
                        .requestMatchers("/api/social/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/social/**").permitAll()
                        .requestMatchers("/api/social/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> { })
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .addFilterAfter((request, response, chain) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    try {
                        if (authentication instanceof JwtAuthenticationToken jwt) {
                            MDC.put("userId", jwt.getName());
                        }
                        chain.doFilter(request, response);
                    } finally {
                        MDC.remove("userId");
                    }
                }, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret, TokenRevocationService revocations) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is missing or blank");
        }
        JwtDecoder signed = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")).build();
        // Verify the signature first, so unsigned or forged tokens never reach Redis. A revocation store
        // outage is an authentication failure (401), not a server error: the frontend retries public
        // reads anonymously on 401, so browsing keeps working while Redis is down.
        return token -> {
            Jwt jwt = signed.decode(token);
            boolean revoked;
            try {
                revoked = revocations.isRevoked(token);
            } catch (RuntimeException unavailable) {
                throw new BadJwtException("Token revocation could not be checked", unavailable);
            }
            if (revoked) {
                throw new BadJwtException("Token has been revoked");
            }
            return jwt;
        };
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
                              String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(status.value(), code, message, null));
    }
}
