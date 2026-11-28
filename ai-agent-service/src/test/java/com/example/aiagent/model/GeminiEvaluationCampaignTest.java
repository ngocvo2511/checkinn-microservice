package com.example.aiagent.model;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.CLOCK;

class GeminiEvaluationCampaignTest {
    @TempDir Path directory;
    GeminiEvaluationCampaign.Policy policy(int requests, int sessions, int perSession, long tokens, BigDecimal exposure) {
        return new GeminiEvaluationCampaign.Policy("TEST_CAMPAIGN", GeminiEvaluationCampaign.Mode.EXTENDED_NLU_EVALUATION,
                MANIFEST, sessions, requests, tokens, exposure, 1, perSession, tokens, INPUT_CAP, OUTPUT_CAP, 0,
                Duration.ofMinutes(30), CLOCK.instant().plusSeconds(1800), BigDecimal.ONE, new BigDecimal("2"), "TEST_ALLOCATION");
    }
    GeminiEvaluationCampaign.Process process(int requests) { return new GeminiEvaluationCampaign.Process(requests, 3481600, new BigDecimal("100")); }
    GeminiEvaluationCampaign open(GeminiEvaluationCampaign.Policy p, GeminiEvaluationCampaign.Process process) {
        return new GeminiEvaluationCampaign(p, directory, directory, process, CLOCK);
    }
    @ParameterizedTest @ValueSource(ints = {3, 5, 30, 60, 100, 200})
    void finiteCampaignsReserveBeforeOutcomeWithoutRefund(int count) {
        var shared = process(count);
        try (var campaign = open(policy(count, 10, count, (long)count*(INPUT_CAP+OUTPUT_CAP), new BigDecimal("100")), shared)) {
            for (int i=0;i<count;i++) { String sample=hash("TEST_SAMPLE_"+i); campaign.reserve("TEST_SESSION", sample); campaign.complete(sample,true,true); }
            assertThat(campaign.requests()).isEqualTo(count); assertThat(shared.requests()).isEqualTo(count);
            assertThatThrownBy(() -> campaign.reserve("OTHER_SESSION", hash("overflow"))).isInstanceOf(ProviderException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"session", "tokens", "financial", "duplicate", "cross-session", "concurrency", "cancel", "unknown", "failure"})
    void failClosedReservations(String kind) {
        var p=policy(3, 2, kind.equals("session")?1:3, kind.equals("tokens")?17408:52224,
                kind.equals("financial")?new BigDecimal("0.02"):BigDecimal.ONE);
        try (var c=open(p,process(3))) {
            String sample=hash("first"); c.reserve("session",sample);
            if (!kind.equals("concurrency")) c.complete(sample,!kind.equals("unknown"),!kind.equals("failure"));
            if (kind.equals("cancel")) c.seal();
            String next=kind.equals("duplicate")||kind.equals("cross-session")?sample:hash("next");
            assertThatThrownBy(() -> c.reserve(kind.equals("cross-session")?"other":"session",next)).isInstanceOf(ProviderException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"", "partial", "corrupt", "RESERVED"})
    void anyExistingTombstoneRejectsRestartWithoutMutation(String content) throws Exception {
        Path file=directory.resolve("evaluation-v1-"+hash("TEST_CAMPAIGN")+".journal"); Files.writeString(file,content);
        assertThatThrownBy(() -> open(policy(3,1,3,52224,BigDecimal.ONE),process(3))).isInstanceOf(ProviderException.class);
        assertThat(Files.readString(file)).isEqualTo(content);
    }
    @Test void closedCampaignCannotReplayAndUnknownInflightNeverResumes() {
        try(var c=open(policy(3,1,3,52224,BigDecimal.ONE),process(3))) { c.reserve("session",hash("in-flight")); }
        assertThatThrownBy(() -> open(policy(3,1,3,52224,BigDecimal.ONE),process(3))).isInstanceOf(ProviderException.class);
    }
    @Test void sharedProcessAllocationCannotBeBypassedByAnotherCampaign() throws Exception {
        var shared=process(1); var p=policy(3,1,3,52224,BigDecimal.ONE);
        try(var c=open(p,shared)) { c.reserve("session",hash("a")); c.complete(hash("a"),true,true); }
        Path another=Files.createDirectory(directory.resolve("another"));
        try(var c=new GeminiEvaluationCampaign(p,another,another,shared,CLOCK)) {
            assertThatThrownBy(() -> c.reserve("session",hash("b"))).isInstanceOf(ProviderException.class);
        }
        assertThat(shared.requests()).isEqualTo(1);
    }
    @Test void concurrentReservationIsExclusive() throws Exception {
        try(var c=open(policy(3,1,3,52224,BigDecimal.ONE),process(3)); var pool=Executors.newFixedThreadPool(2)) {
            var successes=new AtomicInteger(); var ready=new CountDownLatch(1);
            Runnable r=()-> { try { ready.await(); c.reserve("session",hash(Thread.currentThread().getName())); successes.incrementAndGet(); }
                catch(ProviderException expected) {} catch(InterruptedException e) { Thread.currentThread().interrupt(); } };
            var a=pool.submit(r); var b=pool.submit(r); ready.countDown(); a.get(); b.get(); assertThat(successes).hasValue(1);
        }
    }
    @Test void expiredAndWrongDirectoryFailBeforeReservation() throws Exception {
        var p=policy(3,1,3,52224,BigDecimal.ONE); Path other=Files.createDirectory(directory.resolve("other"));
        assertThatThrownBy(() -> new GeminiEvaluationCampaign(p,directory,other,process(3),CLOCK)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(() -> new GeminiEvaluationCampaign(p,directory,directory,process(3),Clock.offset(CLOCK,Duration.ofHours(1)))).isInstanceOf(ProviderException.class);
    }
}
