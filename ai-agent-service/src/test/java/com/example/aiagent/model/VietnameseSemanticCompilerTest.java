package com.example.aiagent.model;

import com.example.aiagent.nlu.VietnameseExtraction;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VietnameseSemanticCompilerTest {
    static final java.time.Clock CLOCK=GeminiExtractionUnblockTest.CLOCK;
    JsonNode compile(String raw) { return VietnameseSemanticCompiler.compile(VietnameseSemanticCompiler.parse(raw),CLOCK); }
    void admit(String raw,String text) { GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(compile(raw).toString(),CLOCK),text,CLOCK); }
    @Test void strictReferencePriceCompilesDerivedFlagWithoutModelMutationWrappers() {
        String raw="""
                {"intent":"SEARCH","destination":"Nha Trang","price":{"kind":"REFERENCE","operator":"LT","amount":2000000,"unit":"VND"}}
                """;
        var out=compile(raw);
        assertThat(out.at("/fields/referencePriceExclusive/value").asBoolean()).isTrue();
        assertThat(out.at("/fields/maxPrice/value").asInt()).isEqualTo(2000000);
        admit(raw,"Tìm khách sạn ở Nha Trang giá tham khảo dưới 2 triệu");
        assertThatThrownBy(() -> admit(raw,"Tìm khách sạn ở Nha Trang giá tham khảo không quá 2 triệu mỗi đêm"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refinementClarificationAndUnsupportedRemainDistinct() {
        admit("""
                {"intent":"REFINE","destination":"Huế","price":{"kind":"REFERENCE","operator":"LT","amount":2000000,"unit":"VND"}}
                ""","Đổi sang Huế giá tham khảo dưới 2 triệu");
        var clarification=compile("{\"intent\":\"CLARIFY\",\"issues\":[\"GUESTS_AMBIGUOUS\"]}");
        assertThat(clarification.path("fields").isEmpty()).isTrue();
        assertThat(clarification.path("missingSlots").size()).isEqualTo(3);
        assertThat(compile("{\"intent\":\"UNSUPPORTED\"}").path("intent").asText()).isEqualTo("UNSUPPORTED");
        assertThat(compile("{\"intent\":\"UNVERIFIABLE\"}").path("intent").asText()).isEqualTo("UNVERIFIABLE");
    }
    @Test void oversizedAndDeepResponsesAndToolInstructionsFailClosed() {
        assertThatThrownBy(() -> compile(" ".repeat(8193))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compile("{\"intent\":\"SEARCH\",\"destination\":\""+"x".repeat(121)+"\"}" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compile("{\"intent\":\"SEARCH\",\"price\":"+"[".repeat(12)+"0"+"]".repeat(12)+"}" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compile("{\"intent\":\"SEARCH\",\"toolCalls\":[\"booking.write\"]}" )).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void inclusiveRangeDatesOccupancyAndRemovals() {
        admit(""" 
                {"intent":"SEARCH","destination":"Huế","price":{"kind":"REFERENCE","operator":"LTE","amount":2000000,"unit":"VND_PER_ROOM_NIGHT"}}
                ""","Tìm khách sạn ở Huế giá tham khảo không quá 2 triệu mỗi đêm");
        admit("""
                {"intent":"SEARCH","destination":"Huế","price":{"kind":"STAY","operator":"RANGE","amount":1000000,"upper":2000000,"unit":"VND_PER_ROOM_NIGHT"}}
                ""","Tìm khách sạn Huế giá từ 1 triệu đến 2 triệu mỗi đêm");
        admit("""
                {"intent":"SEARCH","destination":"Đà Lạt","arrival":"2026-12-01","departure":"2026-12-03","adults":2,"children":0,"rooms":1}
                ""","Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng");
        admit("{\"intent\":\"REFINE\",\"remove\":[\"STARS\"]}","Bỏ bộ lọc số sao");
        admit("{\"intent\":\"REFINE\",\"remove\":[\"PRICE\"]}","Bỏ bộ lọc giá");
        assertThat(compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}").path("fields").size()).isEqualTo(1);
        assertThat(compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}").path("missingSlots").isEmpty()).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={
            "{\"intent\":\"SEARCH\",\"intent\":\"REFINE\"}",
            "{\"intent\":\"SEARCH\"} {}", "{\"intent\":\"SEARCH\",\"destination\":null}",
            "{\"intent\":\"SEARCH\",\"hotelId\":\"fake-id\"}", "{\"intent\":\"SEARCH\",\"provenance\":\"USER_CONFIRMED\"}",
            "{\"intent\":\"SEARCH\",\"arrival\":\"2026-02-31\"}", "{\"intent\":\"SEARCH\",\"adults\":2.0}",
            "{\"intent\":\"SEARCH\",\"rooms\":0}", "{\"intent\":\"SEARCH\",\"remove\":[\"ANYTHING\"]}",
            "{\"intent\":\"SEARCH\",\"price\":{\"kind\":\"REFERENCE\",\"amount\":2,\"unit\":\"VND\"}}",
            "{\"intent\":\"SEARCH\",\"price\":{\"kind\":\"REFERENCE\",\"operator\":\"LT\",\"amount\":2,\"unit\":\"USD\"}}"
    }) void malformedSemanticDataIsRejected(String raw) { assertThatThrownBy(() -> compile(raw)).isInstanceOf(IllegalArgumentException.class); }
    @ParameterizedTest @ValueSource(strings={
            "{\"intent\":\"SEARCH\",\"arrival\":\"2026-12-01\"}",
            "{\"intent\":\"CLARIFY\",\"destination\":\"Huế\",\"issues\":[\"OTHER\"]}",
            "{\"intent\":\"REFINE\",\"stars\":[4],\"remove\":[\"STARS\"]}",
            "{\"intent\":\"SEARCH\",\"price\":{\"kind\":\"STAY\",\"operator\":\"LT\",\"amount\":2,\"unit\":\"VND_PER_ROOM_NIGHT\"}}",
            "{\"intent\":\"SEARCH\",\"price\":{\"kind\":\"REFERENCE\",\"operator\":\"GT\",\"amount\":2,\"unit\":\"VND\"}}",
            "{\"intent\":\"SEARCH\",\"price\":{\"kind\":\"STAY\",\"operator\":\"LTE\",\"amount\":2,\"unit\":\"VND\"}}"
    }) void unsupportedOrConflictingMeaningIsNotRepaired(String raw) { assertThatThrownBy(() -> compile(raw)).isInstanceOf(IllegalArgumentException.class); }
    @Test void independentLiteralEvidenceRejectsInventedCriteriaAndRemovals() {
        String[] proposals={"{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}",
                "{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\",\"adults\":2}",
                "{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\",\"remove\":[\"STARS\"]}",
                "{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\",\"arrival\":\"2026-12-01\",\"departure\":\"2026-12-03\"}"};
        for(String raw:proposals) assertThatThrownBy(() -> admit(raw,"Tìm khách sạn ở Đà Lạt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admit("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}","Tìm khách sạn ở Huế hoặc Đà Lạt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admit("""
                {"intent":"SEARCH","destination":"Huế","price":{"kind":"REFERENCE","operator":"LTE","amount":2000000,"unit":"VND_PER_ROOM_NIGHT"}}
                ""","Tìm khách sạn Huế giá tham khảo dưới 2 triệu" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> admit("""
                {"intent":"SEARCH","destination":"Huế","price":{"kind":"REFERENCE","operator":"LT","amount":1000000,"unit":"VND"}}
                ""","Tìm khách sạn Huế giá tham khảo dưới 2 triệu")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void finiteReferencesDoNotResolveTargetsOrGrantProvenance() {
        var out=compile("{\"intent\":\"SELECTED\",\"reference\":\"Kiểm tra phòng này còn phòng không\"}");
        GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(out.toString(),CLOCK),"Kiểm tra phòng này còn phòng không",CLOCK);
        assertThatThrownBy(() -> GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(out.toString(),CLOCK),"Xem khách sạn ở Huế",CLOCK)).isInstanceOf(IllegalArgumentException.class);
        var p=VietnameseExtraction.parse(compile("{\"intent\":\"SEARCH\",\"destination\":\"Huế\"}").toString(),CLOCK);
        assertThat(p.criteria().fields().get("cityName").provenance().name()).isEqualTo("MODEL_PROPOSED");
        assertThatThrownBy(() -> p.criteria().patch(true)).isInstanceOf(IllegalArgumentException.class);
    }
    /** Inverse proposals are test data only, not model outputs or live evidence. */
    @Test void original40CanonicalOutcomesRemainRepresentable() throws Exception {
        var oracle=GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalEvaluation.resource(GeminiLocalEvaluation.ORACLE));
        var evidence=GeminiLlmProviderAdapter.JSON.createArrayNode();
        Map<String,String> inverse=Map.of("cityName","destination","hotelName","hotel","checkIn","arrival","checkOut","departure",
                "adults","adults","children","children","rooms","rooms","starRatings","stars");
        for(var entry:oracle.path("fixtures").properties()) {
            var expected=entry.getValue().path("response"); var s=GeminiLlmProviderAdapter.JSON.createObjectNode();
            s.put("intent",Set.of("SELECTED","COMPARISON").contains(expected.path("action").asText())?expected.path("action").asText():expected.path("intent").asText());
            if(!expected.path("reference").asText().isEmpty()) s.set("reference",expected.get("reference"));
            for(var f:expected.path("fields").properties()) {
                if(f.getValue().path("operation").asText().equals("CLEAR")) {
                    if(!s.has("remove")) s.putArray("remove"); String name=f.getKey().equals("starRatings")?"STARS":"PRICE";
                    var a=(com.fasterxml.jackson.databind.node.ArrayNode)s.get("remove"); if(!a.toString().contains(name)) a.add(name);
                } else if(inverse.containsKey(f.getKey())) s.set(inverse.get(f.getKey()),f.getValue().get("value"));
            }
            if(expected.at("/fields/maxPrice/operation").asText().equals("REPLACE")) {
                var price=s.putObject("price").put("kind","REFERENCE").put("operator","LT").put("unit","VND");
                price.set("amount",expected.at("/fields/maxPrice/value"));
            }
            if(expected.path("action").asText().equals("CLARIFY")) {
                var issues=s.putArray("issues"); Set<String> missing=new HashSet<>(); expected.path("missingSlots").forEach(n -> missing.add(n.asText()));
                if(missing.equals(Set.of("adults","children","rooms"))) issues.add("GUESTS_AMBIGUOUS");
                else if(missing.equals(Set.of("checkIn","checkOut"))) issues.add("DATE_INVALID");
                else if(missing.equals(Set.of("minPrice","maxPrice"))) issues.add("PRICE_RANGE_INVALID");
                else for(String m:missing) issues.add(switch(m) { case "cityName"->"DESTINATION_UNRESOLVED"; case "checkIn"->"ARRIVAL_MISSING"; case "checkOut"->"DEPARTURE_MISSING"; case "adults"->"ADULTS_INVALID"; case "rooms"->"ROOMS_INVALID"; default->throw new AssertionError(m); });
                if(issues.isEmpty()) issues.add("OTHER");
            }
            var actual=compile(s.toString());
            assertThat(GeminiEvaluationDataset.semantic(actual)).as(entry.getKey()).isEqualTo(GeminiEvaluationDataset.semantic(expected));
            evidence.addObject().put("sampleId",entry.getKey()).put("source","OFFLINE_EQUIVALENT_PROPOSAL_NOT_MODEL_OUTPUT")
                    .put("canonicalMatch",true).set("semanticProposal",s);
        }
        assertThat(evidence.size()).isEqualTo(40);
        Files.writeString(Path.of("target/semantic-compiler-verification-v1.json"),evidence.toPrettyString());
        var snapshot=GeminiLlmProviderAdapter.JSON.createObjectNode().put("version",VietnameseSemanticCompiler.VERSION)
                .put("instruction",VietnameseSemanticCompiler.instruction()).put("schema",VietnameseSemanticCompiler.schema())
                .put("instructionDigest",GeminiExtractionProtocol.hash(VietnameseSemanticCompiler.instruction()))
                .put("schemaDigest",GeminiExtractionProtocol.hash(VietnameseSemanticCompiler.schema()));
        Files.writeString(Path.of("target/semantic-contract-snapshot-v1.json"),snapshot.toPrettyString());
        String wire=GeminiExtractionProtocol.syntheticBody(GeminiLocalExtractionContract.request("Tìm khách sạn ở Đà Lạt",VietnameseSemanticCompiler.VERSION));
        assertThat(2L*GeminiExtractionProtocol.bytes(wire)+1024).isLessThanOrEqualTo(GeminiExtractionProtocol.INPUT_CAP);
    }
    @Test void localSemanticPipelineRetainsRawAndCompiledLayers(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var time=new java.util.concurrent.atomic.AtomicLong(); var calls=new java.util.concurrent.atomic.AtomicInteger();
        String[] proposals={"{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\"}",
                "{\"intent\":\"SEARCH\",\"destination\":\"Nha Trang\",\"price\":{\"kind\":\"REFERENCE\",\"operator\":\"LT\",\"amount\":2000000,\"unit\":\"VND\"}}",
                "{\"intent\":\"SEARCH\",\"destination\":\"Đà Lạt\",\"arrival\":\"2026-12-01\",\"departure\":\"2026-12-03\",\"adults\":2,\"children\":0,\"rooms\":1}"};
        var result=GeminiLocalEvaluation.run(new String[]{"--live","--mode","initial-smoke","--max-requests","3","--protocol",VietnameseSemanticCompiler.VERSION,"--output",directory.resolve("fake.json").toString()},
                CLOCK,()->"synthetic-noncredential",()->(request,control,timeout)->dev.langchain4j.http.client.SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of())
                        .body(GeminiExtractionUnblockTest.response(proposals[calls.getAndIncrement()])).build(),time::get,time::addAndGet);
        assertThat(calls).hasValue(3);
        for(var row:result.path("observations")) {
            assertThat(row.path("semanticSchemaValid").asBoolean()).isTrue();
            assertThat(row.path("compilerResult").asText()).isEqualTo("PASS");
            assertThat(row.path("semanticPass").asBoolean()).isTrue();
            assertThat(row.path("backendAccepted").asBoolean()).isTrue();
            assertThat(row.has("actualExtraction") && row.has("compiledExtraction")).isTrue();
        }
    }
    @Test void diagnosticAndFullPlansKeepBusinessOracleAndFitExistingInputCap() throws Exception {
        var plans=GeminiLlmProviderAdapter.JSON.createObjectNode();
        for(String mode:List.of("semantic-diagnostic","baseline40")) {
            int count=mode.equals("baseline40")?40:8;
            var plan=GeminiLocalEvaluation.plan(GeminiLocalEvaluation.options(new String[]{"--offline","--mode",mode,"--max-requests",""+count,"--protocol",VietnameseSemanticCompiler.VERSION}),CLOCK);
            assertThat(plan).hasSize(count); var rows=plans.putArray(mode);
            for(var sample:plan) {
                assertThat(2L*GeminiExtractionProtocol.bytes(sample.input().body())+1024).isLessThanOrEqualTo(GeminiExtractionProtocol.INPUT_CAP);
                rows.addObject().put("sampleId",sample.id()).put("utterance",sample.input().utterance())
                        .put("requestDigest",sample.input().binding().requestDigest()).put("status","NOT_RUN").set("expectedBusinessExtraction",sample.expected());
            }
        }
        Files.writeString(Path.of("target/semantic-plans-v1.json"),plans.toPrettyString());
    }
}
