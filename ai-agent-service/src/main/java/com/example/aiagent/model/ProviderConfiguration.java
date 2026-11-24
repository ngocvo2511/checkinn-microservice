package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.context.properties.EnableConfigurationProperties(GeminiProperties.class)
public class ProviderConfiguration {
    @Bean
    public LlmProviderAdapter llmProviderAdapter(AgentProperties properties, GeminiProperties gemini, MeterRegistry meters) {
        if (properties.provider() == AgentProperties.Provider.GEMINI) {
            gemini.validate(properties); // Missing gates fail initialization BEFORE secret lookup/transport construction.
            return new GeminiLlmProviderAdapter(properties, gemini, meters, System.getenv(properties.apiKeyEnvironmentVariable()));
        }
        if (properties.provider() != AgentProperties.Provider.FAKE || !properties.isWithinApprovedLimits())
            throw new ProviderException(ProviderException.Category.CONFIGURATION);
        return llmProviderAdapter(properties, meters);
    }
    /** Existing fake-only factory retained for internal foundation tests. */
    public LlmProviderAdapter llmProviderAdapter(AgentProperties properties, MeterRegistry meters) {
        if (properties.provider() != AgentProperties.Provider.FAKE || !properties.isWithinApprovedLimits())
            throw new ProviderException(ProviderException.Category.CONFIGURATION);
        LlmProviderAdapter fake = new FakeLlmProviderAdapter();
        return (request, control) -> {
            if (!properties.enabled()) throw new ProviderException(ProviderException.Category.DISABLED);
            control.check();
            var sample = io.micrometer.core.instrument.Timer.start(meters);
            meters.counter("agent.model.calls", "provider", "FAKE").increment();
            try {
                var response = fake.generate(request, control);
                if (response.tokenUsage() != null && response.tokenUsage().totalTokenCount() != null) {
                    meters.counter("agent.model.tokens", "provider", "FAKE")
                            .increment(response.tokenUsage().totalTokenCount());
                }
                return response;
            } catch (ProviderException error) {
                meters.counter("agent.model.errors", "category", error.category().name()).increment();
                throw error;
            } finally {
                sample.stop(meters.timer("agent.model.duration", "provider", "FAKE"));
            }
        };
    }
}
