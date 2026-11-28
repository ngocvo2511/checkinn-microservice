package com.example.aiagent.model;

import com.example.aiagent.nlu.VietnameseExtraction;
import com.example.aiagent.nlu.VietnameseSecurityPreflight;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.function.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Explicit local synthetic CLI. Never a Spring bean or an ordinary Agent provider.
 * Admission is intentionally separate from optional signed governance. */
public final class GeminiLocalEvaluation {
    static final String ORACLE = "local-development-oracle-v1.json";
    static final String ORACLE_HASH = "c049d90677a652cb6e093c943f639190a6a14bd8f07a6f41f77097c948ff45b3";
    static final String REVIEW = "TECHNICALLY_REVIEWED_DEVELOPMENT_EVALUATION";
    private static final int HARD_CAP = 120;
    private static final Map<String,Integer> PHASE_CAPS = Map.of("TECHNICAL",5,"EXISTING",40,"DIAGNOSTIC",15,"SIMPLIFIED",40,"GENERALIZATION",20);
    private static String phaseKey(String phase, String protocol) {
        return switch(phase) {
            case "INITIAL_SMOKE", "COMPATIBILITY_DIAGNOSTIC" -> "TECHNICAL";
            case "PROMPT_DIAGNOSTIC" -> "DIAGNOSTIC";
            case "SUPPLEMENTAL" -> "GENERALIZATION";
            default -> protocol.equals(VietnameseSemanticCompiler.VERSION) ? "SIMPLIFIED" : "EXISTING";
        };
    }
    record Options(boolean live, String mode, int maxRequests, Path output, boolean debugResponse, String protocol) { }
    record Sample(String id, String phase, GeminiEvaluationDataset.Input input, JsonNode expected, boolean backendExpected) { }
    private GeminiLocalEvaluation() { }

    public static void main(String[] args) {
        // Do not configure Spring or read application/private environment files.
        try {
            var report = run(args, Clock.systemUTC(), () -> System.getenv("GEMINI_API_KEY"),
                    () -> new GeminiHttpTransport().localDiagnostics(), System::nanoTime, nanos -> Thread.sleep(Duration.ofNanos(nanos)));
            System.out.println("LOCAL_DEVELOPMENT_EVALUATION " + report.path("stopReason").asText()
                    + " attempts=" + report.path("actualHttpAttempts").asInt());
            if (!Set.of("OFFLINE_PREFLIGHT", "COMPLETED").contains(report.path("stopReason").asText())) System.exit(2);
        } catch (Exception failure) {
            System.err.println("LOCAL_EVALUATION_REJECTED_NO_RETRY"); System.exit(2);
        }
    }
    @FunctionalInterface interface Pause { void sleep(long nanos) throws InterruptedException; }
    static Options options(String[] args) {
        boolean live = false, debug = false; String mode = "initial-smoke"; int max = 3; Path output = null; String protocol = VERSION;
        var seen = new HashSet<String>();
        for (int i = 0; i < args.length; i++) {
            String flag = args[i]; if (!seen.add(flag)) throw denied();
            switch (flag) {
                case "--live" -> live = true;
                case "--debug-response" -> debug = true;
                case "--offline" -> { }
                case "--protocol" -> { if (++i >= args.length) throw denied(); protocol = args[i]; }
                case "--mode" -> { if (++i >= args.length) throw denied(); mode = args[i]; }
                case "--max-requests" -> { if (++i >= args.length) throw denied(); max = Integer.parseInt(args[i]); }
                case "--output" -> { if (++i >= args.length) throw denied(); output = Path.of(args[i]).toAbsolutePath().normalize(); }
                default -> throw denied();
            }
        }
        if (seen.contains("--offline") && live || !Set.of("semantic-diagnostic", "baseline40", "initial-smoke", "vietnamese-nlu", "compatibility-diagnostic", "reference-price-diagnostic", "quality-baseline", "quality-diagnostic").contains(mode)
                || max < 1 || max > HARD_CAP || mode.equals("initial-smoke") && max != 3
                || mode.equals("compatibility-diagnostic") && max != 1
                || mode.equals("reference-price-diagnostic") && max != 3
                || live && output == null) throw denied();
        if (!Set.of(VERSION, GeminiLocalExtractionContract.V2, GeminiLocalExtractionContract.V3, VietnameseSemanticCompiler.VERSION).contains(protocol)) throw denied();
        return new Options(live, mode, max, output, debug, protocol);
    }
    static byte[] resource(String name) throws Exception {
        try (var in = GeminiLocalEvaluation.class.getResourceAsStream("/gemini/" + name)) {
            byte[] value = Objects.requireNonNull(in).readNBytes(131073);
            if (value.length > 131072) throw denied(); return value;
        }
    }
    static List<Sample> plan(Options options, Clock clock) throws Exception {
        var samples = new ArrayList<Sample>();
        for (int i = 1; i <= 3; i++) {
            String id = "VEX1-%02d".formatted(i); var f = fixture(id);
            var expected = GeminiLlmProviderAdapter.JSON.createObjectNode().put("action", f.path("expectedAction").asText())
                    .put("intent", f.path("expectedIntent").asText()).put("reference", f.path("reference").asText());
            var fields = expected.putObject("fields");
            f.path("expectedCriteria").fields().forEachRemaining(e -> fields.putObject(e.getKey()).put("operation", "REPLACE").set("value", e.getValue()));
            expected.set("missingSlots", f.path("expectedMissingSlots")); expected.putArray("inferredFields");
            samples.add(sample(id, "INITIAL_SMOKE", f, expected, true, clock, options.protocol()));
        }
        if (Set.of("semantic-diagnostic", "baseline40", "vietnamese-nlu", "reference-price-diagnostic", "quality-baseline", "quality-diagnostic").contains(options.mode())) {
            byte[] bytes = resource("vietnamese-nlu-evaluation-candidates-v1.json");
            if (!GeminiEvaluationDataset.HASH.equals(hash(bytes))) throw denied();
            byte[] oracleBytes = resource(ORACLE);
            if (!ORACLE_HASH.equals(hash(oracleBytes))) throw denied();
            var oracle = GeminiLlmProviderAdapter.JSON.readTree(oracleBytes);
            if (!REVIEW.equals(oracle.path("reviewStatus").asText()) || !GeminiEvaluationDataset.HASH.equals(oracle.path("datasetHash").asText())) throw denied();
            var fixtures = new HashMap<String, JsonNode>();
            for (var f : GeminiLlmProviderAdapter.JSON.readTree(bytes).path("fixtures")) fixtures.put(f.path("id").asText(), f);
            for (var entry : oracle.path("fixtures").properties()) {
                var f = fixtures.get(entry.getKey()); var expectation = entry.getValue();
                if (f == null || !expectation.path("remoteApproved").asBoolean() || f.path("remoteDefault").asText().equals("DENY")) throw denied();
                samples.add(sample(entry.getKey(), "VIETNAMESE_NLU", f, expectation.path("response"), expectation.path("backendExpected").asBoolean(), clock, options.protocol()));
            }
        }
        if (options.mode().equals("semantic-diagnostic")) {
            if (options.maxRequests()!=8 || !options.protocol().equals(VietnameseSemanticCompiler.VERSION)) throw denied();
            var diagnostics=new ArrayList<Sample>();
            for(String id:List.of("VNE1-002","VNE1-001","VNE1-003","VNE1-051","VNE1-029","VNE1-014","VNE1-008","VNE1-020")) {
                var source=samples.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
                diagnostics.add(new Sample("SEM-DIAG-"+id,"PROMPT_DIAGNOSTIC",source.input(),source.expected(),source.backendExpected()));
            }
            return List.copyOf(diagnostics);
        }
        if (options.mode().equals("baseline40")) {
            if (options.maxRequests() != 40) throw denied();
            return List.copyOf(samples.subList(3, samples.size()));
        }
        if (Set.of("quality-baseline", "quality-diagnostic").contains(options.mode())) {
            var all = new ArrayList<Sample>();
            for (String id : List.of("VNE1-002", "VNE1-020", "VNE1-050")) {
                var source = samples.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
                all.add(new Sample("DIAG4D-%02d".formatted(all.size() + 1), "PROMPT_DIAGNOSTIC", source.input(), source.expected(), source.backendExpected()));
            }
            for (var probe : GeminiLlmProviderAdapter.JSON.readTree(resource("nlu-quality-diagnostics-v1.json")).path("fixtures"))
                all.add(sample(probe.path("id").asText(), "PROMPT_DIAGNOSTIC", probe, probe.path("expected"), probe.path("backendExpected").asBoolean(), clock, options.protocol()));
            if (options.mode().equals("quality-diagnostic")) {
                // Fixed six remaining diagnostics: three reproductions, decimal, inclusive and non-price.
                all.remove(5);
                if (options.maxRequests() != 6) throw denied();
                return List.copyOf(all);
            }
            all.addAll(samples);
            if (options.maxRequests() != all.size()) throw denied();
            return List.copyOf(all);
        }
        if (options.mode().equals("reference-price-diagnostic")) {
            // Predeclared equivalent-price probes, using the unchanged reviewed expectations.
            // Distinct supplemental identities never overwrite or score as primary observations.
            var probes = new ArrayList<Sample>();
            for (String id : List.of("VNE1-002", "VNE1-020", "VNE1-050")) {
                var source = samples.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
                probes.add(new Sample("SUP1-REF-%02d".formatted(probes.size() + 1), "SUPPLEMENTAL",
                        source.input(), source.expected(), source.backendExpected()));
            }
            return List.copyOf(probes);
        }
        // The count is the total across smoke and main, not a fresh budget for each phase.
        if (options.maxRequests() > samples.size() || options.maxRequests() < 3 && !options.mode().equals("compatibility-diagnostic")) throw denied();
        if (options.mode().equals("compatibility-diagnostic")) {
            var first = samples.getFirst();
            return List.of(new Sample(first.id(), "COMPATIBILITY_DIAGNOSTIC", first.input(), first.expected(), first.backendExpected()));
        }
        return List.copyOf(samples.subList(0, options.maxRequests()));
    }
    private static Sample sample(String id, String phase, JsonNode fixture, JsonNode expected, boolean backend, Clock clock, String protocol) {
        String text = fixture.path("utterance").asText();
        if (!fixture.path("privacyClassification").asText().equals("SYNTHETIC_ONLY")
                || text.isBlank() || VietnameseSecurityPreflight.refused(text)) throw denied();
        VietnameseSecurityPreflight.validate(text); VietnameseExtraction.parse(expected.toString(), clock);
        // Full dated smoke must remain valid under the actual execution clock.
        if (id.equals("VEX1-03")) GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(expected.toString(), clock), text, clock);
        String body = syntheticBody(GeminiLocalExtractionContract.request(text, protocol));
        if (2L * bytes(body) + 1024 > INPUT_CAP) throw denied();
        var input = new GeminiEvaluationDataset.Input(id, text, hash("NO_CONTEXT_SENT"), fixture.path("category").asText(),
                fixture.path("split").asText(), true, body, new Binding(hash(text), hash(GeminiLocalExtractionContract.instruction(protocol)), hash(GeminiLocalExtractionContract.schema(protocol)), hash(body)));
        return new Sample(id, phase, input, expected.deepCopy(), backend);
    }
    /** Monotonic rolling reservations survive phase transitions; never refund failures. */
    static final class Limiter {
        final LongSupplier time; final Pause pause; long deadline;
        final ArrayDeque<Long> reservations = new ArrayDeque<>();
        long last = Long.MIN_VALUE, waited; int reserved;
        Limiter(LongSupplier time, Pause pause) { this.time = time; this.pause = pause; deadline = time.getAsLong() + Duration.ofMinutes(60).toNanos(); }
        void check() {
            if (Thread.currentThread().isInterrupted()) throw new ProviderException(ProviderException.Category.CANCELLED);
            if (time.getAsLong() >= deadline) throw new ProviderException(ProviderException.Category.TIMEOUT);
        }
        void reserve(int cap) throws InterruptedException {
            if (reserved >= cap || reserved >= HARD_CAP) throw denied();
            awaitSlot();
            last = time.getAsLong(); reservations.addLast(last); reserved++;
        }
        void awaitSlot() throws InterruptedException {
            for (;;) {
                check(); long now = time.getAsLong(), minute = Duration.ofSeconds(60).toNanos();
                while (!reservations.isEmpty() && now - reservations.peekFirst() >= minute) reservations.removeFirst();
                long wait = last == Long.MIN_VALUE ? 0 : Math.max(0, Duration.ofSeconds(5).toNanos() - (now - last));
                if (reservations.size() >= 12 || (reservations.size() + 1L) * (INPUT_CAP + OUTPUT_CAP) > 200000)
                    wait = Math.max(wait, minute - (now - reservations.peekFirst()));
                if (wait == 0) return;
                long slice = Math.min(wait, Duration.ofMillis(100).toNanos()); pause.sleep(slice); waited += slice;
            }
        }
        Duration remaining() { check(); return Duration.ofNanos(deadline - time.getAsLong()); }
    }
    static ObjectNode run(String[] args, Clock clock, Supplier<String> key, Supplier<GeminiLlmProviderAdapter.Exchange> transport,
                          LongSupplier time, Pause pause) throws Exception {
        var options = options(args); var samples = plan(options, clock);
        var report = GeminiLlmProviderAdapter.JSON.createObjectNode().put("version", "local-development-evaluation-v2")
                .put("runId", UUID.randomUUID().toString()).put("mode", "LOCAL_DEVELOPMENT_EVALUATION")
                .put("model", MODEL).put("protocolVersion", options.protocol()).put("evaluationMode",options.mode()).put("startedAt", clock.instant().toString())
                .put("responseValidationVersion", "gemini-envelope-usage-extensions-v2")
                .put("instructionDigest", hash(GeminiLocalExtractionContract.instruction(options.protocol()))).put("schemaDigest", hash(GeminiLocalExtractionContract.schema(options.protocol())))
                .put("datasetHash", GeminiEvaluationDataset.HASH).put("oracleHash", hash(resource(ORACLE)))
                .put("oracleReview", REVIEW).put("smokeReview", "DEVELOPER_APPROVED")
                .put("domainCalls", 0).put("supabaseCalls", 0).put("retries", 0).put("fullAgentE2E", "NOT_RUN");
        var rows = report.putArray("observations");
        for (var s : samples) rows.addObject().put("sampleId", s.id()).put("phase", s.phase()).put("status", "NOT_RUN")
                .put("utterance", s.input().utterance()).put("categoryLabel", s.input().category())
                .put("requestDigest", s.input().binding().requestDigest()).put("expectedAction", s.expected().path("action").asText())
                .put("expectedIntent", s.expected().path("intent").asText())
                .put("requestBytes", bytes(s.input().body())).put("conservativeInputBound", 2L * bytes(s.input().body()) + 1024)
                .set("expectedExtraction", s.expected());
        report.put("planned", samples.size()).put("actualHttpAttempts", 0).put("credentialLookups", 0).put("stopReason", "OFFLINE_PREFLIGHT");
        if (!options.live()) return report;
        var limiter = new Limiter(time, pause); var dispatchLimiter = new Limiter(time, pause);
        long runStart = time.getAsLong();
        var metrics = new GeminiEvaluationMetrics();
        var failurePolicy = new GeminiLocalFailurePolicy();
        // CREATE_NEW run record prevents accidentally overwriting/reusing evidence. It is not a distributed quota ledger.
        try (var campaign = FileChannel.open(options.output().resolveSibling("local-campaign.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = campaign.tryLock();
             var file = FileChannel.open(options.output(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            if (lock == null) throw denied();
            // Sibling versioned reports are the local aggregate ledger. Never reuse a directory for a new allowance.
            int previousAttempts = 0;
            var phaseCounts = new HashMap<String,Integer>();
            try (var paths = Files.list(options.output().getParent())) {
                for (var prior : paths.filter(p -> p.toString().endsWith(".json") && !p.equals(options.output())).toList()) {
                    var old = GeminiLlmProviderAdapter.JSON.readTree(Files.readString(prior));
                    if (!old.path("version").asText().startsWith("local-development-evaluation-")) continue;
                    if (!old.path("stopReason").asText().equals("COMPLETED")) throw denied();
                    previousAttempts += old.path("actualHttpAttempts").asInt();
                    long age = Duration.between(Instant.parse(old.path("startedAt").asText()), clock.instant()).toNanos();
                    if (age < 0 || age >= Duration.ofMinutes(60).toNanos()) throw denied();
                    limiter.deadline = Math.min(limiter.deadline, time.getAsLong() + Duration.ofMinutes(60).toNanos() - age);
                    for (var observation : old.path("observations")) {
                        if (!observation.path("status").asText().equals("ATTEMPTED")) throw denied();
                        phaseCounts.merge(phaseKey(observation.path("phase").asText(),old.path("protocolVersion").asText()),1,Integer::sum);
                        long offset = observation.path("dispatchOffsetMillis").asLong() * 1000000L;
                        // Older reports lack an absolute dispatch timestamp: add a conservative second for serialization/fsync.
                        long start = observation.has("dispatchAt") ? time.getAsLong() - Duration.between(Instant.parse(observation.path("dispatchAt").asText()),clock.instant()).toNanos()
                                : time.getAsLong() - age + offset + Duration.ofSeconds(1).toNanos();
                        if (time.getAsLong() - start < Duration.ofSeconds(60).toNanos()) dispatchLimiter.reservations.add(start);
                        dispatchLimiter.last = Math.max(dispatchLimiter.last, start);
                    }
                }
            }
            if (previousAttempts + samples.size() > HARD_CAP) throw denied();
            for(var s:samples) phaseCounts.merge(phaseKey(s.phase(),options.protocol()),1,Integer::sum);
            phaseCounts.forEach((phase,count) -> { if(count>PHASE_CAPS.get(phase)) throw denied(); });
            var ordered = dispatchLimiter.reservations.stream().sorted().toList();
            dispatchLimiter.reservations.clear(); dispatchLimiter.reservations.addAll(ordered);
            dispatchLimiter.reserved = previousAttempts;
            report.put("priorBatchAttempts", previousAttempts);
            report.put("stopReason", "RUNNING"); save(file, report);
            int lookups = 0, attempts = 0;
            for (int i = 0; i < samples.size(); i++) {
                var s = samples.get(i); var row = (ObjectNode) rows.get(i);
                try { limiter.reserve(options.maxRequests()); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); report.put("stopReason", "CANCELLED"); break; }
                catch (ProviderException stopped) { report.put("stopReason", stopped.category().name()); break; }
                row.put("status", "RESERVED"); save(file, report);
                // Rolling waits happen before the per-call 10s clock; reserve again at actual dispatch below.
                dispatchLimiter.awaitSlot();
                var control = new LlmProviderAdapter.CallControl(Duration.ofSeconds(10));
                final int[] reads = {0};
                final String[] credential = {null};
                // Persist the attempted dispatch identity before delegating to the only real exchange.
                final int[] dispatched = {0}; final int[] status = {0};
                final boolean[] terminated = {false}; final String[] retryAfter = {null};
                var result = GeminiExtractionSmoke.evaluate(s.input(), INPUT_CAP, control, limiter::check, limiter::remaining,
                        () -> { reads[0]++; credential[0] = key.get(); return credential[0]; }, () -> (request, cc, timeout) -> {
                            limiter.check();
                            // Recheck actual dispatch starts as well as pre-credential admission:
                            // serialization/credential latency must not shorten rolling wire spacing.
                            try { dispatchLimiter.reserve(HARD_CAP); }
                            catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt(); throw new ProviderException(ProviderException.Category.CANCELLED);
                            }
                            dispatched[0]++;
                            report.put("actualHttpAttempts", report.path("actualHttpAttempts").asInt() + 1);
                            row.put("status", "IN_FLIGHT").put("dispatchOffsetMillis", (time.getAsLong() - runStart) / 1000000).put("dispatchAt",clock.instant().toString());
                            try { save(file, report); } catch (Exception failure) { throw denied(); }
                            // Transport construction is lazy and happens only after all admission checks.
                            var exchange = transport.get();
                            dev.langchain4j.http.client.SuccessfulHttpResponse response;
                            try { response = exchange.execute(request, cc, timeout); }
                            finally { if (exchange instanceof GeminiHttpTransport http) {
                                status[0] = http.observedStatus(); terminated[0] = http.terminated(); retryAfter[0] = http.retryAfter();
                                row.put("transportExceptionClass", http.exceptionClass());
                                if (http.errorBody() != null) row.put("providerErrorBody", http.errorBody());
                            } }
                            status[0] = response.statusCode();
                            terminated[0] = true;
                            if (options.debugResponse()) {
                                try {
                                    Path debugDir = options.output().resolveSibling(options.output().getFileName() + ".responses");
                                    Files.createDirectories(debugDir);
                                    Path debugFile = debugDir.resolve(s.id() + ".json");
                                    Files.writeString(debugFile, sanitizeResponse(response.body(), credential[0]), StandardOpenOption.CREATE_NEW);
                                    row.put("debugResponseArtifact", debugFile.toString());
                                } catch (Exception failure) { throw denied(); }
                            }
                            row.set("wireDiagnostics", diagnose(response.body())); return response;
                        }, clock, GeminiLocalExtractionContract.request(s.input().utterance(), options.protocol()), raw -> {
                            row.put("actualExtraction", sanitizeResponse(raw, credential[0]));
                            if (!options.protocol().equals(VietnameseSemanticCompiler.VERSION)) return raw;
                            row.put("semanticSchemaValid", false).put("compilerResult", "NOT_RUN");
                            var semantic = VietnameseSemanticCompiler.parse(raw);
                            row.put("semanticSchemaValid", true).set("semanticProposal", semantic);
                            row.put("compilerResult", "FAIL");
                            var compiled = VietnameseSemanticCompiler.compile(semantic, clock);
                            row.put("compilerResult", "PASS").set("compiledExtraction",compiled);
                            return compiled.toString();
                        });
                lookups += reads[0]; attempts += dispatched[0];
                var expected = GeminiEvaluationDataset.semantic(s.expected());
                boolean correct = result.schemaValid() && expected.equals(result.semantic());
                metrics.add(new GeminiEvaluationMetrics.Observation(s.id(), true, dispatched[0] > 0, result.apiSuccess(), result.schemaValid(),
                        expected, result.semantic(), s.backendExpected(), result.backendAccepted(), result.unsafe(), result.unauthorizedTool(),
                        result.confirmation(), false, false, result.latencyMillis(), number(result.input()), number(result.output()), number(result.total())));
                row.put("status", dispatched[0] > 0 ? "ATTEMPTED" : "NOT_RUN").put("apiSuccess", result.apiSuccess())
                        .put("httpStatus", status[0]).put("schemaValid", result.schemaValid()).put("semanticPass", correct)
                        .put("backendAccepted", result.backendAccepted()).put("category", result.category()).put("unsafe", result.unsafe())
                        .put("latencyMillis", result.latencyMillis()).put("expectedBackend", s.backendExpected());
                row.put("backendResult", !result.schemaValid() ? "NOT_EVALUATED" : result.backendAccepted() ? "ACCEPTED" : "REJECTED");
                row.put("reservedInputTokens", dispatched[0] * INPUT_CAP).put("reservedOutputTokens", dispatched[0] * OUTPUT_CAP)
                        .put("actualUsageStatus", result.total() == null ? "UNKNOWN" : "KNOWN").put("callTerminated", terminated[0]);
                row.set("pipeline", result.pipeline());
                row.set("expected", expected); row.set("actual", result.unsafe() ? null : result.semantic());
                row.set("inputTokens", GeminiLlmProviderAdapter.JSON.valueToTree(result.input()));
                row.set("outputTokens", GeminiLlmProviderAdapter.JSON.valueToTree(result.output()));
                row.set("totalTokens", GeminiLlmProviderAdapter.JSON.valueToTree(result.total()));
                report.put("actualHttpAttempts", attempts).put("credentialLookups", lookups);
                report.set("scores", GeminiLlmProviderAdapter.JSON.valueToTree(metrics.scores()));
                report.set("usage", GeminiLlmProviderAdapter.JSON.valueToTree(metrics.usage()));
                report.put("rateLimitWaitMillis", (limiter.waited + dispatchLimiter.waited) / 1000000);
                var decision = failurePolicy.decide(status[0], result.category(), result.input() != null && result.output() != null && result.total() != null,
                        result.unsafe() || result.unauthorizedTool() || result.confirmation(), terminated[0], retryAfter[0], clock.instant());
                row.put("continuationDecision", decision.proceed() ? "CONTINUE" : "STOP").put("continuationReason", decision.reason());
                report.put("transientFailures",failurePolicy.cumulative).put("consecutiveTransientFailures",failurePolicy.consecutive)
                        .put("lastContinuationReason",decision.reason());
                String stop = decision.proceed() ? "COMPLETED" : result.category();
                report.put("stopReason", stop); save(file, report);
                if (!stop.equals("COMPLETED")) break;
                if (decision.cooldownSeconds() > 0 && i + 1 < samples.size()) {
                    if (decision.cooldownSeconds() >= limiter.remaining().toSeconds()) { report.put("stopReason", "COOLDOWN_EXCEEDS_DEADLINE"); break; }
                    row.put("cooldownSeconds", decision.cooldownSeconds()); save(file, report);
                    long until = time.getAsLong() + Duration.ofSeconds(decision.cooldownSeconds()).toNanos();
                    while (time.getAsLong() < until) { limiter.check(); pause.sleep(Math.min(Duration.ofMillis(100).toNanos(), until - time.getAsLong())); }
                }
            }
            report.put("durationMillis", (time.getAsLong() - runStart) / 1000000);
            // Keep every eligible planned sample in the scorer's NOT_RUN denominator.
            for (int i = 0; i < samples.size(); i++) if (!rows.get(i).path("status").asText().equals("ATTEMPTED")) {
                var s = samples.get(i);
                // A missing key already produced a nonattempted metrics observation above.
                if (rows.get(i).has("category")) continue;
                metrics.add(new GeminiEvaluationMetrics.Observation(s.id(), true, false, false, false,
                        GeminiEvaluationDataset.semantic(s.expected()), null, s.backendExpected(), false,
                        false, false, false, false, false, 0, null, null, null));
            }
            report.set("scores", GeminiLlmProviderAdapter.JSON.valueToTree(metrics.scores()));
            report.put("usageStatus", metrics.usage().unknownAttempts() == 0 ? "KNOWN" : "UNKNOWN_ATTEMPTS_PRESENT");
            report.put("notRun", samples.size() - attempts); save(file, report);
        }
        return report;
    }
    private static Long number(Integer value) { return value == null ? null : value.longValue(); }
    /** Local synthetic response only; request headers are never accepted by this writer. */
    static String sanitizeResponse(String body, String key) {
        String safe = key == null || key.isBlank() ? body : body.replace(key, "[REDACTED]");
        return safe.replaceAll("AIza[0-9A-Za-z_-]{20,}", "[REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~-]+", "Bearer [REDACTED]")
                .replaceAll("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+", "[REDACTED]")
                .replaceAll("(?i)(\"(?:authorization|x-goog-api-key|cookie|set-cookie|apiKey|password|secret|credential|jwt|access_token|refresh_token)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*(?:\"|$)", "$1\"[REDACTED]\"")
                .replaceAll("(?i)((?:authorization|x-goog-api-key|cookie|set-cookie|api[_-]?key|password|secret|credential|jwt|access_token|refresh_token)\\s*[:=]\\s*)[^\"\\r\\n]+", "$1[REDACTED]");
    }
    /** Shape-only diagnostics; never raw text, arbitrary field names, model IDs, response IDs or headers.
     * This observer cannot admit or repair a response. Shared strict validation remains authoritative. */
    static ObjectNode diagnose(String body) {
        var out = GeminiLlmProviderAdapter.JSON.createObjectNode();
        try {
            if (body == null || bytes(body) > GeminiHttpTransport.MAX_RESPONSE_BYTES) return out.put("bodyBoundValid", false);
            var root = GeminiLlmProviderAdapter.JSON.readTree(body); out.put("jsonObject", root.isObject());
            if (!root.isObject()) return out;
            out.put("rootAllowlistMatch", Set.of("candidates", "usageMetadata", "promptFeedback", "modelVersion", "responseId").containsAll(fields(root)));
            var candidates = root.path("candidates"); out.put("candidateCount", candidates.isArray() ? candidates.size() : -1);
            var c = candidates.path(0);
            if (c.isObject()) out.put("candidateAllowlistMatch", Set.of("content", "finishReason", "index", "safetyRatings").containsAll(fields(c)));
            out.put("finishStop", c.path("finishReason").asText().equals("STOP"));
            var content = c.path("content");
            if (content.isObject()) out.put("contentAllowlistMatch", Set.of("role", "parts").equals(fields(content)));
            var parts = content.path("parts"); out.put("partCount", parts.isArray() ? parts.size() : -1);
            var part = parts.path(0);
            if (part.isObject()) out.put("partAllowlistMatch", Set.of("text", "thoughtSignature", "thought").containsAll(fields(part)));
            out.put("textPresent", part.path("text").isTextual()).put("functionCallPresent", part.has("functionCall"));
            var usage = root.path("usageMetadata");
            if (usage.isObject()) out.put("usageAllowlistMatch", Set.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount", "thoughtsTokenCount", "cachedContentTokenCount").containsAll(fields(usage)));
            boolean counters = true;
            for (String name : List.of("promptTokenCount", "candidatesTokenCount", "totalTokenCount"))
                counters &= usage.path(name).isIntegralNumber() && usage.path(name).canConvertToInt() && usage.path(name).intValue() >= 0;
            out.put("requiredUsageCountersPresent", counters);
        } catch (Exception ignored) { out.put("diagnosticParseFailed", true); }
        return out;
    }
    private static void save(FileChannel file, ObjectNode report) throws Exception {
        byte[] bytes = GeminiLlmProviderAdapter.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        file.position(0); var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) file.write(buffer);
        file.truncate(bytes.length); file.force(true);
    }
}
