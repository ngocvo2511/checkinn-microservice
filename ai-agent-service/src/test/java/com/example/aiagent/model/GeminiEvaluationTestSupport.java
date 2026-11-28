package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.*;

/** Test-owned signatures/evidence/fictional rates only. No production approval or key material. */
final class GeminiEvaluationTestSupport {
    final Path directory;
    final KeyPair signer;
    final GeminiOperatorApproval verifier;
    final byte[] oracle;
    final GeminiEvaluationDataset dataset;
    GeminiEvaluationTestSupport(Path directory) throws Exception {
        this.directory = directory; signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        verifier = new GeminiOperatorApproval(Map.of("TEST_KEY",new GeminiOperatorApproval.Root("TEST_ISSUER",signer.getPublic())),Set.of(),CLOCK);
        var expectations = new LinkedHashMap<String,GeminiEvaluationDataset.Expectation>();
        for(int i=1;i<=3;i++) expectations.put("VNE1-%03d".formatted(i),new GeminiEvaluationDataset.Expectation(
                GeminiOperatorApproval.JSON.readTree(expected("VEX1-%02d".formatted(i))),true,"SUPPORTED",true));
        JsonNode grammar = GeminiOperatorApproval.JSON.readTree(expected("VEX1-01").replace("Đà Lạt","Huế"));
        expectations.put("VNE1-041",new GeminiEvaluationDataset.Expectation(grammar,true,"SUPPORTED",true));
        // Test-owned expectation for the remaining invalid-calendar corroboration gap.
        JsonNode invalidDate=GeminiOperatorApproval.JSON.readTree("{\"action\":\"CLARIFY\",\"intent\":\"CLARIFY\",\"fields\":{},\"reference\":\"\",\"missingSlots\":[\"checkIn\",\"checkOut\"],\"inferredFields\":[]}");
        expectations.put("VNE1-009",new GeminiEvaluationDataset.Expectation(invalidDate,true,"UNSUPPORTED_GRAMMAR",true));
        JsonNode blocked = GeminiOperatorApproval.JSON.readTree("{\"action\":\"CLARIFY\",\"intent\":\"UNSUPPORTED\",\"fields\":{},\"reference\":\"\",\"missingSlots\":[],\"inferredFields\":[]}");
        expectations.put("VNE1-054",new GeminiEvaluationDataset.Expectation(blocked,false,"EXPECTED_REJECTION",false));
        oracle = GeminiOperatorApproval.JSON.writeValueAsBytes(new GeminiEvaluationDataset.Oracle("TEST_ORACLE_V1",GeminiEvaluationDataset.HASH,
                "INDEPENDENTLY_REVIEWED","TEST_ONLY_REVIEWER_NOT_REAL_APPROVAL",expectations));
        dataset = new GeminiEvaluationDataset(oracle,CLOCK);
    }
    byte[] sign(ObjectNode payload) throws Exception {
        Signature signature=Signature.getInstance("Ed25519"); signature.initSign(signer.getPrivate()); signature.update(GeminiOperatorApproval.canonical(payload));
        ObjectNode envelope=GeminiOperatorApproval.JSON.createObjectNode().put("algorithm","Ed25519");
        envelope.set("payload",payload); envelope.put("signature",Base64.getEncoder().encodeToString(signature.sign()));
        return GeminiOperatorApproval.JSON.writeValueAsBytes(envelope);
    }
    GeminiSharedAllocation.Limits allocation(int n, String...campaigns) {
        return new GeminiSharedAllocation.Limits("TEST_ALLOCATION","TEST_PROJECT",CLOCK.instant(),CLOCK.instant().plusSeconds(1800),n,
                (long)n*INPUT_CAP,(long)n*OUTPUT_CAP,(long)n*(INPUT_CAP+OUTPUT_CAP),new BigDecimal("100"),1,List.of(campaigns));
    }
    ObjectNode campaign(int n) {
        var p=new GeminiEvaluationCampaign.Policy("TEST_CAMPAIGN",GeminiEvaluationCampaign.Mode.VIETNAMESE_NLU_EVALUATION,GeminiEvaluationDataset.HASH,
                10,n,(long)n*(INPUT_CAP+OUTPUT_CAP),new BigDecimal("10"),1,Math.min(20,n),Math.min(20L,n)*(INPUT_CAP+OUTPUT_CAP),
                INPUT_CAP,OUTPUT_CAP,0,Duration.ofMinutes(30),CLOCK.instant().plusSeconds(1800),BigDecimal.ONE,new BigDecimal("2"),"TEST_ALLOCATION");
        var samples=new ArrayList<GeminiCampaignApproval.Sample>();
        for(int i=0;i<n;i++) {String id="VNE1-%03d".formatted(i%3+1); var input=dataset.input(id);
            samples.add(new GeminiCampaignApproval.Sample("TEST_SAMPLE_"+i,"TEST_SESSION_"+(i/20),id,input.contextDigest(),input.binding()));}
        var evidence=new GeminiOperatorApproval.Evidence("TEST_PROJECT","TEST_PRICING","TEST_QUOTA","TEST_PRIVACY",CLOCK.instant(),CLOCK.instant().plusSeconds(1800),"TEST_ALLOCATION","TEST_WINDOW");
        return GeminiOperatorApproval.JSON.valueToTree(new GeminiCampaignApproval.Claims("operator-evaluation-campaign-v1","TEST_ISSUER","TEST_KEY","TEST_OPERATOR",
                "TEST_APPROVAL","APPROVED_FOR_SEPARATE_LIVE_BATCH",GeminiCampaignApproval.PURPOSE,MODEL,VERSION,GeminiEvaluationDataset.DATASET_VERSION,
                GeminiEvaluationDataset.HASH,dataset.oracleVersion(),dataset.oracleHash(),"SYNTHETIC_ONLY",CLOCK.instant(),"CONTINUE_SEMANTIC_ONLY",
                "DENY_ON_REVOCATION_OR_UNCERTAINTY","ALL_BILLABLE_CLASSES_UPPER_BOUND",(long)n*INPUT_CAP,(long)n*OUTPUT_CAP,directory,evidence,
                allocation(n,"TEST_CAMPAIGN"),p,samples));
    }
    ObjectNode sequence() {
        var h=new GeminiExtractionUnblockTest(); h.directory=directory; var review=h.review("VEX1-01","VEX1-02","VEX1-03");
        var evidence=new GeminiOperatorApproval.Evidence("TEST_PROJECT","TEST_PRICE","TEST_QUOTA","TEST_PRIVACY",CLOCK.instant(),CLOCK.instant().plusSeconds(120),"TEST_ALLOCATION","TEST_WINDOW");
        var approval=new GeminiOperatorApproval.Claims("operator-extraction-approval-v1","TEST_ISSUER","TEST_KEY","INITIAL_SMOKE","DENY_ON_REVOCATION_OR_UNCERTAINTY",evidence,review);
        return GeminiOperatorApproval.JSON.valueToTree(new GeminiOperatorApproval.SequenceClaims("operator-extraction-sequence-v2","TEST_ISSUER","TEST_KEY",approval,
                List.of("VEX1-01","VEX1-02","VEX1-03"),"STOP_ON_ANY_FAILURE",allocation(3,review.sessionId())));
    }
}
