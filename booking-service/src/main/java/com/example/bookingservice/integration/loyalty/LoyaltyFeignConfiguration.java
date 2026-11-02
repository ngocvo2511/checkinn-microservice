package com.example.bookingservice.integration.loyalty;

import com.example.bookingservice.security.JwtService;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/** Not component-scanned: credential applies only to this Feign client. */
public class LoyaltyFeignConfiguration {
    @Bean public feign.Request.Options loyaltyTimeouts() {
        return new feign.Request.Options(2, java.util.concurrent.TimeUnit.SECONDS, 5, java.util.concurrent.TimeUnit.SECONDS, false);
    }
    @Bean public RequestInterceptor loyaltyServiceAuthentication(JwtService jwt) {
        return template -> {
            template.removeHeader("Authorization");
            template.header("Authorization", "Bearer " + jwt.generateInventoryServiceToken());
        };
    }
}
