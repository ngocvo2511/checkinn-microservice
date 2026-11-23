package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.request.json.*;
import java.util.*;

/** Pinned SDK dialect bridge. Authoritative validation stays in PolicyExecutor/registry/GroundingGate. */
final class GeminiRequestProtocol {
    private GeminiRequestProtocol() { }
    static ChatRequest prepare(ChatRequest source) {
        try {
            boolean tools = source.toolSpecifications() != null && !source.toolSpecifications().isEmpty();
            if (tools && source.responseFormat() != null && source.responseFormat().type() == ResponseFormatType.JSON)
                throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
            var builder = ChatRequest.builder().messages(source.messages());
            if (tools) {
                var specifications = new ArrayList<ToolSpecification>();
                for (var spec : source.toolSpecifications()) specifications.add(ToolSpecification.builder()
                        .name(spec.name()).description(spec.description()).metadata(spec.metadata())
                        .parameters((JsonObjectSchema) toolSchema(spec.parameters())).build());
                builder.toolSpecifications(specifications);
            }
            if (source.responseFormat() != null) {
                var format = source.responseFormat();
                // JsonRawSchema at ROOT uses responseJsonSchema, retaining nested raw/bounds/additionalProperties.
                builder.responseFormat(format.jsonSchema() == null ? format : ResponseFormat.builder().type(format.type())
                        .jsonSchema(JsonSchema.builder().name(format.jsonSchema().name())
                                .rootElement(JsonRawSchema.from(rawSchema(format.jsonSchema().rootElement()).toString())).build()).build());
            }
            return builder.build();
        } catch (ProviderException typed) { throw typed; }
        catch (Exception unsupported) { throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL); }
    }
    private static JsonSchemaElement toolSchema(JsonSchemaElement element) throws Exception {
        if (element instanceof JsonRawSchema raw) return typed(GeminiLlmProviderAdapter.JSON.readTree(raw.schema()));
        if (element instanceof JsonObjectSchema object) {
            var builder = JsonObjectSchema.builder().description(object.description()).required(object.required()).additionalProperties(false);
            if (object.properties() != null) for (var property : object.properties().entrySet()) builder.addProperty(property.getKey(), toolSchema(property.getValue()));
            return builder.build();
        }
        return element;
    }
    private static JsonSchemaElement typed(JsonNode node) throws Exception {
        if (node.has("enum")) {
            var values = new ArrayList<String>(); for (var value : node.get("enum")) {
                if (!value.isTextual()) throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
                values.add(value.textValue());
            }
            return JsonEnumSchema.builder().enumValues(values).build();
        }
        // Bounds/format/pattern are not expressible through this SDK mapper. Server checks remain mandatory.
        return switch (node.path("type").asText()) {
            case "string" -> JsonStringSchema.builder().build();
            case "integer" -> JsonIntegerSchema.builder().build();
            case "number" -> JsonNumberSchema.builder().build();
            case "boolean" -> JsonBooleanSchema.builder().build();
            case "array" -> JsonArraySchema.builder().items(typed(node.path("items"))).build();
            case "object" -> {
                var builder = JsonObjectSchema.builder().additionalProperties(false);
                var properties = node.path("properties").fields();
                while (properties.hasNext()) { var property = properties.next(); builder.addProperty(property.getKey(), typed(property.getValue())); }
                var required = new ArrayList<String>(); node.path("required").forEach(v -> required.add(v.asText()));
                yield builder.required(required).build();
            }
            default -> throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
        };
    }
    private static JsonNode rawSchema(JsonSchemaElement element) throws Exception {
        if (element instanceof JsonRawSchema raw) return GeminiLlmProviderAdapter.JSON.readTree(raw.schema());
        if (element instanceof JsonObjectSchema object) {
            var out = GeminiLlmProviderAdapter.JSON.createObjectNode().put("type", "object").put("additionalProperties", false);
            var properties = out.putObject("properties");
            if (object.properties() != null) for (var property : object.properties().entrySet()) properties.set(property.getKey(), rawSchema(property.getValue()));
            var required = out.putArray("required"); if (object.required() != null) object.required().forEach(required::add);
            return out;
        }
        if (element instanceof JsonArraySchema array) {
            var out = GeminiLlmProviderAdapter.JSON.createObjectNode().put("type", "array"); out.set("items", rawSchema(array.items())); return out;
        }
        if (element instanceof JsonStringSchema) return GeminiLlmProviderAdapter.JSON.createObjectNode().put("type", "string");
        throw new ProviderException(ProviderException.Category.UNSUPPORTED_PROTOCOL);
    }
}
