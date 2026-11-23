package com.example.aiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.example.aiagent.tools.ToolError.Category.*;

/** Internal dispatch boundary. Specifications are declarations, never executable model callbacks. */
public final class ToolRegistry implements AutoCloseable {
    public record Definition(ToolId id, String name, String description, JsonNode inputSchema,
                             JsonNode outputSchema, String method, String gatewayPathTemplate,
                             Duration timeout, int maximumResponseBytes, int maximumOutputBytes,
                             String errorMapping, Handler handler) {}
    @FunctionalInterface public interface Handler { ToolResult execute(JsonNode args, ToolExecutionContext context); }
    private final ToolArguments arguments;
    private final Clock clock;
    private final Duration timeout;
    private final boolean enabled;
    private final GatewayReadClient http;
    private final Map<ToolId, JsonNode> outputs;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("agent-read-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    public ToolRegistry(boolean enabled, URI gatewayOrigin, Duration connectTimeout, Duration timeout, Clock clock) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.toMillis() < 1
                || timeout.compareTo(Duration.ofSeconds(10)) > 0) throw new IllegalArgumentException("Invalid tool deadline");
        this.clock = Objects.requireNonNull(clock); this.arguments = new ToolArguments(clock);
        this.enabled = enabled; this.timeout = timeout; this.http = new GatewayReadClient(gatewayOrigin, connectTimeout);
        try (var stream = getClass().getResourceAsStream("/tools/read-output-schemas.json")) {
            JsonNode root = ToolArguments.JSON.readTree(Objects.requireNonNull(stream));
            var schemas = new EnumMap<ToolId, JsonNode>(ToolId.class);
            for (var id : ToolId.values()) schemas.put(id, root.get(id.toolName()));
            outputs = Collections.unmodifiableMap(schemas);
        } catch (Exception ex) { throw new IllegalStateException("Missing projection schemas"); }
    }
    public List<Definition> definitions() {
        return Arrays.stream(ToolId.values()).map(id -> new Definition(id, id.toolName(), id.description(),
                arguments.schema(id), outputs.get(id).deepCopy(), id.method(), id.path(), timeout,
                GatewayReadClient.MAX_BODY_BYTES, 12 * 1024, "agent-read-v1/UpstreamErrors", (a, c) -> execute(id, a, c))).toList();
    }
    /** SDK 1.17 root JsonObjectSchema cannot express anyOf/dependentRequired. Full authoritative schema is metadata;
     * server validator enforces those and cross-field/context rules before any HTTP, independent of provider support. */
    public List<ToolSpecification> specifications() {
        return definitions().stream().map(definition -> {
            var schema = definition.inputSchema(); var parameters = JsonObjectSchema.builder().additionalProperties(false);
            schema.path("properties").fields().forEachRemaining(e -> parameters.addProperty(e.getKey(), JsonRawSchema.from(e.getValue().toString())));
            List<String> required = new ArrayList<>(); schema.path("required").forEach(n -> required.add(n.textValue()));
            return ToolSpecification.builder().name(definition.name()).description(definition.description()
                    + " Arguments must satisfy the server contract; incomplete criteria are rejected.")
                    .parameters(parameters.required(required).build())
                    .metadata(Map.of("draft202012InputSchema", schema.toString(), "projectionVersion", "agent-read-v1")).build();
        }).toList();
    }
    public ToolResult execute(String toolName, String rawArguments, ToolExecutionContext context) {
        ToolId id = null;
        try {
            id = ToolId.named(toolName);
            return execute(id, arguments.parse(rawArguments), context);
        } catch (ToolFailure ex) { return result(id, context, null, null, null, ex.error()); }
        catch (RuntimeException ex) { return result(id, context, null, null, null, ToolError.of(INTERNAL_ADAPTER_FAILURE, null, "INTERNAL_ADAPTER_FAILURE")); }
    }
    /** Same strict validator used by dispatch; policy cannot substitute a weaker parser. */
    public JsonNode validateProposal(ToolId id, String rawArguments, ToolExecutionContext context) {
        return arguments.validate(id, arguments.parse(rawArguments), context).deepCopy();
    }
    public ToolResult execute(ToolId id, JsonNode input, ToolExecutionContext context) {
        String requestId = null; Future<ToolResult> future = null; org.apache.hc.client5.http.classic.methods.HttpGet request = null;
        long deadline = System.nanoTime() + Math.min(timeout.toNanos(), context == null ? timeout.toNanos() : context.remainingNanos());
        try {
            if (id == null) throw new ToolFailure(UNSUPPORTED_TOOL);
            if (!enabled || context == null) throw new ToolFailure(POLICY_DENIED);
            context.checkCancelled();
            var args = arguments.validate(id, input, context);
            if (System.nanoTime() >= deadline) throw new ToolFailure(BACKEND_TIMEOUT);
            requestId = UUID.randomUUID().toString();
            request = http.request(id, args, requestId, timeout);
            if (context.traceparent() != null) request.setHeader("traceparent", context.traceparent());
            var call = request; var callId = requestId;
            future = workers.submit(() -> {
                if (System.nanoTime() >= deadline) throw new ToolFailure(BACKEND_TIMEOUT);
                var response = http.read(call, id, context, callId);
                context.checkCancelled();
                if (response.status() != 200) return result(id, context, callId, response.requestId(), null,
                        UpstreamErrors.map(response.status(), response.body()));
                try {
                    var projected = new ToolProjection().project(id, response.body(), args, context);
                    return result(id, context, callId, response.requestId(), projected, null);
                } catch (ToolFailure failure) {
                    return result(id, context, callId, response.requestId(), null,
                            ToolError.of(failure.error().category(), 200, failure.error().code()));
                }
            });
            while (true) {
                context.checkCancelled();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new ToolFailure(BACKEND_TIMEOUT);
                try {
                    var completed = future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)), TimeUnit.NANOSECONDS);
                    context.checkCancelled();
                    if (System.nanoTime() >= deadline) throw new ToolFailure(BACKEND_TIMEOUT);
                    return completed;
                }
                catch (TimeoutException polling) { /* Full deadline includes response body, parse and projection. */ }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); return result(id, context, requestId, null, null, ToolError.of(CANCELLED, null, "CANCELLED"));
        } catch (ExecutionException ex) {
            var cause = ex.getCause();
            ToolError error = cause instanceof ToolFailure failure ? failure.error()
                    : ToolError.of(cause instanceof java.net.SocketTimeoutException || cause instanceof java.util.concurrent.TimeoutException
                            ? BACKEND_TIMEOUT : cause instanceof java.io.IOException ? BACKEND_UNAVAILABLE : INTERNAL_ADAPTER_FAILURE,
                            null, "ADAPTER_FAILURE");
            return result(id, context, requestId, cause instanceof ToolFailure failure ? failure.upstreamRequestId() : null, null, error);
        } catch (ToolFailure ex) { return result(id, context, requestId, null, null, ex.error()); }
        catch (RejectedExecutionException ex) { return result(id, context, requestId, null, null, ToolError.of(POLICY_DENIED, null, "ADAPTER_BUSY")); }
        catch (RuntimeException ex) { return result(id, context, requestId, null, null, ToolError.of(INTERNAL_ADAPTER_FAILURE, null, "INTERNAL_ADAPTER_FAILURE")); }
        finally {
            if (request != null) request.cancel();
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }
    private ToolResult result(ToolId id, ToolExecutionContext context, String requestId, String upstreamId,
                              ToolProjection.Projected projected, ToolError error) {
        Instant checkedAt = projected == null ? null : projected.checkedAt();
        var evidence = new ToolResult.Evidence(id == null ? null : id.path(), requestId, upstreamId, clock.instant(), checkedAt,
                checkedAt == null ? "NOT_AVAILABLE" : "SERVER", projected != null && projected.truncated());
        return new ToolResult(id, id == null ? null : id.toolName(), "agent-read-v1", context == null ? null : context.criteriaRevision(),
                error == null ? projected.status() : ToolResult.Status.ERROR, projected == null ? null : projected.data(), evidence, error);
    }
    @Override public void close() throws Exception { workers.shutdownNow(); http.close(); }
}
