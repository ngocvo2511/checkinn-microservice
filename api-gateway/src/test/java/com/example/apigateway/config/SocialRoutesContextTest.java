package com.example.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the gateway to prove the social routes and rate-limiter arguments bind. Redis is not contacted. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "spring.boot.admin.client.enabled=false",
        "jwt.secret=test-only-secret-key-with-at-least-32-bytes!!"
})
class SocialRoutesContextTest {

    @Autowired RouteLocator routes;

    @Test
    void socialRoutesBuildWithRateLimitersOnMutationsOnly() {
        Map<String, Route> social = routes.getRoutes().collectList().block().stream()
                .filter(r -> r.getId().startsWith("travel-social-"))
                .collect(Collectors.toMap(Route::getId, r -> r));

        assertThat(social).containsOnlyKeys("travel-social-create-post", "travel-social-comment", "travel-social-like",
                "travel-social-report", "travel-social-media-upload", "travel-social-other-mutations", "travel-social-read");
        social.forEach((id, route) -> {
            assertThat(route.getUri().toString()).isEqualTo("lb://travel-social-service");
            List<String> filterNames = route.getFilters().stream().map(Object::toString).toList();
            boolean limited = filterNames.stream().anyMatch(f -> f.contains(RequestRateLimiterGatewayFilterFactory.class.getSimpleName()));
            assertThat(limited).as(id).isEqualTo(!id.equals("travel-social-read"));
        });
    }
}
