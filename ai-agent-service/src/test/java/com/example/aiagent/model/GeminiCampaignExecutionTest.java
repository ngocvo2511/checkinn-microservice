package com.example.aiagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.*;

class GeminiCampaignExecutionTest {
    @TempDir Path directory;
    GeminiEvaluationTestSupport support;
    AtomicInteger keys=new AtomicInteger(), factories=new AtomicInteger(), calls=new AtomicInteger();
    @BeforeAll static void networkGuard(){ GeminiExtractionUnblockTest.denyExternalAndKeys(); }
    @AfterAll static void restoreGuard(){ GeminiExtractionUnblockTest.restore(); }
    @BeforeEach void prepare() throws Exception { support=new GeminiEvaluationTestSupport(directory); }
    GeminiEvaluationRunner.Report run(ObjectNode payload, String kind, GeminiEvaluationRunner.Control control) throws Exception {
        byte[] artifact=support.sign(payload);
        return GeminiEvaluationRunner.run(artifact,support.oracle,support.verifier,directory,CLOCK,control,()-> {
            keys.incrementAndGet();
            try(var files=Files.list(directory)) { assertThat(files.filter(p->p.toString().endsWith(".journal")).allMatch(p->{try{return Files.size(p)>0;}catch(Exception e){throw new AssertionError(e);}})).isTrue(); }
            catch(Exception e){throw new AssertionError(e);} return "TEST_SENTINEL_NOT_REAL";
        },()-> { factories.incrementAndGet(); assertThat(factories.get()).isEqualTo(keys.get()); return (request,cc,timeout)-> {
            int index=calls.getAndIncrement(); var sample=payload.path("samples").get(index);
            String id=sample.path("fixtureId").asText(); var input=support.dataset.input(id);
            assertThat(request.url()).isEqualTo(ENDPOINT); assertThat(request.body()).isEqualTo(input.body());
            assertThat(request.headers().keySet()).containsExactlyInAnyOrder("Content-Type","Accept","x-goog-api-key");
            assertThat(timeout).isLessThanOrEqualTo(Duration.ofSeconds(10));
            String raw=support.dataset.expectation(id).response().toString(); int status=200;
            if(index==0) switch(kind) {
                case "semantic" -> raw=raw.replace("SEARCH","BROWSE");
                case "schema" -> raw="{}";
                case "unsafe" -> raw=raw.replace("\"fields\":{","\"fields\":{\"USER_CONFIRMED\":true,");
                case "cancel" -> control.cancel();
                case "revoke" -> control.revoke();
                case "kill" -> control.kill();
                case "quota" -> status=429;
                case "transport" -> throw new IllegalStateException("RAW_PROVIDER_SECRET_MUST_NOT_PERSIST");
                default -> { }
            }
            String body=response(raw);
            if(index==0 && kind.equals("unknown")) body=body.replace("\"totalTokenCount\":20","\"totalTokenCount\":null");
            if(index==0 && kind.equals("budget")) body=body.replace("\"totalTokenCount\":20","\"totalTokenCount\":999999");
            if(index==0 && kind.equals("tool")) body=GeminiProviderTest.response("[{\"functionCall\":{\"name\":\"forbidden_write\",\"args\":{}}}]");
            return SuccessfulHttpResponse.builder().statusCode(status).headers(Map.of()).body(body).build();
        }; });
    }
    @ParameterizedTest @ValueSource(ints={30,60,100,200})
    void signedCampaignRunsEverySampleThroughExactMockAndImmutableIngestion(int count) throws Exception {
        ObjectNode payload=support.campaign(count); byte[] artifact=support.sign(payload);
        assertThat(artifact.length).isLessThanOrEqualTo(GeminiCampaignApproval.MAX_ARTIFACT_BYTES);
        var preflight=GeminiEvaluationRunner.preflight(artifact,support.oracle,support.verifier,directory,CLOCK);
        assertThat(preflight.samples()).isEqualTo(count); assertThat(keys).hasValue(0); assertThat(factories).hasValue(0);
        var report=run(payload,"success",new GeminiEvaluationRunner.Control(true,true));
        assertThat(report.planned()).isEqualTo(count); assertThat(report.attempted()).isEqualTo(count); assertThat(report.notRun()).isZero();
        assertThat(report.schemaValid()).isEqualTo(count); assertThat(report.stopReason()).isEqualTo("COMPLETED");
        assertThat(report.scores().get("exactSemantics")).isEqualTo(new GeminiEvaluationMetrics.Fraction(count,count));
        assertThat(report.usage().knownAttempts()).isEqualTo(count); assertThat(report.usage().unknownAttempts()).isZero();
        assertThat(report.e2eStatus()).isEqualTo("NOT_RUN_EXTRACTION_ONLY");
        assertThat(keys).hasValue(count); assertThat(factories).hasValue(count); assertThat(calls).hasValue(count);
        assertThatThrownBy(()->report.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->run(payload,"success",new GeminiEvaluationRunner.Control(true,true))).isInstanceOf(ProviderException.class);
        assertThat(calls).hasValue(count);
        try(var files=Files.list(directory)) { for(Path p:files.toList()) assertThat(Files.readString(p)).doesNotContain("TEST_SENTINEL_NOT_REAL","RAW_PROVIDER_SECRET","Đà Lạt","functionCall"); }
    }
    @Test void semanticFailureRemainsInDenominatorAndContinuesWithoutRetry() throws Exception {
        var report=run(support.campaign(3),"semantic",new GeminiEvaluationRunner.Control(true,true));
        assertThat(calls).hasValue(3); assertThat(report.notRun()).isZero();
        assertThat(report.scores().get("intent")).isEqualTo(new GeminiEvaluationMetrics.Fraction(2,3));
        assertThat(report.observations().getFirst().stopReason()).isEqualTo("SEMANTIC_MISMATCH");
    }
    @Test void correctSemanticsRejectedByGrammarAreNotModelFailure() throws Exception {
        ObjectNode payload=support.campaign(3); ObjectNode sample=(ObjectNode)payload.path("samples").get(0);
        // 041 is now supported by the reviewed discovery grammar. Invalid-calendar clarification
        // remains a conservative backend coverage gap and still exercises this classification.
        var input=support.dataset.input("VNE1-009"); sample.put("fixtureId",input.fixtureId()).put("contextDigest",input.contextDigest());
        sample.set("binding",GeminiOperatorApproval.JSON.valueToTree(input.binding()));
        var report=run(payload,"success",new GeminiEvaluationRunner.Control(true,true));
        assertThat(report.observations().getFirst().semantics()).isEqualTo("PASS");
        assertThat(report.observations().getFirst().backend()).isEqualTo("REJECTED");
        assertThat(report.scores().get("modelCorrectBackendRejected").numerator()).isEqualTo(1); assertThat(calls).hasValue(3);
    }
    @Test void reviewedNaturalDiscoveryIsAcceptedWithoutChangingModelScore() throws Exception {
        ObjectNode payload=support.campaign(3); ObjectNode sample=(ObjectNode)payload.path("samples").get(0);
        var input=support.dataset.input("VNE1-041"); sample.put("fixtureId",input.fixtureId()).put("contextDigest",input.contextDigest());
        sample.set("binding",GeminiOperatorApproval.JSON.valueToTree(input.binding()));
        var report=run(payload,"success",new GeminiEvaluationRunner.Control(true,true));
        assertThat(report.observations().getFirst().semantics()).isEqualTo("PASS");
        assertThat(report.observations().getFirst().backend()).isEqualTo("ACCEPTED");
        assertThat(report.scores().get("modelCorrectBackendRejected").numerator()).isZero(); assertThat(calls).hasValue(3);
    }
    @ParameterizedTest @ValueSource(strings={"schema","unsafe","unknown","quota","budget","tool","transport","cancel","revoke","kill"})
    void criticalStopRetainsNotRunSamplesAndNeverRetries(String kind) throws Exception {
        var report=run(support.campaign(3),kind,new GeminiEvaluationRunner.Control(true,true));
        assertThat(calls).hasValue(1); assertThat(keys).hasValue(1); assertThat(report.attempted()).isEqualTo(1); assertThat(report.notRun()).isEqualTo(2);
        assertThat(report.scores().get("schemaValid").denominator()).isEqualTo(1); assertThat(report.stopReason()).isNotEqualTo("COMPLETED");
        if(kind.equals("unsafe")) assertThat(report.scores().get("fabricatedConfirmation").numerator()).isEqualTo(1);
        if(kind.equals("tool")) assertThat(report.scores().get("unauthorizedTool").numerator()).isEqualTo(1);
        if(kind.equals("unknown")) assertThat(report.usage().unknownAttempts()).isEqualTo(1);
    }
    @Test void strictSignedSemanticPolicyStopsAtMismatch() throws Exception {
        ObjectNode payload=support.campaign(3); payload.put("continuation","STOP_ON_ANY_FAILURE");
        var report=run(payload,"semantic",new GeminiEvaluationRunner.Control(true,true)); assertThat(calls).hasValue(1); assertThat(report.notRun()).isEqualTo(2);
    }
    @Test void twoSignedCampaignsShareDurableAllocationWithoutResettingConsumption() throws Exception {
        var allocation = support.allocation(6,"C1","C2");
        for (String campaign : List.of("C1","C2")) {
            keys.set(0); factories.set(0); calls.set(0);
            ObjectNode payload = support.campaign(3); payload.set("allocation",GeminiOperatorApproval.JSON.valueToTree(allocation));
            ((ObjectNode)payload.path("policy")).put("campaignId",campaign);
            for(var sample:payload.path("samples")) ((ObjectNode)sample).put("sampleId",campaign+sample.path("sampleId").asText()).put("sessionId",campaign+"_SESSION");
            var report = run(payload,"success",new GeminiEvaluationRunner.Control(true,true));
            assertThat(report.attempted()).isEqualTo(3); assertThat(calls).hasValue(3);
        }
        Path ledger=directory.resolve("allocation-v1-"+hash("TEST_ALLOCATION")+".journal");
        assertThat(Files.readString(ledger).lines().filter(s->s.contains("\"type\":\"RESERVED\"")).count()).isEqualTo(2);
    }
    @Test void changedOracleBytesCannotOverrideSignedOracleIdentity() throws Exception {
        byte[] artifact=support.sign(support.campaign(3));
        byte[] changed=new String(support.oracle,java.nio.charset.StandardCharsets.UTF_8).replace("TEST_ORACLE_V1","TEST_ORACLE_V2").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(()->GeminiEvaluationRunner.run(artifact,changed,support.verifier,directory,CLOCK,new GeminiEvaluationRunner.Control(true,true),
                ()->{keys.incrementAndGet();return "TEST";},()->{factories.incrementAndGet();throw new AssertionError();})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(factories).hasValue(0);
    }
    @Test void deniedDatasetInputNeverDispatchedOrCountedAsRemoteAttempt() throws Exception {
        ObjectNode payload=support.campaign(1); ObjectNode sample=(ObjectNode)payload.path("samples").get(0);
        var input=support.dataset.input("VNE1-054"); sample.put("fixtureId",input.fixtureId()).put("contextDigest",input.contextDigest());
        sample.set("binding",GeminiOperatorApproval.JSON.valueToTree(input.binding()));
        var report=run(payload,"success",new GeminiEvaluationRunner.Control(true,true));
        assertThat(calls).hasValue(0); assertThat(keys).hasValue(0); assertThat(report.premodelBlocked()).isEqualTo(1);
        assertThat(report.remoteEligible()).isZero(); assertThat(report.scores().get("apiSuccess").denominator()).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"issuer","keyId","model","protocol","datasetHash","oracleHash","oracleVersion","purpose","state","continuation","pricingCoverage","classification","notBefore","expired","mode","financial","sample","context","utterance","request","schema","duplicate","missing","unknown","allocation","evidence"})
    void signedSubstitutionsRejectBeforeCredentialLookup(String kind) throws Exception {
        ObjectNode payload=support.campaign(3); ObjectNode sample=(ObjectNode)payload.path("samples").get(0);
        switch(kind) {
            case "issuer","keyId","model","protocol","datasetHash","oracleHash","oracleVersion","purpose","continuation","pricingCoverage","classification" -> payload.put(kind,"WRONG");
            case "state" -> payload.put("state","SEALED");
            case "notBefore" -> payload.put("notBefore",CLOCK.instant().plusSeconds(1).toString());
            case "expired" -> ((ObjectNode)payload.path("policy")).put("expires",CLOCK.instant().minusSeconds(1).toString());
            case "mode" -> ((ObjectNode)payload.path("policy")).put("mode","INITIAL_SMOKE");
            case "financial" -> ((ObjectNode)payload.path("policy")).put("exposure",0.00001);
            case "sample" -> sample.put("fixtureId","UNAPPROVED");
            case "context" -> sample.put("contextDigest","0".repeat(64));
            case "utterance","request","schema" -> ((ObjectNode)sample.path("binding")).put(kind.equals("utterance")?"fixtureDigest":kind+"Digest","0".repeat(64));
            case "duplicate" -> ((ObjectNode)payload.path("samples").get(1)).put("sampleId",sample.path("sampleId").asText());
            case "missing" -> payload.remove("operatorReference");
            case "unknown" -> payload.put("approved",true);
            case "allocation" -> ((ObjectNode)payload.path("allocation")).put("id","OTHER_ALLOCATION");
            case "evidence" -> ((ObjectNode)payload.path("evidence")).put("expires",CLOCK.instant().minusSeconds(1).toString());
            default -> throw new AssertionError(kind);
        }
        assertThatThrownBy(()->run(payload,"success",new GeminiEvaluationRunner.Control(true,true))).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(factories).hasValue(0); assertThat(calls).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings={"signature","root","unsigned","revoked","directory","disabled","cancelled","journal","missing","duplicate-key","oversize"})
    void outerGatesNeverResolveCredentials(String kind) throws Exception {
        ObjectNode payload=support.campaign(3); byte[] artifact=support.sign(payload); var verifier=support.verifier;
        var controls=new GeminiEvaluationRunner.Control(true,!kind.equals("disabled")); Path target=directory;
        if(kind.equals("signature")) { var envelope=(ObjectNode)GeminiOperatorApproval.JSON.readTree(artifact); envelope.put("signature",Base64.getEncoder().encodeToString(new byte[64])); artifact=envelope.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        if(kind.equals("root")) verifier=new GeminiOperatorApproval(Map.of("TEST_KEY",new GeminiOperatorApproval.Root("TEST_ISSUER",KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic())),Set.of(),CLOCK);
        if(kind.equals("unsigned")) artifact=GeminiOperatorApproval.JSON.writeValueAsBytes(payload);
        if(kind.equals("revoked")) verifier=new GeminiOperatorApproval(Map.of("TEST_KEY",new GeminiOperatorApproval.Root("TEST_ISSUER",support.signer.getPublic())),Set.of("TEST_APPROVAL"),CLOCK);
        if(kind.equals("directory")) target=Files.createDirectory(directory.resolve("other"));
        if(kind.equals("cancelled")) controls.cancel();
        if(kind.equals("journal")) Files.writeString(directory.resolve("allocation-v1-"+hash("TEST_ALLOCATION")+".journal"),"partial");
        if(kind.equals("missing")) artifact=null;
        if(kind.equals("duplicate-key")) artifact=new String(artifact,java.nio.charset.StandardCharsets.UTF_8).replace("\"algorithm\":","\"algorithm\":\"Ed25519\",\"algorithm\":").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(kind.equals("oversize")) artifact=new byte[262145];
        final byte[] bytes=artifact; final var trust=verifier; final var dir=target;
        assertThatThrownBy(()->GeminiEvaluationRunner.run(bytes,support.oracle,trust,dir,CLOCK,controls,()->{keys.incrementAndGet();return "TEST";},()->{factories.incrementAndGet();throw new AssertionError();})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0); assertThat(factories).hasValue(0);
    }
}
