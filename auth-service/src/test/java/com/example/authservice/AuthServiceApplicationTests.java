package com.example.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** F-9E-5: synthetic signing secret so the context check needs no private environment (never a real key). */
@SpringBootTest(properties = "SECRET_KEY=synthetic-context-test-signing-secret-0123456789abcdef0123456789abcdef")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
