package com.example.aiagent.model;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;
import static com.example.aiagent.model.GeminiExtractionUnblockTest.CLOCK;

class GeminiSharedAllocationTest {
    @TempDir Path directory;
    Path descriptor;
    @BeforeAll static void guard(){GeminiExtractionUnblockTest.denyExternalAndKeys();}
    @AfterAll static void restore(){GeminiExtractionUnblockTest.restore();}
    void prepare(int requests) throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); descriptor=directory.resolve("TEST_ALLOCATION_DESCRIPTOR.json");
        Files.write(descriptor,GeminiOperatorApproval.JSON.writeValueAsBytes(support.allocation(requests,"C1","C2","C3")));
    }
    Process start(String campaign,String mode,String...extras) throws Exception {
        var command=new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Djava.security.manager=allow","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),GeminiAllocationChild.class.getName(),descriptor.toString(),directory.toString(),campaign,mode));
        command.addAll(List.of(extras));
        return new ProcessBuilder(command).redirectError(directory.resolve("child-"+UUID.randomUUID()+".stderr").toFile()).start();
    }
    int finish(Process child) throws Exception {
        try { assertThat(child.waitFor(15,TimeUnit.SECONDS)).isTrue(); return child.exitValue(); }
        finally { if(child.isAlive()) child.destroyForcibly(); }
    }
    @Test void actualConcurrentJvmsCannotShareOneActiveAllocation() throws Exception {
        prepare(2); Process owner=start("C1","hold");
        try {
            var reader=new BufferedReader(new InputStreamReader(owner.getInputStream()));
            assertThat(reader.readLine()).isEqualTo("ACQUIRED");
            assertThat(finish(start("C2","consume"))).isEqualTo(10);
            owner.getOutputStream().write(10); owner.getOutputStream().flush(); assertThat(finish(owner)).isZero();
            assertThat(finish(start("C2","consume"))).isZero();
        } finally { if(owner.isAlive()) owner.destroyForcibly(); }
    }
    @Test void durableConsumptionSurvivesRestartAndFreshCampaignCannotResetBudget() throws Exception {
        prepare(1); assertThat(finish(start("C1","consume"))).isZero();
        assertThat(finish(start("C2","consume"))).isEqualTo(10); assertThat(finish(start("C1","consume"))).isEqualTo(10);
    }
    @Test void crashedJvmLeavesEntireAllocationUncertainAndNonresumable() throws Exception {
        prepare(2); assertThat(finish(start("C1","crash"))).isZero();
        assertThat(finish(start("C2","consume"))).isEqualTo(10);
    }
    @ParameterizedTest @ValueSource(strings={"","partial","corrupt\n","valid-header-only"})
    void existingUncertainBytesFailClosedAcrossJvmWithoutRepair(String raw) throws Exception {
        prepare(2); Path ledger=directory.resolve("allocation-v1-"+hash("TEST_ALLOCATION")+".journal");
        if(raw.equals("valid-header-only")) {
            var header=GeminiOperatorApproval.JSON.createObjectNode().put("type","HEADER").put("binding",hash(GeminiOperatorApproval.canonical(GeminiOperatorApproval.JSON.readTree(Files.readAllBytes(descriptor)))))
                    .put("previous","0".repeat(64)).putNull("reservation");
            header.put("checksum",hash(GeminiOperatorApproval.canonical(header))); raw=header+"\n";
        }
        Files.writeString(ledger,raw);
        assertThat(finish(start("C1","consume"))).isEqualTo(10); assertThat(Files.readString(ledger)).isEqualTo(raw);
    }
    @ParameterizedTest @ValueSource(strings={"campaign","session","sample"})
    void duplicateIdentitiesCannotBeConsumedByAnotherJvm(String kind) throws Exception {
        prepare(2); assertThat(finish(start("C1","consume","SESSION1","SAMPLE1"))).isZero();
        assertThat(finish(start(kind.equals("campaign")?"C1":"C2","consume",kind.equals("session")?"SESSION1":"SESSION2",kind.equals("sample")?"SAMPLE1":"SAMPLE2"))).isEqualTo(10);
    }
    @Test void modifiedAllocationEnvelopeCannotReplaceExistingIdentity() throws Exception {
        prepare(2); assertThat(finish(start("C1","consume"))).isZero();
        var value=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiOperatorApproval.JSON.readTree(Files.readAllBytes(descriptor)); value.put("requests",3); Files.writeString(descriptor,value.toString());
        assertThat(finish(start("C2","consume"))).isEqualTo(10);
    }
    @Test void directorySubstitutionIsDeniedWithoutCreatingLedger() throws Exception {
        var support=new GeminiEvaluationTestSupport(directory); Path other=Files.createDirectory(directory.resolve("other"));
        var r=new GeminiSharedAllocation.Reservation("C1",List.of("S"),List.of("F"),1,INPUT_CAP,OUTPUT_CAP,INPUT_CAP+OUTPUT_CAP,java.math.BigDecimal.ONE);
        assertThatThrownBy(()->new GeminiSharedAllocation(support.allocation(2,"C1"),r,directory,other,CLOCK)).isInstanceOf(ProviderException.class);
        try(var files=Files.list(other)){assertThat(files.count()).isZero();}
    }
}
