package com.example.aiagent.model;

/** Per-run bounded scalars only. No request, response, key or arbitrary provider strings retained. */
final class GeminiSmokeDiagnostics {
    enum Stage { PRECHECK, WIRE, TRANSPORT, RAW_JSON, RAW_ENVELOPE, RAW_PARTS, RAW_USAGE, SDK_DESERIALIZATION, ADAPTER_RESULT, GROUNDING, COMPLETE }
    private Stage stage = Stage.PRECHECK, firstFailure;
    private String errorCode = "NONE", rootType = "NOT_OBSERVED";
    private Integer partCount;
    private boolean contentPresent, signaturePresent;
    void stage(Stage stage) { this.stage = stage; }
    void fail(String code) { if (firstFailure == null) { firstFailure = stage; errorCode = code; } }
    int attempts;
    private String http = "NOT_OBSERVED", accepted = "NOT_VERIFIED", transport = "NOT_VERIFIED";
    private Integer prompt, candidate, total;
    private String finish = "NOT_OBSERVED";
    void httpStatus(int status) {
        if (status >= 100 && status <= 599) {
            http = (status / 100) + "xx"; accepted = status == 200 ? "true" : "false";
            transport = "LIVE_TRANSPORT_OBSERVED";
        }
    }
    void response(String raw) {
        try {
            if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > GeminiHttpTransport.MAX_RESPONSE_BYTES) return;
            var root = GeminiLlmProviderAdapter.JSON.readTree(raw);
            rootType = root == null ? "NULL" : root.getNodeType().name();
            var content = root == null ? null : root.path("candidates").path(0).get("content");
            contentPresent = content != null && content.isObject();
            var parts = contentPresent ? content.path("parts") : null;
            if (parts != null && parts.isArray()) {
                partCount = parts.size();
                for (var part : parts) signaturePresent |= part.has("thoughtSignature");
            }
            var usage = root.path("usageMetadata");
            if (valid(usage.path("promptTokenCount")) && valid(usage.path("candidatesTokenCount")) && valid(usage.path("totalTokenCount"))) {
                prompt = usage.path("promptTokenCount").intValue(); candidate = usage.path("candidatesTokenCount").intValue();
                total = usage.path("totalTokenCount").intValue();
            }
            var reason = root.path("candidates").path(0).path("finishReason").asText();
            if (java.util.Set.of("STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "OTHER", "MALFORMED_FUNCTION_CALL").contains(reason)) finish = reason;
            else finish = "UNSUPPORTED_OR_MISSING";
        } catch (Exception ignored) { /* Unavailable remains unknown; never retain body or parser error. */ }
    }
    private boolean valid(com.fasterxml.jackson.databind.JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0;
    }
    Result result(String category, String grounding, long started, GeminiBudget budget) {
        return new Result(GeminiSmokeApproval.MODEL, SyntheticGeminiFixtures.SMOKE, attempts, 0, category, http, accepted,
                grounding, prompt, candidate, total, finish, Math.max(0, (System.nanoTime() - started) / 1_000_000),
                budget.exposure(), transport, partCount, contentPresent, rootType, signaturePresent,
                firstFailure == null ? "NONE" : firstFailure.name(), errorCode,
                "GROUNDING_REJECTED".equals(errorCode) ? "GROUNDING_REJECTED" : "NOT_OBSERVED");
    }
    record Result(String model, String fixture, int attempts, int retries, String category, String httpStatusClass,
                  String providerAccepted, String grounding, Integer promptTokens, Integer candidateTokens, Integer totalTokens,
                  String finishReason, long latencyMillis, java.math.BigDecimal localReservationUsd, String liveTransport,
                  Integer candidatePartCount, boolean contentPresent, String jsonRootType, boolean signaturePresent,
                  String firstFailingStage, String parserErrorCode, String schemaErrorCategory) { }
}
