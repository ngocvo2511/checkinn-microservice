package com.example.revenueservice.config;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** STD-030 (Batch 9F): Revenue's Hotel/Regulations client (RestClientConfig) propagates W3C trace context. */
class RestTemplateTracePropagationTest {
    @Configuration(proxyBeanMethods = false)
    static class Client {
        @Bean RestTemplate restTemplate(RestTemplateBuilder builder) { return new RestClientConfig().restTemplate(builder); }
    }

    @Test void outgoingCallCarriesTheCurrentTrace() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, BraveAutoConfiguration.class,
                        MicrometerTracingAutoConfiguration.class, RestTemplateAutoConfiguration.class, HttpClientObservationsAutoConfiguration.class))
                .withUserConfiguration(Client.class)
                .withPropertyValues("management.tracing.sampling.probability=1.0")
                .run(ctx -> {
                    RestTemplate rest = ctx.getBean(RestTemplate.class);
                    Tracer tracer = ctx.getBean(Tracer.class);
                    var server = MockRestServiceServer.bindTo(rest).build();
                    var span = tracer.nextSpan().name("std-030").start();
                    server.expect(header("traceparent", Matchers.matchesPattern("00-" + span.context().traceId() + "-[0-9a-f]{16}-0[01]")))
                            .andRespond(withSuccess());
                    try (var ignored = tracer.withSpan(span)) {
                        rest.getForEntity("http://downstream.invalid/probe", String.class);
                    } finally {
                        span.end();
                    }
                    server.verify();
                });
    }
}
