package com.example.hotelservice.config;

import com.example.hotelservice.security.JwtMdcFilter;
import com.example.hotelservice.security.TokenRevocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Bean
        public JwtMdcFilter jwtMdcFilter(JwtDecoder jwtDecoder, TokenRevocationService tokenRevocationService) {
                return new JwtMdcFilter(jwtDecoder, tokenRevocationService);
        }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtMdcFilter jwtMdcFilter) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> adminOnly = (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwt
                        && "ADMIN".equals(jwt.getToken().getClaimAsString("role")));
        AuthorizationManager<RequestAuthorizationContext> bookingServiceOnly = (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwt
                        && "booking-service".equals(jwt.getToken().getSubject())
                        && "BOOKING_SERVICE".equals(jwt.getToken().getClaimAsString("role")));
        http
                .csrf(csrf -> csrf.disable())
                                .addFilterBefore(jwtMdcFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/availability/hold", "/api/v1/availability/hold/**").access(bookingServiceOnly)
                        .requestMatchers(HttpMethod.POST, "/cities", "/cities/**").access(adminOnly)
                        .requestMatchers(HttpMethod.PUT, "/cities/**").access(adminOnly)
                        .requestMatchers(HttpMethod.DELETE, "/cities/**").access(adminOnly)
                        .requestMatchers(HttpMethod.GET, "/hotels/pending", "/hotels/pending/**").access(adminOnly)
                        .requestMatchers(HttpMethod.PUT, "/hotels/{hotelId}/approve", "/hotels/{hotelId}/deactivate").access(adminOnly)
                        .requestMatchers(HttpMethod.POST, "/hotels/{hotelId}/reject").access(adminOnly)
                        .requestMatchers(HttpMethod.PATCH, "/hotels/{hotelId}/activate").access(adminOnly)
                        .requestMatchers("/hotels/owner", "/hotels/owner/**", "/hotels/{hotelId}/documents").authenticated()
                        .requestMatchers(HttpMethod.POST, "/hotels", "/hotels/**", "/api/v1").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/hotels/**", "/api/v1/{roomTypeId}",
                                "/api/v1/{roomTypeId}/activate", "/api/v1/{roomTypeId}/deactivate").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/hotels/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                );

        return http.build();
    }
    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret}") String secret) {
                if (secret == null || secret.isBlank()) {
                        throw new IllegalStateException("jwt.secret is missing or blank");
                }
        SecretKey key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}

