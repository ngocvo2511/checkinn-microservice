package com.example.aiagent.tools;

/** Intentionally carries no raw upstream cause, URL, payload or exception message. */
final class ToolFailure extends RuntimeException {
    private final ToolError error;
    private final String upstreamRequestId;
    ToolFailure(ToolError.Category category) { this(ToolError.of(category, null, category.name())); }
    ToolFailure(ToolError error) { this(error, null); }
    ToolFailure(ToolError error, String upstreamRequestId) {
        super(error.category().name(), null, false, false); this.error = error; this.upstreamRequestId = upstreamRequestId;
    }
    ToolError error() { return error; }
    String upstreamRequestId() { return upstreamRequestId; }
}
