package com.example.aiagent.model;

import java.math.BigDecimal;

/** JVM-wide production ledger. Reservations never refunded, even on unknown/error/timeout outcomes. */
final class GeminiBudget {
    static final GeminiBudget PROCESS = new GeminiBudget();
    private int requests, active;
    private BigDecimal exposure = BigDecimal.ZERO;
    private boolean stopped;
    synchronized void reserve(GeminiProperties limits) {
        if (stopped || active >= limits.maxConcurrentRequests() || requests >= limits.maxProcessRequests()
                || exposure.add(limits.reservation()).compareTo(limits.maxExposureUsd()) > 0)
            throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED);
        requests++; active++; exposure = exposure.add(limits.reservation());
    }
    synchronized void release() { active--; }
    synchronized void stop() { stopped = true; }
    synchronized BigDecimal exposure() { return exposure; }
}
