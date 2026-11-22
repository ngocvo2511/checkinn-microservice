package com.example.travelsocialservice.service;

import com.example.travelsocialservice.domain.Comment;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.ModerationAction;
import com.example.travelsocialservice.domain.ModerationAudit;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.Report;
import com.example.travelsocialservice.domain.ReportReason;
import com.example.travelsocialservice.domain.ReportStatus;
import com.example.travelsocialservice.domain.TargetType;
import com.example.travelsocialservice.repository.CommentRepository;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.ReportRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InteractionDtos.CloseReportsRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.ModerationResult;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportGroupView;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Administrator-only actions. Every state change writes an audit record in the same transaction. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    static final int QUEUE_SCAN_LIMIT = 500;
    static final int EXCERPT_LENGTH = 280;

    private record TargetKey(TargetType type, UUID id) {
    }

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationAuditRepository auditRepository;
    private final SocialMetrics metrics;
    private final Clock clock;

    /** Open reports grouped by target, oldest-reported target first. */
    @Transactional(readOnly = true)
    public List<ReportGroupView> openReportQueue(Integer limit) {
        int maxGroups = Paging.limit(limit);
        Map<TargetKey, List<Report>> byTarget = new LinkedHashMap<>();
        for (Report report : reportRepository.findByStatusOrderByCreatedAtAscIdAsc(ReportStatus.OPEN, PageRequest.of(0, QUEUE_SCAN_LIMIT))) {
            byTarget.computeIfAbsent(new TargetKey(report.getTargetType(), report.getTargetId()), key -> new ArrayList<>()).add(report);
        }
        return byTarget.entrySet().stream().limit(maxGroups)
                .map(entry -> toGroup(entry.getKey(), entry.getValue())).toList();
    }

    @Transactional
    public int closeReports(Actor admin, CloseReportsRequest request) {
        if (request == null || request.targetType() == null || request.targetId() == null
                || request.outcome() == null || request.outcome() == ReportStatus.OPEN) {
            throw ApiException.badRequest("targetType, targetId and an outcome of RESOLVED or DISMISSED are required");
        }
        List<Report> open = reportRepository.findByTargetTypeAndTargetIdAndStatus(
                request.targetType(), request.targetId(), ReportStatus.OPEN);
        if (open.isEmpty()) {
            throw ApiException.notFound();
        }
        Instant now = Paging.now(clock);
        open.forEach(report -> {
            report.setStatus(request.outcome());
            report.setClosedAt(now);
            report.setClosedBy(admin.id());
        });
        ModerationAction action = request.outcome() == ReportStatus.RESOLVED
                ? ModerationAction.RESOLVE_REPORTS : ModerationAction.DISMISS_REPORTS;
        audit(admin, action, request.targetType(), request.targetId(), request.reason(), now);
        return open.size();
    }

    @Transactional
    public ModerationResult hidePost(Actor admin, UUID postId, String reason) {
        return changePost(admin, postId, ContentStatus.PUBLISHED, ContentStatus.HIDDEN, ModerationAction.HIDE, reason);
    }

    @Transactional
    public ModerationResult restorePost(Actor admin, UUID postId, String reason) {
        return changePost(admin, postId, ContentStatus.HIDDEN, ContentStatus.PUBLISHED, ModerationAction.RESTORE, reason);
    }

    @Transactional
    public ModerationResult hideComment(Actor admin, UUID commentId, String reason) {
        return changeComment(admin, commentId, ContentStatus.PUBLISHED, ContentStatus.HIDDEN, ModerationAction.HIDE, reason);
    }

    @Transactional
    public ModerationResult restoreComment(Actor admin, UUID commentId, String reason) {
        return changeComment(admin, commentId, ContentStatus.HIDDEN, ContentStatus.PUBLISHED, ModerationAction.RESTORE, reason);
    }

    private ModerationResult changePost(Actor admin, UUID postId, ContentStatus from, ContentStatus to,
                                        ModerationAction action, String reason) {
        Post post = postRepository.findById(postId).orElseThrow(ApiException::notFound);
        requireStatus(post.getStatus(), from);
        Instant now = Paging.now(clock);
        post.changeStatus(to, now);
        audit(admin, action, TargetType.POST, postId, reason, now);
        return new ModerationResult(TargetType.POST, postId, to);
    }

    private ModerationResult changeComment(Actor admin, UUID commentId, ContentStatus from, ContentStatus to,
                                           ModerationAction action, String reason) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(ApiException::notFound);
        requireStatus(comment.getStatus(), from);
        Instant now = Paging.now(clock);
        comment.changeStatus(to, now);
        audit(admin, action, TargetType.COMMENT, commentId, reason, now);
        return new ModerationResult(TargetType.COMMENT, commentId, to);
    }

    private static void requireStatus(ContentStatus actual, ContentStatus required) {
        if (actual == ContentStatus.DELETED) {
            throw ApiException.notFound();
        }
        if (actual != required) {
            throw ApiException.conflict("Content is " + actual + ", expected " + required);
        }
    }

    private void audit(Actor admin, ModerationAction action, TargetType targetType, UUID targetId, String reason, Instant at) {
        String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
        auditRepository.save(new ModerationAudit(admin.id(), action, targetType, targetId, trimmed, at));
        metrics.moderationAction(action, targetType);
        log.info("[SOCIAL_MODERATION] action={} target={}:{} actor={}", action, targetType, targetId, admin.id());
    }

    private ReportGroupView toGroup(TargetKey key, List<Report> reports) {
        UUID postId = null;
        UUID authorId = null;
        ContentStatus status = null;
        String body = null;
        if (key.type() == TargetType.POST) {
            Post post = postRepository.findById(key.id()).orElse(null);
            if (post != null) {
                postId = post.getId();
                authorId = post.getAuthorId();
                status = post.getStatus();
                body = post.getBody();
            }
        } else {
            Comment comment = commentRepository.findById(key.id()).orElse(null);
            if (comment != null) {
                postId = comment.getPostId();
                authorId = comment.getAuthorId();
                status = comment.getStatus();
                body = comment.getBody();
            }
        }
        Map<ReportReason, Long> reasons = reports.stream()
                .collect(Collectors.groupingBy(Report::getReason, Collectors.counting()));
        List<ReportItem> items = reports.stream().map(report -> new ReportItem(report.getId(), report.getReporterId(),
                report.getReason(), report.getNote(), report.getCreatedAt())).toList();
        String excerpt = body == null ? null : body.length() <= EXCERPT_LENGTH ? body : body.substring(0, EXCERPT_LENGTH) + "…";
        return new ReportGroupView(key.type(), key.id(), postId, authorId, status, excerpt, reports.size(), reasons,
                reports.get(0).getCreatedAt(), items);
    }
}
