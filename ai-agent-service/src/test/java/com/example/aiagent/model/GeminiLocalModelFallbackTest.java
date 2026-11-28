package com.example.aiagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GeminiLocalModelFallbackTest {
    @TempDir Path directory;
    static final Clock CLOCK=GeminiExtractionUnblockTest.CLOCK;
    static final String HIGH="{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.\"}}";
    final AtomicLong time=new AtomicLong();
    final List<String> models=new ArrayList<>();
    ObjectNode run(int failures, String terminal, boolean eligible) throws Exception {
        return GeminiLocalFallbackEvaluation.run(directory,true,true,"TECHNICAL",CLOCK,()->"synthetic-noncredential",(request,control,timeout)-> {
            models.add(request.url()); assertThat(timeout).isLessThanOrEqualTo(Duration.ofSeconds(10));
            if(models.size()<=failures) return new GeminiLocalFallbackEvaluation.Reply(null,503,HIGH,true,"16");
            if(terminal!=null && terminal.matches("[0-9]+")) return new GeminiLocalFallbackEvaluation.Reply(null,Integer.parseInt(terminal),HIGH,true,null);
            if("UNKNOWN_IN_FLIGHT".equals(terminal)) return new GeminiLocalFallbackEvaluation.Reply(null,503,HIGH,false,null);
            // Deliberately incorrect but strictly valid: semantic failure is contained, never answer-shopped.
            String raw="{\"action\":\"CRITERIA\",\"intent\":\"SEARCH\",\"fields\":{},\"reference\":\"\",\"missingSlots\":[],\"inferredFields\":[]}";
            String body=GeminiExtractionUnblockTest.response("SCHEMA".equals(terminal)?"{}":raw);
            if("SAFETY".equals(terminal)) body=body.replace("STOP","SAFETY");
            if("UNKNOWN_USAGE".equals(terminal)) {
                body=body.replace("promptTokenCount", "untrustedCounter");
            }
            return new GeminiLocalFallbackEvaluation.Reply(SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(body).build(),200,null,true,null);
        },time::get,time::addAndGet,new GeminiLocalModelFallback(m->eligible||m==GeminiLocalModelProfile.FLASH_35_LITE));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void boundedChainAndRunLocalSegmentKeepIdentityAndFullReservations(int failures) throws Exception {
        var report=run(failures,null,true);
        assertThat(report.path("stopReason").asText()).isEqualTo("COMPLETED");
        assertThat(report.path("actualHttpAttempts").asInt()).isEqualTo(3+failures);
        assertThat(report.path("observations").get(0).path("attempts").size()).isEqualTo(failures+1);
        assertThat(report.path("observations").get(1).path("attempts").size()).isEqualTo(1);
        var model=GeminiLocalModelProfile.values()[failures];
        assertThat(models.subList(failures,models.size())).allMatch(url->url.equals(model.endpoint()));
        for(var row:report.path("observations")) for(var attempt:row.path("attempts")) {
            assertThat(attempt.path("reservedInputTokens").asInt()).isEqualTo(16384);
            assertThat(attempt.path("reservedOutputTokens").asInt()).isEqualTo(1024);
            if(attempt.path("httpStatus").asInt()==503) assertThat(attempt.path("totalTokens").isNull()).isTrue();
        }
        assertThatThrownBy(()->run(0,null,true)).isInstanceOf(FileAlreadyExistsException.class);
    }
    @Test void exhaustedChainAndIneligibleProfilesStopWithoutSameModelRetry() throws Exception {
        var report=run(4,null,true);
        assertThat(models).hasSize(4).doesNotHaveDuplicates();
        assertThat(report.path("stopReason").asText()).isEqualTo("ALL_ELIGIBLE_MODELS_UNAVAILABLE");
        assertThat(report.path("observations").get(1).path("status").asText()).isEqualTo("NOT_RUN");
        var selector=new GeminiLocalModelFallback(m->m==GeminiLocalModelProfile.FLASH_35_LITE||m==GeminiLocalModelProfile.FLASH_25);
        assertThat(selector.after(GeminiLocalModelProfile.FLASH_35_LITE,503,true,HIGH,null,CLOCK.instant()).next()).isEqualTo(GeminiLocalModelProfile.FLASH_25);
    }
    @ParameterizedTest @ValueSource(strings={"400","401","403","429"})
    void hardHttpStopsNeverFallback(String status) throws Exception {
        var report=run(0,status,true); assertThat(models).hasSize(1);
        assertThat(report.path("stopReason").asText()).isEqualTo("HTTP_"+status+"_STOP");
    }
    @Test void capacityMustBeActualTerminated503ProviderEnvelopeAndRetryAfterIsBounded() {
        assertThat(GeminiLocalModelFallback.capacity(200,true,HIGH)).isFalse();
        assertThat(GeminiLocalModelFallback.capacity(503,false,HIGH)).isFalse();
        assertThat(GeminiLocalModelFallback.capacity(503,true,"high demand")).isFalse();
        assertThat(GeminiLocalModelFallback.capacity(503,true,"{\"error\":{\"code\":503,\"status\":\"UNAVAILABLE\",\"message\":\"unknown\"}}")).isFalse();
        var selector=new GeminiLocalModelFallback(m->true);
        assertThat(selector.after(GeminiLocalModelProfile.FLASH_35_LITE,503,true,HIGH,"60",CLOCK.instant()).cooldownSeconds()).isEqualTo(60);
        selector.capacityFailures=7;
        assertThat(selector.after(GeminiLocalModelProfile.FLASH_31_LITE,503,true,HIGH,null,CLOCK.instant()).reason()).isEqualTo("CAPACITY_FAILURE_CEILING");
    }
    @Test void modelSpecificRollingLimitsAndDailyCapShareMonotonicTime() throws Exception {
        var limiter=new GeminiLocalEvaluation.Limiter(time::get,time::addAndGet);
        var starts=new ArrayDeque<Long>();
        for(int i=0;i<5;i++) starts.add(0L);
        GeminiLocalFallbackEvaluation.awaitModel(GeminiLocalModelProfile.FLASH_25,starts,5,limiter,time::get,time::addAndGet);
        assertThat(time.get()).isEqualTo(Duration.ofMinutes(1).toNanos());
        assertThatThrownBy(()->GeminiLocalFallbackEvaluation.awaitModel(GeminiLocalModelProfile.FLASH_25,starts,20,limiter,time::get,time::addAndGet)).isInstanceOf(ProviderException.class);
        for(int i=0;i<120;i++) limiter.reserve(120);
        assertThatThrownBy(()->limiter.reserve(120)).isInstanceOf(ProviderException.class);
    }
    @ParameterizedTest @ValueSource(strings={"SCHEMA","SAFETY","UNKNOWN_USAGE","UNKNOWN_IN_FLIGHT"})
    void schemaSafetyUsageAndUncertainCallsCannotTriggerModelFallback(String terminal) throws Exception {
        var report=run(0,terminal,true);
        assertThat(models).allMatch(url->url.equals(GeminiLocalModelProfile.FLASH_35_LITE.endpoint()));
        assertThat(models).hasSize(terminal.equals("SCHEMA")?3:1);
        assertThat(report.path("capacityFailures").asInt()).isZero();
        if(terminal.equals("SCHEMA")) assertThat(report.path("stopReason").asText()).isEqualTo("COMPLETED");
        else assertThat(report.path("stopReason").asText()).isNotEqualTo("COMPLETED");
    }
    @Test void typedProfilesSerializeSdkWithoutChangingBusinessSchemaAndRejectUntrustedEndpoint() throws Exception {
        var request=GeminiLocalExtractionContract.request("Tìm khách sạn Huế",VietnameseSemanticCompiler.VERSION);
        for(var profile:GeminiLocalModelProfile.values()) {
            var capture=new GeminiExtractionProtocol.Capture();
            GeminiExtractionProtocol.model("synthetic-noncredential",capture,Duration.ofSeconds(10),profile).chat(request);
            var wire=GeminiExtractionProtocol.normalize(capture.request,profile);
            assertThat(wire.url()).isEqualTo(profile.endpoint());
            assertThat(wire.body()).contains("responseJsonSchema","maxOutputTokens\":1024").doesNotContain("thinkingConfig","synthetic-noncredential");
            assertThat(GeminiLlmProviderAdapter.JSON.readTree(wire.body()).has("tools")).isFalse();
            var other=profile==GeminiLocalModelProfile.FLASH_25?GeminiLocalModelProfile.FLASH_31_LITE:GeminiLocalModelProfile.FLASH_25;
            assertThatThrownBy(()->GeminiExtractionProtocol.normalize(capture.request,other)).isInstanceOf(ProviderException.class);
        }
    }
    @Test void offlinePreflightFreezesAllNinetyLogicalSamplesWithoutReadingKey() throws Exception {
        var report=GeminiLocalFallbackEvaluation.run(directory,false,true,"ALL",CLOCK,()->{throw new AssertionError("key");},
                (r,c,t)->{throw new AssertionError("socket");},time::get,time::addAndGet,new GeminiLocalModelFallback());
        assertThat(report.path("observations").size()).isEqualTo(90);
        assertThat(report.path("actualHttpAttempts").asInt()).isZero();
        assertThat(report.path("contracts").path(GeminiLocalExtractionContract.V3).path("instructionDigest").asText()).isEqualTo("458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46");
    }
    @Test void servingSchemaSimplificationPreservesCanonicalValidation() throws Exception {
        String canonical=VietnameseSemanticCompiler.schema();
        assertThat(GeminiLocalModelProfile.FLASH_35_LITE.wireSchema(canonical)).isEqualTo(canonical);
        var wire=GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalModelProfile.FLASH_25_LITE.wireSchema(canonical));
        assertThat(wire.path("required").toString()).isEqualTo("[\"intent\"]");
        assertThat(wire.path("additionalProperties").asBoolean()).isFalse();
        assertThat(wire.at("/properties/adults").has("maximum")).isFalse();
        assertThatThrownBy(()->VietnameseSemanticCompiler.parse("{\"intent\":\"SEARCH\",\"adults\":999}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VietnameseSemanticCompiler.parse("{\"intent\":\"SEARCH\",\"arrival\":\"2026-02-31\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThat(VietnameseSemanticCompiler.schema()).isEqualTo(canonical);
        var a=GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalModelProfile.FLASH_25_LITE.wireSchema(GeminiLocalExtractionContract.schema(GeminiLocalExtractionContract.V3)));
        assertThat(a.at("/properties/fields/properties/cityName/oneOf/1/properties/operation/enum").toString()).isEqualTo("[\"REPLACE\"]");
        assertThat(a.at("/properties/fields/properties/cityName/oneOf/1/properties/operation").has("const")).isFalse();
    }
    @Test void distinctCompletedSegmentsShareBudgetReservationsAndCannotReopenPriorRecord() throws Exception {
        run(0,null,true);
        byte[] original=Files.readAllBytes(directory.resolve("campaign.json"));
        var next=GeminiLocalFallbackEvaluation.run(directory,true,true,"MODEL_VERIFICATION",CLOCK,()->"synthetic-noncredential",
                (r,c,t)->new GeminiLocalFallbackEvaluation.Reply(SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                        .body(GeminiExtractionUnblockTest.response("{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\"}")).build(),200,null,true,null),
                time::get,time::addAndGet,new GeminiLocalModelFallback(),"segment-01-verification",true);
        assertThat(next.path("priorBatchAttempts").asInt()).isEqualTo(3);
        assertThat(next.path("actualHttpAttempts").asInt()).isEqualTo(1);
        assertThat(next.at("/observations/0/attempts/0/attemptId").asText()).isEqualTo("ATT-004");
        assertThat(next.at("/phaseAttempts/FALLBACK_RESERVE").asInt()).isEqualTo(1);
        assertThat(Files.readAllBytes(directory.resolve("campaign.json"))).isEqualTo(original);
    }
    @Test void explicitModelVerificationUsesOnlyTypedSelectedModelAndAdaptedWireAfterKnown400() throws Exception {
        run(0,"400",true);
        byte[] original=Files.readAllBytes(directory.resolve("campaign.json"));
        var report=GeminiLocalFallbackEvaluation.run(directory,true,false,"MODEL_VERIFICATION",CLOCK,()->"synthetic-noncredential",(request,control,timeout)-> {
            assertThat(request.url()).isEqualTo(GeminiLocalModelProfile.FLASH_25_LITE.endpoint());
            assertThat(request.body()).doesNotContain("\"minimum\"","\"maximum\"","\"format\"");
            return new GeminiLocalFallbackEvaluation.Reply(SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                    .body(GeminiExtractionUnblockTest.response("{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\"}")).build(),200,null,true,null);
        },time::get,time::addAndGet,new GeminiLocalModelFallback(m->m==GeminiLocalModelProfile.FLASH_25_LITE),"segment-01-wire-verification",false);
        assertThat(report.path("priorBatchAttempts").asInt()).isEqualTo(1);
        assertThat(report.path("actualHttpAttempts").asInt()).isEqualTo(1);
        assertThat(report.at("/observations/0/attempts/0/requestedPrimaryModel").asText()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(report.at("/modelProfiles/0/eligible").asBoolean()).isTrue();
        assertThat(report.at("/modelProfiles/0/selectedForSegment").asBoolean()).isFalse();
        assertThat(Files.readAllBytes(directory.resolve("campaign.json"))).isEqualTo(original);
    }
    @Test void fixedAProtocolVerificationUsesAdaptedProviderSchemaAndOriginalLocalValidator() throws Exception {
        var report=GeminiLocalFallbackEvaluation.run(directory,true,false,"MODEL_VERIFICATION",CLOCK,()->"synthetic-noncredential",(request,control,timeout)-> {
            assertThat(request.url()).isEqualTo(GeminiLocalModelProfile.FLASH_25_LITE.endpoint());
            assertThat(request.body()).contains("oneOf","referencePriceExclusive").doesNotContain("\"minimum\"","\"pattern\"");
            return new GeminiLocalFallbackEvaluation.Reply(SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                    .body(GeminiExtractionUnblockTest.response(GeminiExtractionUnblockTest.expected("VEX1-01"))).build(),200,null,true,null);
        },time::get,time::addAndGet,new GeminiLocalModelFallback(m->m==GeminiLocalModelProfile.FLASH_25_LITE),"segment-01-a-wire",false,GeminiLocalExtractionContract.V3);
        assertThat(report.at("/observations/0/attempts/0/schemaValid").asBoolean()).isTrue();
        assertThat(report.at("/observations/0/attempts/0/backendAccepted").asBoolean()).isTrue();
    }
}
