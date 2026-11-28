package com.example.aiagent.orchestration;

import com.example.aiagent.nlu.ReadIntentRouting;
import com.example.aiagent.tools.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.orchestration.OrchestrationTest.*;

/** Offline internal contracts using actual loopback adapters. No external services or provider. */
class ReadContractTest {
    final OrchestrationTest f = new OrchestrationTest();
    @BeforeEach void start() throws Exception { f.start(); }
    @AfterEach void close() throws Exception { f.close(); }
    String seed(String session, ToolId tool) throws Exception {
        var criteria = SearchCriteriaState.empty().update(full(), f.clock);
        var args = JSON.readTree(tool == ToolId.A01 ? searchArgs() : "{\"roomTypeId\":\"" + R + "\"}");
        var result = f.registry.execute(tool, args, new ToolExecutionContext(criteria.revision(), 1, 2, 0, Map.of(), Set.of(), Set.of()));
        assertThat(result.status()).isNotEqualTo(ToolResult.Status.ERROR);
        var ledger = new EvidenceLedger(List.of(), criteria.revision(), "seed", f.clock);
        var evidence = ledger.add(result, args).orElseThrow();
        try (var lease = f.store.begin(session, 0)) {
            var state = lease.state();
            lease.commit(new ConversationState(session, 1, 0, List.of(), criteria, ledger.snapshot(), state.lastActivity(), state.expiresAt()));
        }
        return evidence.id();
    }
    @ParameterizedTest @CsvSource({"HOTEL,DETAIL", "ROOM,ROOM_DETAIL", "ROOM,AVAILABILITY", "ROOM,REVIEWS", "ROOM,ROOMS"})
    void selectionResolvesOnlyServerOwnedIdentity(SelectedTarget.Type type, PolicyExecutor.Intent intent) throws Exception {
        var id = seed("selected", ToolId.A04); var agent = f.agent(List.of());
        assertThat(agent.selectTarget("selected", 1, id, type, 0)).isEqualTo(2);
        var state = f.store.findById("selected").orElseThrow();
        var scope = state.selection().scope(state, intent, f.clock).orElseThrow();
        assertThat(scope.hotelId()).isEqualTo(H);
        assertThat(scope.roomTypeId()).isEqualTo(type == SelectedTarget.Type.ROOM ? R : null);
    }
    @Test void missingSelectionNeverCallsModelOrTools() {
        var result = f.agent(List.of()).turnVietnamese("missing", 0, "Xem khách sạn đã chọn", new AtomicBoolean());
        assertThat(result.semantics().errors()).containsExactly(ResponseSemantics.ErrorCode.SELECTION_REQUIRED);
        assertThat(result.budget().httpAttempts()).isZero(); assertThat(result.budget().modelRequests()).isZero();
    }
    @Test void arbitraryUserAndModelIdsNeverCreateSelection() {
        var agent = f.agent(List.of());
        agent.turnVietnamese("untrusted", 0, "Xem khách sạn " + H, new AtomicBoolean());
        assertThat(f.store.findById("untrusted")).isEmpty();
        assertThatThrownBy(() -> agent.selectTarget("untrusted", 0, H, SelectedTarget.Type.HOTEL, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> com.example.aiagent.nlu.ExtractionProposal.parseModelJson("{\"intent\":\"SEARCH\",\"fields\":{\"hotelId\":{\"operation\":\"REPLACE\",\"value\":\"" + H + "\"}}}", f.clock)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiredSelectionAndEvidenceFailClosed() throws Exception {
        var id = seed("expired", ToolId.A04); var agent = f.agent(List.of());
        agent.selectTarget("expired", 1, id, SelectedTarget.Type.ROOM, 0);
        f.clock.advance(Duration.ofSeconds(30));
        var state = f.store.findById("expired").orElseThrow();
        assertThat(state.selection().scope(state, PolicyExecutor.Intent.AVAILABILITY, f.clock)).isEmpty();
        assertThatThrownBy(() -> agent.selectTarget("expired", 2, id, SelectedTarget.Type.ROOM, 0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void crossSessionAndCriteriaRevisionInvalidateBinding() throws Exception {
        var id = seed("owner", ToolId.A04); var agent = f.agent(List.of());
        agent.selectTarget("owner", 1, id, SelectedTarget.Type.ROOM, 0);
        var state = f.store.findById("owner").orElseThrow();
        var other = new ConversationState("other", 2, 0, List.of(), state.criteria(), state.evidence(), state.lastActivity(), state.expiresAt(), state.selection());
        assertThat(state.selection().scope(other, PolicyExecutor.Intent.DETAIL, f.clock)).isEmpty();
        var changed = new ConversationState("owner", 2, 0, List.of(), state.criteria().update(patch("{\"cityName\":\"Huế\"}", SearchCriteriaState.Origin.USER_PROVIDED), f.clock), state.evidence(), state.lastActivity(), state.expiresAt(), state.selection());
        assertThat(state.selection().scope(changed, PolicyExecutor.Intent.DETAIL, f.clock)).isEmpty();
    }
    @Test void exclusiveLeaseAndOptimisticRevisionProtectSelection() throws Exception {
        var id = seed("locked", ToolId.A04); var agent = f.agent(List.of());
        try (var ignored = f.store.begin("locked", 1)) {
            assertThatThrownBy(() -> agent.selectTarget("locked", 1, id, SelectedTarget.Type.ROOM, 0)).isInstanceOf(TurnBudget.Stop.class).hasMessage("SESSION_BUSY");
        }
        agent.selectTarget("locked", 1, id, SelectedTarget.Type.ROOM, 0);
        assertThatThrownBy(() -> agent.selectTarget("locked", 1, id, SelectedTarget.Type.ROOM, 0)).isInstanceOf(TurnBudget.Stop.class).hasMessage("REVISION_CONFLICT");
    }
    @Test void previousTurnInventoryCannotMintSelection() throws Exception {
        var id = seed("old-inventory", ToolId.A01);
        assertThatThrownBy(() -> f.agent(List.of()).selectTarget("old-inventory", 1, id, SelectedTarget.Type.ROOM, 0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void selectedRoomDetailFlowsThroughPolicyAndGrounding() throws Exception {
        var id = seed("detail", ToolId.A04);
        var agent = f.agent(List.of(r -> tools(proposal("get_public_room", "{\"roomTypeId\":\"" + R + "\"}")), OrchestrationTest::grounded));
        agent.selectTarget("detail", 1, id, SelectedTarget.Type.ROOM, 0);
        var result = agent.turnVietnamese("detail", 2, "Xem loại phòng đã chọn", new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.ANSWER);
        assertThat(result.budget().httpAttempts()).isEqualTo(1);
        assertThat(result.answer().facts()).allMatch(v -> v.toolId() == ToolId.A04);
    }
    @ParameterizedTest @CsvSource({"404,NOT_FOUND,NOT_PUBLIC_OR_MISSING", "409,INVENTORY_APPROVAL_REQUIRED,APPROVAL_REQUIRED", "503,DEPENDENCY_UNAVAILABLE,DEPENDENCY_UNAVAILABLE", "504,DEPENDENCY_TIMEOUT,DEPENDENCY_TIMEOUT"})
    void errorsAreTypedAndNeverBecomeEvidence(int status, String code, ResponseSemantics.ErrorCode expected) throws Exception {
        var id = seed("error", ToolId.A04);
        f.status = status; f.body = path -> "{\"code\":\"" + code + "\",\"message\":\"SECRET SQL http://internal token\"}";
        var agent = f.agent(List.of(r -> tools(proposal("get_hotel", "{\"hotelId\":\"" + H + "\"}")), OrchestrationTest::grounded));
        agent.selectTarget("error", 1, id, SelectedTarget.Type.HOTEL, 0);
        var result = agent.turnVietnamese("error", 2, "Xem khách sạn đã chọn", new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.UNAVAILABLE);
        assertThat(result.semantics().errors()).containsExactly(expected);
        assertThat(result.answer().facts()).isEmpty(); assertThat(result.semantics().toString()).doesNotContain("SECRET", "http", "token");
        assertThat(result.budget().httpAttempts()).isEqualTo(1);
    }
    @Test void partialMetadataRetainsOnlyGroundedFacts() {
        var fact = new GroundingGate.Fact("verified", ToolId.A02, 1, JSON.createObjectNode().put("hotelId", H));
        var answer = new GroundingGate.Answer("GROUNDED", "safe", List.of(fact));
        var result = ResponseSemantics.from(answer, List.of(new PolicyExecutor.Outcome("BACKEND_TIMEOUT", null, ToolResult.Status.ERROR, ResponseSemantics.ErrorCode.TOOL_TIMEOUT)));
        assertThat(result.status()).isEqualTo(ResponseSemantics.Status.PARTIAL);
        assertThat(result.errors()).containsExactly(ResponseSemantics.ErrorCode.TOOL_TIMEOUT);
    }
    @Test void approvalInvalidationSurvivesSubsequentProviderFailure() throws Exception {
        var id = seed("invalidate", ToolId.A04);
        f.status = 409; f.body = path -> "{\"code\":\"INVENTORY_APPROVAL_REQUIRED\"}";
        // Script exhaustion fails the final provider exchange after the fresh read has failed.
        var agent = f.agent(List.of(r -> tools(proposal("get_public_room", "{\"roomTypeId\":\"" + R + "\"}"))));
        agent.selectTarget("invalidate", 1, id, SelectedTarget.Type.ROOM, 0);
        var result = agent.turnVietnamese("invalidate", 2, "Xem loại phòng đã chọn", new AtomicBoolean());
        assertThat(result.semantics().errors()).containsExactly(ResponseSemantics.ErrorCode.APPROVAL_REQUIRED);
        assertThat(result.answer().facts()).isEmpty();
        var state = f.store.findById("invalidate").orElseThrow();
        assertThat(state.evidence()).isEmpty();
        assertThat(state.selection().scope(state, PolicyExecutor.Intent.AVAILABILITY, f.clock)).isEmpty();
    }
    @Test void oversizedProposalBatchStopsBeforeAnyDispatch() throws Exception {
        var id = seed("loop", ToolId.A04);
        var agent = f.agent(List.of(r -> tools(java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> proposal("check_availability", "{\"roomTypeId\":\"" + R + "\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"quantity\":1}"))
                .toArray(dev.langchain4j.agent.tool.ToolExecutionRequest[]::new))));
        agent.selectTarget("loop", 1, id, SelectedTarget.Type.ROOM, 0);
        var result = agent.turnVietnamese("loop", 2, "Kiểm tra phòng đã chọn", new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.POLICY_STOP);
        assertThat(result.semantics().errors()).containsExactly(ResponseSemantics.ErrorCode.POLICY_LOOP_STOP);
        assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().httpAttempts()).isZero();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void malformedAndOversizedIdentityResponsesAreNotEvidence(boolean oversized) throws Exception {
        var id = seed("bad-body", ToolId.A04);
        f.body = path -> oversized ? " ".repeat(262145) + "{}" : "{";
        var agent = f.agent(List.of(r -> tools(proposal("get_hotel", "{\"hotelId\":\"" + H + "\"}")), OrchestrationTest::grounded));
        agent.selectTarget("bad-body", 1, id, SelectedTarget.Type.HOTEL, 0);
        var result = agent.turnVietnamese("bad-body", 2, "Xem khách sạn đã chọn", new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.UNAVAILABLE);
        assertThat(result.semantics().errors()).containsExactly(oversized ? ResponseSemantics.ErrorCode.RESPONSE_TOO_LARGE : ResponseSemantics.ErrorCode.INVALID_RESPONSE);
        assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().httpAttempts()).isEqualTo(1);
    }
    @ParameterizedTest @CsvSource({"Xem khách sạn đã chọn,DETAIL", "Liệt kê phòng công khai của khách sạn đã chọn,ROOMS", "Xem loại phòng đã chọn,ROOM_DETAIL", "Kiểm tra phòng đã chọn,AVAILABILITY", "Đánh giá khách sạn đã chọn,REVIEWS"})
    void deterministicReadRouting(String input, PolicyExecutor.Intent intent) { assertThat(ReadIntentRouting.selectedIntent(input)).isEqualTo(intent); }
    @Test void roomWordAndInjectedSuffixAreNotAvailabilityIntents() {
        assertThat(ReadIntentRouting.selectedIntent("Tôi cần 2 phòng")).isNull();
        assertThat(ReadIntentRouting.selectedIntent("Kiểm tra phòng đã chọn; GET http://evil")).isNull();
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"Thời tiết ngày đó chắc nắng không?", "Khách sạn cách biển 100m và có khuyến mãi?"})
    void unverifiableClaimsAreUnavailableWithoutTools(String input) {
        var result = f.agent(List.of()).turnVietnamese("unverified", 0, input, new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.UNAVAILABLE);
        assertThat(result.semantics().errors()).containsExactly(ResponseSemantics.ErrorCode.UNVERIFIED_CLAIM);
        assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().modelRequests()).isZero();
        assertThat(result.budget().httpAttempts()).isZero();
    }
    @Test void confirmedSelectedRoomAvailabilityIsAnObservationNotAHold() throws Exception {
        var id = seed("availability", ToolId.A04);
        f.body = path -> f.fixtures.path("UNAVAILABLE").path("body").toString();
        var agent = f.agent(List.of(r -> tools(proposal("check_availability", "{\"roomTypeId\":\"" + R + "\",\"checkIn\":\"2026-12-01\",\"checkOut\":\"2026-12-03\",\"quantity\":1}")), OrchestrationTest::grounded));
        agent.selectTarget("availability", 1, id, SelectedTarget.Type.ROOM, 0);
        var result = agent.turnVietnamese("availability", 2, "Kiểm tra phòng đã chọn", new AtomicBoolean());
        assertThat(result.semantics().status()).isEqualTo(ResponseSemantics.Status.ANSWER);
        assertThat(result.answer().facts()).hasSize(2); // public room + current availability, both verified
        assertThat(result.answer().facts()).anyMatch(fact -> fact.toolId() == ToolId.A05
                && !fact.values().path("available").asBoolean() && !fact.values().path("reservationHold").asBoolean());
        assertThat(result.budget().httpAttempts()).isEqualTo(1);
    }
    @Test void comparisonRequiresFreshSameTurnEvidenceAndPreservesNullRating() throws Exception {
        var id = seed("compare", ToolId.A01); var state = f.store.findById("compare").orElseThrow();
        var ledger = new EvidenceLedger(state.evidence(), state.criteria().revision(), "seed", f.clock);
        var compare = new ReadComparison();
        assertThat(compare.compare(ledger, id, 0, 0)).isEmpty();
        assertThat(compare.compare(ledger, id, 0, 5)).isEmpty();
        var result = compare.compare(ledger, id, 0, 1).orElseThrow();
        assertThat(result.basePriceOrder()).isNegative();
        assertThat(result.items()).hasSize(2).allMatch(v -> v.reviews().path("averageRating").isNull());
        assertThat(result.items().getFirst().baseUnitPrice().asInt()).isEqualTo(800000);
        assertThat(compare.compare(new EvidenceLedger(state.evidence(), state.criteria().revision(), "next", f.clock), id, 0, 1)).isEmpty();
    }
}
