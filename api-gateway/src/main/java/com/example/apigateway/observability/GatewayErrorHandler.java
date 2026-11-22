package com.example.apigateway.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Errors raised by the Gateway itself (no route, no instance, connection refused, response timeout) use the
 * same envelope as the services: {timestamp,status,error,code,message,path,requestId} plus X-Request-ID
 * (STD-026). Never includes exception text (it carries internal hosts/URLs). Downstream error bodies are
 * proxied unchanged and do not pass through here.
 */
@Component
@Order(-2) // before Boot's DefaultErrorWebExceptionHandler (-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        var response = exchange.getResponse();
        if (response.isCommitted()) return Mono.error(failure);
        HttpStatus status = statusOf(failure);
        String requestId = GatewayTrace.requestId(exchange);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("code", codeOf(status));
        body.put("message", messageOf(status));
        body.put("path", exchange.getRequest().getPath().value());
        body.put("requestId", requestId);
        if (status.is5xxServerError())
            log.warn("[GATEWAY_ERROR] traceId={} {} {} -> {} ({})", requestId, exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(), status.value(), failure.getClass().getSimpleName());
        byte[] bytes;
        try { bytes = JSON.writeValueAsBytes(body); } catch (Exception e) { return Mono.error(failure); }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().set("X-Request-ID", requestId);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    static String codeOf(HttpStatus status) {
        return switch (status.value()) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 502, 503 -> "DEPENDENCY_UNAVAILABLE";
            case 504 -> "DEPENDENCY_TIMEOUT";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }

    static String messageOf(HttpStatus status) {
        return switch (status.value()) {
            case 502, 503 -> "Service temporarily unavailable";
            case 504 -> "Dependency timed out";
            default -> status.is5xxServerError() ? "Internal server error" : status.getReasonPhrase();
        };
    }

    static HttpStatus statusOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResponseStatusException known) {
                HttpStatus resolved = HttpStatus.resolve(known.getStatusCode().value());
                return resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
            }
            if (cause instanceof ConnectException || cause instanceof io.netty.channel.ConnectTimeoutException
                    || cause instanceof java.net.UnknownHostException) return HttpStatus.SERVICE_UNAVAILABLE;
            if (cause instanceof java.util.concurrent.TimeoutException) return HttpStatus.GATEWAY_TIMEOUT;
            if (cause instanceof reactor.netty.http.client.PrematureCloseException) return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
