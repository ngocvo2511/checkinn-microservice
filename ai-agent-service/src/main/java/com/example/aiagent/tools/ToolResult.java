package com.example.aiagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ToolResult(ToolId toolId, String tool, String schemaVersion, Long criteriaRevision,
                         Status status, JsonNode data, Evidence evidence, ToolError error) {
    public enum Status { OK, EMPTY, ERROR }
    public record Evidence(String sourceRoute, String requestId, String upstreamRequestId,
                           Instant retrievedAt, Instant checkedAt, String checkedAtSource, boolean truncated) {}
}
