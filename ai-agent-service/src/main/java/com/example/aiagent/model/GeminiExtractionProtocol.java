package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.*;
import dev.langchain4j.http.client.*;
import dev.langchain4j.http.client.sse.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Internal synthetic-only protocol. No key lookup, sockets, operator approval or old-smoke permit. */
final class GeminiExtractionProtocol {
    static final String PURPOSE = "VIETNAMESE_EXTRACTION_SYNTHETIC";
    static final String VERSION = "vietnamese-extraction-compact-v1";
    static final String MODEL = "gemini-3.5-flash-lite";
    static final String MANIFEST = "7188532021d1c2151ccf0cc611cc49a437bb2dd53a26306bf66eba60cfcc75a3";
    static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    static final int INPUT_CAP = 16384, OUTPUT_CAP = 1024, BYTE_CAP = 16384;
    private static final Map<String, JsonNode> FIXTURES = load();
    private GeminiExtractionProtocol() { }
    static ProviderException denied() { return new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY); }
    static String hash(String text) { return hash(text.getBytes(StandardCharsets.UTF_8)); }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception failure) { throw denied(); }
    }
    static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    private static Map<String, JsonNode> load() {
        try (var in = GeminiExtractionProtocol.class.getResourceAsStream("/gemini/extraction-candidates-v1.json")) {
            byte[] bytes = Objects.requireNonNull(in).readNBytes(65537);
            if (bytes.length > 65536 || !hash(bytes).equals(MANIFEST)) throw denied();
            var values = new LinkedHashMap<String, JsonNode>();
            for (var fixture : GeminiLlmProviderAdapter.JSON.readTree(bytes).path("fixtures")) {
                if (!fixture.path("privacyClassification").asText().equals("SYNTHETIC_ONLY")
                        || values.put(fixture.path("id").asText(), fixture) != null) throw denied();
            }
            if (values.size() != 20) throw denied();
            return Collections.unmodifiableMap(values);
        } catch (Exception failure) { throw denied(); }
    }
    static JsonNode fixture(String id) {
        var value = FIXTURES.get(id); if (value == null) throw denied(); return value.deepCopy();
    }
    static String utterance(String id) { return fixture(id).path("utterance").asText(); }
    static String instruction() { return ((SystemMessage) VietnameseExtraction.request("synthetic").messages().getFirst()).text(); }
    static String schema() {
        try { return GeminiLlmProviderAdapter.JSON.readTree(VietnameseExtraction.schema()).toString(); }
        catch (Exception failure) { throw denied(); }
    }
    static ChatRequest request(String id) {
        return syntheticRequest(utterance(id));
    }
    /** Package-only serializer; only a verified pinned sample capability may dispatch its result. */
    static ChatRequest syntheticRequest(String syntheticUtterance) {
        return syntheticRequest(syntheticUtterance, instruction(), schema());
    }
    static ChatRequest syntheticRequest(String syntheticUtterance, String instruction, String schema) {
        return ChatRequest.builder().messages(SystemMessage.from(instruction), UserMessage.from(syntheticUtterance))
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(JsonSchema.builder()
                        .name("vietnamese_read_extraction").rootElement(JsonRawSchema.from(schema)).build()).build()).build();
    }
    record Binding(String fixtureDigest, String instructionDigest, String schemaDigest, String requestDigest) { }
    static Binding binding(String id) {
        return new Binding(hash(utterance(id)), hash(instruction()), hash(schema()), hash(body(id)));
    }
    /** Actual pinned SDK serialization, in-memory SPI; sentinel is not an environment credential. */
    static String body(String id) {
        return syntheticBody(utterance(id));
    }
    static String syntheticBody(String syntheticUtterance) {
        return syntheticBody(syntheticRequest(syntheticUtterance));
    }
    static String syntheticBody(ChatRequest request) {
        var capture = new Capture(); model("synthetic-noncredential", capture, Duration.ofSeconds(10)).chat(request);
        return normalize(capture.request).body();
    }
    static String syntheticBody(ChatRequest request, GeminiLocalModelProfile profile) {
        var capture = new Capture(); model("synthetic-noncredential", capture, Duration.ofSeconds(10), profile).chat(request);
        return normalize(capture.request, profile).body();
    }
    static GoogleAiGeminiChatModel model(String key, HttpClientBuilder transport, Duration timeout) {
        return model(key, transport, timeout, GeminiLocalModelProfile.FLASH_35_LITE);
    }
    static GoogleAiGeminiChatModel model(String key, HttpClientBuilder transport, Duration timeout, GeminiLocalModelProfile profile) {
        return GoogleAiGeminiChatModel.builder().apiKey(key).modelName(profile.id).maxRetries(0).maxOutputTokens(OUTPUT_CAP)
                .timeout(timeout).returnThinking(false).logRequests(false).logResponses(false)
                .allowCodeExecution(false).allowGoogleSearch(false).allowGoogleMaps(false).allowUrlContext(false)
                .httpClientBuilder(transport).build();
    }
    /** Dedicated dispatch, never calls GeminiSmokeWire. Only removes the pinned SDK candidateCount=1. */
    static HttpRequest normalize(HttpRequest source) {
        return normalize(source, GeminiLocalModelProfile.FLASH_35_LITE);
    }
    static HttpRequest normalize(HttpRequest source, GeminiLocalModelProfile profile) {
        try {
            if (!profile.endpoint().equals(source.url()) || source.method() != HttpMethod.POST || source.body() == null
                    || bytes(source.body()) > BYTE_CAP) throw denied();
            var tree = GeminiLlmProviderAdapter.JSON.readTree(source.body());
            if (!fields(tree).equals(Set.of("contents", "systemInstruction", "generationConfig"))) throw denied();
            var config = tree.path("generationConfig");
            if (!fields(config).equals(Set.of("candidateCount", "maxOutputTokens", "responseMimeType", "responseJsonSchema",
                    "responseLogprobs", "enableEnhancedCivicAnswers")) || !config.path("candidateCount").equals(
                    GeminiLlmProviderAdapter.JSON.getNodeFactory().numberNode(1))) throw denied();
            ((ObjectNode) config).remove("candidateCount");
            return HttpRequest.builder().url(profile.endpoint()).method(HttpMethod.POST).body(tree.toString()).build();
        } catch (Exception failure) { throw denied(); }
    }
    static HttpRequest admit(HttpRequest source, String reservedBody) {
        return admit(source, reservedBody, GeminiLocalModelProfile.FLASH_35_LITE);
    }
    static HttpRequest admit(HttpRequest source, String reservedBody, GeminiLocalModelProfile profile) {
        var normalized = normalize(source, profile);
        if (!normalized.body().equals(reservedBody)) throw denied();
        for (var header : source.headers().keySet()) if (!Set.of("content-type", "accept", "x-goog-api-key", "user-agent")
                .contains(header.toLowerCase(Locale.ROOT))) throw denied();
        return normalized;
    }
    static Set<String> fields(JsonNode node) {
        if (!node.isObject()) throw denied(); var names = new HashSet<String>(); node.fieldNames().forEachRemaining(names::add); return names;
    }
    static boolean eligible(String id, java.time.Clock clock) {
        // Pinned candidate classification is an additional exclusion, never model-authored authorization.
        if (fixture(id).path("expectedBackendCorroboration").asText().startsWith("PREMODEL_")) return false;
        String text = utterance(id); VietnameseSecurityPreflight.validate(text);
        var literal = new VietnameseParser().extract(text, clock);
        return !VietnameseSecurityPreflight.refused(text) && literal.intent() != ExtractionProposal.Intent.UNSUPPORTED
                && literal.intent() != ExtractionProposal.Intent.UNVERIFIABLE;
    }
    static final class Capture implements HttpClientBuilder, HttpClient {
        HttpRequest request; private Duration connect, read;
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest value) {
            if (request != null) throw denied(); request = value;
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body("{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"{}\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":1,\"candidatesTokenCount\":1,\"totalTokenCount\":2}}").build();
        }
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) { throw denied(); }
    }
}
