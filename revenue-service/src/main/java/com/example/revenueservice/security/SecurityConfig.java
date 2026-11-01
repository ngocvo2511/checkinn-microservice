package com.example.revenueservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain reportSecurity(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth->auth.requestMatchers("/api/reports", "/api/reports/**").authenticated().anyRequest().permitAll())
            .oauth2ResourceServer(oauth->oauth.jwt(Customizer.withDefaults())).build();
    }
    @Bean JwtDecoder jwtDecoder(@Value("${jwt.secret:${SECRET_KEY}}") String secret, TokenRevocationService revocation) {
        if(secret==null||secret.isBlank()) throw new IllegalStateException("JWT secret required");
        var signed=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).build();
        return token->{
            var jwt=signed.decode(token);
            try {
                if(revocation.isRevoked(token)) throw new BadJwtException("Revoked bearer token");
            } catch(IllegalStateException ex) { throw new BadJwtException("Token verification unavailable"); }
            return jwt;
        };
    }
}
