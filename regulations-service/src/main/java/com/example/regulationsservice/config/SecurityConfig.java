package com.example.regulationsservice.config;

import com.example.regulationsservice.security.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /** Same browser origins as the Gateway CORS list (F-9G-1: 127.0.0.1:3000 was missing); override per environment. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://127.0.0.1:3000,http://localhost:3001}}") java.util.List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session->session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults()) // uses the corsConfigurationSource bean
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/regulations/snapshots", "/api/regulations/snapshots/").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/regulations/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/regulations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/regulations/refresh").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(401);
                            response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request,response,denied)->{
                            response.setContentType("application/json");response.setStatus(403);
                            response.getWriter().write("{\"status\":403,\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
                        })
                );

        return http.build();
    }
}
