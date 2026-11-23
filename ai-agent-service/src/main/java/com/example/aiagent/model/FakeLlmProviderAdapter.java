package com.example.aiagent.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Objects;

/** Immutable scripted result. Scenario is test-owned, never selected by user message content. */
public final class FakeLlmProviderAdapter implements LlmProviderAdapter {
    public enum Scenario { TEXT, TOOL_PROPOSAL, TIMEOUT, FAILURE, MALFORMED }
    private final Scenario scenario;
    private final ChatResponse scriptedResponse;
    private final java.util.Queue<java.util.function.Function<ChatRequest, ChatResponse>> steps = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private boolean multiStep;
    /** Test-owned multi-step script; exhaustion fails closed instead of replaying a response. */
    public FakeLlmProviderAdapter(List<java.util.function.Function<ChatRequest, ChatResponse>> script) {
        this.scenario = Scenario.TEXT; this.scriptedResponse = null;
        this.steps.addAll(List.copyOf(script)); this.multiStep = true;
    }
    public FakeLlmProviderAdapter() { this(Scenario.TEXT); }
    public FakeLlmProviderAdapter(Scenario scenario) {
        this.scenario = Objects.requireNonNull(scenario);
        this.scriptedResponse = null;
    }
    /** Test-owned response preserves arbitrary proposals and optional/provider-specific metadata. */
    public FakeLlmProviderAdapter(ChatResponse response) {
        this.scenario = Scenario.TEXT;
        this.scriptedResponse = Objects.requireNonNull(response);
    }

    @Override public ChatResponse generate(ChatRequest request, CallControl control) {
        Objects.requireNonNull(request);
        control.check();
        if (multiStep) {
            var step = steps.poll();
            if (step == null) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            var response = step.apply(request); control.check();
            if (response == null || response.aiMessage() == null) throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            return response;
        }
        switch (scenario) {
            case TIMEOUT -> throw new ProviderException(ProviderException.Category.TIMEOUT);
            case FAILURE -> throw new ProviderException(ProviderException.Category.UNAVAILABLE);
            case MALFORMED -> throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            default -> { }
        }
        if (scriptedResponse != null) {
            if (scriptedResponse.aiMessage() == null) {
                throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
            }
            return scriptedResponse;
        }
        boolean tool = scenario == Scenario.TOOL_PROPOSAL;
        AiMessage message = tool ? AiMessage.from(List.of(ToolExecutionRequest.builder()
                .id("fake-proposal-1").name("search_hotels").arguments("{\"cityName\":\"Đà Nẵng\"}").build()))
                : AiMessage.from("Fake provider: chưa truy vấn dữ liệu khách sạn.");
        control.check();
        return ChatResponse.builder().id("fake-response-1").modelName("fake-v1")
                .aiMessage(message).tokenUsage(new TokenUsage(12, 8))
                .finishReason(tool ? FinishReason.TOOL_EXECUTION : FinishReason.STOP).build();
    }
}
