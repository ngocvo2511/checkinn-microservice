package com.example.bookingservice.integration.loyalty;

import com.example.bookingservice.security.JwtService;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
class LoyaltyFeignConfigurationTest {
    @Test void callerAlwaysUsesServiceCredentialRatherThanForwardedUserToken() {
        var jwt=mock(JwtService.class); when(jwt.generateInventoryServiceToken()).thenReturn("synthetic-service-token");
        var request=new RequestTemplate(); request.header("Authorization","Bearer user-token");
        new LoyaltyFeignConfiguration().loyaltyServiceAuthentication(jwt).apply(request);
        assertThat(request.headers().get("Authorization")).containsExactly("Bearer synthetic-service-token");
    }
}
