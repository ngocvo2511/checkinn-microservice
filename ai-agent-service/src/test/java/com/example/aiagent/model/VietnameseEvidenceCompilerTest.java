package com.example.aiagent.model;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

class VietnameseEvidenceCompilerTest {
    static final java.time.Clock CLOCK=GeminiQualityEvaluation.EVALUATION_CLOCK;
    static final String STAY="Tìm khách sạn ở Đà Lạt từ 01/12/2026 đến 03/12/2026, 2 người lớn, 0 trẻ em, 1 phòng";
    static ObjectNode fullStay() {
        var root=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","FIND_HOTEL");
        var facts=root.putArray("facts");
        String[][] values={{"DESTINATION","Đà Lạt"},{"STAY_DATES","01/12/2026 đến 03/12/2026"},{"ADULTS","2 người lớn"},{"CHILDREN","0 trẻ em"},{"ROOMS","1 phòng"}};
        for(var v:values) facts.addObject().put("type",v[0]).put("text",v[1]);
        return root;
    }
    @Test void completeEvidenceCompilesWithoutDefaultsAndRetainsZero() {
        var out=VietnameseEvidenceCompiler.compile(fullStay(),STAY,CLOCK);
        assertThat(out.at("/fields/children/value").asInt()).isZero();
        assertThat(out.at("/fields/checkOut/value").asText()).isEqualTo("2026-12-03");
        assertThat(out.path("fields").size()).isEqualTo(6);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4}) void everyOmittedFactRejectsBeforeCompilation(int index) {
        var raw=fullStay(); ((com.fasterxml.jackson.databind.node.ArrayNode)raw.path("facts")).remove(index);
        assertThat(VietnameseEvidenceCompiler.verify(raw,STAY,CLOCK).missing()).isPositive();
        assertThatThrownBy(()->VietnameseEvidenceCompiler.compile(raw,STAY,CLOCK)).hasMessage("Unverified or incomplete evidence");
    }
    @ParameterizedTest @ValueSource(strings={"invented","wrong-type","duplicate","overlap","wrong-request"})
    void membershipIsInsufficientForAdmission(String defect) {
        var raw=fullStay(); var facts=(com.fasterxml.jackson.databind.node.ArrayNode)raw.path("facts");
        switch(defect) {
            case "invented" -> ((ObjectNode)facts.get(0)).put("text","Hà Nội");
            case "wrong-type" -> ((ObjectNode)facts.get(3)).put("type","ADULTS");
            case "duplicate" -> facts.add(facts.get(0).deepCopy());
            case "overlap" -> ((ObjectNode)facts.get(0)).put("text",STAY);
            case "wrong-request" -> raw.put("request","CHANGE_CRITERIA");
        }
        assertThatThrownBy(()->VietnameseEvidenceCompiler.compile(raw,STAY,CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={
            "{\"request\":\"FIND_HOTEL\",\"request\":\"FIND_HOTEL\",\"facts\":[]}",
            "{\"request\":\"FIND_HOTEL\",\"facts\":[],\"provenance\":\"USER_CONFIRMED\"}",
            "{\"request\":\"FIND_HOTEL\",\"facts\":[{\"type\":\"ADULTS\",\"text\":null}]}",
            "{\"request\":\"BOOK\",\"facts\":[]}",
            "{\"request\":\"FIND_HOTEL\",\"facts\":[]} {}"
    }) void strictRawSchemaRejectsAuthorityAndMalformedShapes(String raw) {
        assertThatThrownBy(()->VietnameseEvidenceCompiler.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundedSchemaAndUnicodeRemainLocalAuthority() {
        var root=fullStay(); ((ObjectNode)root.path("facts").get(0)).put("text","x".repeat(201));
        assertThatThrownBy(()->VietnameseEvidenceCompiler.parse(root.toString())).isInstanceOf(IllegalArgumentException.class);
        ((ObjectNode)root.path("facts").get(0)).put("text","Đà\u200bLạt");
        assertThatThrownBy(()->VietnameseEvidenceCompiler.parse(root.toString())).isInstanceOf(IllegalArgumentException.class);
        var many=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","FIND_HOTEL"); var a=many.putArray("facts");
        for(int i=0;i<25;i++) a.addObject().put("type","DESTINATION").put("text","Đà Lạt");
        assertThatThrownBy(()->VietnameseEvidenceCompiler.parse(many.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingDestinationNeverAcquiresCityOrBudget() {
        var raw=fullStay(); ((com.fasterxml.jackson.databind.node.ArrayNode)raw.path("facts")).remove(0);
        var out=VietnameseEvidenceCompiler.compile(raw,STAY.replace("ở Đà Lạt ",""),CLOCK);
        assertThat(out.path("fields").has("cityName")).isFalse(); assertThat(out.path("fields").has("maxPrice")).isFalse();
        assertThat(out.path("missingSlots").toString()).contains("cityName");
    }
    @ParameterizedTest @CsvSource(delimiter='|',value={
            "Tìm khách sạn cho 3 người|AMBIGUOUS|3 người|CLARIFY",
            "Tìm khách sạn nhận phòng 01/12/2026|STAY_DATES|nhận phòng 01/12/2026|CLARIFY",
            "Tìm khách sạn trả phòng 03/12/2026|STAY_DATES|trả phòng 03/12/2026|CLARIFY",
            "Tìm khách sạn từ 31/02/2026 đến 03/03/2026|STAY_DATES|31/02/2026 đến 03/03/2026|CLARIFY",
            "Tìm khách sạn từ 03/12/2026 đến 01/12/2026|STAY_DATES|03/12/2026 đến 01/12/2026|CLARIFY",
            "Tìm khách sạn giá từ 2 triệu đến 1 triệu mỗi đêm|PRICE|giá từ 2 triệu đến 1 triệu mỗi đêm|CLARIFY",
            "Tìm khách sạn giá không quá 2 triệu|PRICE|giá không quá 2 triệu|CLARIFY",
            "Tìm khách sạn ở nơi nào đó|AMBIGUOUS|nơi nào đó|CLARIFY"
    }) void invalidAndAmbiguousFactsAreRetainedWithoutRepair(String text,String type,String evidence,String action) {
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","FIND_HOTEL");
        raw.putArray("facts").addObject().put("type",type).put("text",evidence);
        var out=VietnameseEvidenceCompiler.compile(raw,text,CLOCK);
        assertThat(out.path("action").asText()).isEqualTo(action); assertThat(out.path("fields").isEmpty()).isTrue();
    }
    @Test void dateRoleNegationAndUnitsCannotBeTrimmedOutOfEvidence() {
        for(var entry:List.of(new String[]{"Tìm khách sạn trả phòng 03/12/2026","STAY_DATES","03/12/2026"},
                new String[]{"Tìm khách sạn không cần hồ bơi","UNSUPPORTED","hồ bơi"},
                new String[]{"Tìm khách sạn giá không quá 2 triệu mỗi đêm","PRICE","giá không quá 2 triệu"})) {
            var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","FIND_HOTEL"); raw.putArray("facts").addObject().put("type",entry[1]).put("text",entry[2]);
            assertThatThrownBy(()->VietnameseEvidenceCompiler.compile(raw,entry[0],CLOCK)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void unsupportedTailCannotDisappearBehindAValidDestination() {
        String text="Tìm khách sạn ở Huế wifi mạnh";
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","FIND_HOTEL"); raw.putArray("facts").addObject().put("type","DESTINATION").put("text","Huế");
        assertThat(VietnameseEvidenceCompiler.verify(raw,text,CLOCK).missing()).isEqualTo(2);
        assertThatThrownBy(()->VietnameseEvidenceCompiler.compile(raw,text,CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void recordedDiagnosticReferenceFragmentationIsRejectedWithoutRepair() {
        String text="Kiểm tra phòng này còn phòng không";
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request","ASK_SELECTED"); var facts=raw.putArray("facts");
        facts.addObject().put("type","REFERENCE").put("text","này"); facts.addObject().put("type","ROOMS").put("text","phòng");
        assertThatThrownBy(()->VietnameseEvidenceCompiler.compile(raw,text,CLOCK)).isInstanceOf(IllegalArgumentException.class);
        facts.removeAll(); facts.addObject().put("type","REFERENCE").put("text","phòng này");
        assertThat(VietnameseEvidenceCompiler.compile(raw,text,CLOCK).path("action").asText()).isEqualTo("SELECTED");
    }
    @ParameterizedTest @CsvSource(delimiter='|',value={
            "Tìm khách sạn Huế giá tham khảo dưới hai triệu|PRICE|giá tham khảo dưới hai triệu|FIND_HOTEL",
            "Tìm khách sạn Huế giá không quá 2 triệu mỗi đêm|PRICE|giá không quá 2 triệu mỗi đêm|FIND_HOTEL",
            "Bỏ giới hạn giá đi|REMOVAL|Bỏ giới hạn giá đi|CHANGE_CRITERIA",
            "Phòng này còn không?|REFERENCE|Phòng này|ASK_SELECTED",
            "Tìm khách sạn có hồ bơi ở Đà Lạt|UNSUPPORTED|hồ bơi|FIND_HOTEL",
            "Tìm khách sạn không cần hồ bơi ở Đà Lạt|UNSUPPORTED|không cần hồ bơi|FIND_HOTEL"
    }) void meaningfulClauseMustBeRetained(String utterance,String type,String evidence,String request) {
        var raw=GeminiLlmProviderAdapter.JSON.createObjectNode().put("request",request); var facts=raw.putArray("facts");
        for(var witness:VietnameseEvidenceCompiler.witnesses(utterance)) {
            String surface=witness.type().equals(type)?evidence:utterance.contains("Huế")?"Huế":"Đà Lạt";
            facts.addObject().put("type",witness.type()).put("text",surface);
        }
        assertThat(VietnameseEvidenceCompiler.verify(raw,utterance,CLOCK).exact()).isTrue();
        facts.removeAll(); assertThat(VietnameseEvidenceCompiler.verify(raw,utterance,CLOCK).exact()).isFalse();
    }
    static Stream<Arguments> corpus() throws Exception {
        return GeminiEvidenceEvaluation.plan().stream().filter(p->Set.of("FROZEN40","FRESH").contains(p.phase()))
                .map(p->Arguments.of(p.sample().id(),p.sample().input().utterance(),p.sample().expected()));
    }
    @ParameterizedTest @MethodSource("corpus") void deterministicComparatorPreservesFrozenBusinessOracle(String id,String text,com.fasterxml.jackson.databind.JsonNode expected) {
        var actual=VietnameseEvidenceCompiler.deterministic(text,CLOCK);
        assertThat(GeminiEvaluationDataset.semantic(actual)).as(id).isEqualTo(GeminiEvaluationDataset.semantic(expected));
    }
}
