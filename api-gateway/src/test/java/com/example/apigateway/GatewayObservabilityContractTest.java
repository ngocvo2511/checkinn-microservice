package com.example.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Batch 9G (STD-030/STD-026): the Gateway propagates or creates W3C trace context, logs the trace id per
 * request, and answers its own failures (timeout, refused connection, no route) with the standard envelope.
 * Downstream is a local JDK HttpServer; no Eureka, Config Server or other service is involved.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false", "spring.boot.admin.client.enabled=false",
        "spring.cloud.gateway.httpclient.response-timeout=1s", "spring.cloud.gateway.httpclient.connect-timeout=300"})
// Spring Boot test support disables tracing (noop propagation) unless observability is explicitly enabled.
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
class GatewayObservabilityContractTest {
    static final List<String> downstreamTraceparents = new CopyOnWriteArrayList<>();
    static final HttpServer downstream = start();

    static HttpServer start() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/stub/echo", exchange -> {
                downstreamTraceparents.add(String.valueOf(exchange.getRequestHeaders().getFirst("traceparent")));
                byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.createContext("/stub/slow", exchange -> {
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll static void stop() { downstream.stop(0); }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + downstream.getAddress().getPort();
        registry.add("spring.cloud.gateway.routes[0].id", () -> "stub");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> base);
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/stub/**");
        registry.add("spring.cloud.gateway.routes[1].id", () -> "dead");
        registry.add("spring.cloud.gateway.routes[1].uri", () -> "lb://no-such-service"); // service down: no registered instance
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/dead/**");
    }

    @Autowired WebTestClient client;

    /** The access line is written when the exchange completes, which can be just after the client has the response. */
    static void awaitLog(CapturedOutput output, String expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (!output.getOut().contains(expected) && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(25); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        assertThat(output.getOut()).contains(expected);
    }

    static String traceOf(String traceparent) {
        assertThat(traceparent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
        return traceparent.split("-")[1];
    }

    @Test void clientTraceIsPropagatedDownstreamAndLogged(CapturedOutput output) {
        String trace = "4bf92f3577b34da6a3ce929d0e0e4736";
        downstreamTraceparents.clear();
        client.get().uri("/stub/echo").header("traceparent", "00-" + trace + "-00f067aa0ba902b7-01")
                .exchange().expectStatus().isOk();
        assertThat(downstreamTraceparents).hasSize(1);
        assertThat(traceOf(downstreamTraceparents.get(0))).isEqualTo(trace);
        awaitLog(output, "[GATEWAY_ACCESS] traceId=" + trace + " GET /stub/echo -> 200");
    }

    @Test void gatewayStartsATraceWhenTheClientSendsNone(CapturedOutput output) {
        downstreamTraceparents.clear();
        client.get().uri("/stub/echo?email=private@example.invalid").exchange().expectStatus().isOk();
        String created = traceOf(downstreamTraceparents.get(0));
        awaitLog(output, "[GATEWAY_ACCESS] traceId=" + created + " GET /stub/echo -> 200");
        assertThat(output.getOut()).doesNotContain("private@example.invalid");
    }

    @Test void responseTimeoutIsA504EnvelopeCorrelatedWithTheTrace() {
        String trace = "0af7651916cd43dd8448eb211c80319c";
        client.get().uri("/stub/slow").header("traceparent", "00-" + trace + "-b7ad6b7169203331-01").exchange()
                .expectStatus().isEqualTo(504)
                .expectHeader().valueEquals("X-Request-ID", trace)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.code").isEqualTo("DEPENDENCY_TIMEOUT")
                .jsonPath("$.message").isEqualTo("Dependency timed out")
                .jsonPath("$.path").isEqualTo("/stub/slow")
                .jsonPath("$.requestId").isEqualTo(trace)
                .jsonPath("$.trace").doesNotExist();
    }

    @Test void unavailableServiceAndMissingRouteUseTheEnvelopeWithoutInternalDetail() {
        var refused = client.get().uri("/dead/anything").exchange().expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.code").isEqualTo("DEPENDENCY_UNAVAILABLE")
                .jsonPath("$.requestId").isNotEmpty().returnResult();
        assertThat(new String(refused.getResponseBodyContent(), StandardCharsets.UTF_8)).doesNotContain("no-such-service").doesNotContain("Exception");
        client.get().uri("/no-such-route").exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND").jsonPath("$.requestId").isNotEmpty();
    }
}
