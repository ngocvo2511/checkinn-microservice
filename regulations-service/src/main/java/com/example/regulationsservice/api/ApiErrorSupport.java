package com.example.regulationsservice.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Standard error envelope shared by all services (STD-026, Batch 9H):
 * {timestamp,status,error,code,message,path,requestId} plus the X-Request-ID header. requestId is the current
 * W3C trace id when tracing is active, so it matches the Gateway access log and the other services' logs.
 * Callers pass only safe, client-facing messages; never exception text.
 */
public final class ApiErrorSupport {
    private ApiErrorSupport() {}

    public static String requestId() {
        String trace = org.slf4j.MDC.get("traceId");
        return trace != null && trace.matches("[A-Za-z0-9_-]{1,64}") ? trace : UUID.randomUUID().toString();
    }

    public static String defaultCode(int status) {
        return switch (status) {
            case 400 -> "INVALID_REQUEST"; case 401 -> "UNAUTHORIZED"; case 403 -> "FORBIDDEN"; case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED"; case 409 -> "CONFLICT"; case 415 -> "UNSUPPORTED_MEDIA_TYPE"; case 429 -> "RATE_LIMITED";
            case 502, 503 -> "DEPENDENCY_UNAVAILABLE"; case 504 -> "DEPENDENCY_TIMEOUT";
            default -> status >= 500 ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }

    static String currentPath() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest().getRequestURI() : "";
    }

    /** Envelope body; {@code code}/{@code message} may be null to use the defaults for the status. */
    public static Map<String, Object> body(int status, String code, String message) {
        HttpStatus known = HttpStatus.resolve(status);
        String reason = known == null ? "Request failed" : known.getReasonPhrase();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", reason);
        body.put("code", code == null ? defaultCode(status) : code);
        body.put("message", message == null || message.isBlank() ? reason : message);
        body.put("path", currentPath());
        body.put("requestId", requestId());
        return body;
    }

    /** Body + status + X-Request-ID (same value as body.requestId). */
    public static ResponseEntity<Map<String, Object>> response(int status, String code, String message) {
        return withHeader(status, body(status, code, message));
    }

    public static ResponseEntity<Map<String, Object>> withHeader(int status, Map<String, Object> body) {
        return ResponseEntity.status(status).header("X-Request-ID", String.valueOf(body.get("requestId"))).body(body);
    }
}
