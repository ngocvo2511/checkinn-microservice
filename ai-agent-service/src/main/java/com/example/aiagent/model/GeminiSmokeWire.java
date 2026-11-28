package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.*;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Exact-model, exact fixed-fixture wire bridge for pinned SDK1.17.0. Not a general JSON rewriter. */
final class GeminiSmokeWire {
    private GeminiSmokeWire() { }
    static HttpRequest prepare(HttpRequest source, String model, int outputCap) {
        if (!GeminiSmokeApproval.MODEL.equals(model)) return source;
        try {
            if (source.method() != HttpMethod.POST || !source.url().equals(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
                    || source.body() == null || source.body().getBytes(StandardCharsets.UTF_8).length > 16384)
                throw new IllegalArgumentException();
            var root = GeminiLlmProviderAdapter.JSON.readTree(source.body());
            requireFields(root, Set.of("contents", "systemInstruction", "generationConfig"));
            var config = root.path("generationConfig");
            requireFields(config, Set.of("candidateCount", "maxOutputTokens", "responseMimeType", "responseJsonSchema", "responseLogprobs", "enableEnhancedCivicAnswers"));
            var fixture = SyntheticGeminiFixtures.request(SyntheticGeminiFixtures.SMOKE);
            var system = GeminiLlmProviderAdapter.JSON.createObjectNode().put("role", "model");
            system.putArray("parts").addObject().put("text", ((SystemMessage) fixture.messages().getFirst()).text());
            var user = GeminiLlmProviderAdapter.JSON.createObjectNode().put("role", "user");
            user.putArray("parts").addObject().put("text", ((UserMessage) fixture.messages().get(1)).singleText());
            var contents = GeminiLlmProviderAdapter.JSON.createArrayNode().add(user);
            var schema = GeminiLlmProviderAdapter.JSON.readTree(((JsonRawSchema) fixture.responseFormat().jsonSchema().rootElement()).schema());
            if (!root.path("systemInstruction").equals(system) || !root.path("contents").equals(contents)
                    || !config.path("candidateCount").isIntegralNumber() || !config.path("candidateCount").canConvertToInt()
                    || config.path("candidateCount").intValue() != 1
                    || !config.path("maxOutputTokens").isIntegralNumber() || !config.path("maxOutputTokens").canConvertToInt()
                    || config.path("maxOutputTokens").intValue() != outputCap
                    || !config.path("responseMimeType").asText().equals("application/json")
                    || !config.path("responseJsonSchema").equals(schema)
                    || !config.path("responseLogprobs").isBoolean() || config.path("responseLogprobs").booleanValue()
                    || !config.path("enableEnhancedCivicAnswers").isBoolean() || config.path("enableEnhancedCivicAnswers").booleanValue())
                throw new IllegalArgumentException();
            ((ObjectNode) config).remove("candidateCount"); // Sole mutation. All other nodes/headers remain identical.
            var builder = HttpRequest.builder().url(source.url()).method(source.method()).body(root.toString());
            source.headers().forEach((name, values) -> values.forEach(value -> builder.addHeader(name, value)));
            return builder.build();
        } catch (Exception ignored) { throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL); }
    }
    private static void requireFields(JsonNode object, Set<String> allowed) {
        if (object == null || !object.isObject() || object.size() != allowed.size()) throw new IllegalArgumentException();
        object.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException(); });
    }
}
