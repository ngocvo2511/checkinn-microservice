package com.example.revenueservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * F-9E-5: full context needs the service's real PostgreSQL datasource (default profile, localhost:5432).
 * Opt-in like the other external-resource suites; never point it at hosted or business data.
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "CHECKINN_CONTEXT_TEST", matches = "true")
@SpringBootTest
class RevenueServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
