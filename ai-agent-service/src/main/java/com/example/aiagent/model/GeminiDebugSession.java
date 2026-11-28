package com.example.aiagent.model;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Fixed-session append-only journal. Fsync reservation BEFORE HTTP; unresolved/crashed runs cannot resume. */
final class GeminiDebugSession implements AutoCloseable {
    static final Path DIRECTORY = Path.of("ai-agent-service", ".local", "gemini-diag-auto-20261123").toAbsolutePath().normalize();
    private static final String HEADER = "CHECKINN_GEMINI_DIAG_V1|gemini-3.5-flash-lite|evidence-empty-v1|5|0.06|priorOperatorAttempts=1\n";
    private static final BigDecimal COST = new BigDecimal("0.008576");
    private final FileChannel channel;
    private final FileLock lock;
    private int attempts;
    private boolean pending, stopped;
    static void initialize(Path directory) {
        try {
            // No create-or-replace/reset: even a half-initialized existing directory is a blocker.
            Files.createDirectories(directory.getParent());
            Files.createDirectory(directory);
            try (var file = FileChannel.open(directory.resolve("session.journal"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                write(file, HEADER); file.force(true);
            }
        } catch (IOException failure) { throw denied(); }
    }
    GeminiDebugSession(Path directory) {
        FileChannel opened = null; FileLock acquired = null;
        try {
            if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(directory.resolve("session.journal"))) throw denied();
            opened = FileChannel.open(directory.resolve("session.journal"), StandardOpenOption.READ, StandardOpenOption.WRITE);
            acquired = opened.tryLock();
            if (acquired == null || opened.size() > 65536) throw denied();
            var bytes = ByteBuffer.allocate((int) opened.size());
            while (bytes.hasRemaining()) if (opened.read(bytes) < 0) throw denied();
            String content = new String(bytes.array(), StandardCharsets.UTF_8);
            if (!content.startsWith(HEADER) || !content.endsWith("\n")) throw denied();
            String rest = content.substring(HEADER.length());
            if (!rest.isEmpty()) for (String line : rest.split("\n")) {
                String[] fields = line.split("\\|", 3);
                if (fields.length != 3) throw denied();
                int number = Integer.parseInt(fields[1]);
                if (fields[0].equals("R")) {
                    if (pending || stopped || number != attempts + 1 || !fields[2].equals(COST.toPlainString()) || number > 5) throw denied();
                    attempts = number; pending = true;
                } else if (fields[0].equals("C")) {
                    if (!pending || number != attempts) throw denied();
                    var result = GeminiLlmProviderAdapter.JSON.readValue(fields[2], GeminiSmokeDiagnostics.Result.class);
                    if (!result.model().equals(GeminiSmokeApproval.MODEL) || !result.fixture().equals(SyntheticGeminiFixtures.SMOKE)
                            || result.attempts() != 1 || result.retries() != 0 || result.localReservationUsd().compareTo(COST) != 0
                            || !result.category().matches("SUCCESS|DISABLED|CONFIGURATION|SYNTHETIC_BOUNDARY|BUDGET_EXCEEDED|TIMEOUT|PROVIDER_TIMEOUT|CANCELLED|UNAVAILABLE|MALFORMED_RESPONSE|SAFETY_REFUSAL|QUOTA_EXCEEDED|UNKNOWN_USAGE|UNSUPPORTED_PROTOCOL")) throw denied();
                    pending = false;
                    stopped = terminal(result.category());
                } else throw denied();
            }
            if (pending) throw denied(); // Never guess whether a crashed reserved request reached Google.
            channel = opened; lock = acquired;
        } catch (Exception failure) {
            try { if (acquired != null) acquired.release(); if (opened != null) opened.close(); } catch (IOException ignored) { }
            throw denied();
        }
    }
    void reserve() {
        if (pending || stopped || attempts >= 5 || COST.multiply(BigDecimal.valueOf(attempts + 1)).compareTo(new BigDecimal("0.06")) > 0) throw denied();
        attempts++; pending = true;
        append("R|" + attempts + "|" + COST.toPlainString() + "\n");
    }
    void complete(GeminiSmokeDiagnostics.Result result) {
        if (!pending || result.attempts() != 1 || result.retries() != 0 || !GeminiSmokeApproval.MODEL.equals(result.model())
                || !SyntheticGeminiFixtures.SMOKE.equals(result.fixture()) || result.localReservationUsd().compareTo(COST) != 0) throw denied();
        try { append("C|" + attempts + "|" + GeminiLlmProviderAdapter.JSON.writeValueAsString(result) + "\n"); }
        catch (IOException ignored) { throw denied(); }
        pending = false; stopped = terminal(result.category());
    }
    private static boolean terminal(String category) {
        return java.util.Set.of("SUCCESS", "QUOTA_EXCEEDED", "SYNTHETIC_BOUNDARY", "SAFETY_REFUSAL", "BUDGET_EXCEEDED", "UNKNOWN_USAGE").contains(category);
    }
    int attempts() { return attempts; }
    BigDecimal exposure() { return COST.multiply(BigDecimal.valueOf(attempts)); }
    private void append(String text) {
        try { channel.position(channel.size()); write(channel, text); channel.force(true); }
        catch (IOException failure) { throw denied(); }
    }
    private static void write(FileChannel file, String text) throws IOException {
        var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (bytes.hasRemaining()) file.write(bytes);
    }
    private static ProviderException denied() { return new ProviderException(ProviderException.Category.BUDGET_EXCEEDED); }
    @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
