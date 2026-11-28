package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Operator-only configuration. Attestations are NOT substitutes for external project verification. */
@ConfigurationProperties(prefix = "checkinn.gemini", ignoreUnknownFields = false)
public record GeminiProperties(
        @DefaultValue("false") boolean liveEnabled,
        @DefaultValue("false") boolean syntheticOnly,
        @DefaultValue("") String reviewedModelId,
        @DefaultValue("NOT_VERIFIED") Verification modelVerification,
        @DefaultValue("NOT_VERIFIED") Verification accountRegionFreeTierVerification,
        @DefaultValue("NOT_VERIFIED") Verification dataTermsVerification,
        @DefaultValue("NOT_VERIFIED") Verification quotaBillingVerification,
        @DefaultValue("NOT_VERIFIED") Verification transportAcceptance,
        @DefaultValue("0") int maxInputTokens,
        @DefaultValue("0") int maxOutputTokens,
        @DefaultValue("0") int maxRunRequests,
        @DefaultValue("0") int maxProcessRequests,
        @DefaultValue("0") int maxConcurrentRequests,
        @DefaultValue("0") BigDecimal maxExposureUsd,
        @DefaultValue("0") BigDecimal inputUsdPerMillionUpperBound,
        @DefaultValue("0") BigDecimal outputUsdPerMillionUpperBound) {
    public enum Verification { NOT_VERIFIED, VERIFIED, OPERATOR_CONFIRMED }
    void validateFirstCall(AgentProperties agent) {
        if (!agent.enabled() || agent.provider() != AgentProperties.Provider.GEMINI || !agent.isWithinApprovedLimits()
                || !GeminiSmokeApproval.MODEL.equals(agent.modelId()) || !agent.modelId().equals(reviewedModelId)
                || !"GEMINI_API_KEY".equals(agent.apiKeyEnvironmentVariable())
                || !liveEnabled || !syntheticOnly || modelVerification != Verification.VERIFIED
                || accountRegionFreeTierVerification != Verification.OPERATOR_CONFIRMED
                || dataTermsVerification != Verification.OPERATOR_CONFIRMED || quotaBillingVerification != Verification.OPERATOR_CONFIRMED
                || transportAcceptance != Verification.NOT_VERIFIED || !validBudgets()
                || maxInputTokens != 8192 || maxOutputTokens != 128 || maxRunRequests != 1 || maxProcessRequests != 1
                || maxExposureUsd.compareTo(new BigDecimal("0.01")) != 0
                || agent.modelTimeout().compareTo(java.time.Duration.ofSeconds(10)) > 0
                || inputUsdPerMillionUpperBound.compareTo(BigDecimal.ONE) != 0
                || outputUsdPerMillionUpperBound.compareTo(new BigDecimal("3")) != 0
                || reservation().compareTo(maxExposureUsd) > 0)
            throw new ProviderException(ProviderException.Category.CONFIGURATION);
    }
    public void validate(AgentProperties agent) {
        if (!agent.enabled() || agent.provider() != AgentProperties.Provider.GEMINI || !agent.isWithinApprovedLimits()
                || !liveEnabled || !syntheticOnly || !agent.modelId().equals(reviewedModelId)
                || modelVerification != Verification.VERIFIED || accountRegionFreeTierVerification != Verification.VERIFIED
                || dataTermsVerification != Verification.VERIFIED || quotaBillingVerification != Verification.VERIFIED
                || transportAcceptance != Verification.VERIFIED || !validBudgets())
            throw new ProviderException(ProviderException.Category.CONFIGURATION);
    }
    public boolean validBudgets() {
        return maxInputTokens >= 1024 && maxInputTokens <= 16384 && maxOutputTokens >= 1 && maxOutputTokens <= 1024
                && maxRunRequests >= 1 && maxRunRequests <= 3 && maxProcessRequests >= maxRunRequests && maxProcessRequests <= 9
                && maxConcurrentRequests == 1 && positive(maxExposureUsd) && maxExposureUsd.compareTo(BigDecimal.ONE) <= 0
                && positive(inputUsdPerMillionUpperBound) && positive(outputUsdPerMillionUpperBound)
                && inputUsdPerMillionUpperBound.compareTo(new BigDecimal("1000")) <= 0
                && outputUsdPerMillionUpperBound.compareTo(new BigDecimal("1000")) <= 0;
    }
    BigDecimal reservation() {
        return inputUsdPerMillionUpperBound.multiply(BigDecimal.valueOf(maxInputTokens))
                .add(outputUsdPerMillionUpperBound.multiply(BigDecimal.valueOf(maxOutputTokens)))
                .divide(BigDecimal.valueOf(1_000_000));
    }
    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    @Override public String toString() { return "GeminiProperties[liveEnabled=" + liveEnabled + ", payloads=omitted]"; }
}
