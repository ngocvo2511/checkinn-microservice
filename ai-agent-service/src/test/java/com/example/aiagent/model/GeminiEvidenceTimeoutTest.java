package com.example.aiagent.model;

import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GeminiEvidenceTimeoutTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void realLoopbackExchangeAndSynchronousWorkerHaveEndedBeforeTimeoutContinuation(boolean cancel) throws Exception {
        try(var server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));var workers=Executors.newFixedThreadPool(2)) {
            server.setSoTimeout(5000); var entered=new CountDownLatch(1);
            var socket=workers.submit(()->{try(var peer=server.accept()) {peer.setSoTimeout(5000); entered.countDown();
                try {while(peer.getInputStream().read()!=-1) { }} catch(SocketException closed) { } return true; }});
            var transport=new GeminiHttpTransport(URI.create("http://127.0.0.1:"+server.getLocalPort()+"/")).localDiagnostics();
            var control=new LlmProviderAdapter.CallControl(Duration.ofSeconds(3));
            var request=dev.langchain4j.http.client.HttpRequest.builder().url("https://generativelanguage.googleapis.com/")
                    .method(dev.langchain4j.http.client.HttpMethod.POST).body("{}").addHeader("x-goog-api-key","synthetic-noncredential").build();
            var call=workers.submit(()->GeminiLocalFallbackEvaluation.exchange(transport,request,control,Duration.ofSeconds(2)));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue(); if(cancel) control.cancel();
            var reply=call.get(3,TimeUnit.SECONDS); assertThat(reply.terminated()).isTrue(); assertThat(reply.response()).isNull();
            assertThat(reply.failure()).isIn(ProviderException.Category.PROVIDER_TIMEOUT,ProviderException.Category.CANCELLED);
            assertThat(socket.get(2,TimeUnit.SECONDS)).isTrue(); assertThat(call.isDone()).isTrue();
        }
    }
    @Test void timeoutBeforeDispatchHasNoFabricatedTermination() throws Exception {
        var control=new LlmProviderAdapter.CallControl(Duration.ofMillis(1)); Thread.sleep(5);
        var transport=new GeminiHttpTransport(URI.create("http://127.0.0.1:1/"));
        assertThatThrownBy(()->GeminiLocalFallbackEvaluation.exchange(transport,null,control,Duration.ofSeconds(1))).isInstanceOf(ProviderException.class);
        assertThat(transport.terminated()).isFalse(); assertThat(transport.observedStatus()).isZero();
    }
    @Test void unknownWorkerCannotAdvanceButProvenTimeoutKeepsFullUnknownUsage() {
        var policy=new GeminiLocalFailurePolicy();
        assertThat(policy.decide(0,"TIMEOUT",false,false,false,null,Instant.EPOCH).reason()).isEqualTo("UNKNOWN_IN_FLIGHT");
        policy=new GeminiLocalFailurePolicy(); var decision=policy.decide(0,"TIMEOUT",false,false,true,null,Instant.EPOCH);
        assertThat(decision.proceed()).isTrue(); assertThat(decision.cooldownSeconds()).isGreaterThanOrEqualTo(15);
        assertThat(policy.cumulative).isEqualTo(1);
        assertThat(policy.decide(0,"TIMEOUT",false,false,true,null,Instant.EPOCH).proceed()).isFalse();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void campaignPreservesReservationsAndCannotReopenUnknownTermination(boolean terminated) throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong(); var calls=new java.util.concurrent.atomic.AtomicInteger();
        GeminiLocalFallbackEvaluation.Exchange exchange=(r,c,t)-> {
            if(calls.incrementAndGet()==1) return new GeminiLocalFallbackEvaluation.Reply(null,0,null,terminated,null,ProviderException.Category.TIMEOUT);
            return new GeminiLocalFallbackEvaluation.Reply(dev.langchain4j.http.client.SuccessfulHttpResponse.builder().statusCode(200).headers(java.util.Map.of())
                    .body(GeminiExtractionUnblockTest.response("{\"request\":\"FIND_HOTEL\",\"facts\":[]}")).build(),200,null,true,null);
        };
        var report=GeminiLocalFallbackEvaluation.run(directory,true,false,"DIAGNOSTIC",GeminiQualityEvaluation.EVALUATION_CLOCK,()->"synthetic-noncredential",exchange,
                time::get,time::addAndGet,new GeminiLocalModelFallback(),"campaign",false,VietnameseEvidenceCompiler.VERSION,false,true);
        var first=report.at("/observations/0/attempts/0");
        assertThat(first.path("actualUsageStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(first.path("reservedInputTokens").asInt()).isEqualTo(16384);
        assertThat(first.path("reservedOutputTokens").asInt()).isEqualTo(1024);
        if(terminated) {
            assertThat(calls).hasValue(8); assertThat(report.path("stopReason").asText()).isEqualTo("COMPLETED");
            assertThat(report.at("/observations/1/attempts/0/dispatchOffsetMillis").asLong()).isGreaterThanOrEqualTo(15000);
        } else {
            assertThat(calls).hasValue(1); assertThat(report.path("stopReason").asText()).isEqualTo("UNKNOWN_IN_FLIGHT");
            assertThatThrownBy(()->GeminiLocalFallbackEvaluation.run(directory,true,false,"FROZEN40",GeminiQualityEvaluation.EVALUATION_CLOCK,
                    ()->{throw new AssertionError("credential read after stop");},exchange,time::get,time::addAndGet,new GeminiLocalModelFallback(),
                    "segment-02-cannot-reopen",false,VietnameseEvidenceCompiler.VERSION,false,true)).isInstanceOf(ProviderException.class);
            assertThat(calls).hasValue(1);
        }
    }
}
