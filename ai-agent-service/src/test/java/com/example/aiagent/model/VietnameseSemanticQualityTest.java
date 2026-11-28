package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VietnameseSemanticQualityTest {
    static final Clock CLOCK=GeminiQualityEvaluation.EVALUATION_CLOCK;
    com.fasterxml.jackson.databind.node.ObjectNode compile(String raw) {
        return VietnameseSemanticCompiler.compile(VietnameseSemanticCompiler.parse(raw),CLOCK,VietnameseSemanticCompiler.V2);
    }
    void admit(String raw,String text) { GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(compile(raw).toString(),CLOCK),text,CLOCK); }
    @Test void actualB40ReplayPreservesAllContradictionRejectionsAndFixesFourFalseRejections() throws Exception {
        var data=GeminiLlmProviderAdapter.JSON.readTree(getClass().getResourceAsStream("/gemini/actual-b40-semantic-v1.json"));
        int raw=0,valid=0,exact=0,accepted=0,wrongAccepted=0,compilerReject=0;
        var changed=new TreeSet<String>();
        for(var row:data.path("observations")) {
            var semantic=VietnameseSemanticCompiler.parse(row.path("actualExtractionText").asText()); raw++;
            com.fasterxml.jackson.databind.JsonNode canonical;
            try { canonical=VietnameseSemanticCompiler.compile(semantic,CLOCK); }
            catch(IllegalArgumentException rejected) { compilerReject++; assertThat(row.path("compilerResult").asText()).isEqualTo("FAIL"); continue; }
            valid++; boolean correct=GeminiEvaluationDataset.semantic(row.path("expectedExtraction")).equals(GeminiEvaluationDataset.semantic(canonical));
            if(correct) exact++;
            boolean admitted=false;
            try {
                var proposal=VietnameseExtraction.parse(canonical.toString(),CLOCK);
                var effective=GeminiExtractionSmoke.corroborate(proposal,row.path("utterance").asText(),CLOCK);
                admitted=proposal.action()==VietnameseExtraction.Action.CLARIFY||effective.clarification()==null;
            } catch(IllegalArgumentException|DateTimeException rejected) { }
            if(admitted) { accepted++; if(!correct) wrongAccepted++; }
            if(admitted!=row.path("backendAccepted").asBoolean()) changed.add(row.path("sampleId").asText());
        }
        assertThat(raw).isEqualTo(40); assertThat(valid).isEqualTo(26); assertThat(exact).isEqualTo(22);
        assertThat(accepted).isEqualTo(22); assertThat(wrongAccepted).isZero(); assertThat(compilerReject).isEqualTo(14);
        assertThat(changed).containsExactly("VNE1-016","VNE1-041","VNE1-042","VNE1-048");
    }
    @ParameterizedTest @ValueSource(strings={"Chỉ xem danh sách khách sạn tại Huế","Mình muốn kiếm một chỗ nghỉ tại Huế","Có chỗ nào ngủ ở Huế hông bạn","Cho tôi xem nơi lưu trú tại Huế","kiếm giúp tui khách sạn ở huế nha","Tìm giúp mình khách sạn tại Huế nhé bạn"})
    void naturalDiscoveryRetainsIndependentDestination(String text) {
        admit("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}",text);
        assertThatThrownBy(()->admit("{\"intent\":\"SEARCH\",\"destination\":\"Hà Nội\"}",text)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"Tìm khách sạn Huế có hồ bơi","Có chỗ nào ngủ ở Huế hông bạn thêm điều kiện lạ","kiếm giúp tui khách sạn ở huế nha gần biển","Tìm khách sạn Huế hoặc Đà Lạt","Tìm khách sạn ở Húê"})
    void unknownOrUnsupportedSuffixCannotBeDropped(String text) {
        assertThatThrownBy(()->admit("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}",text)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"Phòng đó giá bao nhiêu?","Khách sạn này còn phòng không","Xem khách sạn đầu tiên","Phòng vừa chọn còn không","Hỏi giá của phòng đã chọn","Kiểm tra phòng này còn phòng không"})
    void selectedRecognitionNeverResolvesTargets(String text) {
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("intent","SELECTED").put("reference",text);
        admit(raw.toString(),text);
        assertThatThrownBy(()->admit("{\"intent\":\"UNVERIFIABLE\"}",text)).isInstanceOf(IllegalArgumentException.class);
        assertThat(compile(raw.toString()).path("fields").isEmpty()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"Huế","huế","HUẾ"," Hue ","  HUẾ  ","Huế"})
    void exactReviewedLabelNormalization(String label) {
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("intent","SEARCH").put("destination",label);
        assertThat(compile(raw.toString()).at("/fields/cityName/value").asText()).isEqualTo("Huế");
        admit(raw.toString(),"Tìm khách sạn ở Huế");
    }
    @ParameterizedTest @ValueSource(strings={"Húê","Huế hoặc Hà Nội","Huế thêm điều kiện","Atlantis","TP Hue"})
    void unreviewedLabelNeverFuzzyMaps(String label) {
        assertThat(DestinationLabels.reviewedDefaults().canonicalLabel(label)).isNull();
    }
    @ParameterizedTest @ValueSource(strings={
            "{\"intent\":\"SEARCH\",\"issues\":[\"OTHER\"]}",
            "{\"intent\":\"CLARIFY\",\"destination\":\"Huế\",\"issues\":[\"OTHER\"]}",
            "{\"intent\":\"SEARCH\",\"reference\":\"ordinary search\"}",
            "{\"intent\":\"SELECTED\",\"destination\":\"Huế\",\"reference\":\"Phòng này\"}",
            "{\"intent\":\"SELECTED\",\"issues\":[\"OTHER\"],\"reference\":\"Phòng này\"}",
            "{\"intent\":\"REFINE\",\"stars\":[4],\"remove\":[\"STARS\"]}",
            "{\"intent\":\"CLARIFY\"}",
            "{\"intent\":\"SEARCH\",\"provenance\":\"USER_CONFIRMED\"}",
            "{\"intent\":\"SEARCH\",\"toolCalls\":[\"WRITE\"]}",
            "{\"intent\":\"SELECTED\",\"roomId\":\"fake\"}",
            "{\"intent\":\"SEARCH\",\"arrival\":\"2026-02-31\"}",
            "{\"intent\":\"SEARCH\",\"arrival\":\"2026-12-20\",\"departure\":\"2026-12-19\"}"
    }) void contradictoryUnsafeOrInvalidProposalsStayRejected(String raw) {
        assertThatThrownBy(()->compile(raw)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fullStayEveryOmissionAndAdditionIsDetected() {
        String text="Tìm khách sạn Huế từ 10/12/2026 đến 12/12/2026, 3 người lớn, 0 trẻ em, 2 phòng";
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("intent","SEARCH").put("destination","Huế")
                .put("arrival","2026-12-10").put("departure","2026-12-12").put("adults",3).put("children",0).put("rooms",2);
        admit(raw.toString(),text);
        for(String field:List.of("destination","arrival","departure","adults","children","rooms")) {
            var omitted=raw.deepCopy(); omitted.remove(field);
            assertThatThrownBy(()->admit(omitted.toString(),text)).as(field).isInstanceOf(IllegalArgumentException.class);
        }
        var altered=raw.deepCopy().put("children",1);
        assertThatThrownBy(()->admit(altered.toString(),text)).isInstanceOf(IllegalArgumentException.class);
        raw.putArray("stars").add(5);
        assertThatThrownBy(()->admit(raw.toString(),text)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void genericGuestsAndMissingStayFactsNeverAcquireDefaults() {
        admit("{\"intent\":\"CLARIFY\",\"issues\":[\"GUESTS_AMBIGUOUS\"]}","Tìm khách sạn cho 7 người");
        assertThatThrownBy(()->admit("{\"intent\":\"SEARCH\",\"adults\":7}","Tìm khách sạn cho 7 người")).isInstanceOf(IllegalArgumentException.class);
        admit("{\"intent\":\"SEARCH\",\"adults\":2,\"children\":0,\"rooms\":1}","Tìm khách sạn cho 2 người lớn, 0 trẻ em, 1 phòng");
        var missing=compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\",\"adults\":2}");
        assertThat(missing.path("fields").has("children")).isFalse();
        assertThat(missing.path("fields").has("rooms")).isFalse();
        admit("{\"intent\":\"CLARIFY\",\"issues\":[\"DEPARTURE_MISSING\"]}","Tìm khách sạn Huế nhận phòng 10/12/2026");
        assertThatThrownBy(()->admit("{\"intent\":\"SEARCH\",\"adults\":2}","Tìm khách sạn cho 2 người lớn và 3 người lớn")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void contractsAndSdkWireStaySeparateAndBounded() throws Exception {
        assertThat(GeminiExtractionProtocol.hash(GeminiLocalExtractionContract.instruction(GeminiLocalExtractionContract.V3))).isEqualTo("458080db549779b3f1452b7e2b49d22a974b546629a9cfe6bf63be62a8805e46");
        assertThat(GeminiExtractionProtocol.hash(GeminiLocalExtractionContract.schema(GeminiLocalExtractionContract.V3))).isEqualTo("0ce096d3d69e162dcc1400bd3985cc9c06d26cd6c62b2bc055d166a702c296f9");
        var v2=GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalExtractionContract.schema(VietnameseSemanticCompiler.V2));
        v2.path("properties").elements().forEachRemaining(p->((com.fasterxml.jackson.databind.node.ObjectNode)p).remove("description"));
        assertThat(v2).isEqualTo(GeminiLlmProviderAdapter.JSON.readTree(VietnameseSemanticCompiler.schema()));
        for(var model:GeminiLocalModelProfile.values()) {
            var request=GeminiLocalExtractionContract.request("Tìm khách sạn Huế",VietnameseSemanticCompiler.V2,model);
            var capture=new GeminiExtractionProtocol.Capture();
            GeminiExtractionProtocol.model("synthetic-noncredential",capture,Duration.ofSeconds(10),model).chat(request);
            var wire=GeminiExtractionProtocol.normalize(capture.request,model);
            assertThat(wire.url()).isEqualTo(model.endpoint());
            assertThat(wire.body()).isEqualTo(GeminiExtractionProtocol.syntheticBody(request,model));
            assertThat(2L*GeminiExtractionProtocol.bytes(wire.body())+1024).isLessThanOrEqualTo(16384);
        }
        var plans=GeminiQualityEvaluation.plan(CLOCK);
        assertThat(plans.stream().filter(p->p.phase().equals("FROZEN40"))).hasSize(40);
        assertThat(plans.stream().filter(p->p.phase().equals("FRESH"))).hasSize(20);
        var originals=GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"baseline40",40,null,false,VietnameseSemanticCompiler.VERSION),CLOCK);
        for(var p:plans) if(p.phase().equals("FROZEN40")) {
            var source=originals.stream().filter(s->s.id().equals(p.sample().id())).findFirst().orElseThrow();
            assertThat(p.sample().input().utterance()).isEqualTo(source.input().utterance());
            assertThat(p.sample().input().category()).isEqualTo(source.input().category());
            assertThat(p.sample().input().split()).isEqualTo(source.input().split());
            assertThat(p.sample().expected()).isEqualTo(source.expected());
        }
    }
    @ParameterizedTest @ValueSource(strings={"Tìm khách sạn Huế từ 31/04/2027 đến 02/05/2027","Tìm khách sạn Huế giá từ 4 triệu đến 900 nghìn mỗi đêm","Tìm khách sạn cho 0 người lớn, 0 phòng","Tìm khách sạn Huế từ 22/12/2026 đến 20/12/2026"})
    void independentlyInvalidFactsAllowOnlyExactEmptyClarification(String text) {
        String issues=text.contains("31/04")?"\"DATE_INVALID\"":text.contains("giá")?"\"PRICE_RANGE_INVALID\"":text.contains("0 người")?"\"ADULTS_INVALID\",\"ROOMS_INVALID\"":"\"DATE_ORDER_INVALID\"";
        var compiled=compile("{\"intent\":\"CLARIFY\",\"issues\":["+issues+"]}");
        var result=GeminiExtractionSmoke.corroborateQuality(VietnameseExtraction.parse(compiled.toString(),CLOCK),text,CLOCK);
        assertThat(result.fields()).isEmpty(); assertThat(result.clarification()).isNotNull();
        assertThatThrownBy(()->GeminiExtractionSmoke.corroborateQuality(VietnameseExtraction.parse(compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}").toString(),CLOCK),text,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        if(!compiled.path("missingSlots").isEmpty()) {
            compiled.putArray("missingSlots");
            assertThatThrownBy(()->GeminiExtractionSmoke.corroborateQuality(VietnameseExtraction.parse(compiled.toString(),CLOCK),text,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void retainedDiagnosticResponsesFixOnlyThreeLiteralClarifications() throws Exception {
        var recorded=GeminiLlmProviderAdapter.JSON.readTree(getClass().getResourceAsStream("/gemini/actual-quality-diagnostic-r1-v2.json"));
        int exact=0,accepted=0,wrongAccepted=0; var changed=new TreeSet<String>();
        for(var row:recorded.path("observations")) {
            boolean admitted=false;
            try {
                var canonical=compile(row.path("actualExtractionText").asText());
                boolean correct=GeminiEvaluationDataset.semantic(canonical).equals(GeminiEvaluationDataset.semantic(row.path("expectedExtraction")));
                if(correct) exact++;
                var proposed=VietnameseExtraction.parse(canonical.toString(),CLOCK);
                var effective=GeminiExtractionSmoke.corroborateQuality(proposed,row.path("utterance").asText(),CLOCK);
                admitted=proposed.action()==VietnameseExtraction.Action.CLARIFY||effective.clarification()==null;
                if(admitted) { accepted++; if(!correct) wrongAccepted++; }
            } catch(IllegalArgumentException|DateTimeException rejected) { }
            if(admitted!=row.path("backendAccepted").asBoolean()) changed.add(row.path("sampleId").asText());
        }
        assertThat(exact).as("exact").isEqualTo(8); assertThat(accepted).as("accepted").isEqualTo(8); assertThat(wrongAccepted).isZero();
        assertThat(changed).containsExactly("DIAG-VNE1-009","DIAG-VNE1-010","DIAG-VNE1-040");
    }
    @Test void invalidClarificationDoesNotAcceptRepairedDateRangeOrFabricatedOccupancy() {
        String text="Tìm khách sạn Huế từ 31/04/2027 đến 02/05/2027";
        var repaired=compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\",\"arrival\":\"2027-04-30\",\"departure\":\"2027-05-02\"}");
        assertThatThrownBy(()->GeminiExtractionSmoke.corroborateQuality(VietnameseExtraction.parse(repaired.toString(),CLOCK),text,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        var unjustified=VietnameseExtraction.parse(compile("{\"intent\":\"CLARIFY\",\"issues\":[\"DATE_INVALID\"]}").toString(),CLOCK);
        assertThatThrownBy(()->GeminiExtractionSmoke.corroborateQuality(unjustified,"Tìm khách sạn ở Huế",CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void explicitRoomNightCurrencyClauseDoesNotStripUnknownUnits() {
        String raw="{\"intent\":\"SEARCH\",\"destination\":\"Huế\",\"price\":{\"kind\":\"STAY\",\"operator\":\"LTE\",\"amount\":2000000,\"unit\":\"VND_PER_ROOM_NIGHT\"}}";
        admit(raw,"Tìm khách sạn Huế giá không quá 2 triệu VND mỗi phòng mỗi đêm");
        assertThatThrownBy(()->admit(raw,"Tìm khách sạn Huế giá không quá 2 triệu USD mỗi phòng mỗi đêm")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void noStatusFailurePreservesOnlyProvenTerminationWithoutEnablingFallback() {
        var transport=org.mockito.Mockito.mock(GeminiHttpTransport.class);
        var request=dev.langchain4j.http.client.HttpRequest.builder().url(GeminiExtractionProtocol.ENDPOINT)
                .method(dev.langchain4j.http.client.HttpMethod.POST).body("{}").build();
        var control=new LlmProviderAdapter.CallControl(Duration.ofSeconds(10));
        org.mockito.Mockito.when(transport.execute(request,control,Duration.ofSeconds(10)))
                .thenThrow(new ProviderException(ProviderException.Category.PROVIDER_TIMEOUT));
        org.mockito.Mockito.when(transport.terminated()).thenReturn(true);
        var reply=GeminiLocalFallbackEvaluation.exchange(transport,request,control,Duration.ofSeconds(10));
        assertThat(reply.status()).isZero(); assertThat(reply.terminated()).isTrue(); assertThat(reply.response()).isNull();
        assertThat(GeminiLocalModelFallback.capacity(reply.status(),reply.terminated(),reply.errorBody())).isFalse();
        assertThat(new GeminiLocalFailurePolicy().decide(0,"PROVIDER_TIMEOUT",false,false,reply.terminated(),null,CLOCK.instant()).reason())
                .isEqualTo("TRANSIENT_NEXT_DISTINCT_SAMPLE");
        org.mockito.Mockito.when(transport.terminated()).thenReturn(false);
        assertThatThrownBy(()->GeminiLocalFallbackEvaluation.exchange(transport,request,control,Duration.ofSeconds(10))).isInstanceOf(ProviderException.class);
        assertThat(new GeminiLocalFailurePolicy().decide(0,"PROVIDER_TIMEOUT",false,false,false,null,CLOCK.instant()).reason()).isEqualTo("UNKNOWN_IN_FLIGHT");
    }
    @Test void criticalStoppedQualityCampaignCannotContinueAfterMetadataFix(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String sealed="{\"version\":\"local-semantic-quality-v2\",\"stopReason\":\"UNKNOWN_IN_FLIGHT\"}";
        java.nio.file.Files.writeString(directory.resolve("campaign.json"),sealed);
        var reads=new java.util.concurrent.atomic.AtomicInteger(); var time=new java.util.concurrent.atomic.AtomicLong();
        assertThatThrownBy(()->GeminiLocalFallbackEvaluation.run(directory,true,true,"FROZEN40",CLOCK,
                ()->{reads.incrementAndGet();return "synthetic-noncredential";},(request,control,timeout)->{throw new AssertionError("No HTTP after critical stop");},
                time::get,time::addAndGet,new GeminiLocalModelFallback(),"segment-no-resume",false,VietnameseSemanticCompiler.V2,true)).isInstanceOf(ProviderException.class);
        assertThat(reads).hasValue(0); assertThat(java.nio.file.Files.readString(directory.resolve("campaign.json"))).isEqualTo(sealed);
    }
}
