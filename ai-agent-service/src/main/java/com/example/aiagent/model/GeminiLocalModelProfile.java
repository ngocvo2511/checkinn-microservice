package com.example.aiagent.model;

/** Typed local-only allowlist. Quota UNKNOWN disables dispatch, even if public pricing lists a Free Tier. */
enum GeminiLocalModelProfile {
    FLASH_35_LITE("gemini-3.5-flash-lite", 15, 250000, 500, "DEVELOPER_PRIMARY_QUOTA_SNAPSHOT"),
    FLASH_31_LITE("gemini-3.1-flash-lite", 15, 250000, 500, "DEVELOPER_ACTIVE_FREE_TIER_QUOTA_SNAPSHOT"),
    FLASH_25_LITE("gemini-2.5-flash-lite", 10, 250000, 20, "DEVELOPER_ACTIVE_FREE_TIER_QUOTA_SNAPSHOT_EXISTING_ACCESS"),
    FLASH_25("gemini-2.5-flash", 5, 250000, 20, "DEVELOPER_ACTIVE_FREE_TIER_QUOTA_SNAPSHOT_DEPRECATED_DECEMBER_1");
    final String id, eligibilityReason;
    final int rpm, tpm, rpd;
    GeminiLocalModelProfile(String id, int rpm, int tpm, int rpd, String reason) {
        this.id = id; this.rpm = rpm; this.tpm = tpm; this.rpd = rpd; eligibilityReason = reason;
    }
    boolean eligible() { return rpm > 0 && tpm >= GeminiExtractionProtocol.INPUT_CAP && rpd > 0; }
    String endpoint() { return "https://generativelanguage.googleapis.com/v1beta/models/" + id + ":generateContent"; }
    String wireVersion() { return ordinal() < 2 ? "original-sdk-wire-v1" : "gemini-25-bounded-wire-v2"; }
    /** Serving-only simplification for 2.5 state explosion. Original strict local schema is unchanged. */
    String wireSchema(String canonical) {
        if (ordinal() < 2) return canonical;
        try {
            var tree = GeminiLlmProviderAdapter.JSON.readTree(canonical); simplify(tree); return tree.toString();
        } catch (Exception invalid) { throw GeminiExtractionProtocol.denied(); }
    }
    private static void simplify(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isObject()) {
            // JSON Schema const is represented by the documented single-valued enum without changing meaning.
            if (node.has("const")) {
                var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                var value = object.remove("const"); object.putArray("enum").add(value);
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove(java.util.List.of("description", "minimum", "maximum", "minItems", "maxItems", "minLength", "maxLength", "uniqueItems", "format", "pattern"));
            node.elements().forEachRemaining(GeminiLocalModelProfile::simplify);
        } else if (node.isArray()) node.elements().forEachRemaining(GeminiLocalModelProfile::simplify);
    }
}
