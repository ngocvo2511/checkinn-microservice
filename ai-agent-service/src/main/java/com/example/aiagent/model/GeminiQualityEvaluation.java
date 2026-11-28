package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Batch-specific synthetic plan; delegates transport, fallback and accounting to the existing local runner. */
public final class GeminiQualityEvaluation {
    static final Clock EVALUATION_CLOCK=Clock.fixed(Instant.parse("2026-11-24T10:18:48.242851600Z"),ZoneOffset.UTC);
    static List<GeminiLocalFallbackEvaluation.Planned> plan(Clock ignored) throws Exception {
        String version=VietnameseSemanticCompiler.V2;
        var result=new ArrayList<GeminiLocalFallbackEvaluation.Planned>();
        var original=GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"baseline40",40,null,false,VietnameseSemanticCompiler.VERSION),EVALUATION_CLOCK);
        Set<String> diagnostics=Set.of("VNE1-003","VNE1-004","VNE1-007","VNE1-009","VNE1-010","VNE1-013","VNE1-033","VNE1-040","VNE1-043","VNE1-049");
        for(var sample:original) if(diagnostics.contains(sample.id())) result.add(planned("DIAG-"+sample.id(),"DIAGNOSTIC",sample.input().utterance(),sample.expected(),version,sample.input().category(),sample.input().split()));
        for(var sample:original) result.add(planned(sample.id(),"FROZEN40",sample.input().utterance(),sample.expected(),version,sample.input().category(),sample.input().split()));
        var fresh=GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalEvaluation.resource("semantic-quality-fresh-v2.json"));
        for(var fixture:fresh.path("fixtures")) result.add(planned(fixture.path("id").asText(),"FRESH",fixture.path("utterance").asText(),fixture.path("expectedExtraction"),version,fixture.path("category").asText(),fixture.path("classification").asText()));
        return List.copyOf(result);
    }
    private static GeminiLocalFallbackEvaluation.Planned planned(String id,String phase,String text,com.fasterxml.jackson.databind.JsonNode expected,String version,String category,String split) {
        VietnameseSecurityPreflight.validate(text); VietnameseExtraction.parse(expected.toString(),EVALUATION_CLOCK);
        String body=syntheticBody(GeminiLocalExtractionContract.request(text,version));
        if(2L*bytes(body)+1024>INPUT_CAP) throw denied();
        var input=new GeminiEvaluationDataset.Input(id,text,hash("NO_CONTEXT_SENT"),category,split,true,body,
                new Binding(hash(text),hash(GeminiLocalExtractionContract.instruction(version)),hash(GeminiLocalExtractionContract.schema(version)),hash(body)));
        return new GeminiLocalFallbackEvaluation.Planned(new GeminiLocalEvaluation.Sample(id,phase,input,expected.deepCopy(),true),phase,version);
    }
    public static void main(String[] args) throws Exception {
        boolean live=false; String phase="ALL", segment="campaign"; Path directory=null;
        var seen=new HashSet<String>();
        for(int i=0;i<args.length;i++) {
            if(!seen.add(args[i])) throw denied();
            switch(args[i]) {
                case "--live" -> live=true;
                case "--phase" -> phase=args[++i];
                case "--segment" -> segment=args[++i];
                case "--output-dir" -> directory=Path.of(args[++i]).toAbsolutePath().normalize();
                default -> throw denied();
            }
        }
        if(directory==null || !Set.of("ALL","DIAGNOSTIC","FROZEN40","FRESH").contains(phase)
                || !segment.matches("(?:campaign|segment-[a-z0-9-]{1,40})")) throw denied();
        var report=GeminiLocalFallbackEvaluation.run(directory,live,true,phase,Clock.systemUTC(),()->System.getenv("GEMINI_API_KEY"),
                (request,control,timeout)-> {
                    return GeminiLocalFallbackEvaluation.exchange(new GeminiHttpTransport().localDiagnostics(),request,control,timeout);
                },System::nanoTime,nanos->Thread.sleep(Duration.ofNanos(nanos)),new GeminiLocalModelFallback(),segment,false,VietnameseSemanticCompiler.V2,true);
        System.out.println("SEMANTIC_QUALITY "+report.path("stopReason").asText()+" attempts="+report.path("actualHttpAttempts").asInt());
        if(!Set.of("COMPLETED","OFFLINE_PREFLIGHT").contains(report.path("stopReason").asText())) System.exit(2);
    }
    private GeminiQualityEvaluation() { }
}
