package com.example.aiagent.orchestration;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.model.LlmProviderAdapter;
import com.example.aiagent.tools.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
class OrchestrationConfiguration {
    @Bean ConversationStore conversationStore() { return new InMemoryConversationStore(Clock.systemUTC()); }
    @Bean(destroyMethod = "close") AgentOrchestrator agentOrchestrator(AgentProperties properties, LlmProviderAdapter provider,
            ToolRegistry registry, ConversationStore store, MeterRegistry meters, io.micrometer.tracing.Tracer tracer) {
        return new AgentOrchestrator(properties, provider, registry, store, Clock.systemUTC(), meters, tracer);
    }
}
