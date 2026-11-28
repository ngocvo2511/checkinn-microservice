package com.example.aiagent.model;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Internal synthetic extraction evaluation only. No Agent/domain tools, context/history dispatch or ingress.
 * Purpose-specific process limits are backed by a durable, fully consumed shared allocation envelope. */
final class GeminiEvaluationRunner {
    static final class Control {
        private final boolean intent, enabled;
        private final AtomicBoolean kill = new AtomicBoolean(), cancelled = new AtomicBoolean(), revoked = new AtomicBoolean();
        private volatile LlmProviderAdapter.CallControl active;
        Control(boolean intent, boolean enabled) { this.intent = intent; this.enabled = enabled; }
        void cancel() { cancelled.set(true); abort(); }
        void revoke() { revoked.set(true); abort(); }
        void kill() { kill.set(true); abort(); }
        private void abort() { var call = active; if (call != null) call.cancel(); }
        void check() {
            if (!intent || !enabled || kill.get() || revoked.get()) throw denied();
            if (cancelled.get()) throw new ProviderException(ProviderException.Category.CANCELLED);
        }
    }
    /** Journal-safe metadata only. No input/context/oracle values/model responses/credentials. */
    record Observation(String campaignId, String sessionId, String sampleId, String fixtureId,
                       String datasetVersion, String datasetHash, String oracleVersion, String oracleHash, String category,
                       String status, String provider, String schema, String semantics, String backend, String safety,
                       Integer inputTokens, Integer outputTokens, Integer totalTokens, long latencyMillis,
                       String stopReason, List<String> diagnostics) {
        Observation { diagnostics = List.copyOf(diagnostics); }
    }
    record Report(List<Observation> observations, Map<String, GeminiEvaluationMetrics.Fraction> scores,
                  GeminiEvaluationMetrics.Usage usage, Long latencyP50, Long latencyP95,
                  int planned, int remoteEligible, int attempted, int schemaValid, int premodelBlocked, int notRun,
                  String stopReason, String e2eStatus) {
        Report { observations = List.copyOf(observations); scores = Map.copyOf(scores); }
    }
    record Preflight(String campaignId, int artifactBytes, int samples, int remoteEligible, String datasetHash, String oracleHash) { }
    private static final class Capability {
        private final GeminiEvaluationRunner owner;
        private final Thread thread = Thread.currentThread();
        private final GeminiCampaignApproval.Sample sample;
        private boolean consumed;
        private Capability(GeminiEvaluationRunner owner, GeminiCampaignApproval.Sample sample) { this.owner = owner; this.sample = sample; }
    }
    private final GeminiCampaignApproval.Claims grant;
    private final GeminiEvaluationDataset dataset;
    private final Clock clock;
    private final Control control;
    private final long deadline;
    private final Set<String> consumed = new HashSet<>();
    private final LinkedHashMap<String, Observation> observations = new LinkedHashMap<>();
    private final GeminiEvaluationMetrics metrics = new GeminiEvaluationMetrics();
    private GeminiEvaluationRunner(GeminiCampaignApproval.Claims grant, GeminiEvaluationDataset dataset, Clock clock, Control control) {
        this.grant = grant; this.dataset = dataset; this.clock = clock; this.control = control;
        deadline = System.nanoTime() + grant.policy().duration().toNanos();
        for (var sample : grant.samples()) observations.put(sample.sampleId(), metadata(sample, "NOT_RUN", null, "NOT_RUN", List.of()));
    }
    static Preflight preflight(byte[] artifact, byte[] oracle, GeminiOperatorApproval verifier, Path directory, Clock clock) {
        var dataset = new GeminiEvaluationDataset(oracle, clock);
        var grant = GeminiCampaignApproval.verify(artifact, verifier, dataset, clock);
        GeminiSharedAllocation.directory(grant.journalDirectory(), directory);
        return new Preflight(grant.policy().campaignId(), artifact.length, grant.samples().size(),
                (int) grant.samples().stream().filter(s -> dataset.input(s.fixtureId()).remoteEligible()).count(), grant.datasetHash(), grant.oracleHash());
    }
    static Report run(byte[] artifact, byte[] oracle, GeminiOperatorApproval verifier, Path directory, Clock clock,
                      Control control, Supplier<String> credentials) {
        return run(artifact, oracle, verifier, directory, clock, control, credentials, GeminiHttpTransport::new);
    }
    static Report run(byte[] artifact, byte[] oracle, GeminiOperatorApproval verifier, Path directory, Clock clock,
                      Control control, Supplier<String> credentials, Supplier<GeminiLlmProviderAdapter.Exchange> transport) {
        var dataset = new GeminiEvaluationDataset(oracle, clock);
        var grant = GeminiCampaignApproval.verify(artifact, verifier, dataset, clock);
        control.check(); GeminiSharedAllocation.directory(grant.journalDirectory(), directory);
        return new GeminiEvaluationRunner(grant, dataset, clock, control).execute(directory, credentials, transport);
    }
    private void check() {
        control.check();
        if (clock.instant().isBefore(grant.notBefore()) || !clock.instant().isBefore(grant.policy().expires()) || System.nanoTime() >= deadline) throw denied();
    }
    private Duration remaining() { check(); return Duration.ofNanos(Math.min(deadline - System.nanoTime(), Duration.between(clock.instant(), grant.policy().expires()).toNanos())); }
    private GeminiEvaluationDataset.Input consume(Capability capability) {
        check();
        if (capability == null || capability.owner != this || capability.thread != Thread.currentThread() || capability.consumed
                || !grant.samples().contains(capability.sample) || !consumed.add(capability.sample.sampleId())) throw denied();
        capability.consumed = true;
        var input = dataset.input(capability.sample.fixtureId());
        if (!input.remoteEligible() || !input.binding().equals(capability.sample.binding()) || !input.contextDigest().equals(capability.sample.contextDigest())) throw denied();
        return input;
    }
    private Report execute(Path directory, Supplier<String> credentials, Supplier<GeminiLlmProviderAdapter.Exchange> transport) {
        var p = grant.policy();
        var reservation = new GeminiSharedAllocation.Reservation(p.campaignId(), grant.samples().stream().map(GeminiCampaignApproval.Sample::sessionId).distinct().toList(),
                grant.samples().stream().map(GeminiCampaignApproval.Sample::sampleId).toList(), p.requests(), grant.inputTokens(), grant.outputTokens(), p.tokens(), p.exposure());
        var process = new GeminiEvaluationCampaign.Process(p.requests(), p.tokens(), p.exposure());
        String stop = "COMPLETED"; boolean reconciled = true;
        try (var allocation = new GeminiSharedAllocation(grant.allocation(), reservation, grant.journalDirectory(), directory, clock);
             var campaign = new GeminiEvaluationCampaign(p, grant.journalDirectory(), directory, process, clock)) {
            for (var sample : grant.samples()) {
                try { check(); }
                catch (ProviderException denied) { stop = denied.category().name(); break; }
                var input = dataset.input(sample.fixtureId());
                if (!input.remoteEligible()) {
                    var local = metadata(sample, "PREMODEL_BLOCKED", null, "LOCAL_ONLY", List.of("PREMODEL_OR_DATASET_DENY"));
                    record(local, null, input); campaign.observation(local); continue;
                }
                try { campaign.reserve(sample.sessionId(), hash(sample.sampleId())); }
                catch (RuntimeException denied) { stop = "RESERVATION_DENIED"; reconciled = false; break; }
                var permit = new Capability(this, sample);
                var call = new LlmProviderAdapter.CallControl(Duration.ofNanos(Math.min(Duration.ofSeconds(10).toNanos(), remaining().toNanos())));
                control.active = call;
                var result = GeminiExtractionSmoke.evaluate(consume(permit), p.inputPerCall(), call, this::check, this::remaining, credentials, transport, clock);
                control.active = null;
                var expected = GeminiEvaluationDataset.semantic(dataset.expectation(sample.fixtureId()).response());
                boolean correct = result.schemaValid() && expected.equals(result.semantic());
                boolean safeCategory = Set.of("SUCCESS", "BACKEND_REJECTED").contains(result.category()) && !result.unsafe();
                boolean continueAllowed = safeCategory && (correct || grant.continuation().equals("CONTINUE_SEMANTIC_ONLY"));
                String reason = !safeCategory ? result.category() : !correct ? "SEMANTIC_MISMATCH" : "SUCCESS";
                var observation = metadata(sample, result.attempted() ? "ATTEMPTED" : "NOT_RUN", result, reason, diagnostics(expected, result));
                record(observation, result, input); campaign.observation(observation);
                boolean known = result.input() != null && result.output() != null && result.total() != null;
                try { campaign.complete(hash(sample.sampleId()), known, continueAllowed); }
                catch (RuntimeException denied) { stop = "JOURNAL_OR_WINDOW_UNCERTAINTY"; reconciled = false; break; }
                if (!continueAllowed || !known) { stop = reason; reconciled = false; break; }
            }
            // All unattempted samples retain NOT_RUN, including after safety/cancellation/budget failure.
            for (var sample : grant.samples()) if (!metricIds.contains(sample.sampleId())) {
                var observation = metadata(sample, "NOT_RUN", null, "CAMPAIGN_STOPPED_" + stop, List.of("NOT_ATTEMPTED"));
                observations.put(sample.sampleId(), observation); campaign.observation(observation);
            }
            campaign.seal(); allocation.finish(reconciled);
        } finally { control.active = null; }
        // Record remaining planned samples exactly once in the metrics denominator plan.
        for (var sample : grant.samples()) if (observations.get(sample.sampleId()).status().equals("NOT_RUN")) {
            var input = dataset.input(sample.fixtureId());
            if (!metricIds.contains(sample.sampleId())) addMetrics(observations.get(sample.sampleId()), null, input);
        }
        var list = List.copyOf(observations.values()); var scores = new LinkedHashMap<>(metrics.scores());
        scores.put("modelCorrectBackendAccepted", fraction(list, o -> o.semantics().equals("PASS") && o.backend().equals("ACCEPTED")));
        scores.put("modelCorrectBackendRejected", fraction(list, o -> o.semantics().equals("PASS") && o.backend().equals("REJECTED")));
        scores.put("budgetViolation", fraction(list, o -> o.stopReason().equals("BUDGET_EXCEEDED")));
        return new Report(list, scores, metrics.usage(), optional(metrics.latencyPercentile(.5)), optional(metrics.latencyPercentile(.95)),
                list.size(), (int) grant.samples().stream().filter(s -> dataset.input(s.fixtureId()).remoteEligible()).count(),
                (int) list.stream().filter(o -> o.status().equals("ATTEMPTED")).count(), (int) list.stream().filter(o -> o.schema().equals("PASS")).count(),
                (int) list.stream().filter(o -> o.status().equals("PREMODEL_BLOCKED")).count(), (int) list.stream().filter(o -> o.status().equals("NOT_RUN")).count(), stop, "NOT_RUN_EXTRACTION_ONLY");
    }
    private final Set<String> metricIds = new HashSet<>();
    private void record(Observation observation, GeminiExtractionSmoke.Detailed result, GeminiEvaluationDataset.Input input) {
        Observation prior = observations.get(observation.sampleId());
        if (prior == null || !prior.status().equals("NOT_RUN") || metricIds.contains(observation.sampleId())) throw denied();
        observations.put(observation.sampleId(), observation); addMetrics(observation, result, input);
    }
    private void addMetrics(Observation o, GeminiExtractionSmoke.Detailed r, GeminiEvaluationDataset.Input input) {
        if (!metricIds.add(o.sampleId())) throw denied();
        var expected = dataset.expectation(o.fixtureId());
        metrics.add(new GeminiEvaluationMetrics.Observation(o.sampleId(), input.remoteEligible(), r != null && r.attempted(), r != null && r.apiSuccess(),
                r != null && r.schemaValid(), GeminiEvaluationDataset.semantic(expected.response()), r == null ? null : r.semantic(),
                expected.backendExpected(), r != null && r.backendAccepted(), r != null && r.unsafe(), r != null && r.unauthorizedTool(),
                r != null && r.confirmation(), false, false, r == null ? 0 : r.latencyMillis(),
                r == null || r.input() == null ? null : r.input().longValue(), r == null || r.output() == null ? null : r.output().longValue(),
                r == null || r.total() == null ? null : r.total().longValue()));
    }
    private Observation metadata(GeminiCampaignApproval.Sample s, String status, GeminiExtractionSmoke.Detailed r, String reason, List<String> diagnostics) {
        boolean correct = r != null && r.schemaValid() && GeminiEvaluationDataset.semantic(dataset.expectation(s.fixtureId()).response()).equals(r.semantic());
        return new Observation(grant.policy().campaignId(), s.sessionId(), s.sampleId(), s.fixtureId(), grant.datasetVersion(), grant.datasetHash(), grant.oracleVersion(), grant.oracleHash(),
                dataset.input(s.fixtureId()).category(), status, r == null ? "NOT_RUN" : r.apiSuccess() ? "SUCCESS" : r.category(),
                r == null ? "NOT_RUN" : r.schemaValid() ? "PASS" : "FAIL", r == null ? "NOT_RUN" : correct ? "PASS" : "FAIL",
                r == null ? "NOT_RUN" : r.backendAccepted() ? "ACCEPTED" : "REJECTED", r == null ? "NOT_RUN" : r.unsafe() || r.unauthorizedTool() ? "STOP" : "NO_CRITICAL_PROPOSAL_OBSERVED",
                r == null ? null : r.input(), r == null ? null : r.output(), r == null ? null : r.total(), r == null ? 0 : r.latencyMillis(), reason, diagnostics);
    }
    private static List<String> diagnostics(com.fasterxml.jackson.databind.JsonNode expected, GeminiExtractionSmoke.Detailed result) {
        if (!result.schemaValid()) return List.of("PROTOCOL_OR_SCHEMA_FAILURE");
        var codes = new ArrayList<String>();
        for (String field : List.of("action", "intent", "criteria", "reference", "missingSlots", "inferredFields"))
            if (!expected.path(field).equals(result.semantic().path(field))) codes.add("MISMATCH_" + field.toUpperCase(Locale.ROOT));
        for (String name : List.of("cityName", "hotelName", "checkIn", "checkOut", "adults", "children", "rooms", "minPrice", "maxPrice", "referencePriceExclusive", "starRatings")) {
            var wanted = expected.path("criteria").path(name); var actual = result.semantic().path("criteria").path(name);
            if (wanted.isMissingNode() && !actual.isMissingNode()) codes.add("UNEXPECTED_FIELD_" + name);
            else if (!wanted.isMissingNode() && actual.isMissingNode()) codes.add("MISSING_FIELD_" + name);
            else if (!wanted.path("operation").equals(actual.path("operation"))) codes.add("WRONG_OPERATION_" + name);
        }
        if (!result.backendAccepted()) codes.add("LITERAL_CORROBORATION_REJECTED"); return List.copyOf(codes);
    }
    private static Long optional(OptionalLong n) { return n.isPresent() ? n.getAsLong() : null; }
    private static GeminiEvaluationMetrics.Fraction fraction(List<Observation> list, java.util.function.Predicate<Observation> predicate) {
        return new GeminiEvaluationMetrics.Fraction(list.stream().filter(predicate).count(), list.stream().filter(o -> o.status().equals("ATTEMPTED")).count());
    }
}
