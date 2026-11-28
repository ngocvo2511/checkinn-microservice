package com.example.aiagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import static com.example.aiagent.model.GeminiExtractionProtocol.*;

/** Bounded Batch10B4D-FINAL local synthetic CLI. No Spring/runtime wiring or domain clients. */
public final class GeminiLocalFallbackEvaluation {
    static final String VERSION = "local-final-evaluation-v1";
    static final Map<String,Integer> CAPS = Map.of("TECHNICAL",3,"REMAINING_A",15,"B_DIAGNOSTIC",8,"B40",40,"SUPPLEMENTAL",24,"FALLBACK_RESERVE",30);
    record Planned(GeminiLocalEvaluation.Sample sample, String phase, String protocol, GeminiLocalModelProfile fixedModel) {
        Planned(GeminiLocalEvaluation.Sample sample, String phase, String protocol) { this(sample, phase, protocol, null); }
    }
    record Reply(SuccessfulHttpResponse response, int status, String errorBody, boolean terminated, String retryAfter,
                 ProviderException.Category failure) {
        Reply(SuccessfulHttpResponse response, int status, String errorBody, boolean terminated, String retryAfter) {
            this(response, status, errorBody, terminated, retryAfter, null);
        }
    }
    @FunctionalInterface interface Exchange { Reply execute(dev.langchain4j.http.client.HttpRequest request, LlmProviderAdapter.CallControl control, Duration timeout); }
    /** Preserve known transport termination even when no HTTP status arrived. Never infers termination. */
    static Reply exchange(GeminiHttpTransport transport, dev.langchain4j.http.client.HttpRequest request,
                          LlmProviderAdapter.CallControl control, Duration timeout) {
        SuccessfulHttpResponse response=null;
        ProviderException.Category category=null;
        try { response=transport.execute(request,control,timeout); }
        catch(RuntimeException failure) {
            if(transport.observedStatus()==0&&!transport.terminated()) throw failure;
            category=failure instanceof ProviderException typed ? typed.category() : ProviderException.Category.UNAVAILABLE;
        }
        return new Reply(response,transport.observedStatus(),transport.errorBody(),transport.terminated(),transport.retryAfter(),category);
    }
    private GeminiLocalFallbackEvaluation() { }
    public static void main(String[] args) {
        try {
            boolean live = false, fallback = false, recheck = false; Path directory = null; String phase = "ALL", segment = "campaign";
            GeminiLocalModelProfile verificationModel = null;
            String verificationProtocol = VietnameseSemanticCompiler.VERSION;
            var seen = new HashSet<String>();
            for (int i=0;i<args.length;i++) {
                if (!seen.add(args[i])) throw denied();
                switch(args[i]) {
                    case "--live" -> live=true;
                    case "--fallback" -> fallback=true;
                    case "--recheck-primary" -> recheck=true;
                    case "--segment" -> segment=args[++i];
                    case "--verification-model" -> {
                        String id=args[++i]; verificationModel=Arrays.stream(GeminiLocalModelProfile.values()).filter(m->m.id.equals(id)).findFirst().orElseThrow(GeminiExtractionProtocol::denied);
                    }
                    case "--verification-protocol" -> verificationProtocol=args[++i];
                    case "--phase" -> phase=args[++i];
                    case "--output-dir" -> directory=Path.of(args[++i]).toAbsolutePath().normalize();
                    default -> throw denied();
                }
            }
            if (directory==null || !segment.matches("(?:campaign|segment-[a-z0-9-]{1,40})")
                    || recheck && !phase.equals("MODEL_VERIFICATION")
                    || verificationModel!=null && (!phase.equals("MODEL_VERIFICATION")||fallback||recheck)
                    || !Set.of(GeminiLocalExtractionContract.V3,VietnameseSemanticCompiler.VERSION).contains(verificationProtocol)
                    || seen.contains("--verification-protocol")&&!phase.equals("MODEL_VERIFICATION")
                    || !Set.of("ALL","TECHNICAL","REMAINING_A","B_DIAGNOSTIC","B40","SUPPLEMENTAL","MODEL_VERIFICATION").contains(phase)) throw denied();
            final var selected=verificationModel;
            var report=run(directory,live,fallback,phase,Clock.systemUTC(),()->System.getenv("GEMINI_API_KEY"), (request,control,timeout)-> {
                return exchange(new GeminiHttpTransport().localDiagnostics(),request,control,timeout);
            },System::nanoTime,nanos->Thread.sleep(Duration.ofNanos(nanos)),new GeminiLocalModelFallback(m->m.eligible()&&(selected==null||selected==m)),segment,recheck,verificationProtocol);
            System.out.println("LOCAL_FINAL_EVALUATION "+report.path("stopReason").asText()+" attempts="+report.path("actualHttpAttempts").asInt());
            if(!Set.of("OFFLINE_PREFLIGHT","COMPLETED").contains(report.path("stopReason").asText())) System.exit(2);
        } catch(Exception rejected) { System.err.println("LOCAL_FINAL_EVALUATION_REJECTED_NO_RETRY"); System.exit(2); }
    }
    static List<Planned> plan(Clock clock) throws Exception {
        String a=GeminiLocalExtractionContract.V3, b=VietnameseSemanticCompiler.VERSION;
        var all=new ArrayList<Planned>();
        var smoke=GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"initial-smoke",3,null,false,a),clock);
        smoke.forEach(s->all.add(new Planned(s,"TECHNICAL",a)));
        var baseline=GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"baseline40",40,null,false,a),clock);
        var remaining=Set.of("VNE1-031","VNE1-032","VNE1-033","VNE1-037","VNE1-039","VNE1-040","VNE1-041","VNE1-042","VNE1-043","VNE1-046","VNE1-047","VNE1-048","VNE1-049","VNE1-050","VNE1-051");
        baseline.stream().filter(s->remaining.contains(s.id())).forEach(s->all.add(new Planned(s,"REMAINING_A",a)));
        GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"semantic-diagnostic",8,null,false,b),clock)
                .forEach(s->all.add(new Planned(s,"B_DIAGNOSTIC",b)));
        GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"baseline40",40,null,false,b),clock)
                .forEach(s->all.add(new Planned(s,"B40",b)));
        // Predeclared same-model A subset, independent of any new result: first 12 original selected IDs.
        for(var s:baseline.subList(0,12)) all.add(new Planned(new GeminiLocalEvaluation.Sample("SUP-A-"+s.id(),"SUPPLEMENTAL",s.input(),s.expected(),s.backendExpected()),"SUPPLEMENTAL",a));
        for(var s:baseline) if(Set.of("VNE1-029","VNE1-030").contains(s.id()))
            all.add(new Planned(new GeminiLocalEvaluation.Sample("RETEST-"+s.id(),"SUPPLEMENTAL",s.input(),s.expected(),s.backendExpected()),"SUPPLEMENTAL",a));
        String[][] variants={
                {"VNE1-027","Giữ lại khách sạn 4 sao"},{"VNE1-027","Chỉ lọc khách sạn 4 sao"},{"VNE1-027","Chỉ giữ khách sạn 4 sao nhé"},
                {"VNE1-028","Chuyển điểm đến sang Huế"},{"VNE1-028","Tìm ở Huế thay vì điểm đến trước"},{"VNE1-028","Tìm ở Huế thay vì Đà Lạt"},
                {"VNE1-005","Tìm khách sạn Đà Lạt ngày 01/12/2026"},{"VNE1-005","Tìm khách sạn tại Đà Lạt nhận phòng 01/12/2026"},
                {"VNE1-008","Tìm khách sạn cho 5 người"},{"VNE1-011","Tìm khách sạn có hồ bơi ở Huế"}};
        for(int i=0;i<variants.length;i++) {
            String source=variants[i][0], text=variants[i][1];
            var s=baseline.stream().filter(v->v.id().equals(source)).findFirst().orElseThrow();
            com.example.aiagent.nlu.VietnameseSecurityPreflight.validate(text);
            String body=syntheticBody(GeminiLocalExtractionContract.request(text,b));
            var input=new GeminiEvaluationDataset.Input(source,text,s.input().contextDigest(),s.input().category(),s.input().split(),true,body,
                    new Binding(hash(text),hash(GeminiLocalExtractionContract.instruction(b)),hash(GeminiLocalExtractionContract.schema(b)),hash(body)));
            all.add(new Planned(new GeminiLocalEvaluation.Sample("GEN-%02d".formatted(i+1),"SUPPLEMENTAL",input,s.expected().deepCopy(),true),"SUPPLEMENTAL",b));
        }
        return List.copyOf(all);
    }
    static ObjectNode run(Path directory, boolean live, boolean fallbackEnabled, String phase, Clock clock, Supplier<String> key,
                          Exchange exchange, LongSupplier time, GeminiLocalEvaluation.Pause pause, GeminiLocalModelFallback availability) throws Exception {
        return run(directory,live,fallbackEnabled,phase,clock,key,exchange,time,pause,availability,"campaign",false);
    }
    static ObjectNode run(Path directory, boolean live, boolean fallbackEnabled, String phase, Clock clock, Supplier<String> key,
                          Exchange exchange, LongSupplier time, GeminiLocalEvaluation.Pause pause, GeminiLocalModelFallback availability, String segment, boolean recheckPrimary) throws Exception {
        return run(directory,live,fallbackEnabled,phase,clock,key,exchange,time,pause,availability,segment,recheckPrimary,VietnameseSemanticCompiler.VERSION);
    }
    static ObjectNode run(Path directory, boolean live, boolean fallbackEnabled, String phase, Clock clock, Supplier<String> key,
                          Exchange exchange, LongSupplier time, GeminiLocalEvaluation.Pause pause, GeminiLocalModelFallback availability, String segment, boolean recheckPrimary, String verificationProtocol) throws Exception {
        return run(directory,live,fallbackEnabled,phase,clock,key,exchange,time,pause,availability,segment,recheckPrimary,verificationProtocol,false);
    }
    static ObjectNode run(Path directory, boolean live, boolean fallbackEnabled, String phase, Clock clock, Supplier<String> key,
                          Exchange exchange, LongSupplier time, GeminiLocalEvaluation.Pause pause, GeminiLocalModelFallback availability, String segment, boolean recheckPrimary, String verificationProtocol, boolean quality) throws Exception {
        return run(directory,live,fallbackEnabled,phase,clock,key,exchange,time,pause,availability,segment,recheckPrimary,verificationProtocol,quality,false);
    }
    static ObjectNode run(Path directory, boolean live, boolean fallbackEnabled, String phase, Clock clock, Supplier<String> key,
                          Exchange exchange, LongSupplier time, GeminiLocalEvaluation.Pause pause, GeminiLocalModelFallback availability, String segment, boolean recheckPrimary, String verificationProtocol, boolean quality, boolean evidence) throws Exception {
        var json=GeminiLlmProviderAdapter.JSON;
        String campaignVersion=evidence?"local-evidence-extraction-v1":quality?"local-semantic-quality-v2":VERSION;
        var phaseCaps=evidence?Map.of("TECHNICAL",4,"DIAGNOSTIC",12,"COMPARISON",32,"FROZEN40",40,"FRESH",20,"FALLBACK_RESERVE",12)
                :quality?Map.of("DIAGNOSTIC",20,"FROZEN40",40,"FRESH",20,"RETEST",20,"FALLBACK_RESERVE",20):CAPS;
        // Retained prior campaign attempts on 24/11; not a claim of account-wide quota knowledge.
        var priorDaily=evidence?Map.of(GeminiLocalModelProfile.FLASH_35_LITE,106,GeminiLocalModelProfile.FLASH_31_LITE,1,
                GeminiLocalModelProfile.FLASH_25_LITE,5,GeminiLocalModelProfile.FLASH_25,1):quality?Map.of(GeminiLocalModelProfile.FLASH_35_LITE,91,GeminiLocalModelProfile.FLASH_31_LITE,1,
                GeminiLocalModelProfile.FLASH_25_LITE,5,GeminiLocalModelProfile.FLASH_25,1):Map.<GeminiLocalModelProfile,Integer>of();
        var plans=evidence ? GeminiEvidenceEvaluation.plan().stream().filter(p->phase.equals("ALL")||p.phase().equals(phase)).toList()
                :quality ? GeminiQualityEvaluation.plan(clock).stream().filter(p->phase.equals("ALL")||p.phase().equals(phase)).toList()
                : phase.equals("MODEL_VERIFICATION") ? List.of(new Planned(GeminiLocalEvaluation.plan(new GeminiLocalEvaluation.Options(false,"initial-smoke",3,null,false,verificationProtocol),clock).getFirst(),"MODEL_VERIFICATION",verificationProtocol))
                : plan(clock).stream().filter(p->phase.equals("ALL")||p.phase().equals(phase)).toList();
        if(plans.isEmpty()) throw denied();
        var report=json.createObjectNode().put("version",campaignVersion).put("startedAt",clock.instant().toString()).put("evaluationClock",clock.instant().toString())
                .put("fallbackEnabled",fallbackEnabled).put("actualHttpAttempts",0).put("domainCalls",0).put("supabaseCalls",0).put("sameModelAutomaticRetries",0)
                .put("segment",segment).put("explicitPrimaryRecheck",recheckPrimary)
                .put("datasetHash",GeminiEvaluationDataset.HASH).put("oracleHash",GeminiLocalEvaluation.ORACLE_HASH)
                .put("compilerVersion",VietnameseSemanticCompiler.VERSION).put("corroboratorVersion","independent-semantic-v3-removal")
                .put("contextUnderstanding","NOT_RUN").put("trustedTargetResolution","NOT_RUN").put("fullAgentE2E","NOT_RUN")
                .put("stopReason","OFFLINE_PREFLIGHT");
        if(quality || evidence) {
            report.put("compilerVersion",evidence?VietnameseEvidenceCompiler.VERSION:VietnameseSemanticCompiler.V2).put("corroboratorVersion","independent-semantic-quality-v2")
                    .put("freshDatasetHash",hash(GeminiLocalEvaluation.resource("semantic-quality-fresh-v2.json")))
                    .set("knownPriorDailyAttempts",json.valueToTree(priorDaily));
            if(evidence) report.put("candidateRevision",VietnameseEvidenceCompiler.REVISION);
        }
        var profiles=report.putArray("modelProfiles");
        for(var m:GeminiLocalModelProfile.values()) profiles.addObject().put("model",m.id).put("eligible",m.eligible())
                .put("selectedForSegment",availability.eligible.test(m)).put("wireVersion",m.wireVersion())
                .put("rpm",m.rpm).put("tpm",m.tpm).put("rpd",m.rpd).put("reason",m.eligibilityReason);
        var contracts=report.putObject("contracts");
        for(String protocol:evidence?List.of(VietnameseEvidenceCompiler.VERSION):quality?List.of(VietnameseSemanticCompiler.V2):List.of(GeminiLocalExtractionContract.V3,VietnameseSemanticCompiler.VERSION)) {
            var c=contracts.putObject(protocol).put("instruction",GeminiLocalExtractionContract.instruction(protocol)).put("schema",GeminiLocalExtractionContract.schema(protocol));
            c.put("instructionDigest",hash(c.path("instruction").asText())).put("schemaDigest",hash(c.path("schema").asText()));
        }
        var observations=report.putArray("observations");
        for(var p:plans) {
            var s=p.sample(); var row=observations.addObject().put("sampleId",s.id()).put("sourceId",s.input().fixtureId()).put("phase",p.phase())
                    .put("protocolVersion",p.protocol()).put("utterance",s.input().utterance()).put("status","NOT_RUN").put("logicalOutcome","NOT_RUN");
            row.set("expectedExtraction",s.expected()); row.set("expected",GeminiEvaluationDataset.semantic(s.expected())); row.putArray("attempts");
            if(quality || evidence) row.put("evaluationClock",GeminiQualityEvaluation.EVALUATION_CLOCK.instant().toString());
            if(evidence) {
                row.put("fixedModel",p.fixedModel()==null?"PRIMARY_WITH_CAPACITY_ONLY_FALLBACK":p.fixedModel().id);
                row.set("expectedEvidenceWitnesses",json.valueToTree(VietnameseEvidenceCompiler.witnesses(s.input().utterance())));
                row.put("expectedRequestMeaning",VietnameseEvidenceCompiler.requestMeaning(s.input().utterance(),GeminiQualityEvaluation.EVALUATION_CLOCK));
            }
            var wires=row.putObject("modelWireDigests");
            for(var model:GeminiLocalModelProfile.values()) wires.put(model.id,hash(syntheticBody(GeminiLocalExtractionContract.request(s.input().utterance(),p.protocol(),model),model)));
        }
        Files.createDirectories(directory);
        if(!live) { Files.writeString(directory.resolve(segment+"-preflight.json"),report.toPrettyString(),StandardOpenOption.CREATE_NEW); return report; }
        var limiter=new GeminiLocalEvaluation.Limiter(time,pause);
        var modelTimes=new EnumMap<GeminiLocalModelProfile,ArrayDeque<Long>>(GeminiLocalModelProfile.class);
        var modelCounts=new EnumMap<GeminiLocalModelProfile,Integer>(GeminiLocalModelProfile.class);
        var counts=new HashMap<String,Integer>(); var failurePolicy=new GeminiLocalFailurePolicy();
        long start=time.getAsLong();
        try(var lockFile=FileChannel.open(directory.resolve("final-campaign.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            var lock=lockFile.tryLock(); var file=FileChannel.open(directory.resolve(segment+".json"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            if(lock==null) throw denied();
            int previousAttempts=0;
            var predecessors=report.putArray("predecessorSegments");
            try(var paths=Files.list(directory)) {
                for(var prior:paths.filter(path->path.toString().endsWith(".json")&&!path.equals(directory.resolve(segment+".json"))).sorted().toList()) {
                    var old=json.readTree(Files.readString(prior));
                    if(!old.path("version").asText().equals(campaignVersion)||old.path("stopReason").asText().equals("OFFLINE_PREFLIGHT")) continue;
                    // Never reopen a segment or continue unknown, quota, credential or safety stops.
                    if(!(evidence?Set.of("COMPLETED"):Set.of("COMPLETED","HTTP_400_STOP")).contains(old.path("stopReason").asText())) throw denied();
                    if(evidence) for(var oldRow:old.path("observations")) if(!oldRow.path("attempts").isEmpty()
                            && plans.stream().anyMatch(p->p.sample().id().equals(oldRow.path("sampleId").asText()))) throw denied();
                    predecessors.addObject().put("segment",old.path("segment").asText()).put("sha256",hash(Files.readAllBytes(prior))).put("terminalReason",old.path("stopReason").asText());
                    long age=Duration.between(Instant.parse(old.path("startedAt").asText()),clock.instant()).toNanos();
                    if(age<0||age>=Duration.ofMinutes(60).toNanos()) throw denied();
                    limiter.deadline=Math.min(limiter.deadline,time.getAsLong()+Duration.ofMinutes(60).toNanos()-age);
                    if(evidence) {
                        failurePolicy.cumulative=Math.max(failurePolicy.cumulative,old.path("cumulativeTransientFailures").asInt());
                        failurePolicy.consecutive=old.path("consecutiveTransientFailures").asInt();
                    }
                    for(var oldRow:old.path("observations")) for(var oldAttempt:oldRow.path("attempts")) {
                        if(!oldAttempt.path("status").asText().equals("ATTEMPTED")) throw denied();
                        var oldModel=Arrays.stream(GeminiLocalModelProfile.values()).filter(m->m.id.equals(oldAttempt.path("actualModel").asText())).findFirst().orElseThrow();
                        previousAttempts++; modelCounts.merge(oldModel,1,Integer::sum); counts.merge(oldAttempt.path("budgetPhase").asText(),1,Integer::sum);
                        long dispatch=time.getAsLong()-Duration.between(Instant.parse(oldAttempt.path("dispatchAt").asText()),clock.instant()).toNanos();
                        limiter.last=Math.max(limiter.last,dispatch);
                        if(time.getAsLong()-dispatch<Duration.ofMinutes(1).toNanos()) {
                            limiter.reservations.add(dispatch); modelTimes.computeIfAbsent(oldModel,m->new ArrayDeque<>()).add(dispatch);
                        }
                        if((!evidence||!oldRow.path("phase").asText().equals("COMPARISON"))
                                &&GeminiLocalModelFallback.capacity(oldAttempt.path("httpStatus").asInt(),oldAttempt.path("callTerminated").asBoolean(),oldAttempt.path("providerErrorBody").asText(null))) {
                            availability.unavailable.add(oldModel); availability.capacityFailures++;
                        }
                        else if(old.path("explicitPrimaryRecheck").asBoolean() && oldAttempt.path("httpStatus").asInt()==200
                                && oldAttempt.path("callTerminated").asBoolean() && oldAttempt.path("actualUsageStatus").asText().equals("KNOWN"))
                            availability.unavailable.remove(oldModel);
                    }
                }
            }
            var sorted=limiter.reservations.stream().sorted().toList(); limiter.reservations.clear(); limiter.reservations.addAll(sorted);
            modelTimes.values().forEach(queue->{var values=queue.stream().sorted().toList();queue.clear();queue.addAll(values);});
            limiter.reserved=previousAttempts; final int previous=previousAttempts;
            report.put("priorBatchAttempts",previous);
            if(recheckPrimary) availability.unavailable.remove(GeminiLocalModelProfile.FLASH_35_LITE);
            // One immutable campaign record per directory: failed, sealed or uncertain records cannot be reopened.
            report.put("stopReason","RUNNING"); save(file,report);
            try { outer: for(int i=0;i<plans.size();i++) {
                var p=plans.get(i); var s=p.sample(); var logical=(ObjectNode)observations.get(i); long logicalStart=time.getAsLong();
                var sampleClock=quality||evidence?GeminiQualityEvaluation.EVALUATION_CLOCK:clock;
                var current=p.fixedModel()==null?availability.first():p.fixedModel();
                if(current==null) { report.put("stopReason","ALL_ELIGIBLE_MODELS_UNAVAILABLE"); break; }
                String predecessor="";
                for(int chain=0;chain<4;chain++) {
                    final var model=current;
                    var deque=modelTimes.computeIfAbsent(model,m->new ArrayDeque<>());
                    awaitModel(model,deque,modelCounts.getOrDefault(model,0)+priorDaily.getOrDefault(model,0),limiter,time,pause);
                    limiter.awaitSlot(); // All rolling waits precede the 10-second call deadline.
                    var attempt=logical.withArray("attempts").addObject().put("attemptId","ATT-%03d".formatted(limiter.reserved+1))
                            .put("requestedPrimaryModel",p.fixedModel()!=null||p.phase().equals("MODEL_VERIFICATION")?model.id:GeminiLocalModelProfile.FLASH_35_LITE.id)
                            .put("actualModel",model.id).put("fallbackIndex",model.ordinal()).put("chainAttemptIndex",chain)
                            .put("previousAttemptId",predecessor).put("trigger",chain==0?(recheckPrimary?"EXPLICIT_PRIMARY_VERIFICATION":"FIXED_AVAILABILITY_SEGMENT"):"PROVIDER_CAPACITY_FALLBACK")
                            .put("wireVersion",model.wireVersion())
                            .put("status","RESERVED").put("httpStatus",0).put("actualUsageStatus","UNKNOWN");
                    String body=syntheticBody(GeminiLocalExtractionContract.request(s.input().utterance(),p.protocol(),model),model);
                    var input=new GeminiEvaluationDataset.Input(s.input().fixtureId(),s.input().utterance(),s.input().contextDigest(),s.input().category(),s.input().split(),true,body,
                            new Binding(hash(s.input().utterance()),hash(GeminiLocalExtractionContract.instruction(p.protocol())),hash(GeminiLocalExtractionContract.schema(p.protocol())),hash(body)));
                    final Reply[] reply={null}; final boolean[] dispatched={false}; final String[] credential={null};
                    var result=GeminiExtractionSmoke.evaluate(input,INPUT_CAP,new LlmProviderAdapter.CallControl(Duration.ofSeconds(10)),limiter::check,limiter::remaining,
                            ()->{credential[0]=key.get();return credential[0];},()->(request,control,timeout)-> {
                                String budgetPhase=chainPhase(attempt,p.phase());
                                if(counts.getOrDefault(budgetPhase,0)>=phaseCaps.get(budgetPhase)) throw denied();
                                try { limiter.reserve(120); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw denied(); }
                                if(!request.url().equals(model.endpoint())) throw denied();
                                dispatched[0]=true; counts.merge(budgetPhase,1,Integer::sum); modelCounts.merge(model,1,Integer::sum); deque.add(time.getAsLong());
                                attempt.put("budgetPhase",budgetPhase).put("status","IN_FLIGHT").put("dispatchAt",clock.instant().toString())
                                        .put("dispatchOffsetMillis",(time.getAsLong()-start)/1000000).put("reservedInputTokens",INPUT_CAP).put("reservedOutputTokens",OUTPUT_CAP)
                                        .put("requestDigest",hash(body));
                                report.put("actualHttpAttempts",limiter.reserved-previous); saveUnchecked(file,report);
                                reply[0]=exchange.execute(request,control,timeout);
                                attempt.put("httpStatus",reply[0].status()).put("callTerminated",reply[0].terminated());
                                if(reply[0].errorBody()!=null) attempt.put("providerErrorBody",GeminiLocalEvaluation.sanitizeResponse(reply[0].errorBody(),credential[0]));
                                if(reply[0].response()==null) throw new ProviderException(reply[0].failure()!=null ? reply[0].failure()
                                        : reply[0].status()==429?ProviderException.Category.QUOTA_EXCEEDED:ProviderException.Category.UNAVAILABLE);
                                try {
                                    Path raw=directory.resolve("responses").resolve(attempt.path("attemptId").asText()+".json"); Files.createDirectories(raw.getParent());
                                    Files.writeString(raw,GeminiLocalEvaluation.sanitizeResponse(reply[0].response().body(),credential[0]),StandardOpenOption.CREATE_NEW);
                                    attempt.put("responseArtifact",raw.toString()); attempt.put("providerModelVersion",json.readTree(reply[0].response().body()).path("modelVersion").asText("NOT_REPORTED"));
                                } catch(Exception failed) { throw denied(); }
                                return reply[0].response();
                            },sampleClock,GeminiLocalExtractionContract.request(s.input().utterance(),p.protocol(),model),raw->{
                                attempt.put("actualExtractionText",GeminiLocalEvaluation.sanitizeResponse(raw,credential[0]));
                                if(evidence) {
                                    attempt.put("evidenceSchemaValid",false).put("compilerResult","NOT_RUN");
                                    var proposed=VietnameseEvidenceCompiler.parse(raw); attempt.put("evidenceSchemaValid",true).set("evidenceProposal",proposed);
                                    var verified=VietnameseEvidenceCompiler.verify(proposed,s.input().utterance(),sampleClock);
                                    attempt.set("evidenceMetrics",json.valueToTree(verified)); attempt.put("rawEvidenceExact",verified.exact()).put("compilerResult","FAIL");
                                    var compiled=VietnameseEvidenceCompiler.compile(proposed,s.input().utterance(),sampleClock);
                                    attempt.put("compilerResult","PASS").set("compiledExtraction",compiled); return compiled.toString();
                                }
                                if(!VietnameseSemanticCompiler.semanticContract(p.protocol())) return raw;
                                attempt.put("semanticSchemaValid",false).put("compilerResult","NOT_RUN");
                                var semantic=VietnameseSemanticCompiler.parse(raw); attempt.put("semanticSchemaValid",true).set("semanticProposal",semantic);
                                attempt.put("compilerResult","FAIL"); var compiled=VietnameseSemanticCompiler.compile(semantic,sampleClock,p.protocol());
                                attempt.put("compilerResult","PASS").set("compiledExtraction",compiled); return compiled.toString();
                            },model,quality||evidence);
                    boolean correct=result.schemaValid()&&GeminiEvaluationDataset.semantic(s.expected()).equals(result.semantic());
                    attempt.put("status",dispatched[0]?"ATTEMPTED":"NOT_DISPATCHED").put("apiSuccess",result.apiSuccess()).put("schemaValid",result.schemaValid())
                            .put("semanticPass",correct).put("backendAccepted",result.backendAccepted()).put("category",result.category()).put("latencyMillis",result.latencyMillis())
                            .put("unsafe",result.unsafe()).put("unauthorizedTool",result.unauthorizedTool()).put("confirmation",result.confirmation());
                    attempt.set("actual",result.semantic()); attempt.set("pipeline",result.pipeline());
                    attempt.set("inputTokens",json.valueToTree(result.input())); attempt.set("outputTokens",json.valueToTree(result.output())); attempt.set("totalTokens",json.valueToTree(result.total()));
                    attempt.put("actualUsageStatus",result.total()==null?"UNKNOWN":"KNOWN");
                    logical.put("status",dispatched[0]?"ATTEMPTED":"NOT_RUN").put("actualModel",model.id).put("terminalAttemptId",attempt.path("attemptId").asText());
                    boolean unsafe=result.unsafe()||result.unauthorizedTool()||result.confirmation();
                    boolean capacity=reply[0]!=null&&GeminiLocalModelFallback.capacity(reply[0].status(),reply[0].terminated(),reply[0].errorBody())&&!unsafe;
                    if(capacity&&fallbackEnabled&&p.fixedModel()==null) {
                        if(evidence && ++failurePolicy.cumulative>=5) {
                            report.put("stopReason","TRANSIENT_FAILURE_THRESHOLD");
                            attempt.put("continuationReason","TRANSIENT_FAILURE_THRESHOLD"); save(file,report); break outer;
                        }
                        var transition=availability.after(model,reply[0].status(),reply[0].terminated(),reply[0].errorBody(),reply[0].retryAfter(),clock.instant());
                        attempt.put("continuationReason",transition.reason()).put("cooldownSeconds",transition.cooldownSeconds()); save(file,report);
                        if(transition.next()==null) { report.put("stopReason",transition.reason()); logical.put("logicalOutcome",transition.reason())
                                .put("logicalLatencyMillis",(time.getAsLong()-logicalStart)/1000000); break outer; }
                        cooldown(transition.cooldownSeconds(),limiter,time,pause); predecessor=attempt.path("attemptId").asText(); current=transition.next(); continue;
                    }
                    var decision=failurePolicy.decide(reply[0]==null?0:reply[0].status(),result.category(),result.total()!=null,unsafe,
                            reply[0]!=null&&reply[0].terminated(),reply[0]==null?null:reply[0].retryAfter(),clock.instant());
                    attempt.put("continuationReason",decision.reason()).put("cooldownSeconds",decision.cooldownSeconds());
                    logical.put("logicalOutcome",result.category()).put("semanticPass",correct).put("backendAccepted",result.backendAccepted()).put("schemaValid",result.schemaValid())
                            .put("apiSuccess",result.apiSuccess()).put("logicalLatencyMillis",(time.getAsLong()-logicalStart)/1000000);
                    logical.set("actual",result.semantic()); save(file,report);
                    if(!decision.proceed()) { report.put("stopReason",decision.reason()); break outer; }
                    if(decision.cooldownSeconds()>0) cooldown(decision.cooldownSeconds(),limiter,time,pause);
                    break;
                }
            } } catch(ProviderException stopped) { report.put("stopReason",stopped.category().name()); }
            catch(InterruptedException stopped) { Thread.currentThread().interrupt(); report.put("stopReason","CANCELLED"); }
            if(report.path("stopReason").asText().equals("RUNNING")) report.put("stopReason","COMPLETED");
            report.put("capacityFailures",availability.capacityFailures).put("rateLimitWaitMillis",limiter.waited/1000000).put("durationMillis",(time.getAsLong()-start)/1000000);
            if(evidence) report.put("cumulativeTransientFailures",failurePolicy.cumulative).put("consecutiveTransientFailures",failurePolicy.consecutive);
            report.set("phaseAttempts",json.valueToTree(counts)); report.set("modelAttempts",json.valueToTree(modelCounts)); save(file,report);
        }
        return report;
    }
    static String chainPhase(ObjectNode attempt,String phase) {
        return phase.equals("MODEL_VERIFICATION")||!attempt.path("previousAttemptId").asText().isEmpty()?"FALLBACK_RESERVE"
                :phase.equals("DIAGNOSTIC_R2")?"DIAGNOSTIC":phase;
    }
    static void awaitModel(GeminiLocalModelProfile model,ArrayDeque<Long> starts,int count,GeminiLocalEvaluation.Limiter limiter,LongSupplier time,GeminiLocalEvaluation.Pause pause) throws InterruptedException {
        if(count>=model.rpd) throw denied();
        for(;;) {
            limiter.check(); long now=time.getAsLong(), minute=Duration.ofMinutes(1).toNanos();
            while(!starts.isEmpty()&&now-starts.getFirst()>=minute) starts.removeFirst();
            if(starts.size()<model.rpm&&(starts.size()+1L)*(INPUT_CAP+OUTPUT_CAP)<=model.tpm) return;
            pause.sleep(Math.min(Duration.ofMillis(100).toNanos(),minute-(now-starts.getFirst())));
        }
    }
    static void cooldown(long seconds,GeminiLocalEvaluation.Limiter limiter,LongSupplier time,GeminiLocalEvaluation.Pause pause) throws InterruptedException {
        if(seconds>=limiter.remaining().toSeconds()) throw denied(); long until=time.getAsLong()+Duration.ofSeconds(seconds).toNanos();
        while(time.getAsLong()<until) { limiter.check(); pause.sleep(Math.min(Duration.ofMillis(100).toNanos(),until-time.getAsLong())); }
    }
    static void save(FileChannel file,ObjectNode report) throws Exception {
        byte[] bytes=GeminiLlmProviderAdapter.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report); file.position(0);
        var buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) file.write(buffer); file.truncate(bytes.length); file.force(true);
    }
    static void saveUnchecked(FileChannel file,ObjectNode report) { try { save(file,report); } catch(Exception failure) { throw denied(); } }
}

