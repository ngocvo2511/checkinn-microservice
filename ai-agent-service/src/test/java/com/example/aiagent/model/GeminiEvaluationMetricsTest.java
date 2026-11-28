package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

class GeminiEvaluationMetricsTest {
    JsonNode expected() throws Exception { return GeminiLlmProviderAdapter.JSON.readTree("{\"action\":\"CRITERIA\",\"intent\":\"SEARCH\",\"criteria\":{\"cityName\":\"Huế\"},\"missingSlots\":[]}"); }
    @ParameterizedTest @ValueSource(strings={"correct","intent","criteria","missingSlots","grammar-reject","unsafe-reject","schema-failure","api-failure"})
    void semanticsAndBackendSafetyAreIndependentAndFailuresRemainInDenominator(String kind) throws Exception {
        var expected=expected(); var actual=expected.deepCopy();
        if(kind.equals("intent")) ((com.fasterxml.jackson.databind.node.ObjectNode)actual).put("intent","CLARIFY");
        if(kind.equals("criteria")||kind.equals("unsafe-reject")) ((com.fasterxml.jackson.databind.node.ObjectNode)actual.path("criteria")).put("cityName","Hà Nội");
        if(kind.equals("missingSlots")) ((com.fasterxml.jackson.databind.node.ObjectNode)actual).putArray("missingSlots").add("rooms");
        boolean schema=!kind.equals("schema-failure")&&!kind.equals("api-failure");
        var metrics=new GeminiEvaluationMetrics();
        metrics.add(new GeminiEvaluationMetrics.Observation("case",true,true,!kind.equals("api-failure"),schema,expected,actual,
                !kind.equals("unsafe-reject"), !kind.equals("grammar-reject")&&!kind.equals("unsafe-reject"),kind.equals("unsafe-reject"),false,false,false,false,12,1L,1L,2L));
        var scores=metrics.scores(); assertThat(scores.get("criteria").denominator()).isEqualTo(1);
        assertThat(scores.get("criteria").numerator()).isEqualTo(schema&&!kind.equals("criteria")&&!kind.equals("unsafe-reject")?1:0);
        assertThat(scores.get("intent").numerator()).isEqualTo(schema&&!kind.equals("intent")?1:0);
        assertThat(scores.get("missingSlots").numerator()).isEqualTo(schema&&!kind.equals("missingSlots")?1:0);
        if(kind.equals("grammar-reject")) { assertThat(scores.get("criteria").numerator()).isEqualTo(1); assertThat(scores.get("falseRejection").numerator()).isEqualTo(1); }
        if(kind.equals("unsafe-reject")) { assertThat(scores.get("criteria").numerator()).isZero(); assertThat(scores.get("falseAcceptance").numerator()).isZero(); }
    }
    @Test void premodelBlocksAndNotRunNeverInflateRemoteSuccess() throws Exception {
        var metrics=new GeminiEvaluationMetrics();
        metrics.add(new GeminiEvaluationMetrics.Observation("blocked",false,false,false,false,expected(),null,false,false,false,false,false,false,false,0,null,null,null));
        metrics.add(new GeminiEvaluationMetrics.Observation("pending",true,false,false,false,expected(),null,false,false,false,false,false,false,false,0,null,null,null));
        assertThat(metrics.scores().get("apiSuccess")).isEqualTo(new GeminiEvaluationMetrics.Fraction(0,0));
        assertThat(metrics.scores().get("notRun")).isEqualTo(new GeminiEvaluationMetrics.Fraction(1,1));
        assertThat(metrics.latencyPercentile(0.95)).isEmpty();
    }
    @Test void snapshotsCannotBeRetunedAndUnknownUsageIsNotZeroUsage() throws Exception {
        var metrics = new GeminiEvaluationMetrics(); var expected = expected(); var actual = expected.deepCopy();
        var observation = new GeminiEvaluationMetrics.Observation("one",true,true,true,true,expected,actual,true,true,false,false,false,false,false,20,2L,3L,5L);
        metrics.add(observation);
        ((com.fasterxml.jackson.databind.node.ObjectNode)actual).put("intent","CHANGED_AFTER_SCORE");
        assertThat(metrics.scores().get("intent")).isEqualTo(new GeminiEvaluationMetrics.Fraction(1,1));
        assertThatThrownBy(() -> metrics.add(observation)).isInstanceOf(IllegalArgumentException.class);
        metrics.add(new GeminiEvaluationMetrics.Observation("two",true,true,false,false,expected,null,false,false,false,false,false,false,false,100,null,null,null));
        assertThat(metrics.usage()).isEqualTo(new GeminiEvaluationMetrics.Usage(2,3,5,1,1));
        assertThat(metrics.latencyPercentile(.5)).hasValue(20); assertThat(metrics.latencyPercentile(.95)).hasValue(100);
        assertThat(metrics.scores().get("apiSuccess")).isEqualTo(new GeminiEvaluationMetrics.Fraction(1,2));
    }
    @Test void manifestIsPinnedAndSplitsAreDisjointWithoutDroppingDifficultCases() throws Exception {
        byte[] bytes;
        try(var in=getClass().getResourceAsStream("/gemini/vietnamese-nlu-evaluation-candidates-v1.json")) { bytes=Objects.requireNonNull(in).readAllBytes(); }
        assertThat(hash(bytes)).isEqualTo("9771883545b6bbb20739d28c44205d9b7fd108ff26abe4a4ddd4ea656634e950");
        var fixtures=GeminiLlmProviderAdapter.JSON.readTree(bytes).path("fixtures"); assertThat(fixtures.size()).isEqualTo(60);
        var ids=new HashSet<String>(); var utterances=new HashSet<String>(); var splits=new HashMap<String,Integer>();
        for(var f:fixtures) {
            assertThat(ids.add(f.path("id").asText())).isTrue(); assertThat(utterances.add(f.path("utterance").asText())).isTrue();
            splits.merge(f.path("split").asText(),1,Integer::sum);
            assertThat(f.path("privacyClassification").asText()).isEqualTo("SYNTHETIC_ONLY");
            assertThat(f.path("oracleReview").asText()).isEqualTo("PENDING_INDEPENDENT_REVIEW");
            assertThat(f.has("expectedCriteria")&&f.has("expectedMissingSlots")&&f.has("forbiddenBehaviors")).isTrue();
            if(f.path("probeType").asText().startsWith("PREMODEL")||f.path("probeType").asText().startsWith("EXTRACTOR")) assertThat(f.path("remoteDefault").asText()).isEqualTo("DENY");
        }
        assertThat(splits).containsEntry("DEVELOPMENT",20).containsEntry("REGRESSION",20).containsEntry("HOLDOUT",20);
    }
}
