package com.example.apigateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the /api/social/** route table in application.yml: ordering, pass-through, and rate limits. */
class SocialRoutesConfigTest {

    static List<Map<String, Object>> socialRoutes;
    static Map<String, Object> locator;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws Exception {
        try (InputStream in = SocialRoutesConfigTest.class.getResourceAsStream("/application.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> gateway = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) root.get("spring")).get("cloud")).get("gateway");
            socialRoutes = ((List<Map<String, Object>>) gateway.get("routes")).stream()
                    .filter(route -> "lb://travel-social-service".equals(route.get("uri"))).toList();
            locator = (Map<String, Object>) ((Map<String, Object>) gateway.get("discovery")).get("locator");
        }
    }

    /** The discovery locator must not create a /travel-social-service/** route that skips the rate limiters. */
    @Test
    void discoveryLocatorExcludesTravelSocialService() {
        Expression include = new SpelExpressionParser().parseExpression((String) locator.get("include-expression"));
        java.util.function.Predicate<String> included = serviceId -> Boolean.TRUE.equals(
                include.getValue(new DefaultServiceInstance("i", serviceId, "localhost", 80, false), Boolean.class));

        assertThat(included.test("TRAVEL-SOCIAL-SERVICE")).isFalse();
        assertThat(included.test("travel-social-service")).isFalse();
        assertThat(included.test("USER-SERVICE")).isTrue();
        assertThat(included.test("HOTEL-SERVICE")).isTrue();
    }

    @SuppressWarnings("unchecked")
    static List<String> predicates(Map<String, Object> route) {
        return (List<String>) route.get("predicates");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> rateLimiterArgs(Map<String, Object> route) {
        List<Object> filters = (List<Object>) route.getOrDefault("filters", List.of());
        return filters.stream().filter(Map.class::isInstance).map(f -> (Map<String, Object>) f)
                .filter(f -> "RequestRateLimiter".equals(f.get("name")))
                .map(f -> (Map<String, Object>) f.get("args")).findFirst().orElse(null);
    }

    @Test
    void readRouteIsLastAndOnlyUnlimitedRoute() {
        Map<String, Object> last = socialRoutes.get(socialRoutes.size() - 1);
        assertThat(last.get("id")).isEqualTo("travel-social-read");
        assertThat(rateLimiterArgs(last)).isNull();
        socialRoutes.subList(0, socialRoutes.size() - 1).forEach(route -> {
            assertThat(predicates(route)).as(route.get("id").toString()).anyMatch(p -> p.startsWith("Method="));
            assertThat(predicates(route)).noneMatch(p -> p.contains("GET"));
            assertThat(rateLimiterArgs(route)).as(route.get("id").toString()).isNotNull()
                    .containsEntry("key-resolver", "#{@socialUserKeyResolver}");
        });
    }

    @Test
    void routesPassPathsThroughWithoutRewrite() {
        socialRoutes.forEach(route -> {
            assertThat(predicates(route)).anyMatch(p -> p.startsWith("Path=/api/social"));
            assertThat(String.valueOf(route.get("filters"))).doesNotContain("RewritePath", "StripPrefix");
        });
    }

    @Test
    void catchAllMutationRouteComesAfterSpecificLimits() {
        List<String> ids = socialRoutes.stream().map(r -> r.get("id").toString()).toList();
        assertThat(ids.indexOf("travel-social-other-mutations"))
                .isGreaterThan(ids.indexOf("travel-social-create-post"))
                .isGreaterThan(ids.indexOf("travel-social-comment"))
                .isGreaterThan(ids.indexOf("travel-social-like"))
                .isGreaterThan(ids.indexOf("travel-social-report"))
                .isGreaterThan(ids.indexOf("travel-social-media-upload"))
                .isLessThan(ids.indexOf("travel-social-read"));
    }

    @Test
    void limitsMatchConfirmedProductValues() {
        // requests per window = burstCapacity / requestedTokens, refilled at 1 token/second
        Map<String, Integer> expectedPerWindow = Map.of(
                "travel-social-create-post", 10, "travel-social-comment", 30, "travel-social-like", 120,
                "travel-social-report", 20, "travel-social-media-upload", 30);
        socialRoutes.stream().filter(r -> expectedPerWindow.containsKey(r.get("id"))).forEach(route -> {
            Map<String, Object> args = rateLimiterArgs(route);
            int burst = (Integer) args.get("redis-rate-limiter.burstCapacity");
            int cost = (Integer) args.get("redis-rate-limiter.requestedTokens");
            assertThat(args.get("redis-rate-limiter.replenishRate")).isEqualTo(1);
            assertThat(burst / cost).as(route.get("id").toString()).isEqualTo(expectedPerWindow.get(route.get("id")));
            assertThat(burst).isEqualTo("travel-social-report".equals(route.get("id")) ? 86_400 : 3_600);
        });
        assertThat(socialRoutes).extracting(r -> r.get("id")).containsAll(expectedPerWindow.keySet());
    }
}
