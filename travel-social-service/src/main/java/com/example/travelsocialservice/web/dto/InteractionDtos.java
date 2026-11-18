package com.example.travelsocialservice.web.dto;

import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.ReportReason;
import com.example.travelsocialservice.domain.ReportStatus;
import com.example.travelsocialservice.domain.TargetType;
import com.example.travelsocialservice.web.dto.PostDtos.AuthorView;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InteractionDtos {

    private InteractionDtos() {
    }

    public record CommentRequest(String body) {
    }

    public record CommentView(UUID id, UUID postId, AuthorView author, String body, Instant createdAt) {
    }

    public record ReportRequest(TargetType targetType, UUID targetId, ReportReason reason, String note) {
    }

    public record ReportReceipt(boolean received) {
    }

    public record MediaUploadView(UUID id, String url) {
    }

    public record ModerationRequest(String reason) {
    }

    public record CloseReportsRequest(TargetType targetType, UUID targetId, ReportStatus outcome, String reason) {
    }

    public record ReportItem(UUID id, UUID reporterId, ReportReason reason, String note, Instant createdAt) {
    }

    /** Admin-only: open reports for one post or comment. Reporter ids never appear in public responses. */
    public record ReportGroupView(
            TargetType targetType,
            UUID targetId,
            UUID postId,
            UUID targetAuthorId,
            ContentStatus targetStatus,
            String excerpt,
            long reportCount,
            Map<ReportReason, Long> reasons,
            Instant firstReportedAt,
            List<ReportItem> reports
    ) {
    }

    public record ModerationResult(TargetType targetType, UUID targetId, ContentStatus status) {
    }
}
