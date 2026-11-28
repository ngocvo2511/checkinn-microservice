package com.example.aiagent.model;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;

/** Run-local availability state, exclusively for the explicitly invoked local evaluation CLI. */
final class GeminiLocalModelFallback {
    record Transition(GeminiLocalModelProfile next, long cooldownSeconds, String reason) { }
    final Predicate<GeminiLocalModelProfile> eligible;
    final Set<GeminiLocalModelProfile> unavailable = EnumSet.noneOf(GeminiLocalModelProfile.class);
    int capacityFailures;
    GeminiLocalModelFallback() { this(GeminiLocalModelProfile::eligible); }
    GeminiLocalModelFallback(Predicate<GeminiLocalModelProfile> eligible) { this.eligible = eligible; }
    GeminiLocalModelProfile first() {
        return Arrays.stream(GeminiLocalModelProfile.values()).filter(eligible).filter(m -> !unavailable.contains(m)).findFirst().orElse(null);
    }
    static boolean capacity(int status, boolean terminated, String errorBody) {
        if (status != 503 || !terminated || errorBody == null) return false;
        try {
            var error = GeminiLlmProviderAdapter.JSON.readTree(errorBody).path("error");
            return error.path("code").isIntegralNumber() && error.path("code").asInt() == 503
                    && error.path("status").asText().equals("UNAVAILABLE")
                    && Set.of("This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
                            "The model is overloaded. Please try again later.").contains(error.path("message").asText());
        } catch (Exception invalid) { return false; }
    }
    Transition after(GeminiLocalModelProfile previous, int status, boolean terminated, String errorBody, String retryAfter, Instant now) {
        if (!capacity(status, terminated, errorBody)) return new Transition(null, 0, "NOT_ELIGIBLE_FOR_FALLBACK");
        unavailable.add(previous); capacityFailures++;
        if (capacityFailures >= 8) return new Transition(null, 0, "CAPACITY_FAILURE_CEILING");
        long cooldown = 15;
        if (retryAfter != null) {
            try { cooldown = Math.max(cooldown, Long.parseLong(retryAfter)); }
            catch (NumberFormatException ignored) {
                try { cooldown = Math.max(cooldown, Duration.between(now, ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()); }
                catch (DateTimeException invalid) { return new Transition(null, 0, "INVALID_RETRY_AFTER"); }
            }
        }
        for (var next : GeminiLocalModelProfile.values()) if (next.ordinal() > previous.ordinal() && eligible.test(next) && !unavailable.contains(next))
            return new Transition(next, cooldown, "PROVIDER_CAPACITY_FALLBACK");
        return new Transition(null, 0, "ALL_ELIGIBLE_MODELS_UNAVAILABLE");
    }
}
