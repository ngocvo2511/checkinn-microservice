package com.example.aiagent.model;

/** Fixed messages only: never retain raw SDK errors, provider bodies or credentials. */
public final class ProviderException extends RuntimeException {
    public enum Category { DISABLED, CONFIGURATION, SYNTHETIC_BOUNDARY, BUDGET_EXCEEDED,
        TIMEOUT, PROVIDER_TIMEOUT, CANCELLED, UNAVAILABLE, MALFORMED_RESPONSE, SAFETY_REFUSAL,
        QUOTA_EXCEEDED, UNKNOWN_USAGE, UNSUPPORTED_PROTOCOL }
    private final Category category;
    public ProviderException(Category category) {
        super("Model call rejected: " + category.name());
        this.category = category;
    }
    public Category category() { return category; }
}
