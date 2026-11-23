package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import dev.langchain4j.http.client.*;
import dev.langchain4j.http.client.sse.*;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** One real SDK exchange; only identity-bound compile-time fixtures can cross this boundary. */
public final class GeminiLlmProviderAdapter implements LlmProviderAdapter {
    @FunctionalInterface interface Exchange {
        SuccessfulHttpResponse execute(HttpRequest request, CallControl control, Duration timeout);
    }
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(65536).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final AgentProperties agent;
    private final GeminiProperties limits;
    private final MeterRegistry meters;
    private final String apiKey;
    private final Exchange exchange;
    private final GeminiBudget budget;
    private final ThreadLocal<Grant> grant = new ThreadLocal<>();
    private static final class Grant {
        final ChatRequest request;
        boolean consumed;
        Grant(ChatRequest request) { this.request = request; }
    }
    GeminiLlmProviderAdapter(AgentProperties agent, GeminiProperties limits, MeterRegistry meters, String apiKey) {
        this(agent, limits, meters, apiKey, new GeminiHttpTransport(), GeminiBudget.PROCESS);
    }
    /** Test seam is package-owned; never a public endpoint/configurable transport. */
    GeminiLlmProviderAdapter(AgentProperties agent, GeminiProperties limits, MeterRegistry meters, String apiKey,
                             Exchange exchange, GeminiBudget budget) {
        limits.validate(agent);
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > 256 || apiKey.chars().anyMatch(Character::isWhitespace)
                || apiKey.chars().anyMatch(Character::isISOControl)) throw new ProviderException(ProviderException.Category.CONFIGURATION);
        this.agent = agent; this.limits = limits; this.meters = meters; this.apiKey = apiKey;
        this.exchange = Objects.requireNonNull(exchange); this.budget = budget;
    }
    /** Only harness can create a grant; one fixed scenario/request per run, no arbitrary prompt argument. */
    ChatResponse fixture(String id, CallControl control) {
        if (grant.get() != null) throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
        var request = SyntheticGeminiFixtures.request(id);
        grant.set(new Grant(request));
        try { return generate(request, control); } finally { grant.remove(); }
    }
    @Override public ChatResponse generate(ChatRequest request, CallControl control) {
        if (!agent.enabled()) throw new ProviderException(ProviderException.Category.DISABLED);
        limits.validate(agent); control.check();
        var admitted = grant.get();
        if (admitted == null || admitted.request != request || admitted.consumed) {
            meters.counter("agent.gemini.synthetic.denials").increment();
            throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
        }
        admitted.consumed = true;
        if (request.responseFormat() != null && request.toolSpecifications() != null && !request.toolSpecifications().isEmpty())
            throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
        try { budget.reserve(limits); }
        catch (ProviderException denied) { meters.counter("agent.gemini.budget.denials").increment(); throw denied; }
        var sample = io.micrometer.core.instrument.Timer.start(meters);
        var transport = new GuardedSdkTransport(control);
        try {
            // Caller cannot override model/output cap; no listener, tools executor, cached content or built-in tools.
            var model = GoogleAiGeminiChatModel.builder().apiKey(apiKey).modelName(agent.modelId())
                    .maxRetries(0).maxOutputTokens(limits.maxOutputTokens()).timeout(min(agent.modelTimeout(), control.remaining()))
                    .httpClientBuilder(transport).logRequests(false).logResponses(false)
                    .allowCodeExecution(false).allowGoogleSearch(false).allowGoogleMaps(false).allowUrlContext(false)
                    .returnThinking(false).build();
            var response = model.chat(GeminiRequestProtocol.prepare(request));
            control.check();
            if (response == null || response.aiMessage() == null || response.finishReason() == null)
                throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var usage = response.tokenUsage();
            if (usage == null || usage.inputTokenCount() == null || usage.outputTokenCount() == null || usage.totalTokenCount() == null)
                throw new ProviderException(ProviderException.Category.UNKNOWN_USAGE);
            meters.counter("agent.gemini.tokens", "kind", "input").increment(usage.inputTokenCount());
            meters.counter("agent.gemini.tokens", "kind", "output").increment(usage.outputTokenCount());
            return response;
        } catch (RuntimeException failure) {
            ProviderException safe = transport.failure != null ? transport.failure
                    : failure instanceof ProviderException typed ? typed : new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            // Do not retain SDK causes/bodies/URLs; never retry or fallback.
            if (safe.category() == ProviderException.Category.UNKNOWN_USAGE || safe.category() == ProviderException.Category.QUOTA_EXCEEDED
                    || safe.category() == ProviderException.Category.BUDGET_EXCEEDED) budget.stop();
            meters.counter("agent.gemini.errors", "category", safe.category().name()).increment();
            if (safe.category() == ProviderException.Category.UNKNOWN_USAGE) meters.counter("agent.gemini.usage.unknown").increment();
            throw safe;
        } finally { budget.release(); sample.stop(meters.timer("agent.gemini.duration")); }
    }
    private final class GuardedSdkTransport implements HttpClientBuilder, HttpClient {
        private Duration connect, read;
        private final CallControl control;
        private boolean attempted;
        private ProviderException failure;
        GuardedSdkTransport(CallControl control) { this.control = control; }
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest request) {
            try {
                control.check();
                if (attempted || !request.url().equals("https://generativelanguage.googleapis.com/v1beta/models/" + agent.modelId() + ":generateContent")
                        || request.method() != HttpMethod.POST)
                    throw new ProviderException(ProviderException.Category.CONFIGURATION);
                // Conservative ESTIMATE, not usage: 2 tokens/UTF-8 byte + 1024 framing allowance.
                // Fixed text-only fixtures only. CountTokens would be an extra provider request; never called implicitly.
                long estimatedUpperBound = 2L * request.body().getBytes(StandardCharsets.UTF_8).length + 1024;
                if (estimatedUpperBound > limits.maxInputTokens()) {
                    meters.counter("agent.gemini.budget.denials").increment();
                    throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED);
                }
                attempted = true; meters.counter("agent.gemini.requests").increment();
                var result = exchange.execute(request, control, min(agent.modelTimeout(), control.remaining()));
                control.check();
                if (result.statusCode() == 429) throw new ProviderException(ProviderException.Category.QUOTA_EXCEEDED);
                if (result.statusCode() != 200) throw new ProviderException(ProviderException.Category.UNAVAILABLE);
                validateRaw(result.body());
                return result;
            } catch (ProviderException typed) { failure = typed; throw typed; }
            catch (dev.langchain4j.exception.HttpException http) {
                failure = new ProviderException(http.statusCode() == 429 ? ProviderException.Category.QUOTA_EXCEEDED : ProviderException.Category.UNAVAILABLE);
                throw failure;
            }
            catch (RuntimeException ignored) { failure = new ProviderException(ProviderException.Category.MALFORMED_RESPONSE); throw failure; }
        }
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
        }
    }
    private void validateRaw(String raw) {
        try {
            if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > GeminiHttpTransport.MAX_RESPONSE_BYTES)
                throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var root = JSON.readTree(raw);
            if (root == null || !root.isObject()) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            if (root.path("promptFeedback").hasNonNull("blockReason")) throw new ProviderException(ProviderException.Category.SAFETY_REFUSAL);
            var candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.size() != 1) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var candidate = candidates.get(0);
            var reason = candidate.path("finishReason").asText();
            if (java.util.Set.of("SAFETY", "RECITATION", "IMAGE_RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY").contains(reason))
                throw new ProviderException(ProviderException.Category.SAFETY_REFUSAL);
            if (!reason.equals("STOP")) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var parts = candidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty() || parts.size() > 6) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            boolean text = false, tool = false;
            for (var part : parts) {
                if (part.size() != 1) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
                if (part.has("text") && part.get("text").isTextual() && !part.get("text").textValue().isBlank()) text = true;
                else if (part.has("functionCall") && part.path("functionCall").path("name").isTextual()
                        && !part.path("functionCall").path("name").asText().isBlank() && part.path("functionCall").path("args").isObject()) tool = true;
                else throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            }
            if (text && tool) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var usage = root.path("usageMetadata");
            for (String field : new String[]{"promptTokenCount", "candidatesTokenCount", "totalTokenCount"})
                if (!usage.path(field).isIntegralNumber() || !usage.path(field).canConvertToInt() || usage.path(field).intValue() < 0)
                    throw new ProviderException(ProviderException.Category.UNKNOWN_USAGE);
            long input = usage.path("promptTokenCount").longValue(), output = usage.path("candidatesTokenCount").longValue();
            long total = usage.path("totalTokenCount").longValue();
            if (input + output > total || input > limits.maxInputTokens() || total - input > limits.maxOutputTokens()
                    || total > (long) limits.maxInputTokens() + limits.maxOutputTokens())
                throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED);
        } catch (ProviderException typed) { throw typed; }
        catch (Exception ignored) { throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE); }
    }
    private static Duration min(Duration first, Duration second) { return first.compareTo(second) < 0 ? first : second; }
}
