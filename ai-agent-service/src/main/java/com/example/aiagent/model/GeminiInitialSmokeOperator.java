package com.example.aiagent.model;

import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Separate operator process only. No Spring startup, default execution or ordinary Agent ingress. */
public final class GeminiInitialSmokeOperator {
    private GeminiInitialSmokeOperator() { }
    public static void main(String[] args) {
        try {
            var results = run(args, Clock.systemUTC(), () -> System.getenv("GEMINI_API_KEY"),
                    GeminiHttpTransport::new, GeminiBudget.PROCESS);
            for (int i = 0; i < results.size(); i++)
                System.out.println("VEX1-%02d ".formatted(i + 1) + GeminiOperatorApproval.JSON.writeValueAsString(results.get(i)));
            if (results.size() != 3 || results.stream().anyMatch(r -> !r.category().equals("SUCCESS")))
                throw denied();
        } catch (Exception failure) { throw new IllegalArgumentException("INITIAL_SMOKE_STOPPED_NO_RETRY", null); }
    }
    static List<GeminiExtractionSmoke.Outcome> run(String[] args, Clock clock, Supplier<String> credentials,
            Supplier<GeminiLlmProviderAdapter.Exchange> transport, GeminiBudget process) throws Exception {
        // Paths/key identities are independently provisioned operator configuration, never read from the artifact.
        if (args.length != 9 || !args[0].equals("--execute-initial-smoke")
                || !args[1].equals("--enable-synthetic-live")) throw denied();
        Path publicKey = Path.of(args[2]), revocations = Path.of(args[5]), directory = Path.of(args[7]), control = Path.of(args[8]);
        byte[] artifact = read(Path.of(args[6]), 65536);
        var verifier = GeminiEvaluationPreflight.loadVerifier(publicKey, args[3], args[4], revocations, clock);
        var sequence = verifier.sequence(artifact); var review = sequence.approval().review();
        if (!sequence.fixtureOrder().equals(List.of("VEX1-01", "VEX1-02", "VEX1-03"))) throw denied();
        GeminiExtractionSession.validate(review, clock);
        GeminiSharedAllocation.directory(review.journalDirectory(), directory);
        var c = review.caps(); var verified = GeminiProperties.Verification.VERIFIED;
        var limits = new GeminiProperties(true, true, MODEL, verified, verified, verified, verified,
                GeminiProperties.Verification.NOT_VERIFIED, c.inputPerCall(), c.outputPerCall(), c.requests(),
                c.processRequests(), 1, c.exposureUsd(), c.inputRateUpperBound(), c.outputRateUpperBound());
        if (!limits.validBudgets()) throw denied();
        java.util.function.BooleanSupplier stopped = () -> {
            try {
                if (!Arrays.equals(read(control, 16), "RUN".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) return true;
                GeminiEvaluationPreflight.loadVerifier(publicKey, args[3], args[4], revocations, clock).sequence(artifact);
                return false;
            } catch (Exception failure) { return true; }
        };
        return GeminiAuthorizedExtraction.sequence(true, true, stopped, artifact, verifier, directory, limits,
                process, clock, stopped, stopped, credentials, transport);
    }
    private static byte[] read(Path file, int cap) throws Exception {
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(cap + 1);
            if (bytes.length == 0 || bytes.length > cap) throw denied();
            return bytes;
        }
    }
}
