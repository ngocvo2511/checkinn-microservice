package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.CLOCK;

class GeminiEvaluationReviewTest {
    @TempDir Path directory;
    @BeforeAll static void guard(){GeminiExtractionUnblockTest.denyExternalAndKeys();}
    @AfterAll static void restore(){GeminiExtractionUnblockTest.restore();}
    @Test void measureSignedArtifactsAtEachIntegratedCampaignSize() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); var measurements=GeminiOperatorApproval.JSON.createArrayNode();
        for(int n:List.of(30,60,100,200)) {
            byte[] artifact=support.sign(support.campaign(n));
            assertThat(artifact.length).isLessThanOrEqualTo(GeminiCampaignApproval.MAX_ARTIFACT_BYTES);
            var verified=GeminiEvaluationRunner.preflight(artifact,support.oracle,support.verifier,directory,CLOCK);
            assertThat(verified.samples()).isEqualTo(n);
            measurements.addObject().put("samples",n).put("signedArtifactBytes",artifact.length).put("canonicalPayloadBytes",GeminiOperatorApproval.canonical(support.campaign(n)).length);
        }
        Files.writeString(Path.of("target/campaign-artifact-measurements.json"),measurements.toPrettyString());
    }
    @Test void equivalentNumericFormsAndMissingSlotOrderDoNotCreateSemanticFailures() throws Exception {
        var a=GeminiOperatorApproval.JSON.readTree("{\"action\":\"CRITERIA\",\"intent\":\"SEARCH\",\"fields\":{\"maxPrice\":{\"operation\":\"REPLACE\",\"value\":2000000}},\"reference\":\"\",\"missingSlots\":[\"rooms\",\"adults\"],\"inferredFields\":[]}");
        var b=GeminiOperatorApproval.JSON.readTree("{\"action\":\"CRITERIA\",\"intent\":\"SEARCH\",\"fields\":{\"maxPrice\":{\"operation\":\"REPLACE\",\"value\":2000000.0}},\"reference\":\"\",\"missingSlots\":[\"adults\",\"rooms\"],\"inferredFields\":[]}");
        assertThat(GeminiEvaluationDataset.semantic(a)).isEqualTo(GeminiEvaluationDataset.semantic(b));
    }
    @Test void reviewAllSixtyWithoutChangingOrApprovingCandidateOracle() throws Exception {
        byte[] bytes;
        try(var stream=getClass().getResourceAsStream("/gemini/vietnamese-nlu-evaluation-candidates-v1.json")){bytes=Objects.requireNonNull(stream).readAllBytes();}
        assertThat(hash(bytes)).isEqualTo(GeminiEvaluationDataset.HASH);
        var rows=GeminiOperatorApproval.JSON.createArrayNode();
        for(var f:GeminiOperatorApproval.JSON.readTree(bytes).path("fixtures")) {
            String id=f.path("id").asText(), utterance=f.path("utterance").asText();
            boolean premodel=VietnameseSecurityPreflight.refused(utterance), denied=f.path("remoteDefault").asText().equals("DENY");
            var response=GeminiOperatorApproval.JSON.createObjectNode().put("action",f.path("expectedAction").asText())
                    .put("intent",f.path("expectedIntent").asText()).put("reference",f.path("expectedReference").asText());
            ObjectNode fields=response.putObject("fields");
            f.path("expectedCriteria").fields().forEachRemaining(e->{ var change=fields.putObject(e.getKey()); if(e.getValue().isNull())change.put("operation","CLEAR"); else change.put("operation","REPLACE").set("value",e.getValue()); });
            response.set("missingSlots",f.path("expectedMissingSlots")); response.putArray("inferredFields");
            var issues=new ArrayList<String>(); String backend="NOT_RUN"; String effectiveIntent="NOT_RUN";
            try {
                var proposal=VietnameseExtraction.parse(response.toString(),CLOCK);
                try {
                    var effective=GeminiExtractionSmoke.corroborate(proposal,utterance,CLOCK); effectiveIntent=effective.intent().name();
                    backend=proposal.action()==VietnameseExtraction.Action.CLARIFY||effective.clarification()==null?"LITERAL_ACCEPTED":"BACKEND_CLARIFIES";
                    if(backend.equals("BACKEND_CLARIFIES"))issues.add("MODEL_TARGET_NOT_LITERAL_CORROBORATED");
                } catch(java.time.DateTimeException rejected){backend="LITERAL_DATE_REJECTED";issues.add("INVALID_DATE_LITERAL_EXCEPTION_NOT_MODEL_FAILURE");}
                catch(IllegalArgumentException rejected){backend="LITERAL_REJECTED";issues.add("CANDIDATE_OR_GRAMMAR_MISMATCH_REVIEW_REQUIRED");}
            } catch(IllegalArgumentException invalid){issues.add("EXPECTED_PROTOCOL_INVALID");}
            if(premodel&&!denied)issues.add("REVIEW_REQUIRED_BUT_PREMODEL_BLOCKED");
            if(denied&&!premodel)issues.add("DATASET_DENY_REMAINS_LOCAL_WITHOUT_GRAMMAR_BLOCK");
            if(!f.path("syntheticContext").path("kind").asText().equals("EMPTY"))issues.add("SINGLE_UTTERANCE_PROTOCOL_NO_MODEL_CONTEXT");
            if(f.path("expectedAction").asText().equals("SELECTED")||f.path("expectedAction").asText().equals("COMPARISON"))issues.add("NO_REFERENCE_RESOLUTION_OR_E2E_PROOF");
            if(f.path("expectedMissingSlots").isEmpty()&&f.path("category").asText().startsWith("missing-"))issues.add("MISSING_SLOT_ORACLE_REVIEW");
            var criteria=f.path("expectedCriteria"); var missing=new HashSet<String>(); f.path("expectedMissingSlots").forEach(slot->missing.add(slot.asText()));
            if(criteria.has("checkIn")||criteria.has("checkOut")) for(String slot:List.of("checkIn","checkOut","rooms","adults","children"))
                if(!criteria.has(slot)&&!missing.contains(slot))issues.add("DATED_TARGET_MISSING_SLOT_REVIEW_"+slot);
            String body=syntheticBody(utterance); int estimate=2*bytes(body)+1024;
            assertThat(estimate).isLessThanOrEqualTo(INPUT_CAP); assertThat(bytes(body)).isLessThanOrEqualTo(BYTE_CAP);
            var tree=GeminiOperatorApproval.JSON.readTree(body); assertThat(tree.has("tools")).isFalse();
            assertThat(tree.path("generationConfig").path("responseJsonSchema")).isEqualTo(GeminiOperatorApproval.JSON.readTree(schema()));
            var row=rows.addObject().put("id",id).put("category",f.path("category").asText()).put("split",f.path("split").asText())
                    .put("remoteCandidate",!denied&&!premodel).put("datasetDeny",denied).put("premodelBlocked",premodel)
                    .put("backendCheck",backend).put("effectiveIntent",effectiveIntent).put("bodyBytes",bytes(body)).put("conservativeInput",estimate)
                    .put("instructionDigest",hash(instruction())).put("schemaDigest",hash(schema())).put("requestDigest",hash(body));
            row.set("issues",GeminiOperatorApproval.JSON.valueToTree(issues));
            assertThat(f.path("oracleReview").asText()).isEqualTo("PENDING_INDEPENDENT_REVIEW");
        }
        assertThat(rows.size()).isEqualTo(60); Files.writeString(Path.of("target/vietnamese-candidate-technical-review.json"),rows.toPrettyString());
    }
}
