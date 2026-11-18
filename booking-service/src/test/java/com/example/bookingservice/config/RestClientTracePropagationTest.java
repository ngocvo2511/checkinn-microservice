package com.example.bookingservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.micrometer.tracing.Tracer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.client.HttpClientObservationsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** F-9C-2: Booking -> Hotel REST calls must carry the caller's W3C trace context and keep configured timeouts. */
class RestClientTracePropagationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, BraveAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class, RestTemplateAutoConfiguration.class,
                    HttpClientObservationsAutoConfiguration.class))
            .withUserConfiguration(RestClientConfig.class)
            .withPropertyValues("clients.connect-timeout-ms=1234", "clients.read-timeout-ms=4321",
                    "management.tracing.sampling.probability=1.0");

    @Test void outgoingCallPropagatesCurrentTraceAndKeepsTimeouts() {
        context.run(ctx -> {
            RestTemplate rest = ctx.getBean(RestTemplate.class);
            Tracer tracer = ctx.getBean(Tracer.class);

            var factory = (SimpleClientHttpRequestFactory) rest.getRequestFactory();
            assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(1234);
            assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(4321);

            var server = MockRestServiceServer.bindTo(rest).build();
            var span = tracer.nextSpan().name("agent-write").start();
            String traceId = span.context().traceId();
            server.expect(requestTo("http://hotel-service/api/v1/holds"))
                    .andExpect(header("traceparent", Matchers.matchesPattern("00-" + traceId + "-[0-9a-f]{16}-0[01]")))
                    .andRespond(withSuccess());
            try (var ignored = tracer.withSpan(span)) {
                rest.getForEntity("http://hotel-service/api/v1/holds", String.class);
            } finally {
                span.end();
            }
            server.verify();
        });
    }
}
