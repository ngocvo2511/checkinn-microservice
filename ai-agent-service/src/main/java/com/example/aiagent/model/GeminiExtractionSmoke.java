package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.http.client.*;
import dev.langchain4j.http.client.sse.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.ProviderException.Category.*;

/** INTERNAL_TEST_ONLY until a trusted operator issuer and external-verification integration are reviewed.
 * No public constructor, main, bean, environment lookup, default HTTP transport or domain registry. */
final class GeminiExtractionSmoke {
    record Outcome(String category, String schemaResult, String semanticResult, boolean backendAccepted) { }
    private GeminiExtractionSmoke() { }
    static Outcome run(GeminiExtractionSession session, GeminiExtractionSession.Capability permit, int attempt,
                       LlmProviderAdapter.CallControl control, Supplier<String> keySource,
                       GeminiLlmProviderAdapter.Exchange exchange, GeminiProperties limits, GeminiBudget process, Clock clock) {
        if (session == null) throw denied();
        if (limits == null || !limits.validBudgets() || !MODEL.equals(limits.reviewedModelId())
                || limits.maxInputTokens() < session.caps().inputPerCall() || limits.maxOutputTokens() != OUTPUT_CAP
                || limits.maxRunRequests() != session.caps().requests() || limits.maxInputTokens() != session.caps().inputPerCall()
                || limits.maxProcessRequests() != session.caps().processRequests()
                || limits.maxExposureUsd().compareTo(session.caps().exposureUsd()) != 0
                || limits.inputUsdPerMillionUpperBound().compareTo(session.caps().inputRateUpperBound()) != 0
                || limits.outputUsdPerMillionUpperBound().compareTo(session.caps().outputRateUpperBound()) != 0) throw denied();
        String expected = session.reserve(permit, attempt, control); // No key lookup or transport before durable reservation.
        long start = System.nanoTime(); boolean processReserved = false;
        String schema = "NOT_RUN", semantic = "NOT_RUN", category = "SUCCESS";
        boolean accepted = false;
        var wire = new Wire(expected, control, session, exchange);
        try {
            process.reserve(limits); processReserved = true;
            session.check(); control.check();
            String key = keySource.get();
            if (key == null || key.isBlank() || key.length() > 256 || key.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
                throw new ProviderException(CONFIGURATION);
            wire.key = key;
            var response = model(key, wire, min(control.remaining(), session.remaining()))
                    .chat(request(session.fixtureId(permit)));
            control.check(); session.check();
            if (response == null || response.aiMessage() == null || response.aiMessage().hasToolExecutionRequests())
                throw new ProviderException(MALFORMED_RESPONSE);
            VietnameseExtraction.Proposal proposal;
            try { proposal = VietnameseExtraction.parse(response.aiMessage().text(), clock); }
            catch (IllegalArgumentException failure) { schema = "FAIL"; throw new ProviderException(MALFORMED_RESPONSE); }
            schema = "PASS";
            String id = session.fixtureId(permit), utterance = utterance(id);
            try {
                String literal = VietnameseExtraction.literalUtterance(utterance);
                boolean comparing = ComparisonRouting.candidate(literal);
                var selected = ReadIntentRouting.selectedIntent(literal);
                var action = proposal.action();
                if (action != VietnameseExtraction.Action.CLARIFY && (comparing ? action != VietnameseExtraction.Action.COMPARISON
                        : selected != null ? action != VietnameseExtraction.Action.SELECTED : action != VietnameseExtraction.Action.CRITERIA))
                    throw new IllegalArgumentException();
                var explicit = comparing || selected != null
                        ? new ExtractionProposal(ExtractionProposal.Intent.CLARIFY, Map.of(), null)
                        : new VietnameseParser().extract(literal, clock);
                VietnameseExtraction.corroborate(proposal, explicit, utterance);
                accepted = true;
            } catch (IllegalArgumentException failure) { category = "BACKEND_REJECTED"; }
            semantic = semanticMatch(proposal, fixture(id)) ? "PASS" : "FAIL";
            if (accepted && semantic.equals("FAIL")) category = "MODEL_SEMANTIC_MISMATCH";
            control.check(); session.check();
        } catch (RuntimeException failure) {
            var safe = wire.failure != null ? wire.failure : failure instanceof ProviderException typed ? typed : new ProviderException(UNAVAILABLE);
            category = safe.category().name(); accepted = false;
            if (safe.category() == UNKNOWN_USAGE || safe.category() == BUDGET_EXCEEDED || safe.category() == QUOTA_EXCEEDED) process.stop();
        } finally {
            if (processReserved) process.release();
            session.complete(wire.http, wire.input, wire.output, wire.total, schema, semantic, category,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
        return new Outcome(category, schema, semantic, accepted);
    }
    private static boolean semanticMatch(VietnameseExtraction.Proposal proposal, JsonNode expected) {
        if (!proposal.action().name().equals(expected.path("expectedAction").asText())
                || !proposal.criteria().intent().name().equals(expected.path("expectedIntent").asText())
                || !proposal.reference().equals(expected.path("reference").asText()) || !proposal.inferredFields().isEmpty()) return false;
        var values = GeminiLlmProviderAdapter.JSON.createObjectNode();
        for (var change : proposal.criteria().fields().entrySet()) {
            if (change.getValue().operation() != ExtractionProposal.Operation.REPLACE) return false;
            values.set(change.getKey(), change.getValue().value());
        }
        var missing = new HashSet<String>(); expected.path("expectedMissingSlots").forEach(item -> missing.add(item.asText()));
        return values.equals(expected.path("expectedCriteria")) && new HashSet<>(proposal.missingSlots()).equals(missing);
    }
    private static Duration min(Duration a, Duration b) { return a.compareTo(b) < 0 ? a : b; }
    private static final class Wire implements HttpClientBuilder, HttpClient {
        final String expected;
        final LlmProviderAdapter.CallControl control;
        final GeminiExtractionSession session;
        final GeminiLlmProviderAdapter.Exchange exchange;
        Duration connect, read; String key, http = "NOT_RUN";
        Integer input, output, total;
        boolean attempted; ProviderException failure;
        Wire(String expected, LlmProviderAdapter.CallControl control, GeminiExtractionSession session, GeminiLlmProviderAdapter.Exchange exchange) {
            this.expected = expected; this.control = control; this.session = session; this.exchange = exchange;
        }
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest source) {
            try {
                control.check(); session.check();
                String body = admit(source, expected).body();
                if (attempted || !body.equals(expected)) throw denied();
                if (bytes(body) > BYTE_CAP || 2L * bytes(body) + 1024 > session.caps().inputPerCall()) throw new ProviderException(BUDGET_EXCEEDED);
                // Reconstruct headers; incoming headers, including any unexpected credentials, are never forwarded.
                var outbound = HttpRequest.builder().url(ENDPOINT).method(HttpMethod.POST).body(body)
                        .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                        .addHeader("x-goog-api-key", key).build();
                attempted = true;
                var result = exchange.execute(outbound, control, min(control.remaining(), session.remaining()));
                control.check(); session.check();
                if (result == null) throw new ProviderException(MALFORMED_RESPONSE);
                http = result.statusCode() / 100 + "xx";
                if (result.statusCode() == 429) throw new ProviderException(QUOTA_EXCEEDED);
                if (result.statusCode() != 200) throw new ProviderException(UNAVAILABLE);
                validate(result.body()); return result;
            } catch (ProviderException safe) { failure = safe; throw safe; }
            catch (dev.langchain4j.exception.HttpException status) {
                http = status.statusCode() / 100 + "xx";
                failure = new ProviderException(status.statusCode() == 429 ? QUOTA_EXCEEDED : UNAVAILABLE); throw failure;
            } catch (RuntimeException ignored) { failure = new ProviderException(MALFORMED_RESPONSE); throw failure; }
        }
        private void validate(String raw) {
            try {
                if (raw == null || bytes(raw) > GeminiHttpTransport.MAX_RESPONSE_BYTES) throw new ProviderException(MALFORMED_RESPONSE);
                var root = GeminiLlmProviderAdapter.JSON.readTree(raw);
                if (!Set.of("candidates", "usageMetadata", "promptFeedback", "modelVersion", "responseId").containsAll(fields(root)))
                    throw new ProviderException(MALFORMED_RESPONSE);
                for (var field : List.of("modelVersion", "responseId")) if (root.has(field)
                        && (!root.get(field).isTextual() || root.get(field).asText().length() > 256)) throw new ProviderException(MALFORMED_RESPONSE);
                if (root.path("promptFeedback").hasNonNull("blockReason")) throw new ProviderException(SAFETY_REFUSAL);
                var candidates = root.path("candidates");
                if (!candidates.isArray() || candidates.size() != 1) throw new ProviderException(MALFORMED_RESPONSE);
                var c = candidates.get(0);
                if (!Set.of("content", "finishReason", "index", "safetyRatings").containsAll(fields(c))) throw new ProviderException(MALFORMED_RESPONSE);
                String reason = c.path("finishReason").asText();
                if (Set.of("SAFETY", "RECITATION", "IMAGE_RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY").contains(reason))
                    throw new ProviderException(SAFETY_REFUSAL);
                if (!reason.equals("STOP")) throw new ProviderException(MALFORMED_RESPONSE);
                var content = c.path("content");
                if (!fields(content).equals(Set.of("role", "parts")) || !content.path("role").asText().equals("model")) throw new ProviderException(MALFORMED_RESPONSE);
                var parts = content.path("parts");
                if (!parts.isArray() || parts.size() != 1) throw new ProviderException(MALFORMED_RESPONSE);
                var part = parts.get(0);
                if (part.has("functionCall")) throw new ProviderException(UNSUPPORTED_PROTOCOL);
                if (!Set.of("text", "thoughtSignature", "thought").containsAll(fields(part)) || !part.path("text").isTextual()
                        || part.path("text").asText().isBlank() || bytes(part.path("text").asText()) > 8192
                        || part.has("thought") && (!part.get("thought").isBoolean() || part.get("thought").asBoolean())
                        || part.has("thoughtSignature") && (!part.get("thoughtSignature").isTextual()
                        || part.get("thoughtSignature").asText().isBlank() || part.get("thoughtSignature").asText().length() > 16384))
                    throw new ProviderException(MALFORMED_RESPONSE);
                var usage = root.path("usageMetadata");
                for (var field : List.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount"))
                    if (!usage.path(field).isIntegralNumber() || !usage.path(field).canConvertToInt() || usage.path(field).intValue() < 0)
                        throw new ProviderException(UNKNOWN_USAGE);
                if (!Set.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount", "thoughtsTokenCount", "cachedContentTokenCount")
                        .containsAll(fields(usage))) throw new ProviderException(MALFORMED_RESPONSE);
                for (var field : List.of("thoughtsTokenCount", "cachedContentTokenCount")) if (usage.has(field)
                        && (!usage.get(field).isIntegralNumber() || !usage.get(field).canConvertToInt() || usage.get(field).intValue() < 0))
                    throw new ProviderException(UNKNOWN_USAGE);
                int i = usage.path("promptTokenCount").intValue(), o = usage.path("candidatesTokenCount").intValue(), t = usage.path("totalTokenCount").intValue();
                if ((long) i + o > t || i > session.caps().inputPerCall() || (long) t - i > OUTPUT_CAP || t > INPUT_CAP + OUTPUT_CAP)
                    throw new ProviderException(BUDGET_EXCEEDED);
                input = i; output = o; total = t;
            } catch (ProviderException safe) {
                if (safe.category() == SYNTHETIC_BOUNDARY) throw new ProviderException(MALFORMED_RESPONSE);
                throw safe;
            }
            catch (RuntimeException ignored) { throw new ProviderException(MALFORMED_RESPONSE); }
            catch (Exception ignored) { throw new ProviderException(MALFORMED_RESPONSE); }
        }
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) { throw new ProviderException(UNSUPPORTED_PROTOCOL); }
    }
}
