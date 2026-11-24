package com.example.aiagent.tools;

import com.example.aiagent.model.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.*;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.tools.ToolError.Category.*;

/** Real sockets on loopback only, synthetic DTO fixtures. Not real Gateway/domain acceptance. */
class ReadToolsTest {
    static final String H = "11111111-1111-4111-8111-111111111111";
    static final String R = "33333333-3333-4333-8333-333333333333";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-22T03:00:00Z"), ZoneOffset.UTC);
    HttpServer server;
    ExecutorService serverExecutor;
    ToolRegistry registry;
    JsonNode fixtures;
    final AtomicInteger received = new AtomicInteger();
    final List<String> paths = new CopyOnWriteArrayList<>();
    final List<Headers> headers = new CopyOnWriteArrayList<>();
    final List<String> methods = new CopyOnWriteArrayList<>();
    volatile int responseStatus = 200;
    volatile String responseBody = "[]", contentType = "application/json", contentEncoding = null;
    volatile long headerDelay, bodyDelay;
    volatile boolean chunked;
    @BeforeEach void start() throws Exception {
        fixtures = ToolArguments.JSON.readTree(getClass().getResourceAsStream("/tools/synthetic-fixtures.json"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().factory());
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            received.incrementAndGet(); paths.add(exchange.getRequestURI().toString());
            headers.add(exchange.getRequestHeaders()); methods.add(exchange.getRequestMethod());
            try {
                Thread.sleep(headerDelay);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("X-Request-ID", "fixture-trace-1");
                if (contentEncoding != null) exchange.getResponseHeaders().set("Content-Encoding", contentEncoding);
                if (responseStatus == 302) exchange.getResponseHeaders().set("Location", origin() + "/admin/private");
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus, chunked ? 0 : bytes.length);
                if (bodyDelay > 0) {
                    exchange.getResponseBody().write(bytes, 0, 1); exchange.getResponseBody().flush(); Thread.sleep(bodyDelay);
                    exchange.getResponseBody().write(bytes, 1, bytes.length - 1);
                } else exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (IOException ignored) { /* Caller deadline closes socket. */ }
            finally { exchange.close(); }
        });
        server.start(); registry = create(Duration.ofSeconds(2), CLOCK, true);
    }
    @AfterEach void stop() throws Exception { registry.close(); server.stop(0); serverExecutor.shutdownNow(); }
    URI origin() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
    ToolRegistry create(Duration timeout, Clock clock, boolean enabled) {
        return new ToolRegistry(enabled, origin(), Duration.ofSeconds(1), timeout, clock);
    }
    ToolExecutionContext context() { return new ToolExecutionContext(7L, 1, 2, 0, Map.of(R, H), Set.of("HOTEL"), Set.of("WIFI")); }
    ObjectNode args(ToolId id) {
        return switch (id) {
            case A01 -> json("{\"cityName\":\"Synthetic\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"rooms\":1,\"adults\":2,\"children\":0}");
            case A02, A03 -> json("{\"hotelId\":\"" + H + "\"}");
            case A04 -> json("{\"roomTypeId\":\"" + R + "\"}");
            case A05 -> json("{\"roomTypeId\":\"" + R + "\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"quantity\":1}");
            case A06 -> json("{\"query\":\"Synthetic\"}");
            case A09 -> json("{\"hotelIds\":[\"" + H + "\"]}");
            default -> json("{}");
        };
    }
    static ObjectNode json(String value) { return (ObjectNode) new ToolArguments(CLOCK).parse(value); }
    void fixture(String name) { responseBody = fixtures.get(name).get("body").toString(); }
    String happy(ToolId id) {
        return switch (id) {
            case A01 -> fixtures.get("PAGE").get("body").toString();
            case A02 -> fixtures.get("DETAIL").get("body").toString();
            case A03 -> fixtures.get("ROOMS").get("body").toString();
            case A04 -> fixtures.get("ROOM").get("body").toString();
            case A05 -> fixtures.get("AVAILABLE").get("body").toString();
            case A06 -> "[{\"id\":\"" + H + "\",\"name\":\"Synthetic\",\"parentName\":null,\"parentId\":null,\"type\":\"PROVINCE\",\"latitude\":1}]";
            case A07 -> "[{\"code\":\"HOTEL\",\"label\":\"Hotel\"}]";
            case A08 -> "[{\"code\":\"WIFI\",\"label\":\"Wifi\",\"active\":true,\"revision\":0,\"description\":\"drop\"}]";
            case A09 -> "[{\"hotelId\":\"" + H + "\",\"totalReviews\":0,\"averageRating\":null,\"reviewerId\":\"SECRET\"}]";
            case A10 -> "{\"version\":\"STANDARD_24H_V1\",\"checkInTime\":\"14:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"freeCancellationHours\":24,\"refundAmount\":999}";
        };
    }
    @Test void registryContainsExactlyTenImmutableReadDefinitionsAndPassiveSpecifications() {
        assertThat(registry.definitions()).hasSize(10).extracting(ToolRegistry.Definition::id).containsExactly(ToolId.values());
        assertThat(registry.definitions()).extracting(ToolRegistry.Definition::name).doesNotHaveDuplicates();
        for (var d : registry.definitions()) {
            assertThat(d.method()).isEqualTo("GET"); assertThat(d.gatewayPathTemplate()).isEqualTo(d.id().path());
            assertThat(d.inputSchema().path("additionalProperties").booleanValue()).isFalse();
            assertThat(d.outputSchema().path("additionalProperties").booleanValue()).isFalse();
            assertThat(d.maximumResponseBytes()).isEqualTo(262144); assertThat(d.maximumOutputBytes()).isEqualTo(12288);
            assertThat(d.handler()).isNotNull();
        }
        var specs = registry.specifications(); assertThat(specs).hasSize(10);
        for (var spec : specs) {
            assertThat(spec.parameters().additionalProperties()).isFalse();
            assertThat(spec.metadata()).containsKey("draft202012InputSchema");
        }
        var request = ChatRequest.builder().messages(UserMessage.from("synthetic" )).toolSpecifications(specs).build();
        var proposal = new FakeLlmProviderAdapter(FakeLlmProviderAdapter.Scenario.TOOL_PROPOSAL)
                .generate(request, new LlmProviderAdapter.CallControl(Duration.ofSeconds(1)));
        assertThat(proposal.aiMessage().hasToolExecutionRequests()).isTrue(); assertThat(received).hasValue(0);
        var unknown = registry.execute("http_get", "{}", context());
        assertThat(unknown.error().category()).isEqualTo(UNSUPPORTED_TOOL); assertThat(received).hasValue(0);
    }
    @ParameterizedTest @EnumSource(ToolId.class)
    void everyToolHappyPathFixedRouteAnonymousProjectionAndEvidence(ToolId id) {
        responseBody = happy(id); var ctx = context(); var result = registry.execute(id, args(id), ctx);
        assertThat(result.error()).as(id.name()).isNull(); assertThat(result.status()).isEqualTo(ToolResult.Status.OK);
        assertThat(ctx.attempts()).isEqualTo(1); assertThat(received).hasValue(1); assertThat(methods).containsExactly("GET");
        String expected = id.path().replace("{hotelId}", H).replace("{roomTypeId}", R);
        assertThat(paths.getFirst().split("\\?")[0]).isEqualTo(expected);
        assertThat(headers.getFirst()).doesNotContainKeys("Authorization", "Cookie", "X-Role", "X-User-Id");
        assertThat(headers.getFirst().getFirst("X-Request-ID")).isEqualTo(result.evidence().requestId());
        assertThat(result.evidence().requestId()).isNotBlank(); assertThat(result.evidence().upstreamRequestId()).isEqualTo("fixture-trace-1");
        assertThat(result.evidence().retrievedAt()).isEqualTo(CLOCK.instant()); assertThat(result.criteriaRevision()).isEqualTo(7L);
        assertThat(result.evidence().sourceRoute()).isEqualTo(id.path());
        assertThat(ctx.attemptLedger().getFirst().requestId()).isEqualTo(result.evidence().requestId());
        String data = result.data().toString();
        assertThat(data).doesNotContain("ownerId", "contactEmail", "contactPhone", "reviewerId", "SECRET", "refundAmount", "totalRooms", "mediaAssets", "latitude", "finalPayable", "holdId");
        Set<String> projectedFields = switch (id) {
            case A01 -> Set.of("items", "page", "size", "totalElements", "totalPages", "sort", "occupancyBasis");
            case A02 -> Set.of("hotelId", "name", "description", "address", "starRating", "accommodationTypeLabel", "lowestBasePrice", "policies", "faqs", "amenities");
            case A03 -> Set.of("hotelId", "rooms", "hasMore");
            case A04 -> Set.of("roomTypeId", "hotelId", "name", "basePrice", "capacity", "amenities", "description");
            case A05 -> Set.of("roomTypeId", "checkIn", "checkOut", "quantity", "available", "availableRooms");
            case A06 -> Set.of("locations", "hasMore"); case A07 -> Set.of("types"); case A08 -> Set.of("amenities");
            case A09 -> Set.of("summaries", "missingHotelIds"); case A10 -> Set.of("version", "checkInTime", "timeZone", "freeCancellationHours");
        };
        assertThat(fieldNames(result.data())).isEqualTo(projectedFields);
        if (id == ToolId.A05) assertThat(result.evidence().checkedAt()).isEqualTo(CLOCK.instant());
        if (id == ToolId.A01) {
            assertThat(result.data().at("/items/0/offer/unitPrice").decimalValue()).isEqualByComparingTo("800000");
            assertThat(result.data().at("/items/0/reviews/averageRating").isNull()).isTrue();
        }
    }
    static Set<String> fieldNames(JsonNode n) { Set<String> keys = new HashSet<>(); n.fieldNames().forEachRemaining(keys::add); return keys; }
    @Test void inputSchemaResourceExactlyMatchesApprovedDraft202012Block() throws Exception {
        String design = java.nio.file.Files.readString(java.nio.file.Path.of("../Docs/ai-agent/AI_AGENT_READ_TOOL_DESIGN.md"));
        var match = java.util.regex.Pattern.compile("```json\\s*\\n(.*?)```", java.util.regex.Pattern.DOTALL).matcher(design);
        assertThat(match.find()).isTrue();
        JsonNode documented = ToolArguments.JSON.readTree(match.group(1));
        JsonNode resource = ToolArguments.JSON.readTree(getClass().getResourceAsStream("/tools/read-input-schemas.json"));
        assertThat(resource).isEqualTo(documented);
        assertThat(resource.get("$schema").textValue()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        for (var d : registry.definitions()) assertThat(d.inputSchema().has("$ref")).isFalse();
    }
    @ParameterizedTest @EnumSource(ToolId.class)
    void everyInputRejectsWrongTypeAndRequiredFieldsWithoutHttp(ToolId id) {
        var valid = args(id); var wrong = valid.deepCopy();
        if (valid.isEmpty()) {
            var result = registry.execute(id, ToolProjection.list(), context());
            assertThat(result.error().category()).isEqualTo(INVALID_ARGUMENTS);
        } else {
            String field = valid.fieldNames().next(); wrong.putNull(field);
            assertThat(registry.execute(id, wrong, context()).status()).isEqualTo(ToolResult.Status.ERROR);
            assertThat(registry.execute(id, json("{}"), context()).status()).isEqualTo(ToolResult.Status.ERROR);
        }
        assertThat(received).hasValue(0);
    }
    @ParameterizedTest @EnumSource(ToolId.class)
    void everyToolRejectsUnknownFieldsBeforeNetwork(ToolId id) {
        var args = args(id).put("url", "http://bad.invalid/admin").put("method", "POST"); var ctx = context();
        var result = registry.execute(id, args, ctx);
        assertThat(result.error().category()).isEqualTo(INVALID_ARGUMENTS); assertThat(received).hasValue(0); assertThat(ctx.attempts()).isZero();
    }
    @ParameterizedTest @EnumSource(ToolId.class)
    void everyToolEmptyOrNotFoundAndUpstreamFailure(ToolId id) {
        boolean collection = Set.of(ToolId.A01, ToolId.A03, ToolId.A06, ToolId.A07, ToolId.A08, ToolId.A09).contains(id);
        responseStatus = collection ? 200 : 404;
        responseBody = id == ToolId.A01 ? fixtures.get("EMPTY").get("body").toString() : collection ? "[]" : "{\"code\":\"NOT_FOUND\"}";
        var result = registry.execute(id, args(id), context());
        if (collection) assertThat(result.status()).isEqualTo(ToolResult.Status.EMPTY);
        else assertThat(result.error().category()).isEqualTo(NOT_FOUND);
        responseStatus = 503; responseBody = "{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"SECRET SQL\"}";
        var ctx = context(); result = registry.execute(id, args(id), ctx);
        assertThat(result.error().category()).isEqualTo(BACKEND_UNAVAILABLE); assertThat(result.error().status()).isEqualTo(503);
        assertThat(result.data()).isNull(); assertThat(result.error().toString()).doesNotContain("SECRET", "SQL");
        assertThat(ctx.attempts()).isEqualTo(1); assertThat(received).hasValue(2);
    }
    static Stream<String> invalidSearch() {
        return Stream.of("{}", "[]", "null", "{", "{\"cityName\":\"x\",\"cityName\":\"y\"}", "{\"cityName\":3}",
                "{\"cityId\":\"../admin\"}", "{\"cityId\":\"1-1-3-8-1\"}", "{\"cityName\":\" \"}",
                "{\"cityName\":\"x\",\"sort\":\"UNKNOWN\"}", "{\"cityName\":\"x\",\"size\":6}",
                "{\"cityName\":\"x\",\"page\":1.5}", "{\"cityName\":\"x\",\"checkIn\":\"2026-12-01\"}",
                "{\"cityName\":\"x\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\"}",
                "{\"cityName\":\"x\",\"minPrice\":1}", "{\"cityName\":\"x\",\"amenityCodes\":[\"EVIL\"]}",
                "{\"cityName\":\"x\",\"accommodationTypes\":[\"MADE_UP\"]}", "{\"cityName\":\"x\",\"amenityCodes\":[\"WIFI\",\"WIFI\"]}",
                "{\"cityName\":\"x\",\"provinceId\":\"" + H + "\"}", "{\"cityName\":\"x\",\"headers\":{\"Authorization\":\"SECRET\"}}");
    }
    @ParameterizedTest @MethodSource("invalidSearch")
    void invalidArgumentsNeverReachHttp(String raw) {
        var ctx = context(); var result = registry.execute("search_hotels", raw, ctx);
        assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR); assertThat(ctx.attempts()).isZero(); assertThat(received).hasValue(0);
    }
    @Test void oversizedArgumentsAndDeepDirectJsonNodesRejected() {
        var raw = "{\"cityName\":\"" + "x".repeat(16385) + "\"}";
        assertThat(registry.execute("search_hotels", raw, context()).error().category()).isEqualTo(INVALID_ARGUMENTS);
        assertThat(registry.execute("get_hotel", "{} {}", context()).error().category()).isEqualTo(INVALID_ARGUMENTS);
        assertThat(received).hasValue(0);
    }
    @ParameterizedTest @CsvSource({"2026-11-21,2026-11-23", "2026-12-01,2026-12-01", "2026-12-03,2026-12-01", "2026-12-01,2027-01-01", "2026-02-30,2026-12-01", "2026-12-1,2026-12-03"})
    void invalidCivilDates(String start, String end) {
        var args = args(ToolId.A01).put("checkIn", start).put("checkOut", end);
        assertThat(registry.execute(ToolId.A01, args, context()).status()).isEqualTo(ToolResult.Status.ERROR);
        assertThat(received).hasValue(0);
    }
    @ParameterizedTest @CsvSource({"rooms,0", "rooms,21", "adults,0", "adults,41", "children,-1", "children,41"})
    void invalidOccupancy(String key, int value) {
        assertThat(registry.execute(ToolId.A01, args(ToolId.A01).put(key, value), context()).error().category()).isEqualTo(INVALID_DATE_OCCUPANCY);
        assertThat(received).hasValue(0);
    }
    @Test void thirtyNightsAndBusinessMidnightAndUuidV3AreAccepted() throws Exception {
        responseBody = fixtures.get("EMPTY").get("body").toString();
        try (var local = create(Duration.ofSeconds(2), Clock.fixed(Instant.parse("2026-11-21T17:00:00Z"), ZoneOffset.UTC), true)) {
            var args = args(ToolId.A01).put("checkIn", "2026-11-22").put("checkOut", "2026-12-22")
                    .put("cityId", "11111111-1111-3111-8111-111111111111"); args.remove("cityName");
            assertThat(local.execute(ToolId.A01, args, context()).status()).isEqualTo(ToolResult.Status.EMPTY);
            args.put("checkIn", "2026-11-21");
            assertThat(local.execute(ToolId.A01, args, context()).error().category()).isEqualTo(INVALID_DATE_OCCUPANCY);
        }
        assertThat(received).hasValue(1);
    }
    @Test void browseDefaultsAreNotConfirmedOccupancyAndQueriesAreEncoded() throws Exception {
        ObjectNode page = (ObjectNode) ToolArguments.JSON.readTree(happy(ToolId.A01));
        for (var entry : page.get("content")) for (String key : List.of("availableRooms", "checkIn", "checkOut", "checkedAt"))
            ((ObjectNode) entry.get("offer")).putNull(key);
        responseBody = page.toString(); var result = registry.execute(ToolId.A01, json("{\"cityName\":\"x&url=http://bad.invalid#\"}"), context());
        assertThat(result.error()).isNull(); assertThat(result.data().path("occupancyBasis").textValue()).isEqualTo("BACKEND_DEFAULT_BROWSE");
        assertThat(paths.getFirst()).contains("%26url%3Dhttp%3A%2F%2Fbad.invalid%23", "size=5", "page=0", "sort=DEFAULT").doesNotContain("adults=", "rooms=", "children=");
    }
    @Test void availabilityNeedsGroundingAndConfirmedQuantityAndIsNotAHold() {
        var args = args(ToolId.A05);
        assertThat(registry.execute(ToolId.A05, args, ToolExecutionContext.empty()).error().category()).isEqualTo(MISSING_REQUIRED_CRITERIA);
        assertThat(registry.execute(ToolId.A05, args, new ToolExecutionContext(1L, 1, Map.of(), Set.of(), Set.of())).error().category()).isEqualTo(POLICY_DENIED);
        fixture("UNAVAILABLE"); var result = registry.execute(ToolId.A05, args, context());
        assertThat(result.status()).isEqualTo(ToolResult.Status.OK); assertThat(result.data().get("available").booleanValue()).isFalse();
        assertThat(result.data().get("availableRooms").intValue()).isZero();
        assertThat(result.data().toString()).doesNotContain("hold", "price"); assertThat(received).hasValue(1);
    }
    @ParameterizedTest @CsvSource({"400,INVALID_REQUEST,INVALID_ARGUMENTS", "401,UNAUTHORIZED,UNAUTHORIZED_UPSTREAM", "403,FORBIDDEN,FORBIDDEN_UPSTREAM", "404,NOT_FOUND,NOT_FOUND", "409,INVENTORY_APPROVAL_REQUIRED,INVENTORY_APPROVAL_REQUIRED", "409,INVENTORY_NOT_CONFIGURED,INVENTORY_UNKNOWN", "409,INVENTORY_INCONSISTENT,INVENTORY_UNKNOWN", "409,CONFLICT,STATE_CONFLICT", "429,RATE_LIMITED,RATE_LIMITED", "500,INTERNAL_ERROR,BACKEND_UNAVAILABLE", "502,DEPENDENCY_UNAVAILABLE,BACKEND_UNAVAILABLE", "503,DEPENDENCY_UNAVAILABLE,BACKEND_UNAVAILABLE", "504,DEPENDENCY_TIMEOUT,BACKEND_TIMEOUT", "201,UNKNOWN,INVALID_UPSTREAM_RESPONSE", "302,UNKNOWN,INVALID_UPSTREAM_RESPONSE"})
    void statusAndBusinessCodesDoNotRetryOrRedirect(int status, String code, ToolError.Category category) {
        responseStatus = status; responseBody = "{\"code\":\"" + code + "\",\"message\":\"SECRET http://internal\"}";
        var ctx = context(); var result = registry.execute(ToolId.A04, args(ToolId.A04), ctx);
        assertThat(result.error().category()).isEqualTo(category); assertThat(result.error().status()).isEqualTo(status);
        assertThat(result.error().retryable()).isFalse(); assertThat(result.toString()).doesNotContain("SECRET", "internal");
        assertThat(received).hasValue(1); assertThat(ctx.attempts()).isEqualTo(1); assertThat(result.data()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"{", "null", "{} {}", "{\"available\":true,\"available\":false}"})
    void invalidJsonOrShapeFailsClosed(String raw) {
        responseBody = raw; var result = registry.execute(ToolId.A05, args(ToolId.A05), context());
        assertThat(result.status()).isEqualTo(ToolResult.Status.ERROR); assertThat(result.data()).isNull();
    }
    @Test void wrongContentTypeEncodingDepthAndOversizeAreRejected() {
        responseBody = happy(ToolId.A07); contentType = "text/html";
        assertThat(registry.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(INVALID_UPSTREAM_RESPONSE);
        contentType = "application/json"; contentEncoding = "gzip";
        assertThat(registry.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(INVALID_UPSTREAM_RESPONSE);
        contentEncoding = null; responseBody = "[".repeat(33) + "0" + "]".repeat(33);
        assertThat(registry.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(INVALID_UPSTREAM_JSON);
        responseBody = "[\"" + "x".repeat(262144) + "\"]";
        for (boolean streaming : new boolean[]{false, true}) {
            chunked = streaming;
            assertThat(registry.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(OVERSIZED_UPSTREAM_RESPONSE);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fullDeadlineIncludesStalledHeadersAndBody(boolean body) throws Exception {
        responseBody = happy(ToolId.A10); if (body) bodyDelay = 1500; else headerDelay = 1500;
        try (var shortDeadline = create(Duration.ofMillis(200), CLOCK, true)) {
            long start = System.nanoTime(); var ctx = context(); var result = shortDeadline.execute(ToolId.A10, args(ToolId.A10), ctx);
            assertThat(result.error().category()).isEqualTo(BACKEND_TIMEOUT);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
            assertThat(ctx.attempts()).isEqualTo(1); assertThat(received).hasValue(1);
        }
    }
    @Test void cancelAndInterruptAbortInflightBodyAndPreserveInterrupt() throws Exception {
        responseBody = happy(ToolId.A10); bodyDelay = 1500;
        var ctx = context(); var result = new AtomicReference<ToolResult>();
        Thread caller = Thread.ofPlatform().start(() -> result.set(registry.execute(ToolId.A10, args(ToolId.A10), ctx)));
        awaitAttempt(ctx); ctx.cancel(); caller.join(1000);
        assertThat(caller.isAlive()).isFalse(); assertThat(result.get().error().category()).isEqualTo(CANCELLED); assertThat(ctx.attempts()).isEqualTo(1);
        var ctx2 = context(); var interrupted = new AtomicBoolean();
        caller = Thread.ofPlatform().start(() -> { result.set(registry.execute(ToolId.A10, args(ToolId.A10), ctx2)); interrupted.set(Thread.currentThread().isInterrupted()); });
        awaitAttempt(ctx2); caller.interrupt(); caller.join(1000);
        assertThat(caller.isAlive()).isFalse(); assertThat(result.get().error().category()).isEqualTo(CANCELLED); assertThat(interrupted).isTrue();
        var pre = context(); pre.cancel();
        assertThat(registry.execute(ToolId.A10, args(ToolId.A10), pre).error().category()).isEqualTo(CANCELLED); assertThat(pre.attempts()).isZero();
    }
    static void awaitAttempt(ToolExecutionContext ctx) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (ctx.attempts() == 0 && System.nanoTime() < end) Thread.sleep(5);
        assertThat(ctx.attempts()).isEqualTo(1);
    }
    @Test void disabledRegistryAndUntrustedOriginsDoNotCallNetwork() throws Exception {
        try (var disabled = create(Duration.ofSeconds(1), CLOCK, false)) {
            assertThat(disabled.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(POLICY_DENIED);
        }
        for (String origin : List.of("http://user:pass@localhost", "http://localhost/admin", "http://localhost?x=1", "file:///tmp/x", "https://localhost#x"))
            assertThatThrownBy(() -> new ToolRegistry(true, URI.create(origin), Duration.ofSeconds(1), Duration.ofSeconds(1), CLOCK)).isInstanceOf(IllegalArgumentException.class);
        assertThat(received).hasValue(0);
    }
    @Test void catalogEvidenceIsRequiredWithoutHiddenExtraHttpCalls() {
        var args = json("{\"cityName\":\"Synthetic\"}"); args.putArray("amenityCodes").add("WIFI");
        var absent = registry.execute(ToolId.A01, args, ToolExecutionContext.empty());
        assertThat(absent.error().category()).isEqualTo(MISSING_REQUIRED_CRITERIA); assertThat(received).hasValue(0);
        fixture("EMPTY");
        assertThat(registry.execute(ToolId.A01, args, context()).status()).isEqualTo(ToolResult.Status.EMPTY);
        assertThat(received).hasValue(1); assertThat(paths.getFirst()).contains("amenityCodes=WIFI");
    }
    @Test void modelArgumentsCannotInventUserConfirmedOccupancy() {
        var args = args(ToolId.A01);
        assertThat(registry.execute(ToolId.A01, args, ToolExecutionContext.empty()).error().category()).isEqualTo(MISSING_REQUIRED_CRITERIA);
        args.put("children", 1);
        assertThat(registry.execute(ToolId.A01, args, context()).error().category()).isEqualTo(POLICY_DENIED);
        assertThat(received).hasValue(0);
    }
    @Test void roomNullCapacityPriceAndMissingReviewsRemainUnknown() throws Exception {
        ObjectNode room = (ObjectNode) ToolArguments.JSON.readTree(happy(ToolId.A04)); room.putNull("capacity").putNull("basePrice");
        responseBody = room.toString(); var result = registry.execute(ToolId.A04, args(ToolId.A04), context());
        assertThat(result.error()).isNull(); assertThat(result.data().at("/capacity/adults").isNull()).isTrue(); assertThat(result.data().get("basePrice").isNull()).isTrue();
        responseBody = "[]"; result = registry.execute(ToolId.A09, args(ToolId.A09), context());
        assertThat(result.data().get("missingHotelIds").get(0).textValue()).isEqualTo(H);
        assertThat(result.data().get("summaries")).isEmpty();
    }
    @Test void rejectsUnpublishedInconsistentAndFabricatedUpstreamFacts() throws Exception {
        ObjectNode detail = (ObjectNode) ToolArguments.JSON.readTree(happy(ToolId.A02)); detail.put("approvedStatus", "PENDING");
        responseBody = detail.toString(); assertThat(registry.execute(ToolId.A02, args(ToolId.A02), context()).data()).isNull();
        ObjectNode available = json(happy(ToolId.A05)); available.remove("checkedAt"); responseBody = available.toString();
        assertThat(registry.execute(ToolId.A05, args(ToolId.A05), context()).error().category()).isEqualTo(INVALID_UPSTREAM_RESPONSE);
        available.put("checkedAt", CLOCK.instant().toString()).put("availableRooms", 0); responseBody = available.toString();
        assertThat(registry.execute(ToolId.A05, args(ToolId.A05), context()).data()).isNull();
        ObjectNode page = (ObjectNode) ToolArguments.JSON.readTree(happy(ToolId.A01));
        ((ObjectNode) page.at("/content/0/offer")).put("totalPerNight", 1); responseBody = page.toString();
        assertThat(registry.execute(ToolId.A01, args(ToolId.A01), context()).data()).isNull();
    }
    @Test void truncationKeepsCompletePolicyClausesAndCatalogOverflowFails() throws Exception {
        ObjectNode detail = (ObjectNode) ToolArguments.JSON.readTree(happy(ToolId.A02));
        var policies = detail.putArray("policies"); policies.add(json("{\"category\":\"GENERAL\",\"content\":\"Complete clause\"}"));
        policies.add(ToolProjection.node().put("category", "LONG").put("content", "x".repeat(1001)));
        detail.put("description", "x".repeat(2001)); responseBody = detail.toString();
        var result = registry.execute(ToolId.A02, args(ToolId.A02), context());
        assertThat(result.error()).isNull(); assertThat(result.evidence().truncated()).isTrue();
        assertThat(result.data().get("policies")).hasSize(1); assertThat(result.data().has("description")).isFalse();
        var catalog = ToolProjection.list(); for (int i = 0; i < 201; i++) catalog.add(ToolProjection.node().put("code", "CODE" + i).put("label", "x").put("active", true));
        responseBody = catalog.toString(); assertThat(registry.execute(ToolId.A08, args(ToolId.A08), context()).data()).isNull();
    }
    @Test void outputByteCapAndNetworkFailuresAreCounted() throws Exception {
        var catalog = ToolProjection.list(); for (int i = 0; i < 50; i++) catalog.add(ToolProjection.node().put("code", "CODE" + i).put("label", "x".repeat(255)));
        responseBody = catalog.toString(); assertThat(registry.execute(ToolId.A07, args(ToolId.A07), context()).error().category()).isEqualTo(OUTPUT_TOO_LARGE);
        server.stop(0); var ctx = context(); var result = registry.execute(ToolId.A07, args(ToolId.A07), ctx);
        assertThat(result.error().category()).isEqualTo(BACKEND_UNAVAILABLE); assertThat(ctx.attempts()).isEqualTo(1);
    }
}
