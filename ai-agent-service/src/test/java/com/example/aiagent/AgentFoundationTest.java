package com.example.aiagent;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.model.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.http.client.*;
import dev.langchain4j.http.client.sse.*;
import java.util.Map;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.Permission;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AiAgentApplication.class)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@SuppressWarnings("removal")
class AgentFoundationTest {
    @Autowired AgentProperties properties;
    @Autowired LlmProviderAdapter provider;
    @Autowired MockMvc mvc;
    @Autowired MeterRegistry meters;
    @Autowired org.springframework.context.ApplicationContext context;
    private static SecurityManager previous;
    private static final AtomicInteger connections = new AtomicInteger();
    private static final ChatRequest REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("synthetic-input-do-not-log")).build();

    // Java 21 test-only network guard. All test model construction/generation happens inside it.
    @BeforeAll static void denyNetwork() {
        previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) {
                connections.incrementAndGet();
                throw new SecurityException("Outbound network forbidden in Agent foundation tests");
            }
        });
    }
    @AfterAll static void restoreNetwork() {
        System.setSecurityManager(previous);
        assertThat(connections).hasValue(0);
    }
    private static LlmProviderAdapter.CallControl control() {
        return new LlmProviderAdapter.CallControl(Duration.ofSeconds(1));
    }
    private static void error(Runnable operation, ProviderException.Category category) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProviderException.class,
                ex -> assertThat(ex.category()).isEqualTo(category));
    }

    @Test void defaultContextIsOfflineDisabledAndHasNoDomainInfrastructure() throws Exception {
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.provider()).isEqualTo(AgentProperties.Provider.FAKE);
        error(() -> provider.generate(REQUEST, control()), ProviderException.Category.DISABLED);
        assertThat(meters.find("agent.model.calls").counter()).isNull();
        assertThat(context.getBeansOfType(ChatModel.class)).isEmpty();
        assertThat(context.getBeanDefinitionNames()).noneMatch(name -> name.contains("gemini-smoke"));
        for (String forbidden : new String[]{"javax.sql.DataSource", "jakarta.persistence.EntityManager",
                "org.flywaydb.core.Flyway", "org.springframework.amqp.rabbit.core.RabbitTemplate",
                "io.grpc.ManagedChannel", "dev.langchain4j.service.AiServices"}) {
            if (forbidden.equals("javax.sql.DataSource")) {
                assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();
            } else {
                assertThatThrownBy(() -> Class.forName(forbidden)).isInstanceOf(ClassNotFoundException.class);
            }
        }
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Request-ID"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(post("/api/ai/read/chat").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }


    @Test void localHealthContinuesW3cTraceWithoutTrustingRequestId() throws Exception {
        mvc.perform(get("/actuator/health")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .header("X-Request-ID", "caller-controlled"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", "0123456789abcdef0123456789abcdef"));
    }

    @Test void deterministicTextPreservesUsageAndFinishReason(CapturedOutput output) {
        var fake = new FakeLlmProviderAdapter();
        var first = fake.generate(REQUEST, control());
        assertThat(first).isEqualTo(fake.generate(REQUEST, control()));
        assertThat(first.aiMessage().text()).contains("Fake provider");
        assertThat(first.aiMessage().hasToolExecutionRequests()).isFalse();
        assertThat(first.tokenUsage().totalTokenCount()).isEqualTo(20);
        assertThat(first.finishReason().name()).isEqualTo("STOP");
        assertThat(output).doesNotContain("synthetic-input-do-not-log", first.aiMessage().text());
    }

    @Test void toolDeclarationAndStructuredOutputContractRemainAvailableWithoutExecution() {
        var tool = ToolSpecification.builder().name("search_hotels")
                .parameters(JsonObjectSchema.builder().addStringProperty("cityName").build()).build();
        var schema = JsonSchema.builder().name("answer")
                .rootElement(JsonObjectSchema.builder().addStringProperty("text").build()).build();
        var request = ChatRequest.builder().messages(REQUEST.messages()).toolSpecifications(tool)
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).jsonSchema(schema).build()).build();
        var response = new FakeLlmProviderAdapter(FakeLlmProviderAdapter.Scenario.TOOL_PROPOSAL)
                .generate(request, control());
        assertThat(request.toolSpecifications()).containsExactly(tool);
        assertThat(request.responseFormat().jsonSchema()).isEqualTo(schema);
        assertThat(response.aiMessage().toolExecutionRequests()).singleElement().satisfies(proposal -> {
            assertThat(proposal.id()).isEqualTo("fake-proposal-1");
            assertThat(proposal.name()).isEqualTo("search_hotels");
            assertThat(proposal.arguments()).contains("cityName");
        });
        assertThat(response.finishReason().name()).isEqualTo("TOOL_EXECUTION");
    }


    @Test void scriptedResponseCanPreserveUnknownUsageWithoutFabricatingZero() {
        var response = dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from("scripted-answer")).build();
        var actual = new FakeLlmProviderAdapter(response).generate(REQUEST, control());
        assertThat(actual).isSameAs(response);
        assertThat(actual.tokenUsage()).isNull();
        assertThat(actual.finishReason()).isNull();
        assertThatThrownBy(() -> dev.langchain4j.model.chat.response.ChatResponse.builder().build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("aiMessage cannot be null");
    }

    @Test void scriptedFaultsAreTypedAndSanitized() {
        error(() -> new FakeLlmProviderAdapter(FakeLlmProviderAdapter.Scenario.TIMEOUT)
                .generate(REQUEST, control()), ProviderException.Category.TIMEOUT);
        error(() -> new FakeLlmProviderAdapter(FakeLlmProviderAdapter.Scenario.FAILURE)
                .generate(REQUEST, control()), ProviderException.Category.UNAVAILABLE);
        error(() -> new FakeLlmProviderAdapter(FakeLlmProviderAdapter.Scenario.MALFORMED)
                .generate(REQUEST, control()), ProviderException.Category.MALFORMED_RESPONSE);
    }

    @Test void cancellationAndExpiredDeadlineStopBeforeGeneration() {
        var cancelled = control(); cancelled.cancel();
        error(() -> new FakeLlmProviderAdapter().generate(REQUEST, cancelled), ProviderException.Category.CANCELLED);
        var expired = new LlmProviderAdapter.CallControl(Duration.ofNanos(1));
        error(() -> new FakeLlmProviderAdapter().generate(REQUEST, expired), ProviderException.Category.TIMEOUT);
    }

    @Test void selectedProviderBuildersLoadWithBootManagedDependenciesWithoutCallingModels(CapturedOutput output) {
        // Synthetic non-credential; constructors only. No .chat(), no live adapter bean.
        String sentinel = "synthetic-secret-not-a-real-key";
        ChatModel gemini = GoogleAiGeminiChatModel.builder().apiKey(sentinel).modelName("unselected")
                .maxRetries(0).timeout(Duration.ofSeconds(1))
                .logRequests(false).logResponses(false).allowCodeExecution(false).allowGoogleSearch(false).build();
        ChatModel openai = OpenAiChatModel.builder().apiKey(sentinel).modelName("unselected")
                .maxRetries(0).timeout(Duration.ofSeconds(1)).logRequests(false).logResponses(false).build();
        assertThat(gemini).isInstanceOf(ChatModel.class);
        assertThat(openai).isInstanceOf(ChatModel.class);
        assertThat(output).doesNotContain(sentinel);
    }


    @Test void sdkSerializationAndUsageParsingWorkWithBootJacksonOffline(CapturedOutput output) {
        var geminiTransport = new FixtureTransport("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"fixture-answer"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":8,"totalTokenCount":20}}
                """);
        var gemini = GoogleAiGeminiChatModel.builder().apiKey("synthetic-key")
                .modelName("unselected").httpClientBuilder(geminiTransport).maxRetries(0)
                .logRequests(false).logResponses(false).build();
        var geminiResponse = gemini.chat(REQUEST);
        assertThat(geminiResponse.aiMessage().text()).isEqualTo("fixture-answer");
        assertThat(geminiResponse.tokenUsage().totalTokenCount()).isEqualTo(20);
        assertThat(geminiTransport.calls).hasValue(1);
        assertThat(geminiTransport.request.body()).contains("synthetic-input-do-not-log");

        var openaiTransport = new FixtureTransport("""
                {"id":"fixture-1","model":"unselected","choices":[{"index":0,
                 "message":{"role":"assistant","content":"fixture-answer"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                """);
        var openai = OpenAiChatModel.builder().apiKey("synthetic-key").modelName("unselected")
                .httpClientBuilder(openaiTransport).maxRetries(0).logRequests(false).logResponses(false).build();
        var openaiResponse = openai.chat(REQUEST);
        assertThat(openaiResponse.aiMessage().text()).isEqualTo("fixture-answer");
        assertThat(openaiResponse.tokenUsage().totalTokenCount()).isEqualTo(20);
        assertThat(openaiTransport.calls).hasValue(1);
        assertThat(output).doesNotContain("synthetic-key", "fixture-answer", "synthetic-input-do-not-log");
    }

    @Test void sdkRetriesAreExplicitlyDisabledForBothCandidates() {
        for (boolean google : new boolean[]{true, false}) {
            var transport = new FixtureTransport(null);
            ChatModel model = google
                    ? GoogleAiGeminiChatModel.builder().apiKey("synthetic-key").modelName("unselected")
                        .httpClientBuilder(transport).maxRetries(0).logRequests(false).logResponses(false).build()
                    : OpenAiChatModel.builder().apiKey("synthetic-key").modelName("unselected")
                        .httpClientBuilder(transport).maxRetries(0).logRequests(false).logResponses(false).build();
            assertThatThrownBy(() -> model.chat(REQUEST)).isInstanceOf(RuntimeException.class);
            assertThat(transport.calls).hasValue(1);
        }
    }

    /** In-process HTTP SPI fixture, no socket or API request. */
    static final class FixtureTransport implements HttpClientBuilder, HttpClient {
        final AtomicInteger calls = new AtomicInteger();
        final String body;
        HttpRequest request;
        Duration connect;
        Duration read;
        FixtureTransport(String body) { this.body = body; }
        public Duration connectTimeout() { return connect; }
        public HttpClientBuilder connectTimeout(Duration value) { connect = value; return this; }
        public Duration readTimeout() { return read; }
        public HttpClientBuilder readTimeout(Duration value) { read = value; return this; }
        public HttpClient build() { return this; }
        public SuccessfulHttpResponse execute(HttpRequest value) {
            calls.incrementAndGet();
            request = value;
            if (body == null) throw new dev.langchain4j.exception.HttpException(503, "synthetic-unavailable");
            return SuccessfulHttpResponse.builder().statusCode(200).headers(Map.of()).body(body).build();
        }
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new AssertionError("Streaming is outside 10B.1");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentProperties.class)
    static class PropertiesOnly { }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(PropertiesOnly.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tool-timeout=11s", "turn-deadline=31s", "model-timeout=31s",
            "tool-timeout=0s", "model-timeout=-1s", "turn-deadline=0s",
            "http-attempts=7", "tool-proposals=7", "model-requests=4", "tool-rounds=3",
            "http-attempts=0", "tool-proposals=0", "model-requests=0", "tool-rounds=0",
            "automatic-retries=1", "automatic-retries=-1", "log-payloads=true",
            "provider=GEMINI", "provider=OPENAI", "provider=unknown", "http-attempt=1"})
    void invalidConfigurationFailsClosed(String setting) {
        runner().withPropertyValues("checkinn.agent." + setting)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test void reducedLimitsAndEnabledFakeNeedNoKey(CapturedOutput output) {
        runner().withPropertyValues("checkinn.agent.enabled=true", "checkinn.agent.http-attempts=1",
                "checkinn.agent.tool-proposals=1", "checkinn.agent.model-requests=1",
                "checkinn.agent.tool-rounds=1", "checkinn.agent.tool-timeout=1s",
                "checkinn.agent.turn-deadline=2s", "checkinn.agent.model-timeout=1s")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    var config = ctx.getBean(AgentProperties.class);
                    assertThat(config.toString()).doesNotContain("GEMINI_API_KEY");
                    var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
                    // Exercise the exact production bean factory in enabled mode.
                    var configured = new ProviderConfiguration().llmProviderAdapter(config, registry);
                    assertThat(configured.generate(REQUEST, control()).tokenUsage().totalTokenCount()).isEqualTo(20);
                    assertThat(registry.counter("agent.model.calls", "provider", "FAKE").count()).isEqualTo(1);
                });
        assertThat(output).doesNotContain("synthetic-input-do-not-log");
    }
}
