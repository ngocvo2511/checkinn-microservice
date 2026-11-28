package com.example.aiagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.*;

class GeminiSmokeSequenceTest {
    @TempDir Path directory;
    @BeforeAll static void networkGuard(){ GeminiExtractionUnblockTest.denyExternalAndKeys(); }
    @AfterAll static void restoreGuard(){ GeminiExtractionUnblockTest.restore(); }
    @ParameterizedTest @ValueSource(strings={"success","failure2","cancel","revoke"})
    void oneSignedSessionRunsOnlyItsOrderedScopeAndSeals(String kind) throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); ObjectNode payload=support.sequence(); byte[] artifact=support.sign(payload);
        var keys=new AtomicInteger(); var calls=new AtomicInteger(); var cancelled=new AtomicBoolean(); var revoked=new AtomicBoolean();
        var results=GeminiAuthorizedExtraction.sequence(true,true,()->false,artifact,support.verifier,directory,limits(3),new GeminiBudget(),CLOCK,
                cancelled::get,revoked::get,()->{keys.incrementAndGet();return "TEST_SENTINEL";},()->(request,control,timeout)-> {
                    int call=calls.incrementAndGet(); String id="VEX1-%02d".formatted(call);
                    assertThat(request.url()).isEqualTo(ENDPOINT); assertThat(request.body()).isEqualTo(body(id));
                    if(call==2 && kind.equals("cancel")) cancelled.set(true); if(call==2 && kind.equals("revoke")) revoked.set(true);
                    return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(response(call==2&&kind.equals("failure2")?"{}":expected(id))).build();
                });
        assertThat(calls).hasValue(kind.equals("success")?3:2); assertThat(keys.get()).isEqualTo(calls.get()); assertThat(results).hasSize(calls.get());
        assertThatThrownBy(()->GeminiAuthorizedExtraction.sequence(true,true,()->false,artifact,support.verifier,directory,limits(3),new GeminiBudget(),CLOCK,
                ()->false,()->false,()->{keys.incrementAndGet();return "TEST";},()->{throw new AssertionError();})).isInstanceOf(ProviderException.class);
        assertThat(keys.get()).isEqualTo(calls.get());
        try(var files=Files.list(directory)) {
            Path journal=files.filter(p->p.getFileName().toString().startsWith("vex-")).findFirst().orElseThrow();
            String text=Files.readString(journal); assertThat(text.lines().count()).isEqualTo(2L*calls.get());
            if(kind.equals("success")) assertThat(text).contains("SUCCESS_SEALED");
        }
    }
    @ParameterizedTest @ValueSource(strings={"duplicate","fourth","unauthorized","order"})
    void signedButInvalidScopeCannotLookupKey(String kind) throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); ObjectNode payload=support.sequence();
        var order=(com.fasterxml.jackson.databind.node.ArrayNode)payload.path("fixtureOrder");
        if(kind.equals("duplicate")) order.set(1,order.get(0)); if(kind.equals("fourth")) order.add("VEX1-01");
        if(kind.equals("unauthorized")) order.set(1,GeminiOperatorApproval.JSON.getNodeFactory().textNode("VEX1-04"));
        if(kind.equals("order")) order.remove(2);
        byte[] artifact=support.sign(payload); var keys=new AtomicInteger();
        assertThatThrownBy(()->GeminiAuthorizedExtraction.sequence(true,true,()->false,artifact,support.verifier,directory,limits(3),new GeminiBudget(),CLOCK,
                ()->false,()->false,()->{keys.incrementAndGet();return "TEST";},()->{throw new AssertionError();})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0);
    }
    @Test void oldV1RealTransportEntryCannotBypassSharedAllocation() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); var keys=new AtomicInteger();
        assertThatThrownBy(()->GeminiAuthorizedExtraction.execute(true,true,false,null,support.verifier,directory,"VEX1-01",limits(1),CLOCK,
                control(),()->{keys.incrementAndGet();return "TEST";})).isInstanceOf(ProviderException.class);
        assertThat(keys).hasValue(0);
    }
}
