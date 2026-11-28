package com.example.aiagent.model;

import com.example.aiagent.nlu.VietnameseExtraction;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Replays retained synthetic envelopes through the real SDK with an in-memory exchange. No credentials/network. */
public final class GeminiSemanticReplay {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("source segment and new output required");
        Path source = Path.of(args[0]); var json = GeminiLlmProviderAdapter.JSON;
        var original = json.readTree(Files.readAllBytes(source));
        var clock = Clock.fixed(Instant.parse(original.path("evaluationClock").asText()), ZoneOffset.UTC);
        var out = json.createObjectNode().put("version", "actual-semantic-envelope-replay-v2")
                .put("sourceSha256", hash(Files.readAllBytes(source))).put("evaluationClock", clock.instant().toString())
                .put("actualHttpAttempts", 0).put("credentialReads", 0);
        var rows = out.putArray("observations");
        for (var old : original.path("observations")) {
            if (old.path("attempts").isEmpty()) continue;
            var attempt = old.path("attempts").get(old.path("attempts").size()-1);
            var row = rows.addObject(); row.setAll((com.fasterxml.jackson.databind.node.ObjectNode) old.deepCopy());
            if (attempt.path("httpStatus").asInt()!=200) { row.put("replayStatus","NO_SUCCESS_ENVELOPE"); continue; }
            String protocol=old.path("protocolVersion").asText(), text=old.path("utterance").asText();
            var profile=Arrays.stream(GeminiLocalModelProfile.values()).filter(m->m.id.equals(attempt.path("actualModel").asText())).findFirst().orElseThrow();
            Path envelope=source.getParent().resolve("responses").resolve(attempt.path("attemptId").asText()+".json");
            String retained=Files.readString(envelope), body=syntheticBody(GeminiLocalExtractionContract.request(text,protocol,profile),profile);
            row.put("envelopeSha256",hash(retained)).put("semanticSchemaValid",false).put("compilerResult","NOT_RUN");
            var input=new GeminiEvaluationDataset.Input(old.path("sampleId").asText(),text,hash("NO_CONTEXT_SENT"),"REPLAY","DEVELOPMENT",true,body,
                    new Binding(hash(text),hash(GeminiLocalExtractionContract.instruction(protocol)),hash(GeminiLocalExtractionContract.schema(protocol)),hash(body)));
            var result=GeminiExtractionSmoke.evaluate(input,INPUT_CAP,new LlmProviderAdapter.CallControl(Duration.ofSeconds(10)),
                    ()->{},()->Duration.ofSeconds(10),()->"synthetic-noncredential",()->(request,control,timeout)->
                            SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(retained).build(),clock,
                    GeminiLocalExtractionContract.request(text,protocol,profile),raw->{
                        row.put("actualExtractionText",raw);
                        if (!protocol.equals(VietnameseSemanticCompiler.VERSION)) return raw;
                        var semantic=VietnameseSemanticCompiler.parse(raw); row.put("semanticSchemaValid",true).set("semanticProposal",semantic);
                        row.put("compilerResult","FAIL"); var compiled=VietnameseSemanticCompiler.compile(semantic,clock);
                        row.put("compilerResult","PASS").set("compiledExtraction",compiled); return compiled.toString();
                    },profile);
            row.put("schemaValid",result.schemaValid()).put("backendAccepted",result.backendAccepted())
                    .put("semanticPass",result.schemaValid()&&GeminiEvaluationDataset.semantic(old.path("expectedExtraction")).equals(result.semantic()))
                    .put("replayStatus",result.category()); row.set("actual",result.semantic()); row.set("pipeline",result.pipeline());
        }
        int valid=0, raw=0, exact=0, ca=0, cr=0, ia=0, ir=0, rejected=0;
        for(var r:rows) {
            if(r.path("semanticSchemaValid").asBoolean()) raw++;
            if(r.path("compilerResult").asText().equals("FAIL")) rejected++;
            if(!r.path("schemaValid").asBoolean()) continue; valid++;
            boolean correct=r.path("semanticPass").asBoolean(), accepted=r.path("backendAccepted").asBoolean();
            if(correct) { exact++; if(accepted) ca++; else cr++; } else if(accepted) ia++; else ir++;
        }
        var metrics=out.putObject("metrics").put("rawSchemaValid",raw).put("canonicalTypedValid",valid).put("exactBusinessSemantics",exact)
                .put("correctAccepted",ca).put("correctRejected",cr).put("incorrectAccepted",ia).put("incorrectRejected",ir).put("compilerRejections",rejected);
        Files.writeString(Path.of(args[1]),out.toPrettyString(),StandardOpenOption.CREATE_NEW); System.out.println(metrics);
    }
    private GeminiSemanticReplay() { }
}
