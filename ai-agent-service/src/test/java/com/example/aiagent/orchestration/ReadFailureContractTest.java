package com.example.aiagent.orchestration;

import com.example.aiagent.tools.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.orchestration.OrchestrationTest.*;

/** Execute original fault fixtures directly through the registry when historical NLU cannot authorize them. */
class ReadFailureContractTest {
    @ParameterizedTest @ValueSource(strings = {"TIMEOUT_HEADERS", "TIMEOUT_BODY", "OVERSIZE", "NO_PUBLIC_ROOMS", "UNAVAILABLE", "INJECTION"})
    void originalOfflineFixturesNeverBecomeInventedFacts(String name) throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
        var fixture = JSON.readTree(getClass().getResourceAsStream("/nlu/historical-10a.json")).path("fixtures").path(name);
        var executor = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (name.equals("TIMEOUT_HEADERS")) Thread.sleep(11000);
                byte[] bytes = fixture.path("body").toString().getBytes(StandardCharsets.UTF_8);
                if (name.equals("OVERSIZE")) {
                    var buffer = new java.io.ByteArrayOutputStream();
                    try (var gzip = new java.util.zip.GZIPOutputStream(buffer)) {
                        gzip.write((" ".repeat(262143) + "{}").getBytes(StandardCharsets.UTF_8));
                    }
                    bytes = buffer.toByteArray(); exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                }
                if (name.equals("TIMEOUT_BODY")) {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('{'); exchange.getResponseBody().flush(); Thread.sleep(11000);
                } else { exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException cancelled) { /* caller closes the bounded request */ }
            finally { exchange.close(); }
        }); server.start();
        try (var registry = new ToolRegistry(true, URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofMillis(500), Duration.ofMillis(500), clock)) {
            ToolId tool = name.equals("NO_PUBLIC_ROOMS") ? ToolId.A03 : name.equals("UNAVAILABLE") ? ToolId.A05 : name.equals("INJECTION") ? ToolId.A02 : ToolId.A01;
            var args = JSON.readTree(tool == ToolId.A01 ? searchArgs() : tool == ToolId.A05
                    ? "{\"roomTypeId\":\"" + R + "\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"quantity\":1}"
                    : "{\"hotelId\":\"" + H + "\"}");
            var context = new ToolExecutionContext(1L, 1, 2, 0, Map.of(R, H), Set.of(), Set.of());
            var result = registry.execute(tool, args, context);
            assertThat(context.attempts()).isEqualTo(1);
            var ledger = new EvidenceLedger(List.of(), 1, "turn", clock);
            var added = ledger.add(result, args);
            if (name.startsWith("TIMEOUT")) {
                assertThat(added).isEmpty(); assertThat(result.data()).isNull();
                assertThat(ResponseSemantics.classify(result.error())).isEqualTo(ResponseSemantics.ErrorCode.TOOL_TIMEOUT);
            } else if (name.equals("OVERSIZE")) {
                // Compression is prohibited by the existing transport; never enable decompression to satisfy an old fixture.
                assertThat(added).isEmpty(); assertThat(result.data()).isNull();
                assertThat(ResponseSemantics.classify(result.error())).isEqualTo(ResponseSemantics.ErrorCode.INVALID_RESPONSE);
            } else {
                assertThat(added).isPresent();
                if (name.equals("UNAVAILABLE")) { assertThat(result.data().path("available").asBoolean()).isFalse(); assertThat(result.data().path("availableRooms").asInt()).isZero(); }
                if (name.equals("NO_PUBLIC_ROOMS")) assertThat(result.status()).isEqualTo(ToolResult.Status.EMPTY);
                if (name.equals("INJECTION")) {
                    var answer = new GroundingGate().validate("{\"evidenceIds\":[\"" + added.orElseThrow().id() + "\"]}", ledger,
                            SearchCriteriaState.empty().update(full(), clock), scope(PolicyExecutor.Intent.DETAIL));
                    assertThat(answer.category()).isEqualTo("GROUNDED");
                    assertThat(answer.toString()).doesNotContain("API_KEY_SENTINEL", "POST", "description");
                }
            }
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
