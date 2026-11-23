package com.example.aiagent.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

/** Approved turn limits. Selected Gemini additionally requires GeminiProperties/startup gates. No secret value is bound. */
@Validated
@ConfigurationProperties(prefix = "checkinn.agent", ignoreUnknownFields = false)
public record AgentProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("FAKE") Provider provider,
        @DefaultValue("fake-v1") String modelId,
        @DefaultValue("GEMINI_API_KEY") String apiKeyEnvironmentVariable,
        @DefaultValue("10s") Duration modelTimeout,
        @DefaultValue("10s") Duration toolTimeout,
        @DefaultValue("30s") Duration turnDeadline,
        @DefaultValue("6") int httpAttempts,
        @DefaultValue("6") int toolProposals,
        @DefaultValue("3") int modelRequests,
        @DefaultValue("2") int toolRounds,
        @DefaultValue("0") int automaticRetries,
        @DefaultValue("false") boolean logPayloads) {
    public enum Provider { FAKE, GEMINI, OPENAI }

    @AssertTrue(message = "Agent configuration violates approved limits or provider/model selection")
    public boolean isWithinApprovedLimits() {
        return (provider == Provider.FAKE || provider == Provider.GEMINI && modelId != null && modelId.matches("gemini-[a-zA-Z0-9.-]{1,90}"))
                && modelId != null && modelId.matches("[A-Za-z0-9._/-]{1,100}")
                && apiKeyEnvironmentVariable != null && apiKeyEnvironmentVariable.matches("[A-Z][A-Z0-9_]{0,63}")
                && positive(modelTimeout) && positive(toolTimeout) && positive(turnDeadline)
                && toolTimeout.compareTo(Duration.ofSeconds(10)) <= 0
                && turnDeadline.compareTo(Duration.ofSeconds(30)) <= 0
                && modelTimeout.compareTo(turnDeadline) <= 0 && toolTimeout.compareTo(turnDeadline) <= 0
                && httpAttempts >= 1 && httpAttempts <= 6
                && toolProposals >= 1 && toolProposals <= 6
                && modelRequests >= 1 && modelRequests <= 3
                && toolRounds >= 1 && toolRounds <= 2 && automaticRetries == 0 && !logPayloads;
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isNegative() && !value.isZero();
    }

    @Override public String toString() {
        return "AgentProperties[enabled=" + enabled + ", provider=" + provider + ", payloadLogging=false]";
    }
}
