package com.example.aiagent.orchestration;

import com.example.aiagent.tools.ToolError;
import java.util.*;

/** Internal response metadata; never evidence and never downstream free text. */
public record ResponseSemantics(Status status, List<ErrorCode> errors) {
    public enum Status { ANSWER, EMPTY, CLARIFICATION, REFUSED, UNAVAILABLE, PARTIAL, POLICY_STOP }
    public enum ErrorCode { NOT_PUBLIC_OR_MISSING, APPROVAL_REQUIRED, TOOL_TIMEOUT, DEPENDENCY_TIMEOUT,
        DEPENDENCY_UNAVAILABLE, RESPONSE_TOO_LARGE, INVALID_RESPONSE, POLICY_LOOP_STOP, UNVERIFIED_CLAIM,
        SELECTION_REQUIRED, STATE_CONFLICT, CANCELLED, UNAVAILABLE }
    public ResponseSemantics { Objects.requireNonNull(status); errors = List.copyOf(errors); if (errors.size() > 6) throw new IllegalArgumentException("Too many errors"); }
    public static ErrorCode classify(ToolError error) {
        return switch (error.category()) {
            case NOT_FOUND -> ErrorCode.NOT_PUBLIC_OR_MISSING;
            case INVENTORY_APPROVAL_REQUIRED -> ErrorCode.APPROVAL_REQUIRED;
            case BACKEND_TIMEOUT -> Integer.valueOf(504).equals(error.status()) ? ErrorCode.DEPENDENCY_TIMEOUT : ErrorCode.TOOL_TIMEOUT;
            case BACKEND_UNAVAILABLE -> ErrorCode.DEPENDENCY_UNAVAILABLE;
            case OVERSIZED_UPSTREAM_RESPONSE, OUTPUT_TOO_LARGE -> ErrorCode.RESPONSE_TOO_LARGE;
            case INVALID_UPSTREAM_JSON, INVALID_UPSTREAM_RESPONSE -> ErrorCode.INVALID_RESPONSE;
            default -> ErrorCode.UNAVAILABLE;
        };
    }
    static ResponseSemantics from(GroundingGate.Answer answer, List<PolicyExecutor.Outcome> outcomes) {
        var errors = outcomes.stream().map(PolicyExecutor.Outcome::error).filter(Objects::nonNull).distinct().limit(6).toList();
        boolean facts = !answer.facts().isEmpty();
        String category = answer.category();
        if (category.equals("GROUNDING_REJECTED") || category.equals("UNVERIFIED_CLAIM")) return new ResponseSemantics(Status.UNAVAILABLE, List.of(ErrorCode.UNVERIFIED_CLAIM));
        if (category.contains("BUDGET") || category.equals("POLICY_LOOP_STOP")) return new ResponseSemantics(Status.POLICY_STOP, List.of(ErrorCode.POLICY_LOOP_STOP));
        if (!errors.isEmpty()) return new ResponseSemantics(facts ? Status.PARTIAL : Status.UNAVAILABLE, errors);
        if (category.equals("COMPARISON_PARTIAL") && facts) return new ResponseSemantics(Status.PARTIAL, List.of());
        if (facts) {
            boolean empty = answer.facts().stream().allMatch(f -> f.values().path("outcome").asText().equals("EMPTY"));
            return new ResponseSemantics(empty ? Status.EMPTY : Status.ANSWER, List.of());
        }
        if (category.equals("UNSUPPORTED")) return new ResponseSemantics(Status.REFUSED, List.of());
        if (category.equals("SELECTION_REQUIRED")) return new ResponseSemantics(Status.CLARIFICATION, List.of(ErrorCode.SELECTION_REQUIRED));
        if (category.equals("SESSION_BUSY") || category.equals("REVISION_CONFLICT")) return new ResponseSemantics(Status.UNAVAILABLE, List.of(ErrorCode.STATE_CONFLICT));
        if (category.equals("CANCELLED")) return new ResponseSemantics(Status.UNAVAILABLE, List.of(ErrorCode.CANCELLED));
        if (category.startsWith("NEED_") || Set.of("NLU_CLARIFICATION", "INVALID_CRITERIA", "INVALID_NLU").contains(category))
            return new ResponseSemantics(Status.CLARIFICATION, List.of());
        return new ResponseSemantics(Status.UNAVAILABLE, List.of(ErrorCode.UNAVAILABLE));
    }
}
