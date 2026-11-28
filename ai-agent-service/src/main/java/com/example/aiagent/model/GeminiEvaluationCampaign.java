package com.example.aiagent.model;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Isolated OFFLINE reservation infrastructure, NOT a live grant or alternative provider dispatch path.
 * A campaign is CREATE_NEW only; any restart seals its remaining allocation. Never automatically resume. */
final class GeminiEvaluationCampaign implements AutoCloseable {
    enum Mode { INITIAL_SMOKE, VIETNAMESE_NLU_EVALUATION, EXTENDED_NLU_EVALUATION, SYNTHETIC_E2E_EVALUATION }
    record Policy(String campaignId, Mode mode, String manifest, int sessions, int requests,
                  long tokens, BigDecimal exposure, int concurrency, int sessionRequests, long sessionTokens,
                  int inputPerCall, int outputPerCall, int retries, Duration duration, Instant expires,
                  BigDecimal inputRate, BigDecimal outputRate, String allocationReference) { }
    /** One shared instance per reviewed project allocation/process; finite, never refunds uncertain spend. */
    static final class Process {
        private final int maxRequests;
        private final long maxTokens;
        private final BigDecimal maxExposure;
        private int requests;
        private long tokens;
        private BigDecimal exposure = BigDecimal.ZERO;
        Process(int requests, long tokens, BigDecimal exposure) {
            if (requests < 1 || tokens < 1 || exposure == null || exposure.signum() <= 0) throw denied();
            maxRequests = requests; maxTokens = tokens; maxExposure = exposure;
        }
        synchronized void reserve(long count, BigDecimal cost) {
            if (requests >= maxRequests || count > maxTokens - tokens || exposure.add(cost).compareTo(maxExposure) > 0) throw denied();
            requests++; tokens += count; exposure = exposure.add(cost);
        }
        synchronized int requests() { return requests; }
    }
    private final Policy policy;
    private final Clock clock;
    private final Process process;
    private final FileChannel journal;
    private final FileLock lock;
    private final long deadline;
    private final Map<String, Integer> sessions = new HashMap<>();
    private final Map<String, Long> sessionTokens = new HashMap<>();
    private final Set<String> attempts = new HashSet<>();
    private final Set<String> active = new HashSet<>();
    private int requests;
    private long tokens;
    private BigDecimal exposure = BigDecimal.ZERO;
    private boolean sealed;
    GeminiEvaluationCampaign(Policy p, Path trustedDirectory, Path requestedDirectory, Process process, Clock clock) {
        FileChannel opened = null; FileLock acquired = null;
        try {
            validate(p, clock); this.policy = p; this.clock = clock; this.process = Objects.requireNonNull(process);
            Path directory = trustedDirectory.toAbsolutePath().normalize();
            if (!trustedDirectory.isAbsolute() || !directory.equals(requestedDirectory.toAbsolutePath().normalize())
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw denied();
            for (Path part = directory; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw denied();
            opened = FileChannel.open(directory.resolve("evaluation-v1-" + hash(p.campaignId()) + ".journal"),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            acquired = opened.tryLock(); if (acquired == null) throw denied();
            journal = opened; lock = acquired; deadline = System.nanoTime() + p.duration().toNanos();
            append("CAMPAIGN " + hash(p.campaignId()) + " " + p.manifest());
        } catch (Exception failure) {
            try { if (acquired != null) acquired.release(); if (opened != null) opened.close(); } catch (Exception ignored) { }
            throw denied();
        }
    }
    static void validate(Policy p, Clock clock) {
        if (p == null || p.mode() == null || p.campaignId() == null || !p.campaignId().matches("[A-Za-z0-9_-]{1,100}")
                || p.manifest() == null || !p.manifest().matches("[a-f0-9]{64}") || p.sessions() < 1 || p.sessions() > 200
                || p.requests() < 1 || p.requests() > 200 || p.tokens() < 1 || p.tokens() > 3481600
                || p.sessionRequests() < 1 || p.sessionRequests() > p.requests() || p.sessionTokens() < 1 || p.sessionTokens() > p.tokens()
                || p.concurrency() != 1 || p.retries() != 0 || p.inputPerCall() < 1024 || p.inputPerCall() > INPUT_CAP
                || p.outputPerCall() < 1 || p.outputPerCall() > OUTPUT_CAP || p.duration() == null || p.duration().isNegative()
                || p.duration().isZero() || p.duration().compareTo(Duration.ofHours(2)) > 0
                || p.expires() == null || !clock.instant().isBefore(p.expires())
                || p.exposure() == null || p.exposure().signum() <= 0 || p.inputRate() == null || p.inputRate().signum() <= 0
                || p.outputRate() == null || p.outputRate().signum() <= 0
                || p.exposure().precision() > 32 || Math.abs((long) p.exposure().scale()) > 12
                || p.inputRate().precision() > 32 || Math.abs((long) p.inputRate().scale()) > 12
                || p.outputRate().precision() > 32 || Math.abs((long) p.outputRate().scale()) > 12
                || p.allocationReference() == null || !p.allocationReference().matches("[A-Za-z0-9_-]{1,100}")) throw denied();
    }
    /** fixtureSampleDigest identifies an independently approved fixture/sample, not a retry counter. */
    synchronized void reserve(String session, String fixtureSampleDigest) {
        check();
        if (session == null || !session.matches("[A-Za-z0-9_-]{1,100}") || fixtureSampleDigest == null
                || !fixtureSampleDigest.matches("[a-f0-9]{64}") || attempts.contains(fixtureSampleDigest)
                || !active.isEmpty() || !sessions.containsKey(session) && sessions.size() >= policy.sessions()) throw denied();
        long count = (long) policy.inputPerCall() + policy.outputPerCall();
        BigDecimal cost = policy.inputRate().multiply(BigDecimal.valueOf(policy.inputPerCall()))
                .add(policy.outputRate().multiply(BigDecimal.valueOf(policy.outputPerCall()))).movePointLeft(6);
        if (requests >= policy.requests() || count > policy.tokens() - tokens
                || sessions.getOrDefault(session, 0) >= policy.sessionRequests()
                || count > policy.sessionTokens() - sessionTokens.getOrDefault(session, 0L)
                || exposure.add(cost).compareTo(policy.exposure()) > 0) throw denied();
        // Process consumption may remain if disk append fails. Never reclaim uncertain reservations.
        process.reserve(count, cost);
        append("RESERVED " + hash(session) + " " + fixtureSampleDigest + " " + count + " " + cost.toPlainString());
        sessions.merge(session, 1, Integer::sum); sessionTokens.merge(session, count, Long::sum);
        attempts.add(fixtureSampleDigest); active.add(fixtureSampleDigest); requests++; tokens += count; exposure = exposure.add(cost);
    }
    synchronized void complete(String sample, boolean usageKnown, boolean success) {
        check(); if (!active.remove(sample)) throw denied();
        append("OUTCOME " + sample + " " + (usageKnown ? "KNOWN" : "UNKNOWN") + " " + (success ? "CONTINUE" : "STOP"));
        if (!usageKnown || !success) sealed = true;
    }
    synchronized void observation(GeminiEvaluationRunner.Observation observation) {
        try { append(GeminiOperatorApproval.JSON.writeValueAsString(observation)); }
        catch (Exception failure) { sealed = true; throw denied(); }
    }
    synchronized void seal() { if (!sealed) { append("SEALED"); sealed = true; } }
    private void check() {
        if (sealed || !lock.isValid() || !clock.instant().isBefore(policy.expires()) || System.nanoTime() >= deadline) {
            sealed = true; throw denied();
        }
    }
    private void append(String value) {
        try {
            byte[] bytes = (value + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 4096 || journal.size() + bytes.length > 524288) throw denied();
            ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) journal.write(buffer); journal.force(true);
        } catch (Exception failure) { sealed = true; throw denied(); }
    }
    synchronized int requests() { return requests; }
    @Override public synchronized void close() {
        try { seal(); } finally {
            try { lock.release(); journal.close(); } catch (Exception failure) { throw denied(); }
        }
    }
}
