package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VietnameseCorroborationFixTest {
    static final Clock CLOCK = GeminiExtractionUnblockTest.CLOCK;
    @Test void actualHistoricalResponsesKeepModelClassificationAndFixFiveBackendDecisions() throws Exception {
        var data = GeminiLlmProviderAdapter.JSON.readTree(getClass().getResourceAsStream("/gemini/actual-historical-responses-v1.json"));
        var clock = Clock.fixed(Instant.parse(data.path("clock").asText()), ZoneOffset.UTC);
        int correctAccepted = 0, wrongRejected = 0, invalid = 0;
        for (var row : data.path("observations")) {
            boolean accepted = false;
            try {
                var proposal = VietnameseExtraction.parse(row.path("actualExtractionText").asText(), clock);
                var effective = GeminiExtractionSmoke.corroborate(proposal, row.path("utterance").asText(), clock);
                accepted = proposal.action() == VietnameseExtraction.Action.CLARIFY || effective.clarification() == null;
            } catch (IllegalArgumentException | DateTimeException rejected) { }
            if (!row.path("schemaValid").asBoolean()) { assertThat(accepted).isFalse(); invalid++; }
            else if (row.path("semanticPass").asBoolean()) { assertThat(accepted).as(row.path("sampleId").asText()).isTrue(); correctAccepted++; }
            else { assertThat(accepted).as(row.path("sampleId").asText()).isFalse(); wrongRejected++; }
        }
        assertThat(correctAccepted).isEqualTo(7); assertThat(wrongRejected).isEqualTo(13); assertThat(invalid).isEqualTo(3);
    }
    void semantic(String raw, String text) {
        var compiled = VietnameseSemanticCompiler.compile(VietnameseSemanticCompiler.parse(raw), CLOCK);
        GeminiExtractionSmoke.corroborate(VietnameseExtraction.parse(compiled.toString(), CLOCK), text, CLOCK);
    }
    @ParameterizedTest @ValueSource(strings = {"Chỉ giữ khách sạn 4 sao", "Giữ lại khách sạn 4 sao", "Chỉ lọc khách sạn 4 sao"})
    void keepFilterParaphrases(String text) {
        semantic("{\"intent\":\"REFINE\",\"stars\":[4]}", text);
        assertThatThrownBy(() -> semantic("{\"intent\":\"REFINE\",\"stars\":[5]}", text)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"Đổi điểm đến sang Huế", "Chuyển điểm đến sang Huế", "Tìm ở Huế thay vì điểm đến trước", "Tìm ở Huế thay vì Đà Lạt"})
    void destinationReplacementParaphrases(String text) {
        semantic("{\"intent\":\"REFINE\",\"destination\":\"Huế\"}", text);
        assertThatThrownBy(() -> semantic("{\"intent\":\"REFINE\",\"destination\":\"Đà Lạt\"}", text)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void partialDateClarificationAndIndependentMissingSlotsWithoutState() {
        semantic("{\"intent\":\"CLARIFY\",\"issues\":[\"DEPARTURE_MISSING\"]}", "Tìm khách sạn Huế nhận phòng 20/12/2026");
        semantic("{\"intent\":\"CLARIFY\",\"issues\":[\"ARRIVAL_MISSING\"]}", "Tìm khách sạn Huế trả phòng 20/12/2026");
        assertThatThrownBy(() -> semantic("{\"intent\":\"CLARIFY\"}", "Tìm khách sạn Huế nhận phòng 20/12/2026")).isInstanceOf(IllegalArgumentException.class);
        semantic("{\"intent\":\"SEARCH\",\"destination\":\"Huế\",\"arrival\":\"2026-12-20\",\"departure\":\"2026-12-22\"}",
                "Tìm khách sạn Huế từ 20/12/2026 đến 22/12/2026");
    }
    @Test void selectedQuestionCannotBecomeUnsupportedAndOrdinaryClarificationStillWorks() {
        String text = "Phòng này giá bao nhiêu và còn phòng không?";
        semantic("{\"intent\":\"SELECTED\",\"reference\":\"" + text + "\"}", text);
        assertThatThrownBy(() -> semantic("{\"intent\":\"UNSUPPORTED\"}", text)).isInstanceOf(IllegalArgumentException.class);
        semantic("{\"intent\":\"CLARIFY\",\"issues\":[\"GUESTS_AMBIGUOUS\"]}", "Tìm khách sạn cho 5 người");
        semantic("{\"intent\":\"CLARIFY\",\"issues\":[\"UNSUPPORTED_FILTER\"]}", "Tìm khách sạn có hồ bơi ở Huế");
        assertThatThrownBy(() -> semantic("{\"intent\":\"CLARIFY\"}", "Tìm khách sạn ở Huế")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> semantic("{\"intent\":\"REFINE\",\"destination\":\"Huế\"}", "Đổi điểm đến sang nơi nào đó")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> semantic("{\"intent\":\"REFINE\",\"destination\":\"Huế\"}", "Tìm ở Huế thay vì Đà Lạt thêm yêu cầu lạ")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"Xem khách sạn thứ hai","Giá phòng thứ nhất bao nhiêu","Phòng đã chọn còn chỗ không"})
    void referenceEvidenceDoesNotResolveTargets(String text) {
        semantic("{\"intent\":\"SELECTED\",\"reference\":\""+text+"\"}",text);
        assertThatThrownBy(()->semantic("{\"intent\":\"CLARIFY\"}",text)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"Bỏ giới hạn giá đi","Xóa giới hạn giá","Không cần giới hạn giá nữa"})
    void observedRemovalDiagnosticKeepsFullOperationValidation(String text) {
        semantic("{\"intent\":\"REFINE\",\"remove\":[\"PRICE\"]}",text);
        assertThatThrownBy(()->semantic("{\"intent\":\"REFINE\",\"remove\":[\"STARS\"]}",text)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void starRemovalAndUnknownRemovalSuffixRemainDistinct() {
        semantic("{\"intent\":\"REFINE\",\"remove\":[\"STARS\"]}","Không cần giới hạn số sao nữa");
        assertThatThrownBy(()->semantic("{\"intent\":\"REFINE\",\"remove\":[\"PRICE\"]}","Bỏ giới hạn giá thêm điều kiện lạ")).isInstanceOf(IllegalArgumentException.class);
    }
}
