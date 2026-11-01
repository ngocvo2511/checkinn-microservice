package com.example.bookingservice.integration.loyalty;

import com.example.bookingservice.security.JwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/** Not component-scanned: credential applies only to this Feign client. */
public class LoyaltyFeignConfiguration {
    @Bean public RequestInterceptor loyaltyServiceAuthentication(JwtService jwt) {
        return template -> {
            template.removeHeader("Authorization");
            template.header("Authorization", "Bearer " + jwt.generateInventoryServiceToken());
        };
    }
}
