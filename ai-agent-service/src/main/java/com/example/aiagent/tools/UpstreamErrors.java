package com.example.aiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import static com.example.aiagent.tools.ToolError.Category.*;

final class UpstreamErrors {
    private static final Set<String> CODES = Set.of("INVALID_REQUEST", "VALIDATION_ERROR", "NOT_FOUND", "UNAUTHORIZED",
            "FORBIDDEN", "INVENTORY_APPROVAL_REQUIRED", "INVENTORY_NOT_CONFIGURED", "INVENTORY_INCONSISTENT",
            "CONFLICT", "RATE_LIMITED", "INTERNAL_ERROR", "DEPENDENCY_UNAVAILABLE", "DEPENDENCY_TIMEOUT");
    static ToolError map(int status, JsonNode body) {
        String raw = body != null && body.isObject() ? body.path("code").asText() : "";
        String code = CODES.contains(raw) ? raw : "UPSTREAM_FAILURE";
        ToolError.Category category = switch (status) {
            case 400 -> INVALID_ARGUMENTS; case 401 -> UNAUTHORIZED_UPSTREAM; case 403 -> FORBIDDEN_UPSTREAM;
            case 404 -> NOT_FOUND; case 429 -> RATE_LIMITED; case 504 -> BACKEND_TIMEOUT;
            case 409 -> switch (code) {
                case "INVENTORY_APPROVAL_REQUIRED" -> INVENTORY_APPROVAL_REQUIRED;
                case "INVENTORY_NOT_CONFIGURED", "INVENTORY_INCONSISTENT" -> INVENTORY_UNKNOWN;
                default -> STATE_CONFLICT;
            };
            case 500, 502, 503 -> BACKEND_UNAVAILABLE;
            default -> INVALID_UPSTREAM_RESPONSE;
        };
        return ToolError.of(category, status, code);
    }
}
