package com.example.hotelservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class HotelServiceTokenProviderTest {
    @Test void generatedTokenHasExactServiceIdentityAndFiveMinuteLifetime() {
        String secret="synthetic-unit-test-key-never-a-production-credential-00000000";
        String bearer=new HotelServiceTokenProvider(secret).authorization();
        var decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).build();
        var jwt=decoder.decode(bearer.substring(7));
        assertThat(jwt.getSubject()).isEqualTo("hotel-service");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("HOTEL_SERVICE");
        assertThat(java.time.Duration.between(jwt.getIssuedAt(),jwt.getExpiresAt()).toSeconds()).isEqualTo(300);
    }
}
