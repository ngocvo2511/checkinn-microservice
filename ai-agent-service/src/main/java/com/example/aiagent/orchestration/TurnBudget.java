package com.example.aiagent.orchestration;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.tools.ToolExecutionContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** One sequential turn. Counters never roll back, including rejected proposals and failed calls. */
public final class TurnBudget {
    public static final class Stop extends RuntimeException {
        private final String category;
        public Stop(String category) { super(category, null, false, false); this.category = category; }
        public String category() { return category; }
    }
    public record Snapshot(int modelRequests, int toolProposals, int httpAttempts, int toolRounds,
                           int policyDenials, boolean cancelled, long durationNanos) {}
    private final AgentProperties limits;
    private final LongSupplier ticker;
    private final long started;
    private final long deadline;
    private final AtomicBoolean cancelled;
    private final List<ToolExecutionContext> contexts = new ArrayList<>();
    private int models, proposals, rounds, denials;

    public TurnBudget(AgentProperties limits, AtomicBoolean cancelled) { this(limits, cancelled, System::nanoTime); }
    public TurnBudget(AgentProperties limits, AtomicBoolean cancelled, LongSupplier ticker) {
        if (!limits.isWithinApprovedLimits()) throw new IllegalArgumentException("Invalid limits");
        this.limits = limits; this.cancelled = Objects.requireNonNull(cancelled); this.ticker = ticker;
        started = ticker.getAsLong(); deadline = started + limits.turnDeadline().toNanos();
    }
    public void check() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new Stop("CANCELLED");
        if (deadline - ticker.getAsLong() <= 0) throw new Stop("TURN_TIMEOUT");
    }
    public long remainingNanos() { check(); return deadline - ticker.getAsLong(); }
    public Duration modelTimeout() { return Duration.ofNanos(Math.min(remainingNanos(), limits.modelTimeout().toNanos())); }
    public void reserveModel() { check(); if (models >= limits.modelRequests()) throw new Stop("MODEL_BUDGET"); models++; }
    public void reserveProposals(int count) {
        check();
        if (count < 0 || count > limits.toolProposals() - proposals) throw new Stop("PROPOSAL_BUDGET");
        proposals += count;
    }
    public void reserveRound() { check(); if (rounds >= limits.toolRounds()) throw new Stop("ROUND_BUDGET"); rounds++; }
    public void attach(ToolExecutionContext context) {
        contexts.add(context);
        context.bindTurnLimits(this::checkHttpAdmission, () -> Math.min(remainingNanos(), limits.toolTimeout().toNanos()));
    }
    public void checkHttpAdmission() {
        check(); if (attempts() >= limits.httpAttempts()) throw new Stop("HTTP_BUDGET");
    }
    private int attempts() { return contexts.stream().mapToInt(c -> c.attemptLedger().size()).sum(); }
    public void denied() { denials++; }
    public void cancelCalls() { contexts.forEach(ToolExecutionContext::cancel); }
    public Snapshot snapshot() {
        return new Snapshot(models, proposals, attempts(), rounds, denials, cancelled.get(), Math.max(0, ticker.getAsLong() - started));
    }
}
