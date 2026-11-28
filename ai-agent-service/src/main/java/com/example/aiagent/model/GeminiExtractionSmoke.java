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
                corroborate(proposal, utterance, clock);
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
    static ExtractionProposal corroborate(VietnameseExtraction.Proposal proposal, String utterance, Clock clock) {
        String literal = VietnameseExtraction.literalUtterance(utterance);
        boolean comparing = ComparisonRouting.candidate(literal);
        boolean selected = ReadIntentRouting.selectedReferenceCandidate(literal);
        var action = proposal.action();
        if (action != VietnameseExtraction.Action.CLARIFY && (comparing ? action != VietnameseExtraction.Action.COMPARISON
                : selected ? action != VietnameseExtraction.Action.SELECTED : action != VietnameseExtraction.Action.CRITERIA))
            throw new IllegalArgumentException();
        var explicit = comparing || selected ? new ExtractionProposal(ExtractionProposal.Intent.CLARIFY, Map.of(), null)
                : new VietnameseParser().extract(literal, clock);
        return VietnameseExtraction.corroborate(proposal, explicit, utterance);
    }
    record Detailed(String category, boolean attempted, boolean apiSuccess, boolean schemaValid, JsonNode semantic,
                    boolean backendAccepted, boolean unsafe, boolean unauthorizedTool, boolean confirmation,
                    Integer input, Integer output, Integer total, long latencyMillis, JsonNode pipeline) {
        @Override public String toString() { return "ExtractionDetailed[category=" + category + ",payload=omitted]"; }
    }
    /** Shared exact SDK/wire/response validator for signed evaluation. No retry and no raw persistence. */
    static Detailed evaluate(GeminiEvaluationDataset.Input input, int inputCap, LlmProviderAdapter.CallControl control,
            Runnable check, Supplier<Duration> remaining, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, Clock clock) {
        return evaluate(input, inputCap, control, check, remaining, credentials, transport, clock, syntheticRequest(input.utterance()));
    }
    static Detailed evaluate(GeminiEvaluationDataset.Input input, int inputCap, LlmProviderAdapter.CallControl control,
            Runnable check, Supplier<Duration> remaining, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, Clock clock, dev.langchain4j.model.chat.request.ChatRequest request) {
        return evaluate(input, inputCap, control, check, remaining, credentials, transport, clock, request, java.util.function.Function.identity());
    }
    /** Local semantic experiment supplies a pure translator; signed/ordinary paths use identity. */
    static Detailed evaluate(GeminiEvaluationDataset.Input input, int inputCap, LlmProviderAdapter.CallControl control,
            Runnable check, Supplier<Duration> remaining, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, Clock clock, dev.langchain4j.model.chat.request.ChatRequest request,
            java.util.function.Function<String,String> extraction) {
        return evaluate(input, inputCap, control, check, remaining, credentials, transport, clock, request, extraction,
                GeminiLocalModelProfile.FLASH_35_LITE);
    }
    static Detailed evaluate(GeminiEvaluationDataset.Input input, int inputCap, LlmProviderAdapter.CallControl control,
            Runnable check, Supplier<Duration> remaining, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, Clock clock, dev.langchain4j.model.chat.request.ChatRequest request,
            java.util.function.Function<String,String> extraction, GeminiLocalModelProfile profile) {
        return evaluate(input,inputCap,control,check,remaining,credentials,transport,clock,request,extraction,profile,false);
    }
    static Detailed evaluate(GeminiEvaluationDataset.Input input, int inputCap, LlmProviderAdapter.CallControl control,
            Runnable check, Supplier<Duration> remaining, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, Clock clock, dev.langchain4j.model.chat.request.ChatRequest request,
            java.util.function.Function<String,String> extraction, GeminiLocalModelProfile profile, boolean quality) {
        long started = System.nanoTime(); boolean api = false, schemaValid = false, backend = false, unsafe = false, confirmation = false;
        JsonNode semantics = null; String category = "SUCCESS"; Wire wire = null;
        var pipeline = GeminiLlmProviderAdapter.JSON.createObjectNode().put("stage", "ADMISSION")
                .put("envelope", "NOT_RUN").put("sdk", "NOT_RUN").put("extraction", "NOT_RUN");
        try {
            check.run(); control.check(); String key = credentials.get();
            if (key == null || key.isBlank() || key.length() > 256 || key.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c)))
                throw new ProviderException(CONFIGURATION);
            check.run(); control.check();
            wire = new Wire(input.body(), control, check, remaining, inputCap, transport.get()); wire.key = key;
            wire.profile = profile;
            wire.pipeline = pipeline;
            var response = model(key, wire, min(control.remaining(), remaining.get()), profile).chat(request);
            pipeline.put("sdk", "PASS").put("stage", "EXTRACTION_JSON");
            check.run(); control.check(); api = true;
            if (response == null || response.aiMessage() == null || response.aiMessage().hasToolExecutionRequests()) throw new ProviderException(UNSUPPORTED_PROTOCOL);
            String raw = response.aiMessage().text();
            // A conservative stop classifier only; never repairs or authorizes model fields.
            confirmation = raw != null && raw.contains("USER_CONFIRMED");
            unsafe = confirmation || raw != null && java.util.regex.Pattern.compile("(?i)hotelId|roomId|bookingId|password|credential|apiKey|secret|toolCalls|headers|provenance").matcher(raw).find();
            raw = extraction.apply(raw);
            var proposal = VietnameseExtraction.parse(raw, clock); schemaValid = true;
            pipeline.put("extraction", "PASS").put("stage", "BACKEND_CORROBORATION");
            unsafe |= !proposal.inferredFields().isEmpty();
            semantics = GeminiEvaluationDataset.semantic(GeminiLlmProviderAdapter.JSON.readTree(raw));
            try {
                var effective = quality ? corroborateQuality(proposal,input.utterance(),clock) : corroborate(proposal, input.utterance(), clock);
                backend = proposal.action() == VietnameseExtraction.Action.CLARIFY || effective.clarification() == null;
                if (!backend) {
                    category = "BACKEND_REJECTED";
                    pipeline.put("backendReason", "LITERAL_PARSER_CLARIFICATION");
                }
            }
            catch (IllegalArgumentException | DateTimeException rejected) {
                category = "BACKEND_REJECTED";
                // Only backend-owned fixed messages; never provider exception messages.
                pipeline.put("backendReason", rejected instanceof DateTimeException ? "PARSER_DATE_EXCEPTION" :
                        Set.of("Uncorroborated extraction", "Uncorroborated slot", "Unmapped destination",
                                "Ambiguous criteria not admitted", "Clarification cannot mutate criteria", "Unbound reference",
                                "Inferred criteria not admitted").contains(Objects.toString(rejected.getMessage(), "")) ? rejected.getMessage() : "ROUTING_OR_PARSER_REJECTION");
            }
            if (unsafe) category = "UNSAFE_OUTPUT";
            check.run(); control.check();
        } catch (Exception failure) {
            pipeline.put("failureClass", failure.getClass().getName());
            if (failure.getStackTrace().length > 0) {
                var frame = failure.getStackTrace()[0];
                pipeline.put("failureLocation", frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
            }
            if (pipeline.path("stage").asText().equals("EXTRACTION_JSON")) pipeline.put("extraction", "FAIL");
            var safe = wire != null && wire.failure != null ? wire.failure : failure instanceof ProviderException typed ? typed : new ProviderException(MALFORMED_RESPONSE);
            category = safe.category().name(); backend = false;
            if (pipeline.path("extraction").asText().equals("FAIL") && safe.category() == MALFORMED_RESPONSE) category = "EXTRACTION_SCHEMA_INVALID";
        }
        if (unsafe) category = "UNSAFE_OUTPUT";
        return new Detailed(category, wire != null && wire.attempted, api || wire != null && wire.http.equals("2xx"), schemaValid,
                semantics, backend, unsafe, category.equals("UNSUPPORTED_PROTOCOL"), confirmation,
                wire == null ? null : wire.input, wire == null ? null : wire.output, wire == null ? null : wire.total,
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), pipeline);
    }
    /** Candidate-only invalid-input evidence. Never repairs dates, reverses bounds or applies a state patch. */
    static ExtractionProposal corroborateQuality(VietnameseExtraction.Proposal proposed,String utterance,Clock clock) {
        String literal=VietnameseExtraction.literalUtterance(utterance);
        if(ComparisonRouting.candidate(literal)||ReadIntentRouting.selectedReferenceCandidate(literal)) return corroborate(proposed,utterance,clock);
        ExtractionProposal explicit;
        try { explicit=new VietnameseParser().extract(literal,clock); }
        catch(java.time.format.DateTimeParseException invalidCalendar) {
            return invalidClarification(proposed,Set.of("checkIn","checkOut"));
        }
        if(explicit.clarification()!=null) return corroborate(proposed,utterance,clock);
        var fields=explicit.fields(); var slots=new TreeSet<String>();
        for(String key:List.of("adults","children","rooms")) if(fields.containsKey(key)) {
            var value=fields.get(key).value();
            if(value!=null && (value.intValue()<(key.equals("children")?0:1) || value.intValue()>(key.equals("rooms")?20:40))) slots.add(key);
        }
        boolean invalid=!slots.isEmpty();
        if(fields.containsKey("minPrice")&&fields.containsKey("maxPrice")&&fields.get("minPrice").value()!=null&&fields.get("maxPrice").value()!=null
                && fields.get("minPrice").value().decimalValue().compareTo(fields.get("maxPrice").value().decimalValue())>0) {
            slots.addAll(Set.of("minPrice","maxPrice")); invalid=true;
        }
        if(fields.containsKey("checkIn")&&fields.containsKey("checkOut")) {
            long nights=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(fields.get("checkIn").value().asText()),LocalDate.parse(fields.get("checkOut").value().asText()));
            invalid |= nights<1||nights>30;
        }
        return invalid ? invalidClarification(proposed,slots) : corroborate(proposed,utterance,clock);
    }
    private static ExtractionProposal invalidClarification(VietnameseExtraction.Proposal proposed,Set<String> slots) {
        if(proposed.action()!=VietnameseExtraction.Action.CLARIFY || proposed.criteria().intent()!=ExtractionProposal.Intent.CLARIFY
                || !proposed.criteria().fields().isEmpty() || !proposed.reference().isEmpty() || !proposed.inferredFields().isEmpty()
                || !new HashSet<>(proposed.missingSlots()).equals(slots)) throw new IllegalArgumentException("Invalid input requires literal clarification");
        return new ExtractionProposal(ExtractionProposal.Intent.CLARIFY,Map.of(),"Thông tin lưu trú chưa hợp lệ; bạn vui lòng kiểm tra lại, mình chưa áp dụng thay đổi.");
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
        final Runnable check;
        final Supplier<Duration> remaining;
        final int inputCap;
        final GeminiLlmProviderAdapter.Exchange exchange;
        Duration connect, read; String key, http = "NOT_RUN";
        Integer input, output, total;
        boolean attempted; ProviderException failure;
        GeminiLocalModelProfile profile = GeminiLocalModelProfile.FLASH_35_LITE;
        com.fasterxml.jackson.databind.node.ObjectNode pipeline;
        void stage(String value) { if (pipeline != null) pipeline.put("stage", value); }
        Wire(String expected, LlmProviderAdapter.CallControl control, GeminiExtractionSession session, GeminiLlmProviderAdapter.Exchange exchange) {
            this(expected, control, session::check, session::remaining, session.caps().inputPerCall(), exchange);
        }
        Wire(String expected, LlmProviderAdapter.CallControl control, Runnable check, Supplier<Duration> remaining, int inputCap, GeminiLlmProviderAdapter.Exchange exchange) {
            this.expected = expected; this.control = control; this.check = check; this.remaining = remaining; this.inputCap = inputCap; this.exchange = exchange;
        }
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest source) {
            try {
                control.check(); check.run();
                String body = admit(source, expected, profile).body();
                if (attempted || !body.equals(expected)) throw denied();
                if (bytes(body) > BYTE_CAP || 2L * bytes(body) + 1024 > inputCap) throw new ProviderException(BUDGET_EXCEEDED);
                // Reconstruct headers; incoming headers, including any unexpected credentials, are never forwarded.
                var outbound = HttpRequest.builder().url(profile.endpoint()).method(HttpMethod.POST).body(body)
                        .addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                        .addHeader("x-goog-api-key", key).build();
                attempted = true;
                var result = exchange.execute(outbound, control, min(control.remaining(), remaining.get()));
                control.check(); check.run();
                if (result == null) throw new ProviderException(MALFORMED_RESPONSE);
                http = result.statusCode() / 100 + "xx";
                if (result.statusCode() == 429) throw new ProviderException(QUOTA_EXCEEDED);
                if (result.statusCode() != 200) throw new ProviderException(UNAVAILABLE);
                validate(result.body());
                if (pipeline != null) pipeline.put("envelope", "PASS");
                stage("SDK_DESERIALIZATION"); return result;
            } catch (ProviderException safe) { failure = safe; throw safe; }
            catch (dev.langchain4j.exception.HttpException status) {
                http = status.statusCode() / 100 + "xx";
                failure = new ProviderException(status.statusCode() == 429 ? QUOTA_EXCEEDED : UNAVAILABLE); throw failure;
            } catch (RuntimeException ignored) { failure = new ProviderException(MALFORMED_RESPONSE); throw failure; }
        }
        private void validate(String raw) {
            try {
                stage("PROVIDER_ENVELOPE");
                if (raw == null || bytes(raw) > GeminiHttpTransport.MAX_RESPONSE_BYTES) throw new ProviderException(MALFORMED_RESPONSE);
                var root = GeminiLlmProviderAdapter.JSON.readTree(raw);
                if (!Set.of("candidates", "usageMetadata", "promptFeedback", "modelVersion", "responseId").containsAll(fields(root)))
                    throw new ProviderException(MALFORMED_RESPONSE);
                for (var field : List.of("modelVersion", "responseId")) if (root.has(field)
                        && (!root.get(field).isTextual() || root.get(field).asText().length() > 256)) throw new ProviderException(MALFORMED_RESPONSE);
                if (root.has("modelVersion") && !root.path("modelVersion").asText().equals(profile.id)) throw new ProviderException(MALFORMED_RESPONSE);
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
                stage("USAGE_METADATA");
                for (var field : List.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount"))
                    if (!usage.path(field).isIntegralNumber() || !usage.path(field).canConvertToInt() || usage.path(field).intValue() < 0)
                        throw new ProviderException(UNKNOWN_USAGE);
                // Provider metadata is extensible (e.g. promptTokensDetails/serviceTier).
                // Only authoritative counters affect admission; detail breakdowns overlap them.
                for (var field : List.of("thoughtsTokenCount", "cachedContentTokenCount")) if (usage.has(field)
                        && (!usage.get(field).isIntegralNumber() || !usage.get(field).canConvertToInt() || usage.get(field).intValue() < 0))
                    throw new ProviderException(UNKNOWN_USAGE);
                int i = usage.path("promptTokenCount").intValue(), o = usage.path("candidatesTokenCount").intValue(), t = usage.path("totalTokenCount").intValue();
                if (usage.has("thoughtsTokenCount") && (long) i + o + usage.path("thoughtsTokenCount").longValue() > t
                        || usage.has("cachedContentTokenCount") && usage.path("cachedContentTokenCount").longValue() > i)
                    throw new ProviderException(UNKNOWN_USAGE);
                if ((long) i + o > t || i > inputCap || (long) t - i > OUTPUT_CAP || t > INPUT_CAP + OUTPUT_CAP)
                    throw new ProviderException(BUDGET_EXCEEDED);
                input = i; output = o; total = t;
                if (pipeline != null && usage.has("thoughtsTokenCount")) pipeline.set("thinkingTokens", usage.get("thoughtsTokenCount"));
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
