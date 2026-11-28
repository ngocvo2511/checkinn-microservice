package com.example.aiagent.model;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.ProviderException.Category.*;

/** Real Apache sockets to synthetic loopback only. Not live Google/TLS cancellation acceptance. */
@SuppressWarnings("removal")
class GeminiTransportTest {
    static SecurityManager previous;
    static final AtomicInteger external = new AtomicInteger();
    @BeforeAll static void loopbackOnly() {
        previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) {
                if (!host.equals("127.0.0.1")) { external.incrementAndGet(); throw new SecurityException("External connections denied"); }
            }
        });
    }
    @AfterAll static void restore() { System.setSecurityManager(previous); assertThat(external).hasValue(0); }
    static HttpRequest request() {
        return HttpRequest.builder().method(HttpMethod.POST)
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-offline-fixture:generateContent")
                .addHeader("x-goog-api-key", "synthetic-noncredential").addHeader("Authorization", "synthetic-do-not-forward")
                .addHeader("Cookie", "synthetic-do-not-forward").addHeader("X-User-Id", "synthetic-do-not-forward")
                .body("{\"syntheticFixture\":\"fixed\"}").build();
    }
    @ParameterizedTest @ValueSource(ints = {200, 302, 307, 429, 500, 503})
    void oneActualHttpAttemptWithNoRedirectRetryOrCredentialForwarding(int status) throws Exception {
        var calls = new AtomicInteger(); var captured = new AtomicReference<com.sun.net.httpserver.Headers>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); captured.set(exchange.getRequestHeaders()); exchange.getRequestBody().readAllBytes();
            var body = "{\"synthetic\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirect-forbidden");
            exchange.sendResponseHeaders(status, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            Runnable call = () -> transport.execute(request(), new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)), Duration.ofSeconds(1));
            if (status == 200) call.run(); else GeminiProviderTest.error(call, status == 429 ? QUOTA_EXCEEDED : UNAVAILABLE);
            assertThat(calls).hasValue(1);
            assertThat(transport.observedStatus()).isEqualTo(status);
            assertThat(captured.get().getFirst("x-goog-api-key")).isEqualTo("synthetic-noncredential");
            for (String forbidden : List.of("Authorization", "Cookie", "X-User-Id", "traceparent")) assertThat(captured.get().getFirst(forbidden)).isNull();
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellationClosesUnderlyingSocketWhileWaitingForHeadersOrBody(boolean bodyStarted) throws Exception {
        socketAbort(bodyStarted, true);
    }
    @ParameterizedTest @ValueSource(ints = {400,401,403,429,500,502,503,504})
    void localErrorsCaptureSanitizedEvidenceWithoutRetries(int status) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":{\"message\":\"synthetic-noncredential\",\"code\":503}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.getResponseHeaders().set("Retry-After","17");
            exchange.sendResponseHeaders(status,body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).localDiagnostics();
            assertThatThrownBy(() -> transport.execute(request(),new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)),Duration.ofSeconds(1)))
                    .isInstanceOf(ProviderException.class);
            assertThat(transport.observedStatus()).isEqualTo(status);
            assertThat(transport.errorBody()).contains("REDACTED","503").doesNotContain("synthetic-noncredential");
            assertThat(transport.retryAfter()).isEqualTo("17"); assertThat(transport.terminated()).isTrue();
            assertThat(calls).hasValue(1);
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void providerTimeoutClosesUnderlyingSocketWhileWaitingForHeadersOrBody(boolean bodyStarted) throws Exception {
        socketAbort(bodyStarted, false);
    }
    private void socketAbort(boolean bodyStarted, boolean cancel) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newFixedThreadPool(2)) {
            server.setSoTimeout(3000);
            var entered = new CountDownLatch(1); var closed = new CountDownLatch(1);
            var observer = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    int length = 0; String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty())
                        if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                    for (int i = 0; i < length; i++) assertThat(reader.read()).isNotEqualTo(-1);
                    if (bodyStarted) {
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 9999\r\n\r\n{".getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().flush();
                    }
                    entered.countDown();
                    try { assertThat(reader.read()).isEqualTo(-1); }
                    catch (SocketException reset) { /* Reset is also an observed transport abort. */ }
                    closed.countDown();
                } catch (IOException ex) { throw new UncheckedIOException(ex); }
            });
            var control = new LlmProviderAdapter.CallControl(Duration.ofSeconds(3));
            var transport = new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/"));
            long start = System.nanoTime();
            var request = executor.submit(() -> GeminiProviderTest.error(() -> transport.execute(request(), control,
                    cancel ? Duration.ofSeconds(2) : Duration.ofMillis(400)), cancel ? CANCELLED : PROVIDER_TIMEOUT));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            if (cancel) control.cancel();
            request.get(2, TimeUnit.SECONDS);
            assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue(); observer.get(1, TimeUnit.SECONDS);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        }
    }
    @ParameterizedTest @ValueSource(strings = {"oversize", "compressed", "html"})
    void responseBoundsRejectBeforeSdkParsing(String kind) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var bytes = (kind.equals("oversize") ? "x".repeat(65537) : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", kind.equals("html") ? "text/html" : "application/json");
            if (kind.equals("compressed")) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } catch (IOException expectedAbort) { } finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            GeminiProviderTest.error(() -> transport.execute(request(), new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)), Duration.ofSeconds(1)), MALFORMED_RESPONSE);
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(strings = {"RESOURCE_EXHAUSTED", "QUOTA_EXCEEDED"})
    void resourceExhaustedBodyIsTypedAndSanitizedEvenWhenStatusIsNot429(String code) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var bytes = ("{\"error\":{\"status\":\"" + code + "\",\"message\":\"synthetic-error-secret\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(403, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        }); server.start();
        try {
            var transport = new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            GeminiProviderTest.error(() -> transport.execute(request(), new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)), Duration.ofSeconds(1)), QUOTA_EXCEEDED);
        } finally { server.stop(0); }
    }
    @Test void actualSdkAdapterAndProductionSocketTransportIntegrateWithSyntheticLoopback() throws Exception {
        var calls = new AtomicInteger(); var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); var sent = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sent).contains("responseJsonSchema", "maxOutputTokens");
            var bytes = GeminiProviderTest.text().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        }); server.start();
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        try {
            var adapter = new GeminiLlmProviderAdapter(GeminiProviderTest.agent(), GeminiProviderTest.limits(), meters,
                    "synthetic-noncredential", new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")), new GeminiBudget());
            var result = adapter.fixture(SyntheticGeminiFixtures.SMOKE, new LlmProviderAdapter.CallControl(Duration.ofSeconds(2)));
            assertThat(result.aiMessage().text()).isEqualTo("{\"evidenceIds\":[]}"); assertThat(calls).hasValue(1);
        } finally { server.stop(0); meters.close(); }
    }
    @Test void firstCallSendsAdaptedSdkWireThroughApacheOnceToSyntheticLoopback() throws Exception {
        var calls = new AtomicInteger(); var body = new AtomicReference<String>(); var key = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            var bytes = GeminiProviderTest.text().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        }); server.start();
        try {
            var result = GeminiSyntheticSmoke.runFixed(GeminiFirstCallTest.agent(GeminiSmokeApproval.MODEL), GeminiFirstCallTest.limits(),
                    GeminiFirstCallTest.approvals(), "synthetic-noncredential",
                    new GeminiHttpTransport(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")),
                    new GeminiBudget(), new GeminiSmokeDiagnostics());
            assertThat(result.category()).isEqualTo("SUCCESS"); assertThat(calls).hasValue(1);
            var sent = GeminiLlmProviderAdapter.JSON.readTree(body.get());
            assertThat(sent.path("generationConfig").has("candidateCount")).isFalse();
            assertThat(sent.path("generationConfig").path("maxOutputTokens").intValue()).isEqualTo(128);
            assertThat(sent.path("generationConfig").path("responseJsonSchema").path("additionalProperties").booleanValue()).isFalse();
            assertThat(sent.has("tools")).isFalse(); assertThat(body.get()).doesNotContain("synthetic-noncredential");
            assertThat(key.get()).isEqualTo("synthetic-noncredential");
        } finally { server.stop(0); }
    }
}
