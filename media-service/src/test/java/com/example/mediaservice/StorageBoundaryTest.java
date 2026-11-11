package com.example.mediaservice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class StorageBoundaryTest {
    HttpServer server;SupabaseStorageService storage;String base;
    volatile String response="{}";volatile int status=200,bodyDelayMillis;volatile boolean legacyBatch;
    final AtomicInteger calls=new AtomicInteger();
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{calls.incrementAndGet();exchange.getRequestBody().readAllBytes();
            boolean missingBatch=legacyBatch && exchange.getRequestURI().getPath().equals("/storage/v1/object/sign/private");
            String body=missingBatch?"{}":legacyBatch?"{\"signedURL\":\""+exchange.getRequestURI().getRawPath().substring("/storage/v1".length())+"?token=synthetic\"}":response;
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(missingBatch?404:status,bytes.length);
            int delay=missingBatch?0:bodyDelayMillis;
            if(delay>0){exchange.getResponseBody().write(bytes,0,1);exchange.getResponseBody().flush();try{Thread.sleep(delay);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
            try{exchange.getResponseBody().write(bytes,delay>0?1:0,bytes.length-(delay>0?1:0));}finally{exchange.close();}});
        server.start();base="http://127.0.0.1:"+server.getAddress().getPort();
        storage=new SupabaseStorageService(base,"public","private","synthetic-local-storage-test-key");
    }
    @AfterEach void stop() {server.stop(0);}
    @Test void signedUrlMustIdentifyTheRequestedPrivateObject() {
        response="{\"signedURL\":\"/object/sign/private/another-person.pdf?token=synthetic\"}";
        assertThatThrownBy(()->storage.createSignedUrl("identity.pdf",300)).isInstanceOf(java.io.IOException.class);
    }
    @Test void legacyBatchRouteFallbackRemainsCompatibleAndDeduplicatesPaths() throws Exception {
        legacyBatch=true;
        assertThat(storage.createSignedUrls(List.of("identity.pdf","license.pdf","identity.pdf"),300))
                .containsEntry("identity.pdf",base+"/storage/v1/object/sign/private/identity.pdf?token=synthetic")
                .containsEntry("license.pdf",base+"/storage/v1/object/sign/private/license.pdf?token=synthetic");
        assertThat(calls.get()).isEqualTo(3);
    }
    @Test void legacyFallbackHasOneTotalBodyDeadlineInsteadOfOneBudgetPerFile() {
        legacyBatch=true;bodyDelayMillis=400;
        storage=new SupabaseStorageService(base,"public","private","synthetic-local-key",java.net.http.HttpClient.newHttpClient(),600);
        long start=System.nanoTime();
        assertThatThrownBy(()->storage.createSignedUrls(List.of("identity.pdf","license.pdf"),300))
                .isInstanceOf(java.net.http.HttpTimeoutException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toMillis()).isLessThan(1100);
    }
    @Test void deadlineIncludesASlowResponseBodyAfterHeaders() {
        response="{\"signedURL\":\"/object/sign/private/document.pdf?token=synthetic\"}";bodyDelayMillis=1000;
        storage=new SupabaseStorageService(base,"public","private","synthetic-local-key",java.net.http.HttpClient.newHttpClient(),150);
        long start=System.nanoTime();
        assertThatThrownBy(()->storage.createSignedUrl("document.pdf",300)).isInstanceOf(java.net.http.HttpTimeoutException.class);
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start).toMillis()).isLessThan(700);
    }
    @Test void providerRelativeSignedPathIsJoinedExactlyOnce() throws Exception {
        response="{\"signedURL\":\"/object/sign/private/document.pdf?token=synthetic\"}";
        assertThat(storage.createSignedUrl("document.pdf",300)).isEqualTo(base+"/storage/v1/object/sign/private/document.pdf?token=synthetic");
    }
    @Test void batchTransportFailureIsNotAFanoutOfEmptySuccessfulResults() {
        status=503;response="synthetic-private-provider-body";
        assertThatThrownBy(()->storage.createSignedUrls(List.of("identity.pdf","license.pdf"),300))
                .isInstanceOf(java.io.IOException.class).hasMessageNotContaining(response);
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void incompleteBatchCannotClaimThatStoredDocumentsAreAbsent() {
        response="[{\"path\":\"identity.pdf\",\"error\":\"synthetic-private-error\"}]";
        assertThatThrownBy(()->storage.createSignedUrls(List.of("identity.pdf"),300)).isInstanceOf(java.io.IOException.class);
    }
    @Test void providerCannotReturnAnUnrequestedPathOrConflictingDuplicate() {
        response="[{\"path\":\"other.pdf\",\"signedURL\":\"/object/sign/private/other.pdf?token=synthetic\"}]";
        assertThatThrownBy(()->storage.createSignedUrls(List.of("identity.pdf"),300)).isInstanceOf(java.io.IOException.class);
    }
    @Test void signingFailureDoesNotExposeProviderBodyOrDocumentPath(CapturedOutput output) {
        status=503;response="synthetic-private-provider-body";
        assertThatThrownBy(()->storage.createSignedUrl("synthetic-private-identity.pdf",300))
                .isInstanceOf(java.io.IOException.class).hasMessageNotContaining(response).hasMessageNotContaining("synthetic-private-identity");
        assertThat(output.getAll()).doesNotContain(response,"synthetic-private-identity");
    }
    @ParameterizedTest @ValueSource(strings={"../identity.pdf","folder/../identity.pdf","/absolute.pdf","folder\\identity.pdf",""})
    void invalidObjectPathsAreRejectedBeforePrivilegedHttp(String path) {
        assertThatThrownBy(()->storage.createSignedUrl(path,300)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isZero();
    }
}
