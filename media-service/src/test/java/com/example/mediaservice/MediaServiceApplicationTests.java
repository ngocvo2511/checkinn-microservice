package com.example.mediaservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Context construction is isolated; provider calls are covered separately by loopback tests.
@SpringBootTest(properties={
    "supabase.url=http://127.0.0.1:9",
    "supabase.bucket=synthetic-public",
    "supabase.private-bucket=synthetic-private",
    "supabase.service-role-key=synthetic-context-provider-key",
    "SECRET_KEY=synthetic-context-jwt-key-012345678901234567890123456789",
    "grpc.server.port=0","grpc.server.address=127.0.0.1",
    "eureka.client.enabled=false","spring.cloud.discovery.enabled=false",
    "spring.boot.admin.client.enabled=false"
})
class MediaServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
