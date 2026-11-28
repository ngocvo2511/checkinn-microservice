package com.example.aiagent.model;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Closed campaign-v1 payload; every sample/session/continuation/allocation field is signed. */
final class GeminiCampaignApproval {
    static final int MAX_ARTIFACT_BYTES = 262144; // Separate campaign cap; smoke remains 64KiB.
    static final String PURPOSE = "VIETNAMESE_NLU_SYNTHETIC_EVALUATION";
    record Sample(String sampleId, String sessionId, String fixtureId, String contextDigest, Binding binding) { }
    record Claims(String version, String issuer, String keyId, String operatorReference, String approvalReference,
                  String state, String purpose, String model, String protocol, String datasetVersion, String datasetHash,
                  String oracleVersion, String oracleHash, String classification, Instant notBefore,
                  String continuation, String revocationPolicy, String pricingCoverage,
                  long inputTokens, long outputTokens, Path journalDirectory, GeminiOperatorApproval.Evidence evidence,
                  GeminiSharedAllocation.Limits allocation, GeminiEvaluationCampaign.Policy policy, List<Sample> samples) {
        Claims { samples = List.copyOf(samples); }
        @Override public String toString() { return "CampaignClaims[payloads=omitted]"; }
    }
    private GeminiCampaignApproval() { }
    static Claims verify(byte[] artifact, GeminiOperatorApproval verifier, GeminiEvaluationDataset dataset, Clock clock) {
        try {
            Claims c = GeminiOperatorApproval.JSON.treeToValue(verifier.signedPayload(artifact, MAX_ARTIFACT_BYTES), Claims.class);
            validate(c, verifier, dataset, clock); return c;
        } catch (Exception failure) { throw denied(); }
    }
    static void validate(Claims c, GeminiOperatorApproval verifier, GeminiEvaluationDataset dataset, Clock clock) {
        var p = c.policy(); var e = c.evidence(); var a = c.allocation();
        verifier.checkReference(c.approvalReference()); verifier.checkReference(p.campaignId()); verifier.checkReference(a.id());
        GeminiSharedAllocation.validate(a, clock); GeminiEvaluationCampaign.validate(p, clock);
        if (!"operator-evaluation-campaign-v1".equals(c.version()) || !PURPOSE.equals(c.purpose())
                || !GeminiSharedAllocation.identity(c.operatorReference()) || !GeminiSharedAllocation.identity(c.approvalReference())
                || !"APPROVED_FOR_SEPARATE_LIVE_BATCH".equals(c.state()) || !MODEL.equals(c.model()) || !VERSION.equals(c.protocol())
                || !GeminiEvaluationDataset.DATASET_VERSION.equals(c.datasetVersion()) || !GeminiEvaluationDataset.HASH.equals(c.datasetHash())
                || !c.oracleVersion().equals(dataset.oracleVersion()) || !c.oracleHash().equals(dataset.oracleHash())
                || !"SYNTHETIC_ONLY".equals(c.classification()) || clock.instant().isBefore(c.notBefore())
                || !c.notBefore().isBefore(p.expires()) || p.expires().isAfter(a.expires()) || c.notBefore().isBefore(a.notBefore())
                || p.duration().compareTo(Duration.between(c.notBefore(), p.expires())) > 0
                || !Set.of(GeminiEvaluationCampaign.Mode.VIETNAMESE_NLU_EVALUATION, GeminiEvaluationCampaign.Mode.EXTENDED_NLU_EVALUATION).contains(p.mode())
                || !p.manifest().equals(c.datasetHash()) || p.outputPerCall() != OUTPUT_CAP
                || !Set.of("CONTINUE_SEMANTIC_ONLY", "STOP_ON_ANY_FAILURE").contains(c.continuation())
                || !"DENY_ON_REVOCATION_OR_UNCERTAINTY".equals(c.revocationPolicy())
                || !"ALL_BILLABLE_CLASSES_UPPER_BOUND".equals(c.pricingCoverage())
                || !p.allocationReference().equals(a.id()) || !e.allocationReference().equals(a.id())
                || !e.projectReference().equals(a.project()) || !a.campaigns().contains(p.campaignId())
                || e.verifiedAt().isAfter(clock.instant()) || e.expires().isBefore(p.expires())
                || c.journalDirectory() == null || !c.journalDirectory().isAbsolute()
                || c.samples().isEmpty() || c.samples().size() != p.requests() || c.samples().size() > 200
                || c.inputTokens() < (long) p.requests() * p.inputPerCall() || c.inputTokens() > a.inputTokens()
                || c.outputTokens() < (long) p.requests() * p.outputPerCall() || c.outputTokens() > a.outputTokens()
                || p.tokens() < (long) p.requests() * (p.inputPerCall() + p.outputPerCall())
                || p.tokens() > a.totalTokens() || p.requests() > a.requests() || p.exposure().compareTo(a.exposure()) > 0) throw denied();
        for (String reference : List.of(e.projectReference(), e.pricingReference(), e.quotaReference(), e.privacyReference(),
                e.allocationReference(), e.coordinatedWindowReference())) if (!GeminiSharedAllocation.identity(reference)) throw denied();
        var ids = new HashSet<String>(); var sessions = new HashMap<String, Integer>(); var ended = new HashSet<String>(); String last = null;
        for (Sample sample : c.samples()) {
            var input = dataset.input(sample.fixtureId()); var expected = dataset.expectation(sample.fixtureId());
            if (!GeminiSharedAllocation.identity(sample.sampleId()) || !ids.add(sample.sampleId()) || !GeminiSharedAllocation.identity(sample.sessionId())
                    || !sample.binding().equals(input.binding()) || !sample.contextDigest().equals(input.contextDigest())
                    || !expected.remoteApproved() && input.remoteEligible() || bytes(input.body()) > BYTE_CAP
                    || 2L * bytes(input.body()) + 1024 > p.inputPerCall()) throw denied();
            if (!Objects.equals(last, sample.sessionId())) {
                if (last != null) ended.add(last); if (ended.contains(sample.sessionId())) throw denied(); last = sample.sessionId();
            }
            if (sessions.merge(sample.sessionId(), 1, Integer::sum) > p.sessionRequests()
                    || (long) sessions.get(sample.sessionId()) * (p.inputPerCall() + p.outputPerCall()) > p.sessionTokens()) throw denied();
        }
        if (sessions.size() > p.sessions() || p.inputRate().multiply(java.math.BigDecimal.valueOf((long) p.requests() * p.inputPerCall()))
                .add(p.outputRate().multiply(java.math.BigDecimal.valueOf((long) p.requests() * p.outputPerCall())))
                .movePointLeft(6).compareTo(p.exposure()) > 0) throw denied();
    }
}
