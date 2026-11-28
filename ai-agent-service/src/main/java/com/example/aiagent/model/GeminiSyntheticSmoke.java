package com.example.aiagent.model;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.orchestration.*;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Explicit Java CLI, never a runner/controller/scheduled job in the application. Default is preflight ONLY. */
public final class GeminiSyntheticSmoke {
    @EnableConfigurationProperties({AgentProperties.class, GeminiProperties.class, GeminiSmokeApproval.class})
    static class ConfigurationOnly { }
    private static final AtomicBoolean EXECUTION_CLAIMED = new AtomicBoolean();
    private GeminiSyntheticSmoke() { }
    public static void main(String[] args) {
        boolean failed = false;
        boolean initialize = args.length == 1 && args[0].equals("--init-debug-session");
        boolean debug = args.length == 1 && args[0].equals("--execute-debug-synthetic");
        boolean execute = args.length == 1 && args[0].equals("--execute-fixed-synthetic");
        if (args.length > 0 && !execute && !initialize && !debug) throw new ProviderException(ProviderException.Category.CONFIGURATION);
        try (var context = new SpringApplicationBuilder(ConfigurationOnly.class).web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF).logStartupInfo(false).run()) {
            var agent = context.getBean(AgentProperties.class); var limits = context.getBean(GeminiProperties.class);
            System.out.println(preflight(agent, limits));
            var approval = context.getBean(GeminiSmokeApproval.class);
            System.out.println("First-call attestations: " + approval + "; liveTransport=NOT_VERIFIED");
            String key = System.getenv(agent.apiKeyEnvironmentVariable());
            boolean present = key != null && !key.isBlank();
            System.out.println("GEMINI_API_KEY_PRESENT=" + present);
            System.out.println("First-call local gates=" + firstCallPreflight(agent, limits, approval)
                    + "; accountEvidence=OPERATOR_ATTESTATIONS_ONLY; keyValidity=NOT_VERIFIED");
            if (initialize) {
                if (!present) throw new ProviderException(ProviderException.Category.CONFIGURATION);
                if (!java.nio.file.Files.isRegularFile(java.nio.file.Path.of("ai-agent-service", "pom.xml")))
                    throw new ProviderException(ProviderException.Category.CONFIGURATION);
                GeminiDebugSession.initialize(GeminiDebugSession.DIRECTORY);
                System.out.println("Debug session initialized: attempts=0; exposureUsd=0; cap=5; exposureCapUsd=0.06"); return;
            }
            if (!execute && !debug) return;
            if (!EXECUTION_CLAIMED.compareAndSet(false, true)) throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
            var diagnostics = new GeminiSmokeDiagnostics();
            GeminiSmokeDiagnostics.Result result;
            if (debug) {
                if (!present) throw new ProviderException(ProviderException.Category.CONFIGURATION);
                try (var session = new GeminiDebugSession(GeminiDebugSession.DIRECTORY)) {
                    var transport = new GeminiHttpTransport(diagnostics::httpStatus);
                    result = runFixed(agent, limits, approval, key, (request, control, timeout) -> {
                        control.check(); session.reserve(); control.check();
                        return transport.execute(request, control, timeout);
                    }, GeminiBudget.PROCESS, diagnostics);
                    if (result.attempts() == 1) session.complete(result);
                    System.out.println("Debug ledger: attempts=" + session.attempts() + "; exposureUsd=" + session.exposure());
                } catch (java.io.IOException ignored) { throw new ProviderException(ProviderException.Category.BUDGET_EXCEEDED); }
            } else result = runFixed(agent, limits, approval, key, new GeminiHttpTransport(diagnostics::httpStatus), GeminiBudget.PROCESS, diagnostics);
            System.out.println(result); failed = !result.category().equals("SUCCESS");
        }
        if (failed) System.exit(2);
    }
    static String firstCallPreflight(AgentProperties agent, GeminiProperties limits, GeminiSmokeApproval approval) {
        try { limits.validateFirstCall(agent); approval.validate(); return "PASS"; }
        catch (ProviderException denied) { return "FAIL"; }
    }
    /** Package-owned seam for offline tests. Only main selects production transport/ledger. */
    static GeminiSmokeDiagnostics.Result runFixed(AgentProperties agent, GeminiProperties limits, GeminiSmokeApproval approval,
            String key, GeminiLlmProviderAdapter.Exchange exchange, GeminiBudget budget, GeminiSmokeDiagnostics diagnostics) {
        long started = System.nanoTime();
        String category = "SUCCESS", grounding = "NOT_RUN";
        var meters = new SimpleMeterRegistry();
        try {
            var permit = authorize(agent, limits, approval);
            var provider = new GeminiLlmProviderAdapter(agent, limits, meters, key, (request, control, timeout) -> {
                diagnostics.attempts++;
                try {
                    var result = exchange.execute(request, control, timeout);
                    diagnostics.httpStatus(result.statusCode()); diagnostics.response(result.body()); return result;
                } catch (dev.langchain4j.exception.HttpException status) { diagnostics.httpStatus(status.statusCode()); throw status; }
            }, budget, permit, diagnostics);
            var result = provider.firstCall(new LlmProviderAdapter.CallControl(agent.modelTimeout()));
            diagnostics.stage(GeminiSmokeDiagnostics.Stage.GROUNDING);
            var answer = new GroundingGate().validate(result.aiMessage().text(),
                    new EvidenceLedger(List.of(), 0, "synthetic-smoke", Clock.systemUTC()), SearchCriteriaState.empty(),
                    new PolicyExecutor.Scope(PolicyExecutor.Intent.CANCELLATION, null, null));
            grounding = result.aiMessage().hasToolExecutionRequests() || answer.category().equals("GROUNDING_REJECTED") ? "FAIL" : "PASS";
            if (!grounding.equals("PASS")) { diagnostics.fail("GROUNDING_REJECTED"); throw new ProviderException(ProviderException.Category.MALFORMED_RESPONSE); }
            diagnostics.stage(GeminiSmokeDiagnostics.Stage.COMPLETE);
        } catch (ProviderException failure) { category = failure.category().name(); diagnostics.fail(category); }
        catch (RuntimeException ignored) { category = "UNAVAILABLE"; diagnostics.fail(category); }
        finally { meters.close(); }
        return diagnostics.result(category, grounding, started, budget);
    }
    static FirstCallPermit authorize(AgentProperties agent, GeminiProperties limits, GeminiSmokeApproval approval) {
        limits.validateFirstCall(agent); approval.validate();
        return new FirstCallPermit(agent, limits, approval);
    }
    /** Cannot be constructed/deserialized by ordinary factory/orchestrator or configuration binding. */
    static final class FirstCallPermit {
        private final AgentProperties agent;
        private final GeminiProperties limits;
        private final GeminiSmokeApproval approval;
        private final Thread thread = Thread.currentThread();
        private final ChatRequest request = SyntheticGeminiFixtures.request(SyntheticGeminiFixtures.SMOKE);
        private final AtomicBoolean consumed = new AtomicBoolean();
        private FirstCallPermit(AgentProperties agent, GeminiProperties limits, GeminiSmokeApproval approval) {
            this.agent = agent; this.limits = limits; this.approval = approval;
        }
        ChatRequest request() { return request; }
        void validate(AgentProperties agent, GeminiProperties limits) {
            if (Thread.currentThread() != thread || this.agent != agent || this.limits != limits)
                throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
            limits.validateFirstCall(agent); approval.validate();
        }
        void consume(ChatRequest request) {
            if (Thread.currentThread() != thread || this.request != request || !consumed.compareAndSet(false, true))
                throw new ProviderException(ProviderException.Category.SYNTHETIC_BOUNDARY);
        }
        @Override public String toString() { return "FirstCallPermit[internal-only]"; }
    }
    static String preflight(AgentProperties agent, GeminiProperties limits) {
        String model = safeModel(agent.modelId());
        return "Gemini preflight: model=" + model + "; fixture=" + SyntheticGeminiFixtures.SMOKE
                + "; reviewedModel=" + safeModel(limits.reviewedModelId())
                + "; fixedRequests=1; runCap=" + limits.maxRunRequests() + "; processCap=" + limits.maxProcessRequests()
                + "; concurrencyCap=" + limits.maxConcurrentRequests()
                + "; inputTokenCap=" + limits.maxInputTokens() + "; outputTokenCap=" + limits.maxOutputTokens()
                + "; localExposureUsd=" + limits.maxExposureUsd() + "; agentEnabled=" + agent.enabled()
                + "; provider=" + agent.provider() + "; liveEnabled=" + limits.liveEnabled()
                + "; syntheticOnly=" + limits.syntheticOnly() + "; modelVerification=" + limits.modelVerification()
                + "; accountRegionFreeTier=" + limits.accountRegionFreeTierVerification()
                + "; dataTerms=" + limits.dataTermsVerification() + "; quotaBilling=" + limits.quotaBillingVerification()
                + "; transportAcceptance=" + limits.transportAcceptance() + "; estimatedInput=true; externalCostGuarantee=false"
                + "\nLocal gates: feature=" + pass(agent.enabled()) + "; provider=" + pass(agent.provider() == AgentProperties.Provider.GEMINI)
                + "; live=" + pass(limits.liveEnabled()) + "; synthetic=" + pass(limits.syntheticOnly())
                + "; reviewedModelMatch=" + pass(agent.modelId() != null && agent.modelId().startsWith("gemini-")
                        && agent.modelId().equals(limits.reviewedModelId()))
                + "; agentLimits=" + pass(agent.isWithinApprovedLimits()) + "; budgets=" + pass(limits.validBudgets())
                + "; oneRequestExposure=" + pass(limits.validBudgets() && limits.reservation().compareTo(limits.maxExposureUsd()) <= 0)
                + "; keyValidity=NOT_VERIFIED; accountEvidence=NOT_VERIFIED; liveInference=NOT_RUN"
                + "; verificationValues=OPERATOR_ATTESTATIONS_ONLY";
    }
    private static String safeModel(String value) {
        return value == null || value.isBlank() ? "UNSET" : value.matches("[A-Za-z0-9.-]{1,100}") ? value : "INVALID";
    }
    private static String pass(boolean value) { return value ? "PASS" : "FAIL"; }
}
