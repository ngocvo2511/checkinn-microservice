package com.example.aiagent.model;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

/** Dedicated single-attempt transport. No ambient proxy/auth/cookies, redirects, compression or retry. */
final class GeminiHttpTransport implements GeminiLlmProviderAdapter.Exchange {
    static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("gemini-deadline").factory());
    private final URI testEndpoint;
    private final java.util.function.IntConsumer statusObserver;
    GeminiHttpTransport() { this(status -> { }); }
    GeminiHttpTransport(java.util.function.IntConsumer statusObserver) { testEndpoint = null; this.statusObserver = statusObserver; }
    /** Package-only loopback seam; production endpoint is never externally configurable. */
    GeminiHttpTransport(URI endpoint) {
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getRawQuery() != null || endpoint.getRawUserInfo() != null || endpoint.getRawFragment() != null)
            throw new IllegalArgumentException("Loopback test endpoint required");
        testEndpoint = endpoint; statusObserver = status -> { };
    }
    @Override public SuccessfulHttpResponse execute(HttpRequest source, LlmProviderAdapter.CallControl control, Duration timeout) {
        control.check();
        long nanos = Math.min(timeout.toNanos(), control.remaining().toNanos());
        long millis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos));
        var post = new HttpPost(testEndpoint == null ? URI.create(source.url()) : testEndpoint);
        post.setConfig(RequestConfig.custom().setRedirectsEnabled(false).setAuthenticationEnabled(false)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(millis)).setResponseTimeout(Timeout.ofMilliseconds(millis)).build());
        post.setHeader("Content-Type", "application/json"); post.setHeader("Accept", "application/json");
        post.setHeader("Accept-Encoding", "identity");
        // Header reconstruction prevents accidental JWT/cookie/trace baggage forwarding.
        var key = source.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("x-goog-api-key"))
                .findFirst().orElseThrow(() -> new ProviderException(ProviderException.Category.CONFIGURATION));
        post.setHeader("x-goog-api-key", key.getValue().getFirst());
        post.setEntity(new StringEntity(source.body(), ContentType.APPLICATION_JSON));
        var expired = new AtomicBoolean();
        var timer = DEADLINES.schedule(() -> { expired.set(true); post.cancel(); }, nanos, TimeUnit.NANOSECONDS);
        try (var cancellation = control.onCancel(post::cancel);
             var pool = PoolingHttpClientConnectionManagerBuilder.create().setMaxConnTotal(1).setMaxConnPerRoute(1)
                     .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(millis))
                             .setSocketTimeout(Timeout.ofMilliseconds(millis)).build()).build();
             var client = HttpClients.custom().setConnectionManager(pool).disableAutomaticRetries().disableRedirectHandling()
                     .disableCookieManagement().disableAuthCaching().disableContentCompression().build()) {
            control.check();
            var result = client.execute(post, response -> {
                int status = response.getCode();
                statusObserver.accept(status);
                if (status == 429) { post.cancel(); throw new ProviderException(ProviderException.Category.QUOTA_EXCEEDED); }
                if (status >= 300 && status < 400) { post.cancel(); throw new ProviderException(ProviderException.Category.UNAVAILABLE); }
                var entity = response.getEntity();
                if (entity == null || entity.getContentType() == null
                        || !entity.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")
                        || entity.getContentEncoding() != null && !entity.getContentEncoding().equalsIgnoreCase("identity")
                        || entity.getContentLength() > MAX_RESPONSE_BYTES)
                    { post.cancel(); throw new ProviderException(status == 200 ? ProviderException.Category.MALFORMED_RESPONSE : ProviderException.Category.UNAVAILABLE); }
                try (var body = entity.getContent(); var bytes = new ByteArrayOutputStream()) {
                    byte[] block = new byte[4096]; int count;
                    while ((count = body.read(block, 0, Math.min(block.length, MAX_RESPONSE_BYTES + 1 - bytes.size()))) != -1) {
                        control.check(); bytes.write(block, 0, count);
                        if (bytes.size() > MAX_RESPONSE_BYTES) { post.cancel(); throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE); }
                    }
                    String payload = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
                    if (status != 200) {
                        boolean quota = false;
                        try {
                            var error = GeminiLlmProviderAdapter.JSON.readTree(payload).path("error");
                            quota = java.util.Set.of("RESOURCE_EXHAUSTED", "QUOTA_EXCEEDED").contains(error.path("status").asText())
                                    || error.path("code").asInt() == 429;
                        } catch (Exception ignored) { /* Never retain or expose arbitrary error bodies. */ }
                        post.cancel();
                        throw new ProviderException(quota ? ProviderException.Category.QUOTA_EXCEEDED : ProviderException.Category.UNAVAILABLE);
                    }
                    return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(payload).build();
                }
            });
            control.check();
            if (expired.get()) throw new ProviderException(ProviderException.Category.PROVIDER_TIMEOUT);
            return result;
        } catch (ProviderException typed) { throw typed; }
        catch (Exception ignored) {
            control.check();
            throw new ProviderException(expired.get() || ignored instanceof java.net.SocketTimeoutException
                    ? ProviderException.Category.PROVIDER_TIMEOUT : ProviderException.Category.UNAVAILABLE);
        } finally { timer.cancel(false); post.cancel(); }
    }
}
