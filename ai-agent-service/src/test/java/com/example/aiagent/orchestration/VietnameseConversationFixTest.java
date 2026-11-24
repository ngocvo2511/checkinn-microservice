package com.example.aiagent.orchestration;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static com.example.aiagent.orchestration.OrchestrationTest.*;

/** Composes the existing loopback harness without inheriting/rerunning its test methods. */
class VietnameseConversationFixTest {
    final OrchestrationTest harness = new OrchestrationTest();
    @BeforeEach void start() throws Exception { harness.start(); }
    @AfterEach void close() throws Exception { harness.close(); }
    AgentOrchestrator.Result turn(AgentOrchestrator agent, String id, long revision, String text) {
        return agent.turnVietnamese(id, revision, text, new AtomicBoolean());
    }
    void seed(String id, SearchCriteriaState.Origin origin) {
        try (var lease = harness.store.begin(id, 0)) {
            var state = lease.state();
            var criteria = state.criteria().update(patch(searchArgs(), origin), harness.clock);
            lease.commit(new ConversationState(id, 1, 0, List.of(), criteria, List.of(), state.lastActivity(), state.expiresAt()));
        }
    }
    @Test void caseAExactlyTwoClarificationsThenGroundedSearch() {
        harness.body = path -> {
            var body = harness.fixtures.path("PAGE").path("body").deepCopy();
            body.path("content").forEach(item -> {
                ((com.fasterxml.jackson.databind.node.ObjectNode) item.path("offer")).put("checkIn", "2026-12-15").put("checkOut", "2026-12-17");
            }); return body.toString();
        };
        var agent = harness.agent(List.of(r -> tools(proposal("search_hotels", context(r).path("confirmedCriteria").toString())), OrchestrationTest::grounded));
        var a = turn(agent, "ux-a", 0, "Tìm khách sạn ở Đà Lạt ngày 15/12/2026.");
        assertThat(a.answer().category()).isEqualTo("NEED_DATES"); assertThat(a.answer().message()).contains("nhận", "trả").doesNotContain("ở đâu", "người lớn");
        var b = turn(agent, "ux-a", 1, "15/12/2026 đến 17/12/2026.");
        assertThat(b.answer().category()).isEqualTo("NEED_OCCUPANCY"); assertThat(b.answer().message()).contains("phòng", "người lớn", "trẻ em").doesNotContain("ngày", "ở đâu");
        assertThat(harness.model).hasValue(0); assertThat(harness.http).hasValue(0);
        var c = turn(agent, "ux-a", 2, "2 người lớn, 0 trẻ em, 1 phòng.");
        assertThat(c.answer().category()).isEqualTo("GROUNDED"); assertThat(harness.http).hasValue(1);
        var state = harness.store.findById("ux-a").orElseThrow();
        assertThat(state.criteria().slots().get("checkIn").origin()).isEqualTo(SearchCriteriaState.Origin.USER_PROVIDED);
        assertThat(state.criteria().slots().get("adults").origin()).isEqualTo(SearchCriteriaState.Origin.USER_CONFIRMED);
    }
    @Test void caseBNamesMissingRelatedFieldsWithoutDefaultingRooms() {
        seed("ux-b", SearchCriteriaState.Origin.USER_CONFIRMED);
        var agent = harness.agent(List.of());
        var result = turn(agent, "ux-b", 1, "Đổi thành 3 người lớn.");
        assertThat(result.answer().message()).startsWith("Cần xác nhận số trẻ em và số phòng.").contains("xác nhận trọn nhóm").doesNotContain("ngày", "ở đâu", "Cần xác nhận số người lớn");
        var criteria = harness.store.findById("ux-b").orElseThrow().criteria();
        assertThat(criteria.occupancy("adults")).isEqualTo(3); assertThat(criteria.occupancy("rooms")).isNull(); assertThat(criteria.occupancy("children")).isNull();
        assertThat(criteria.revision()).isEqualTo(2); assertThat(harness.model).hasValue(0); assertThat(harness.http).hasValue(0);
    }
    @Test void caseCIncrementIsNotReplacementOrAutomaticConfirmation() {
        seed("ux-c", SearchCriteriaState.Origin.USER_CONFIRMED);
        var before = harness.store.findById("ux-c").orElseThrow().criteria();
        var result = turn(harness.agent(List.of()), "ux-c", 1, "Thêm 1 trẻ em.");
        assertThat(result.answer().category()).isEqualTo("NLU_CLARIFICATION");
        assertThat(result.answer().message()).contains("Chưa áp dụng phép tăng", "tổng cộng", "người lớn", "trẻ em", "phòng").doesNotContain("ngày", "ở đâu");
        assertThat(harness.store.findById("ux-c").orElseThrow().criteria()).isEqualTo(before);
        assertThat(result.budget().modelRequests()).isZero(); assertThat(result.budget().httpAttempts()).isZero();
    }
    @Test void caseDDatePairInvalidatedQuestionTargetsCheckoutAndNoUnrelatedSlots() {
        seed("ux-d", SearchCriteriaState.Origin.USER_CONFIRMED);
        var result = turn(harness.agent(List.of()), "ux-d", 1, "Đổi ngày nhận phòng sang 16/12/2026.");
        assertThat(result.answer().category()).isEqualTo("NEED_DATES");
        assertThat(result.answer().message()).startsWith("Đã ghi nhận ngày nhận phòng.").contains("xác nhận ngày trả phòng", "cả hai ngày").doesNotContain("người lớn", "trẻ em", "ở đâu");
        var criteria = harness.store.findById("ux-d").orElseThrow().criteria();
        assertThat(criteria.confirmed("checkIn").asText()).isEqualTo("2026-12-16"); assertThat(criteria.confirmed("checkOut")).isNull();
        assertThat(criteria.hasOccupancy()).isTrue(); assertThat(result.budget().modelRequests()).isZero(); assertThat(result.budget().httpAttempts()).isZero();
    }
    @Test void mixedAmbiguityKeepsAuthoritativeStateAndTellsUserNothingApplied() {
        seed("ux-mixed", SearchCriteriaState.Origin.USER_CONFIRMED);
        var before = harness.store.findById("ux-mixed").orElseThrow().criteria();
        var result = turn(harness.agent(List.of()), "ux-mixed", 1, "Tìm khách sạn ở Đà Lạt, cuối tuần sau, 2 người.");
        assertThat(result.answer().message()).contains("chưa được áp dụng", "ngày nhận/trả", "người lớn", "trẻ em").doesNotContain("đã lưu", "ở đâu");
        assertThat(harness.store.findById("ux-mixed").orElseThrow().criteria()).isEqualTo(before);
        assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().httpAttempts()).isZero();
    }
    @Test void providedOccupancyNeverBecomesConfirmedThroughUnrelatedRefinement() {
        seed("ux-unconfirmed", SearchCriteriaState.Origin.USER_PROVIDED);
        var result = turn(harness.agent(List.of()), "ux-unconfirmed", 1, "Đổi sang Nha Trang");
        assertThat(result.answer().category()).isEqualTo("NEED_OCCUPANCY");
        var criteria = harness.store.findById("ux-unconfirmed").orElseThrow().criteria();
        assertThat(criteria.occupancy("adults")).isNull();
        assertThat(criteria.slots().get("adults").origin()).isEqualTo(SearchCriteriaState.Origin.USER_PROVIDED);
        assertThat(harness.model).hasValue(0); assertThat(harness.http).hasValue(0);
    }
    @ParameterizedTest @ValueSource(strings = {"ba người lớn, không trẻ em, một phòng", "ngày năm tháng mười hai năm hai nghìn không trăm hai mươi sáu đến ngày bảy tháng mười hai năm hai nghìn không trăm hai mươi sáu"})
    void expandedGrammarCannotReuseOldInventory(String change) {
        var old = new AtomicReference<String>();
        var agent = harness.agent(List.of(r -> tools(proposal("search_hotels", context(r).path("confirmedCriteria").toString())), r -> {
            old.set(context(r).at("/untrustedEvidence/0/evidenceId").asText()); return grounded(r);
        }, r -> {
            assertThat(context(r).path("untrustedEvidence")).isEmpty(); return text("{\"evidenceIds\":[\"" + old.get() + "\"]}");
        }));
        assertThat(turn(agent, "ux-evidence", 0, "Đà Lạt 1/12/2026 đến 3/12/2026, hai người lớn, không trẻ em, một phòng").answer().category()).isEqualTo("GROUNDED");
        var result = turn(agent, "ux-evidence", 1, change);
        assertThat(result.answer().category()).isEqualTo("GROUNDING_REJECTED"); assertThat(result.answer().facts()).isEmpty(); assertThat(result.budget().httpAttempts()).isZero();
        assertThat(harness.store.findById("ux-evidence").orElseThrow().criteria().revision()).isEqualTo(2);
    }
    @Test void expandedGrammarUsesExistingLeaseRevisionAndTtl() {
        seed("ux-lifecycle", SearchCriteriaState.Origin.USER_CONFIRMED); var agent = harness.agent(List.of());
        assertThat(turn(agent, "ux-lifecycle", 0, "bốn người lớn").answer().category()).isEqualTo("REVISION_CONFLICT");
        try (var lease = harness.store.begin("ux-lifecycle", 1)) {
            assertThat(turn(agent, "ux-lifecycle", 1, "ngày ba mươi mốt tháng hai năm 2027").answer().category()).isEqualTo("SESSION_BUSY");
        }
        harness.clock.advance(java.time.Duration.ofMinutes(31));
        assertThat(turn(agent, "ux-lifecycle", 1, "bốn người lớn").answer().category()).isEqualTo("REVISION_CONFLICT");
        assertThat(harness.store.findById("ux-lifecycle")).isEmpty(); assertThat(harness.model).hasValue(0); assertThat(harness.http).hasValue(0);
    }
}
