package com.example.aiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.*;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

/** Dedicated anonymous transport: no inherited proxy, cookie store, credentials, retries or redirects. */
final class GatewayReadClient implements AutoCloseable {
    static final int MAX_BODY_BYTES = 256 * 1024;
    private final URI origin;
    private final CloseableHttpClient client;
    GatewayReadClient(URI origin, Duration connectTimeout) {
        if (origin == null || !Set.of("http", "https").contains(origin.getScheme()) || origin.getHost() == null
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/"))
                || connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()
                || connectTimeout.toMillis() < 1
                || connectTimeout.compareTo(Duration.ofSeconds(3)) > 0)
            throw new IllegalArgumentException("Invalid trusted Gateway origin/connect timeout");
        this.origin = origin;
        var pool = PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(2).setMaxConnPerRoute(2)
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setSocketTimeout(Timeout.ofSeconds(10)).build()).build();
        client = HttpClients.custom().setConnectionManager(pool).disableAutomaticRetries()
                .disableRedirectHandling().disableCookieManagement().disableAuthCaching()
                .disableContentCompression().build();
    }
    HttpGet request(ToolId tool, JsonNode args, String requestId, Duration timeout) {
        String path = tool.path(); List<String> query = new ArrayList<>();
        var fields = args.fields();
        while (fields.hasNext()) {
            var entry = fields.next(); String key = entry.getKey(); var value = entry.getValue();
            String token = "{" + key + "}";
            if (path.contains(token)) path = path.replace(token, value.textValue()); // already UUID validated
            else if (value.isArray()) for (var item : value) query.add(encode(key) + "=" + encode(item.asText()));
            else query.add(encode(key) + "=" + encode(value.asText()));
        }
        if (tool == ToolId.A01) {
            if (!args.has("page")) query.add("page=0");
            if (!args.has("size")) query.add("size=5");
            if (!args.has("sort")) query.add("sort=DEFAULT");
        }
        URI uri = origin.resolve(path + (query.isEmpty() ? "" : "?" + String.join("&", query)));
        HttpGet request = new HttpGet(uri);
        request.setConfig(RequestConfig.custom().setRedirectsEnabled(false)
                .setAuthenticationEnabled(false).setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build());
        request.setHeader("Accept", "application/json");
        request.setHeader("Accept-Encoding", "identity");
        request.setHeader("X-Request-ID", requestId);
        return request;
    }
    record Response(int status, JsonNode body, String requestId) {}
    Response read(HttpGet request, ToolId tool, ToolExecutionContext context, String requestId) throws IOException {
        context.beforeAttempt(tool, requestId); // Immediately before the single execute, failed attempts included.
        return client.execute(request, response -> {
            int status = response.getCode();
            String upstreamId = safeId(response.getFirstHeader("X-Request-ID") == null ? null
                    : response.getFirstHeader("X-Request-ID").getValue());
            var entity = response.getEntity();
            if (entity == null) return new Response(status, null, upstreamId);
            String contentType = entity.getContentType();
            String encoding = entity.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity"))
                throw abort(request, ToolError.Category.INVALID_UPSTREAM_RESPONSE, status, upstreamId);
            if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                request.cancel();
                if (status != 200) return new Response(status, null, upstreamId);
                throw abort(request, ToolError.Category.INVALID_UPSTREAM_RESPONSE, status, upstreamId);
            }
            if (entity.getContentLength() > MAX_BODY_BYTES)
                throw abort(request, ToolError.Category.OVERSIZED_UPSTREAM_RESPONSE, status, upstreamId);
            try (InputStream body = entity.getContent(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] block = new byte[4096]; int count;
                while ((count = body.read(block, 0, Math.min(block.length, MAX_BODY_BYTES + 1 - buffer.size()))) != -1) {
                    context.checkCancelled(); buffer.write(block, 0, count);
                    if (buffer.size() > MAX_BODY_BYTES)
                        throw abort(request, ToolError.Category.OVERSIZED_UPSTREAM_RESPONSE, status, upstreamId);
                }
                JsonNode json;
                try { json = ToolArguments.JSON.readTree(buffer.toByteArray()); }
                catch (Exception ex) {
                    if (status != 200) return new Response(status, null, upstreamId);
                    throw abort(request, ToolError.Category.INVALID_UPSTREAM_JSON, status, upstreamId);
                }
                return new Response(status, json, upstreamId);
            }
        });
    }
    private ToolFailure abort(HttpGet request, ToolError.Category category, int status, String id) {
        request.cancel(); // Do not drain an oversized/stalled entity while closing its stream.
        return new ToolFailure(ToolError.of(category, status, category.name()), id);
    }
    private static String encode(String text) { return URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8); }
    static String safeId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,64}") ? id : null; }
    @Override public void close() throws IOException { client.close(); }
}
