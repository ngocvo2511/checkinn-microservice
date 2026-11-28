package com.example.aiagent.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Separate trusted CLI configuration. Never registered by the normal application/provider factory. */
@ConfigurationProperties(prefix = "checkinn.gemini-smoke", ignoreUnknownFields = false)
record GeminiSmokeApproval(
        @DefaultValue("NOT_VERIFIED") Status offlineTransportSafety,
        @DefaultValue("NOT_VERIFIED") Status firstCall,
        @DefaultValue("NOT_VERIFIED") Status noPaidBilling,
        @DefaultValue("NOT_VERIFIED") Status exclusiveQuota) {
    static final String MODEL = "gemini-3.5-flash-lite";
    enum Status { NOT_VERIFIED, OFFLINE_TRANSPORT_SAFETY_VERIFIED, FIRST_CALL_AUTHORIZED, OPERATOR_CONFIRMED }
    void validate() {
        if (offlineTransportSafety != Status.OFFLINE_TRANSPORT_SAFETY_VERIFIED || firstCall != Status.FIRST_CALL_AUTHORIZED
                || noPaidBilling != Status.OPERATOR_CONFIRMED || exclusiveQuota != Status.OPERATOR_CONFIRMED)
            throw new ProviderException(ProviderException.Category.CONFIGURATION);
    }
}
