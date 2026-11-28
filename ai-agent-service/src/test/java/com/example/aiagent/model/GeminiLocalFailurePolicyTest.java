package com.example.aiagent.model;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GeminiLocalFailurePolicyTest {
    final GeminiLocalFailurePolicy policy = new GeminiLocalFailurePolicy();
    GeminiLocalFailurePolicy.Decision decision(int status, String category, boolean known, boolean safe, boolean terminated) {
        return policy.decide(status, category, known, !safe, terminated, null, Instant.EPOCH);
    }
    @ParameterizedTest @ValueSource(ints = {400,401,403,429}) void criticalHttpStops(int status) {
        assertThat(decision(status, "UNAVAILABLE", false, true, true).proceed()).isFalse();
    }
    @ParameterizedTest @ValueSource(ints = {500,502,503,504}) void transientKeepsUnknownUsageAndContinuesOnce(int status) {
        var next = decision(status, "UNAVAILABLE", false, true, true);
        assertThat(next.proceed()).isTrue(); assertThat(next.cooldownSeconds()).isEqualTo(15);
        assertThat(decision(status, "UNAVAILABLE", false, true, true).proceed()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"TIMEOUT","PROVIDER_TIMEOUT"}) void timeoutRequiresTerminatedCall(String category) {
        assertThat(decision(0, category, false, true, false).reason()).isEqualTo("UNKNOWN_IN_FLIGHT");
        assertThat(new GeminiLocalFailurePolicy().decide(0,category,false,false,true,null,Instant.EPOCH).proceed()).isTrue();
    }
    @Test void knownSuccessResetsConsecutiveButNotCumulative() {
        for (int i=0;i<4;i++) {
            assertThat(decision(503,"UNAVAILABLE",false,true,true).proceed()).isTrue();
            assertThat(decision(200,"BACKEND_REJECTED",true,true,true).proceed()).isTrue();
        }
        assertThat(decision(503,"UNAVAILABLE",false,true,true).proceed()).isFalse();
    }
    @Test void usageAndSafetyStopButContainedSchemaContinues() {
        assertThat(decision(200,"UNKNOWN_USAGE",false,true,true).proceed()).isFalse();
        assertThat(decision(200,"SUCCESS",true,false,true).proceed()).isFalse();
        assertThat(decision(200,"EXTRACTION_SCHEMA_INVALID",true,true,true).proceed()).isTrue();
    }
    @Test void retryAfterAndCredentialRedaction() {
        assertThat(policy.decide(503,"UNAVAILABLE",false,false,true,"90",Instant.EPOCH).cooldownSeconds()).isEqualTo(90);
        assertThat(GeminiLocalEvaluation.sanitizeResponse("{\"error\":\"test-key\",\"Authorization\":\"Bearer abc\",\"cookie\":\"private\"}","test-key"))
                .doesNotContain("test-key","Bearer abc","private");
        assertThat(GeminiLocalEvaluation.sanitizeResponse("{\"message\":\"Cookie: session=private; another=hidden\",\"access_token\":\"opaque-token\"}",null))
                .doesNotContain("private","hidden","opaque-token");
        assertThat(GeminiLocalEvaluation.sanitizeResponse("{\"password\":\"truncated-secret",null)).doesNotContain("truncated-secret");
    }
}
