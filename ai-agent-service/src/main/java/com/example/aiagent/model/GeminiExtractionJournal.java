package com.example.aiagent.model;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Separate create-once session tombstone. NEVER resume/recreate a session, even an empty or corrupt one. */
final class GeminiExtractionJournal implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private boolean failed;
    GeminiExtractionJournal(Path directory, String verifiedSessionId) {
        FileChannel opened = null; FileLock acquired = null;
        try {
            Path root = directory.toAbsolutePath().normalize();
            for (Path part = root; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw GeminiExtractionProtocol.denied();
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw GeminiExtractionProtocol.denied();
            Path file = root.resolve("vex-" + GeminiExtractionProtocol.hash(verifiedSessionId) + ".journal");
            opened = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            acquired = opened.tryLock(); if (acquired == null) throw GeminiExtractionProtocol.denied();
            opened.force(true); // Existing file, including zero/partial bytes, permanently denies a second opener.
            channel = opened; lock = acquired;
        } catch (Exception failure) {
            try { if (acquired != null) acquired.release(); if (opened != null) opened.close(); } catch (Exception ignored) { }
            throw GeminiExtractionProtocol.denied();
        }
    }
    synchronized void append(GeminiExtractionSession.Metadata metadata) {
        try {
            if (failed || !lock.isValid() || channel.size() > 60000) throw GeminiExtractionProtocol.denied();
            byte[] bytes = (GeminiLlmProviderAdapter.JSON.writeValueAsString(metadata) + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 4096 || channel.size() + bytes.length > 65536) throw GeminiExtractionProtocol.denied();
            var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true); // Reservation durable BEFORE caller may obtain a key or dispatch.
        } catch (Exception failure) { failed = true; throw GeminiExtractionProtocol.denied(); }
    }
    @Override public void close() {
        try { lock.release(); channel.close(); }
        catch (Exception failure) { throw GeminiExtractionProtocol.denied(); }
    }
}
