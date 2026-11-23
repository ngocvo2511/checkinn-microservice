package com.example.aiagent.tools;

public record ToolError(Category category, Integer status, String code, String safeMessage, boolean retryable) {
    public enum Category {
        INVALID_ARGUMENTS, MISSING_REQUIRED_CRITERIA, INVALID_DATE_OCCUPANCY, UNSUPPORTED_TOOL,
        POLICY_DENIED, NOT_FOUND, INVENTORY_APPROVAL_REQUIRED, INVENTORY_UNKNOWN, STATE_CONFLICT,
        UNAUTHORIZED_UPSTREAM, FORBIDDEN_UPSTREAM, RATE_LIMITED, BACKEND_UNAVAILABLE, BACKEND_TIMEOUT,
        INVALID_UPSTREAM_JSON, INVALID_UPSTREAM_RESPONSE, OVERSIZED_UPSTREAM_RESPONSE,
        OUTPUT_TOO_LARGE, CANCELLED, INTERNAL_ADAPTER_FAILURE
    }
    public static ToolError of(Category category, Integer status, String code) {
        // Neither upstream messages nor exceptions are model-facing.
        String message = switch (category) {
            case INVALID_ARGUMENTS, INVALID_DATE_OCCUPANCY -> "Tiêu chí không hợp lệ; cần kiểm tra lại đầu vào.";
            case MISSING_REQUIRED_CRITERIA -> "Chưa đủ tiêu chí đã xác nhận để kiểm tra.";
            case NOT_FOUND -> "Không tìm thấy dữ liệu công khai phù hợp.";
            case INVENTORY_APPROVAL_REQUIRED -> "Tồn phòng cần được duyệt trước khi nhận đặt mới.";
            case CANCELLED -> "Đã dừng lần kiểm tra này.";
            case POLICY_DENIED, UNSUPPORTED_TOOL -> "Thao tác không được phép trong công cụ READ.";
            default -> "Chưa thể xác minh dữ liệu; vui lòng thử lại theo yêu cầu mới.";
        };
        return new ToolError(category, status, code, message, false);
    }
}
