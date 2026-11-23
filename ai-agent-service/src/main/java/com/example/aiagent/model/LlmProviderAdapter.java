package com.example.aiagent.model;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One model exchange only. ChatRequest retains messages, model parameters, tool declarations
 * and response schema; ChatResponse retains proposals, provider metadata, usage and finish reason.
 * Proposals are untrusted data, never executable callbacks. No AI Services or automatic memory.
 */
public interface LlmProviderAdapter {
    ChatResponse generate(ChatRequest request, CallControl control);

    final class CallControl {
        private final long started = System.nanoTime();
        private final Duration timeout;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final java.util.concurrent.atomic.AtomicReference<Runnable> abort = new java.util.concurrent.atomic.AtomicReference<>();

        public CallControl(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("Invalid model deadline");
            }
        }
        public void cancel() { cancelled.set(true); var action = abort.get(); if (action != null) action.run(); }
        public Duration remaining() {
            check();
            return Duration.ofNanos(Math.max(1, timeout.toNanos() - (System.nanoTime() - started)));
        }
        /** One in-flight exchange, transport cancellation registered before dispatch. */
        public AutoCloseable onCancel(Runnable action) {
            if (!abort.compareAndSet(null, action)) throw new IllegalStateException("Cancellation already registered");
            if (cancelled.get()) action.run();
            return () -> abort.compareAndSet(action, null);
        }
        public void check() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new ProviderException(ProviderException.Category.CANCELLED);
            }
            if (System.nanoTime() - started >= timeout.toNanos()) {
                throw new ProviderException(ProviderException.Category.TIMEOUT);
            }
        }
    }
}
