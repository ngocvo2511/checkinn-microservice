package com.example.aiagent.model;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.CLOCK;

class GeminiEvaluationOperatorToolTest {
    @TempDir Path directory;
    @BeforeAll static void guard(){GeminiExtractionUnblockTest.denyExternalAndKeys();}
    @AfterAll static void restore(){GeminiExtractionUnblockTest.restore();}
    @ParameterizedTest @ValueSource(strings={"initial-smoke","nlu","extended"})
    void templateIsUnsignedNonexecutableAndNeverOverwritten(String kind) throws Exception {
        Path output=directory.resolve("UNSIGNED.json");
        GeminiEvaluationPreflight.main(new String[]{"template",kind,output.toString()});
        byte[] bytes=Files.readAllBytes(output); var support=new GeminiEvaluationTestSupport(directory);
        assertThatThrownBy(()->support.verifier.signedPayload(bytes,262144)).isInstanceOf(ProviderException.class);
        assertThatThrownBy(()->GeminiEvaluationPreflight.main(new String[]{"template",kind,output.toString()})).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(output)).isEqualTo(bytes);
    }
    @Test void canonicalPreparationIsDeterministicAndDuplicateKeysCannotBeSignedByTool() throws Exception {
        Path input=directory.resolve("payload.json"), output=directory.resolve("canonical.bin");
        Files.writeString(input,"{\"z\":10.0,\"a\":1}"); GeminiEvaluationPreflight.main(new String[]{"canonicalize",input.toString(),output.toString()});
        assertThat(Files.readAllBytes(output)).isEqualTo(GeminiOperatorApproval.canonical(GeminiOperatorApproval.JSON.readTree("{\"a\":1,\"z\":10}")));
        Files.writeString(input,"{\"a\":1,\"a\":2}");
        assertThatThrownBy(()->GeminiEvaluationPreflight.main(new String[]{"canonicalize",input.toString(),directory.resolve("bad.bin").toString()})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void independentlyProvidedPublicKeyIsUsableAndRevocationsApply() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); Path pub=directory.resolve("TEST_PUBLIC.der"), revoked=directory.resolve("TEST_REVOCATIONS.json");
        Files.write(pub,support.signer.getPublic().getEncoded()); Files.writeString(revoked,"[]");
        var verifier=GeminiEvaluationPreflight.loadVerifier(pub,"TEST_ISSUER","TEST_KEY",revoked,CLOCK);
        byte[] artifact=support.sign(support.campaign(3));
        assertThat(GeminiEvaluationRunner.preflight(artifact,support.oracle,verifier,directory,CLOCK).samples()).isEqualTo(3);
        Files.writeString(revoked,"[\"TEST_KEY\"]"); var revokedVerifier=GeminiEvaluationPreflight.loadVerifier(pub,"TEST_ISSUER","TEST_KEY",revoked,CLOCK);
        assertThatThrownBy(()->GeminiEvaluationRunner.preflight(artifact,support.oracle,revokedVerifier,directory,CLOCK)).isInstanceOf(ProviderException.class);
        try(var files=Files.list(directory)){assertThat(files.noneMatch(p->p.toString().endsWith(".journal"))).isTrue();}
    }
    @Test void noExecuteOrSecretCommandExists() {
        assertThatThrownBy(()->GeminiEvaluationPreflight.main(new String[]{"execute"})).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void externalSignatureCanBeEnvelopedWithoutLoadingAnyPrivateKey() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); var payload=support.campaign(3);
        var signed=GeminiOperatorApproval.JSON.readTree(support.sign(payload));
        Path input=directory.resolve("payload.json"), signature=directory.resolve("TEST_SIGNATURE.bin"), output=directory.resolve("artifact.json");
        Files.write(input,GeminiOperatorApproval.JSON.writeValueAsBytes(payload));
        Files.write(signature,Base64.getDecoder().decode(signed.path("signature").textValue()));
        GeminiEvaluationPreflight.main(new String[]{"envelope",input.toString(),signature.toString(),output.toString()});
        assertThat(GeminiEvaluationRunner.preflight(Files.readAllBytes(output),support.oracle,support.verifier,directory,CLOCK).samples()).isEqualTo(3);
    }
    @Test void bindScopePreparesExactUserSelectedSamplesButDoesNotApproveOrExecute() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory);
        Path scope=directory.resolve("scope.json"), oracle=directory.resolve("TEST_ORACLE.json"), output=directory.resolve("unsigned-payload.json");
        Files.writeString(scope,"[{\"sampleId\":\"SAMPLE1\",\"sessionId\":\"SESSION1\",\"fixtureId\":\"VNE1-001\"}]");
        Files.write(oracle,support.oracle);
        GeminiEvaluationPreflight.main(new String[]{"bind-scope","nlu",scope.toString(),oracle.toString(),"CAMPAIGN1",output.toString()});
        var payload=GeminiOperatorApproval.JSON.readTree(Files.readAllBytes(output));
        assertThat(payload.path("state").asText()).isEqualTo("DRAFT"); assertThat(payload.path("samples").size()).isEqualTo(1);
        assertThat(payload.path("samples").get(0).path("binding")).isEqualTo(GeminiOperatorApproval.JSON.valueToTree(support.dataset.input("VNE1-001").binding()));
        assertThatThrownBy(()->support.verifier.signedPayload(Files.readAllBytes(output),262144)).isInstanceOf(ProviderException.class);
    }
}
