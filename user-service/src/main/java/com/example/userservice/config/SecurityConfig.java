package com.example.userservice.config;

import com.example.userservice.security.JwtMdcFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtMdcFilter jwtMdcFilter;

    public SecurityConfig(JwtMdcFilter jwtMdcFilter) {
        this.jwtMdcFilter = jwtMdcFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Disable default login form / basic auth and allow our manual JWT validation in controllers
        http.csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(jwtMdcFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .httpBasic(Customizer.withDefaults())
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
