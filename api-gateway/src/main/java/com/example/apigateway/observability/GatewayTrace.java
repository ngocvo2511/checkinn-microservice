package com.example.apigateway.observability;

import io.micrometer.tracing.handler.TracingObservationHandler;
import java.util.UUID;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;

/** Trace id of the current server request observation (STD-030); the same id is propagated downstream. */
public final class GatewayTrace {
    private GatewayTrace() {}

    /** The W3C trace id, or {@code null} when no server observation/span exists for this exchange. */
    public static String traceId(ServerWebExchange exchange) {
        var context = ServerRequestObservationContext.findCurrent(exchange.getAttributes()).orElse(null);
        if (context == null) return null;
        TracingObservationHandler.TracingContext tracing = context.get(TracingObservationHandler.TracingContext.class);
        if (tracing == null || tracing.getSpan() == null) return null;
        String id = tracing.getSpan().context().traceId();
        return id != null && id.matches("[0-9a-f]{16,32}") ? id : null;
    }

    /** Client-visible correlation id: the trace id when present, otherwise a fresh random id. */
    public static String requestId(ServerWebExchange exchange) {
        String trace = traceId(exchange);
        return trace != null ? trace : UUID.randomUUID().toString();
    }
}
