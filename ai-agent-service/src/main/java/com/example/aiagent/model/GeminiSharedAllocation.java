package com.example.aiagent.model;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Single-machine, trusted-filesystem allocation. Holds the OS lock for the whole campaign.
 * Reserves the ENTIRE signed campaign envelope up front, without refunds. A lost owner seals
 * the allocation: an unterminated reservation cannot be resumed by any campaign/JVM. */
final class GeminiSharedAllocation implements AutoCloseable {
    record Limits(String id, String project, Instant notBefore, Instant expires, int requests,
                  long inputTokens, long outputTokens, long totalTokens, BigDecimal exposure,
                  int concurrency, List<String> campaigns) {
        Limits { campaigns = List.copyOf(campaigns); }
    }
    record Reservation(String campaign, List<String> sessions, List<String> samples, int requests,
                       long inputTokens, long outputTokens, long totalTokens, BigDecimal exposure) {
        Reservation { sessions = List.copyOf(sessions); samples = List.copyOf(samples); }
    }
    private final FileChannel file;
    private final FileLock lock;
    private final String binding;
    private String previous = "0".repeat(64);
    private boolean finished, failed, closed;
    static Path directory(Path trusted, Path requested) {
        try {
            Path root = trusted.toAbsolutePath().normalize();
            if (!trusted.isAbsolute() || !root.equals(requested.toAbsolutePath().normalize())
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw denied();
            for (Path part = root; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw denied();
            return root;
        } catch (Exception failure) { throw denied(); }
    }
    static void validate(Limits l, Clock clock) {
        if (l == null || !identity(l.id()) || !identity(l.project()) || clock.instant().isBefore(l.notBefore())
                || !clock.instant().isBefore(l.expires()) || !l.notBefore().isBefore(l.expires())
                || Duration.between(l.notBefore(), l.expires()).compareTo(Duration.ofHours(2)) > 0
                || l.requests() < 1 || l.requests() > 200 || l.inputTokens() < 1 || l.inputTokens() > 3276800
                || l.outputTokens() < 1 || l.outputTokens() > 204800 || l.totalTokens() < 1 || l.totalTokens() > 3481600
                || l.totalTokens() > l.inputTokens() + l.outputTokens() || !money(l.exposure()) || l.concurrency() != 1
                || l.campaigns().isEmpty() || l.campaigns().size() > 200
                || new HashSet<>(l.campaigns()).size() != l.campaigns().size()
                || l.campaigns().stream().anyMatch(c -> !identity(c))) throw denied();
    }
    static boolean identity(String s) { return s != null && s.matches("[A-Za-z0-9_-]{1,100}"); }
    static boolean money(BigDecimal v) { return v != null && v.signum() > 0 && v.precision() <= 32 && Math.abs((long) v.scale()) <= 12; }
    GeminiSharedAllocation(Limits limits, Reservation reservation, Path trusted, Path requested, Clock clock) {
        FileChannel opened = null; FileLock acquired = null;
        try {
            validate(limits, clock);
            binding = hash(GeminiOperatorApproval.canonical(GeminiOperatorApproval.JSON.valueToTree(limits)));
            Path target = directory(trusted, requested).resolve("allocation-v1-" + hash(limits.id()) + ".journal");
            boolean created;
            try { opened = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); created = true; }
            catch (FileAlreadyExistsException existing) {
                opened = FileChannel.open(target, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); created = false;
            }
            acquired = opened.tryLock(); if (acquired == null) throw denied(); file = opened; lock = acquired;
            var reservations = created ? new ArrayList<Reservation>() : read();
            if (created) append("HEADER", null);
            validateReservation(limits, reservation, reservations);
            append("RESERVED", reservation); // All envelope ceilings durable BEFORE any key/transport.
        } catch (Exception failure) {
            try { if (acquired != null) acquired.release(); if (opened != null) opened.close(); } catch (Exception ignored) { }
            throw denied();
        }
    }
    private static void validateReservation(Limits l, Reservation r, List<Reservation> previous) {
        if (r == null || !l.campaigns().contains(r.campaign()) || r.sessions().isEmpty() || r.sessions().size() > 200
                || r.samples().isEmpty() || r.samples().size() != r.requests() || r.requests() < 1 || r.requests() > 200
                || r.sessions().stream().anyMatch(s -> !identity(s)) || r.samples().stream().anyMatch(s -> !identity(s))
                || new HashSet<>(r.sessions()).size() != r.sessions().size() || new HashSet<>(r.samples()).size() != r.samples().size()
                || r.inputTokens() < 1 || r.outputTokens() < 1 || r.totalTokens() < 1
                || r.totalTokens() > r.inputTokens() + r.outputTokens() || !money(r.exposure())) throw denied();
        long requests = r.requests(), input = r.inputTokens(), output = r.outputTokens(), total = r.totalTokens();
        BigDecimal cost = r.exposure();
        var sessions = new HashSet<>(r.sessions()); var samples = new HashSet<>(r.samples());
        for (var p : previous) {
            if (p.campaign().equals(r.campaign()) || p.sessions().stream().anyMatch(s -> !sessions.add(s))
                    || p.samples().stream().anyMatch(s -> !samples.add(s))) throw denied();
            requests = Math.addExact(requests, p.requests()); input = Math.addExact(input, p.inputTokens());
            output = Math.addExact(output, p.outputTokens()); total = Math.addExact(total, p.totalTokens()); cost = cost.add(p.exposure());
        }
        if (requests > l.requests() || input > l.inputTokens() || output > l.outputTokens()
                || total > l.totalTokens() || cost.compareTo(l.exposure()) > 0) throw denied();
    }
    private ArrayList<Reservation> read() throws Exception {
        if (file.size() < 1 || file.size() > 524288) throw denied();
        ByteBuffer buffer = ByteBuffer.allocate((int) file.size()); file.position(0);
        while (buffer.hasRemaining()) if (file.read(buffer) < 0) throw denied();
        String text = new String(buffer.array(), StandardCharsets.UTF_8);
        if (!text.endsWith("\n")) throw denied();
        var values = new ArrayList<Reservation>(); boolean pending = false; int line = 0;
        for (String raw : text.split("\n")) {
            if (raw.length() > 65536) throw denied();
            // Missing reservation is represented explicitly as JSON null in HEADER/terminal records.
            var node = GeminiOperatorApproval.JSON.readTree(raw);
            if (!fields(node).equals(Set.of("type", "binding", "previous", "reservation", "checksum"))) throw denied();
            String checksum = node.path("checksum").asText();
            var unsigned = ((com.fasterxml.jackson.databind.node.ObjectNode) node).deepCopy(); unsigned.remove("checksum");
            if (!hash(GeminiOperatorApproval.canonical(unsigned)).equals(checksum)
                    || !node.path("binding").asText().equals(binding) || !node.path("previous").asText().equals(previous)) throw denied();
            String type = node.path("type").asText();
            if (line++ == 0) { if (!type.equals("HEADER") || !node.path("reservation").isNull()) throw denied(); }
            else if (type.equals("RESERVED") && !pending) {
                values.add(GeminiOperatorApproval.JSON.treeToValue(node.get("reservation"), Reservation.class)); pending = true;
            } else if (type.equals("COMPLETE") && pending && node.path("reservation").isNull()) pending = false;
            else throw denied(); // UNCERTAIN, extra header, corrupt or unknown record permanently denies reuse.
            previous = checksum;
        }
        if (pending || values.isEmpty()) throw denied(); return values; // A header-only bootstrap is uncertain too.
    }
    private void append(String type, Reservation reservation) {
        try {
            if (failed || !lock.isValid()) throw denied();
            var node = GeminiOperatorApproval.JSON.createObjectNode().put("type", type).put("binding", binding).put("previous", previous);
            // Only allowlisted reviewed identities/counters; no utterances, credentials or model payloads.
            if (reservation == null) node.putNull("reservation");
            else node.set("reservation", GeminiOperatorApproval.JSON.valueToTree(reservation));
            String checksum = hash(GeminiOperatorApproval.canonical(node)); node.put("checksum", checksum);
            byte[] bytes = (node + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 65536 || file.size() + bytes.length > 524288) throw denied();
            file.position(file.size()); ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer);
            file.force(true); previous = checksum;
        } catch (Exception failure) { failed = true; throw denied(); }
    }
    /** Terminal accounting is never a refund. Only a fully reconciled run permits another signed campaign. */
    synchronized void finish(boolean reconciled) { if (finished || closed) throw denied(); append(reconciled ? "COMPLETE" : "UNCERTAIN", null); finished = true; }
    @Override public synchronized void close() {
        if (closed) return;
        try { if (!finished && !failed) append("UNCERTAIN", null); }
        finally { closed = true; try { lock.release(); file.close(); } catch (Exception failure) { throw denied(); } }
    }
}
