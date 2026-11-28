package com.example.aiagent.model;

import com.example.aiagent.nlu.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Explicit local synthetic experiment; no Spring bean, domain client or runtime routing. */
public final class GeminiEvidenceEvaluation {
    static List<GeminiLocalFallbackEvaluation.Planned> plan() throws Exception {
        var original = GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"baseline40",40,null,false,VietnameseSemanticCompiler.VERSION),GeminiQualityEvaluation.EVALUATION_CLOCK);
        var all = new ArrayList<GeminiLocalFallbackEvaluation.Planned>();
        all.add(planned("TECH-"+original.getFirst().id(),"TECHNICAL",original.getFirst(),null));
        var diagnostic = Set.of("VNE1-003","VNE1-004","VNE1-007","VNE1-005","VNE1-002","VNE1-029","VNE1-013","VNE1-011");
        for (var sample : original) if (diagnostic.contains(sample.id())) all.add(planned("DIAG-"+sample.id(),"DIAGNOSTIC",sample,null));
        var selected=original.stream().filter(s->s.id().equals("VNE1-013")).findFirst().orElseThrow();
        all.add(planned("DIAG-R2-SELECTED-01","DIAGNOSTIC_R2",selected,null));
        String variant="Hỏi giá của phòng đã chọn";
        var expected=selected.expected().deepCopy(); ((ObjectNode)expected).put("reference",variant);
        var variantInput=new GeminiEvaluationDataset.Input("GENERALIZED-SELECTED-R2",variant,hash("NO_CONTEXT_SENT"),"selected-reference","GENERALIZED_DIAGNOSTIC",true,"",null);
        all.add(planned("DIAG-R2-SELECTED-02","DIAGNOSTIC_R2",new GeminiLocalEvaluation.Sample("GENERALIZED-SELECTED-R2","DIAGNOSTIC_R2",variantInput,expected,true),null));
        var comparison = Set.of("VNE1-003","VNE1-004","VNE1-007","VNE1-041","VNE1-002","VNE1-013","VNE1-029","VNE1-011");
        for (var model : GeminiLocalModelProfile.values()) for (var sample : original) if (comparison.contains(sample.id()))
            all.add(planned("CMP-"+model.name()+"-"+sample.id(),"COMPARISON",sample,model));
        for (var sample : original) all.add(planned(sample.id(),"FROZEN40",sample,null));
        var fresh = GeminiLlmProviderAdapter.JSON.readTree(GeminiLocalEvaluation.resource("semantic-quality-fresh-v2.json"));
        for (var row : fresh.path("fixtures")) {
            var input = new GeminiEvaluationDataset.Input(row.path("id").asText(),row.path("utterance").asText(),hash("NO_CONTEXT_SENT"),row.path("category").asText(),"FRESH_DEVELOPMENT_GENERALIZATION",true,"",null);
            all.add(planned(input.fixtureId(),"FRESH",new GeminiLocalEvaluation.Sample(input.fixtureId(),"FRESH",input,row.path("expectedExtraction"),true),null));
        }
        return List.copyOf(all);
    }
    private static GeminiLocalFallbackEvaluation.Planned planned(String id,String phase,GeminiLocalEvaluation.Sample source,GeminiLocalModelProfile model) {
        String text = source.input().utterance(), version = VietnameseEvidenceCompiler.VERSION;
        VietnameseSecurityPreflight.validate(text); VietnameseExtraction.parse(source.expected().toString(),GeminiQualityEvaluation.EVALUATION_CLOCK);
        String body = syntheticBody(GeminiLocalExtractionContract.request(text,version));
        if (2L*bytes(body)+1024>INPUT_CAP) throw denied();
        var input = new GeminiEvaluationDataset.Input(source.input().fixtureId(),text,hash("NO_CONTEXT_SENT"),source.input().category(),source.input().split(),true,body,
                new Binding(hash(text),hash(VietnameseEvidenceCompiler.instruction()),hash(VietnameseEvidenceCompiler.schema()),hash(body)));
        return new GeminiLocalFallbackEvaluation.Planned(new GeminiLocalEvaluation.Sample(id,phase,input,source.expected().deepCopy(),source.backendExpected()),phase,version,model);
    }
    static ObjectNode candidate() throws Exception {
        var json=GeminiLlmProviderAdapter.JSON;
        var out=json.createObjectNode().put("version",VietnameseEvidenceCompiler.VERSION).put("revision",VietnameseEvidenceCompiler.REVISION).put("evaluationClock",GeminiQualityEvaluation.EVALUATION_CLOCK.instant().toString())
                .put("primaryDevelopmentModel",GeminiLocalModelProfile.FLASH_35_LITE.id).put("ordinaryRuntime","OFF/FAKE/liveOFF")
                .put("promptHash",hash(VietnameseEvidenceCompiler.instruction())).put("schemaHash",hash(VietnameseEvidenceCompiler.schema()))
                .put("freshHash",hash(GeminiLocalEvaluation.resource("semantic-quality-fresh-v2.json")))
                .put("datasetHash",GeminiEvaluationDataset.HASH).put("oracleHash",GeminiLocalEvaluation.ORACLE_HASH);
        var sources=out.putObject("sources");
        for(String name:List.of("model/VietnameseEvidenceCompiler.java","model/GeminiEvidenceEvaluation.java","model/GeminiLocalFallbackEvaluation.java",
                "model/GeminiHttpTransport.java","model/GeminiExtractionSmoke.java","model/GeminiExtractionProtocol.java","model/GeminiLocalExtractionContract.java",
                "model/GeminiLocalFailurePolicy.java","model/GeminiLocalModelFallback.java","model/GeminiLocalModelProfile.java",
                "nlu/VietnameseParser.java","nlu/VietnameseExtraction.java","nlu/DestinationLabels.java","nlu/VietnameseNumbers.java","nlu/ReadIntentRouting.java","nlu/ComparisonRouting.java")) {
            var path=Path.of("ai-agent-service/src/main/java/com/example/aiagent").resolve(name);
            sources.put(path.toString().replace('\\','/'),hash(Files.readAllBytes(path)));
        }
        return out;
    }
    static void freeze(Path directory) throws Exception {
        Files.createDirectories(directory); var path=directory.resolve("frozen-candidate.json"); var now=candidate();
        if(Files.exists(path)) { if(!GeminiLlmProviderAdapter.JSON.readTree(Files.readString(path)).equals(now)) throw denied(); }
        else Files.writeString(path,now.toPrettyString(),StandardOpenOption.CREATE_NEW);
    }
    static void exportOffline(Path directory) throws Exception {
        Files.createDirectories(directory); var json=GeminiLlmProviderAdapter.JSON;
        var out=json.createObjectNode().put("version","evidence-extraction-offline-v1").put("actualHttpAttempts",0).put("credentialReads",0);
        out.set("candidate",candidate()); out.put("instruction",VietnameseEvidenceCompiler.instruction()).put("schema",VietnameseEvidenceCompiler.schema());
        var rows=out.putArray("observations");
        for(var p:plan()) {
            var s=p.sample(); var row=rows.addObject().put("sampleId",s.id()).put("sourceId",s.input().fixtureId()).put("phase",p.phase())
                    .put("utterance",s.input().utterance()).put("status","NOT_RUN").put("expectedRequestMeaning",VietnameseEvidenceCompiler.requestMeaning(s.input().utterance(),GeminiQualityEvaluation.EVALUATION_CLOCK));
            row.set("expectedExtraction",s.expected()); row.set("expectedEvidenceWitnesses",json.valueToTree(VietnameseEvidenceCompiler.witnesses(s.input().utterance())));
            var wires=row.putObject("wireDigests"); for(var model:GeminiLocalModelProfile.values()) wires.put(model.id,hash(syntheticBody(GeminiLocalExtractionContract.request(s.input().utterance(),VietnameseEvidenceCompiler.VERSION,model),model)));
            try {
                var canonical=VietnameseEvidenceCompiler.deterministic(s.input().utterance(),GeminiQualityEvaluation.EVALUATION_CLOCK);
                row.set("deterministicExtraction",canonical); row.put("deterministicExact",GeminiEvaluationDataset.semantic(canonical).equals(GeminiEvaluationDataset.semantic(s.expected())));
            } catch(RuntimeException invalid) { row.put("deterministicExact",false).put("deterministicResult","REJECTED"); }
        }
        Files.writeString(directory.resolve("offline-plan-and-parser-comparator-v1.json"),out.toPrettyString(),StandardOpenOption.CREATE_NEW);
    }
    public static void main(String[] args) throws Exception {
        if(args.length==2&&args[0].equals("--offline-export")) { exportOffline(Path.of(args[1])); return; }
        boolean live=false; String phase="ALL",segment="campaign"; Path directory=null;
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
        if(directory==null||!directory.endsWith(Path.of("ai-agent-service/.local/evidence-extraction-v1"))
                ||!Set.of("ALL","TECHNICAL","DIAGNOSTIC","DIAGNOSTIC_R2","COMPARISON","FROZEN40","FRESH").contains(phase)
                ||!segment.matches("(?:campaign|segment-[a-z0-9-]{1,40})")||live&&phase.equals("ALL")) throw denied();
        if(live&&Set.of("COMPARISON","FROZEN40","FRESH").contains(phase)) freeze(directory);
        var report=GeminiLocalFallbackEvaluation.run(directory,live,!phase.equals("COMPARISON"),phase,Clock.systemUTC(),()->System.getenv("GEMINI_API_KEY"),
                (request,control,timeout)->GeminiLocalFallbackEvaluation.exchange(new GeminiHttpTransport().localDiagnostics(),request,control,timeout),
                System::nanoTime,nanos->Thread.sleep(Duration.ofNanos(nanos)),new GeminiLocalModelFallback(),segment,false,VietnameseEvidenceCompiler.VERSION,false,true);
        System.out.println("EVIDENCE_EXTRACTION "+report.path("stopReason").asText()+" attempts="+report.path("actualHttpAttempts").asInt());
        if(!Set.of("COMPLETED","OFFLINE_PREFLIGHT").contains(report.path("stopReason").asText())) System.exit(2);
    }
    private GeminiEvidenceEvaluation() { }
}
