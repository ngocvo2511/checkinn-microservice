package com.example.aiagent.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.*;
import dev.langchain4j.model.chat.request.json.*;
import java.util.List;

/** Compile-time owned fixtures. No user text, sessions, backend data or synthetic boolean accepted. */
final class SyntheticGeminiFixtures {
    static final String SMOKE = "evidence-empty-v1";
    private SyntheticGeminiFixtures() { }
    static ChatRequest request(String id) {
        var builder = ChatRequest.builder().messages(
                SystemMessage.from("Synthetic CheckInn READ test. Never execute tools. Select only supplied evidence IDs."),
                UserMessage.from("Synthetic scenario with no hotels, rooms, customers or evidence. Return {\"evidenceIds\":[]}."));
        return switch (id) {
            case SMOKE -> builder.responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON)
                    .jsonSchema(JsonSchema.builder().name("grounded_evidence_selection")
                            .rootElement(JsonRawSchema.from("{\"type\":\"object\",\"properties\":{\"evidenceIds\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"maxItems\":6}},\"required\":[\"evidenceIds\"],\"additionalProperties\":false}")).build()).build()).build();
            case "text-v1" -> builder.build();
            case "tool-v1" -> builder.toolSpecifications(ToolSpecification.builder().name("list_accommodation_types")
                    .description("Passive synthetic READ declaration; execution requires application policy.")
                    .parameters(JsonObjectSchema.builder().additionalProperties(false).build()).build()).build();
            case "tool-result-v1" -> builder.messages(List.of(UserMessage.from("Synthetic catalog query."),
                    AiMessage.from(List.of(dev.langchain4j.agent.tool.ToolExecutionRequest.builder().id("synthetic-call")
                            .name("list_accommodation_types").arguments("{}").build())),
                    ToolExecutionResultMessage.from("synthetic-call", "list_accommodation_types", "{\"types\":[]}")))
                    .responseFormat(request(SMOKE).responseFormat()).build();
            default -> throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
        };
    }
}
