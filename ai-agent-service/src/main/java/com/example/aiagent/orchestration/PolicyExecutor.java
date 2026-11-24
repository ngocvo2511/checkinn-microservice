package com.example.aiagent.orchestration;

import com.example.aiagent.tools.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.*;

/** Sole orchestration dispatch path. No tool rights originate in model or tool-output text. */
public final class PolicyExecutor {
    public enum Intent { SEARCH, BROWSE, DETAIL, ROOMS, AVAILABILITY, REVIEWS, LOCATIONS, CANCELLATION }
    public record Scope(Intent intent, String hotelId, String roomTypeId) { public Scope { Objects.requireNonNull(intent); } }
    public record Outcome(String category, String evidenceId, ToolResult.Status status) {}
    private final ToolRegistry registry;
    private final io.micrometer.tracing.Tracer tracer;
    public PolicyExecutor(ToolRegistry registry) { this(registry, null); }
    public PolicyExecutor(ToolRegistry registry, io.micrometer.tracing.Tracer tracer) { this.registry = registry; this.tracer = tracer; }

    public Set<ToolId> exposed(Scope scope, SearchCriteriaState criteria, EvidenceLedger evidence) {
        var ids = EnumSet.noneOf(ToolId.class);
        switch (scope.intent()) {
            case SEARCH, BROWSE -> {
                if (criteria.hasDestination() && (scope.intent() == Intent.BROWSE || criteria.hasDates() && criteria.hasOccupancy())) {
                    boolean needTypes = criteria.confirmed("accommodationTypes") != null && !criteria.confirmed("accommodationTypes").isEmpty();
                    boolean needAmenities = criteria.confirmed("amenityCodes") != null && !criteria.confirmed("amenityCodes").isEmpty();
                    if (needTypes && evidence.codes(ToolId.A07, "types").isEmpty()) ids.add(ToolId.A07);
                    if (needAmenities && evidence.codes(ToolId.A08, "amenities").isEmpty()) ids.add(ToolId.A08);
                    if (ids.isEmpty()) ids.add(ToolId.A01);
                }
            }
            case DETAIL -> { if (scope.hotelId() != null) ids.add(ToolId.A02); }
            case ROOMS -> { if (scope.hotelId() != null) ids.add(ToolId.A03); }
            case AVAILABILITY -> {
                if (scope.roomTypeId() != null && criteria.hasDates() && criteria.hasOccupancy()) {
                    if (evidence.roomHotels().containsKey(scope.roomTypeId())) ids.add(ToolId.A05); else ids.add(ToolId.A04);
                }
            }
            case REVIEWS -> {
                if (scope.hotelId() != null && evidence.current().stream().noneMatch(r -> r.toolId() == ToolId.A01 && r.hotelIds().contains(scope.hotelId()))) ids.add(ToolId.A09);
            }
            case LOCATIONS -> { if (criteria.confirmed("cityName") != null) ids.add(ToolId.A06); }
            case CANCELLATION -> ids.add(ToolId.A10);
        }
        return Set.copyOf(ids);
    }
    public List<dev.langchain4j.agent.tool.ToolSpecification> specifications(Set<ToolId> ids) {
        return registry.specifications().stream().filter(s -> ids.stream().anyMatch(id -> id.toolName().equals(s.name()))).toList();
    }
    /** Atomic admission of a complete response before any proposal executes. Sequential dispatch only. */
    public List<Outcome> executeRound(List<ToolExecutionRequest> proposals, Set<ToolId> offered, Scope scope,
                                      SearchCriteriaState criteria, EvidenceLedger evidence, TurnBudget budget, Set<String> executed) {
        if (proposals.isEmpty()) throw new IllegalArgumentException("Empty tool round");
        budget.reserveProposals(proposals.size()); budget.reserveRound();
        var outcomes = new ArrayList<Outcome>();
        for (var proposal : proposals) outcomes.add(execute(proposal, offered, scope, criteria, evidence, budget, executed));
        return List.copyOf(outcomes);
    }
    private Outcome execute(ToolExecutionRequest proposal, Set<ToolId> offered, Scope scope, SearchCriteriaState criteria,
                           EvidenceLedger evidence, TurnBudget budget, Set<String> executed) {
        budget.check();
        ToolId id = Arrays.stream(ToolId.values()).filter(t -> t.toolName().equals(proposal.name())).findFirst().orElse(null);
        if (id == null || !"GET".equals(id.method()) || !offered.contains(id) || !exposed(scope, criteria, evidence).contains(id)) return deny(budget, "TOOL_NOT_ALLOWED");
        var context = new ToolExecutionContext(criteria.revision(), criteria.occupancy("rooms"), criteria.occupancy("adults"),
                criteria.occupancy("children"), evidence.roomHotels(), evidence.codes(ToolId.A07, "types"), evidence.codes(ToolId.A08, "amenities"));
        if (tracer != null && tracer.currentSpan() != null) {
            var trace = tracer.currentSpan().context();
            context.traceparent("00-" + trace.traceId() + "-" + trace.spanId() + "-" + (Boolean.TRUE.equals(trace.sampled()) ? "01" : "00"));
        }
        JsonNode args;
        try {
            args = registry.validateProposal(id, proposal.arguments(), context);
            if (!matches(id, args, scope, criteria, evidence)) return deny(budget, "CRITERIA_OR_EVIDENCE_MISMATCH");
        } catch (RuntimeException invalid) { return deny(budget, "INVALID_PROPOSAL"); }
        var normalized = (ObjectNode) args.deepCopy();
        if (id == ToolId.A01) {
            if (!normalized.has("page")) normalized.put("page", 0);
            if (!normalized.has("size")) normalized.put("size", 5);
            if (!normalized.has("sort")) normalized.put("sort", "DEFAULT");
        }
        String relationship = id == ToolId.A05 ? evidence.roomHotels().get(scope.roomTypeId()) : "";
        String key = id + ":" + EvidenceLedger.fingerprint(normalized) + ":" + criteria.revision() + ":" + relationship;
        if (!executed.add(key)) return deny(budget, "DUPLICATE");
        budget.checkHttpAdmission(); budget.attach(context);
        var result = registry.execute(id, args, context);
        budget.check();
        var record = evidence.add(result, normalized);
        return new Outcome(result.error() == null ? "VERIFIED" : result.error().category().name(), record.map(EvidenceLedger.Record::id).orElse(null), result.status());
    }
    private boolean matches(ToolId id, JsonNode args, Scope scope, SearchCriteriaState criteria, EvidenceLedger evidence) {
        if (id == ToolId.A01) {
            var expected = (ObjectNode) criteria.confirmedJson();
            if (scope.intent() == Intent.BROWSE) {
                // Explicit browse only. Do not silently drop a supplied stay, occupancy or price filter.
                if (List.of("checkIn", "checkOut", "rooms", "adults", "children", "minPrice", "maxPrice").stream().anyMatch(expected::has)) return false;
            }
            var actual = (ObjectNode) args.deepCopy();
            for (String key : List.of("page", "size", "sort")) actual.remove(key);
            return actual.equals(expected);
        }
        if (id == ToolId.A02 || id == ToolId.A03) return Objects.equals(scope.hotelId(), args.path("hotelId").asText());
        if (id == ToolId.A04 || id == ToolId.A05) {
            if (!Objects.equals(scope.roomTypeId(), args.path("roomTypeId").asText())) return false;
            if (id == ToolId.A05) return Objects.equals(criteria.confirmed("checkIn"), args.get("checkIn"))
                    && Objects.equals(criteria.confirmed("checkOut"), args.get("checkOut"))
                    && (scope.hotelId() == null || Objects.equals(scope.hotelId(), evidence.roomHotels().get(scope.roomTypeId())));
        }
        if (id == ToolId.A06) return Objects.equals(criteria.confirmed("cityName"), args.get("query"));
        if (id == ToolId.A09) return args.path("hotelIds").size() == 1 && Objects.equals(scope.hotelId(), args.path("hotelIds").get(0).asText());
        return true;
    }
    private Outcome deny(TurnBudget budget, String category) { budget.denied(); return new Outcome(category, null, ToolResult.Status.ERROR); }
}
