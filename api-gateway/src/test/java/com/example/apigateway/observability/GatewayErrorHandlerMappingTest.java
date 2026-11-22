package com.example.apigateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Batch 9G (STD-026): connection-level upstream failures map to dependency statuses, never to 500. */
class GatewayErrorHandlerMappingTest {
    @Test void upstreamFailuresMapToDependencyStatuses() {
        assertThat(GatewayErrorHandler.statusOf(new RuntimeException("wrapped", new ConnectException("refused 10.0.0.5:8083"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(GatewayErrorHandler.statusOf(new io.netty.channel.ConnectTimeoutException("connect timed out")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(GatewayErrorHandler.statusOf(new java.util.concurrent.TimeoutException("slow")))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GatewayErrorHandler.statusOf(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT)))
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(GatewayErrorHandler.statusOf(new IllegalStateException("bug"))).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(GatewayErrorHandler.codeOf(HttpStatus.SERVICE_UNAVAILABLE)).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(GatewayErrorHandler.codeOf(HttpStatus.BAD_GATEWAY)).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(GatewayErrorHandler.codeOf(HttpStatus.GATEWAY_TIMEOUT)).isEqualTo("DEPENDENCY_TIMEOUT");
    }
}
