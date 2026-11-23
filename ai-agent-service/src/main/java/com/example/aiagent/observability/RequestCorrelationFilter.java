package com.example.aiagent.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private final Tracer tracer;
    public RequestCorrelationFilter(Tracer tracer) { this.tracer = tracer; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        var span = tracer.currentSpan();
        String id = span == null ? UUID.randomUUID().toString() : span.context().traceId();
        response.setHeader("X-Request-ID", id);
        response.setHeader("Cache-Control", "no-store");
        // No access log of URI/query/headers/body. W3C parsing belongs to the tracing bridge.
        chain.doFilter(request, response);
    }
}
