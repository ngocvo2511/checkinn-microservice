package com.example.aiagent.model;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** Local evaluation only. A continuation always advances to a distinct sample, never retries. */
final class GeminiLocalFailurePolicy {
    record Decision(boolean proceed, long cooldownSeconds, String reason) { }
    int consecutive, cumulative;
    Decision decide(int status, String category, boolean knownUsage, boolean unsafe,
                    boolean terminated, String retryAfter, Instant now) {
        if (unsafe) return new Decision(false, 0, "CRITICAL_SAFETY");
        if (Set.of(400, 401, 403, 429).contains(status) || category.equals("QUOTA_EXCEEDED"))
            return new Decision(false, 0, "HTTP_" + status + "_STOP");
        boolean transientFailure = Set.of(500, 502, 503, 504).contains(status)
                || status == 0 && Set.of("TIMEOUT", "PROVIDER_TIMEOUT", "UNAVAILABLE").contains(category);
        if (transientFailure) {
            consecutive++; cumulative++;
            if (!terminated) return new Decision(false, 0, "UNKNOWN_IN_FLIGHT");
            if (consecutive >= 2 || cumulative >= 5) return new Decision(false, 0, "TRANSIENT_FAILURE_THRESHOLD");
            long cooldown = 15;
            if (retryAfter != null) {
                try { cooldown = Math.max(cooldown, Long.parseLong(retryAfter)); }
                catch (NumberFormatException ignored) {
                    try { cooldown = Math.max(cooldown, Duration.between(now,
                            ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds()); }
                    catch (DateTimeException invalid) { return new Decision(false, 0, "INVALID_RETRY_AFTER"); }
                }
            }
            return new Decision(true, cooldown, "TRANSIENT_NEXT_DISTINCT_SAMPLE");
        }
        if (knownUsage && Set.of("SUCCESS", "BACKEND_REJECTED", "EXTRACTION_SCHEMA_INVALID").contains(category)) {
            consecutive = 0;
            return new Decision(true, 0, "CONTAINED_NEXT_DISTINCT_SAMPLE");
        }
        return new Decision(false, 0, status == 200 && !knownUsage ? "SUCCESS_UNKNOWN_USAGE" : category);
    }
}
