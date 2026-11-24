package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.orchestration.*;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Explicit Java CLI, never a runner/controller/scheduled job in the application. Default is preflight ONLY. */
public final class GeminiSyntheticSmoke {
    @EnableConfigurationProperties({AgentProperties.class, GeminiProperties.class})
    static class ConfigurationOnly { }
    private GeminiSyntheticSmoke() { }
    public static void main(String[] args) {
        boolean execute = args.length == 1 && args[0].equals("--execute-fixed-synthetic");
        if (args.length > 0 && !execute) throw new ProviderException(ProviderException.Category.CONFIGURATION);
        try (var context = new SpringApplicationBuilder(ConfigurationOnly.class).web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF).logStartupInfo(false).run()) {
            var agent = context.getBean(AgentProperties.class); var limits = context.getBean(GeminiProperties.class);
            System.out.println(preflight(agent, limits));
            if (!execute) return;
            limits.validate(agent);
            var meters = new SimpleMeterRegistry();
            try {
                var provider = (GeminiLlmProviderAdapter) new ProviderConfiguration().llmProviderAdapter(agent, limits, meters);
                var result = provider.fixture(SyntheticGeminiFixtures.SMOKE, new LlmProviderAdapter.CallControl(agent.modelTimeout()));
                // SDK schema is advisory; strict server parser remains authoritative, no response text is displayed.
                var answer = new GroundingGate().validate(result.aiMessage().text(),
                        new EvidenceLedger(List.of(), 0, "synthetic-smoke", Clock.systemUTC()), SearchCriteriaState.empty(),
                        new PolicyExecutor.Scope(PolicyExecutor.Intent.CANCELLATION, null, null));
                if (result.aiMessage().hasToolExecutionRequests() || answer.category().equals("GROUNDING_REJECTED"))
                    throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE);
                System.out.println("Synthetic smoke completed: one request; strict evidence-selection validated; no tools executed.");
            } finally { meters.close(); }
        }
    }
    static String preflight(AgentProperties agent, GeminiProperties limits) {
        String model = agent.modelId() != null && agent.modelId().matches("[A-Za-z0-9.-]{1,100}") ? agent.modelId() : "INVALID";
        return "Gemini preflight: model=" + model + "; fixture=" + SyntheticGeminiFixtures.SMOKE
                + "; fixedRequests=1; runCap=" + limits.maxRunRequests() + "; processCap=" + limits.maxProcessRequests()
                + "; inputTokenCap=" + limits.maxInputTokens() + "; outputTokenCap=" + limits.maxOutputTokens()
                + "; localExposureUsd=" + limits.maxExposureUsd() + "; agentEnabled=" + agent.enabled()
                + "; provider=" + agent.provider() + "; liveEnabled=" + limits.liveEnabled()
                + "; syntheticOnly=" + limits.syntheticOnly() + "; modelVerification=" + limits.modelVerification()
                + "; accountRegionFreeTier=" + limits.accountRegionFreeTierVerification()
                + "; dataTerms=" + limits.dataTermsVerification() + "; quotaBilling=" + limits.quotaBillingVerification()
                + "; transportAcceptance=" + limits.transportAcceptance() + "; estimatedInput=true; externalCostGuarantee=false";
    }
}
