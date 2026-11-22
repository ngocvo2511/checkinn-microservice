package com.example.apigateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * One line per routed request with the trace id (STD-030). Logs method, path (never the query string,
 * which can carry e-mail addresses), status and duration; no headers, tokens or bodies.
 */
@Component
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(GatewayAccessLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long started = System.nanoTime();
        String trace = GatewayTrace.traceId(exchange);
        return chain.filter(exchange).doFinally(signal -> {
            var status = exchange.getResponse().getStatusCode();
            log.info("[GATEWAY_ACCESS] traceId={} {} {} -> {} in {} ms", trace == null ? "-" : trace,
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                    status == null ? "-" : status.value(), (System.nanoTime() - started) / 1_000_000);
        });
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
