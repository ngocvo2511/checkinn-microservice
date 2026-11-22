package com.example.travelsocialservice.service;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.Comment;
import com.example.travelsocialservice.domain.ReportReason;
import com.example.travelsocialservice.domain.TargetType;
import com.example.travelsocialservice.repository.CommentRepository;
import com.example.travelsocialservice.repository.ReportRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final SocialProperties properties;
    private final SocialMetrics metrics;
    private final Clock clock;

    /** A repeat report succeeds without creating a row, so the caller cannot tell it from the first. */
    @Transactional
    public void report(Actor actor, ReportRequest request) {
        if (request == null || request.targetType() == null || request.targetId() == null || request.reason() == null) {
            throw ApiException.badRequest("targetType, targetId and reason are required");
        }
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (note != null && note.length() > properties.limits().reportNoteMax()) {
            throw ApiException.invalid("note", "note must be at most " + properties.limits().reportNoteMax() + " characters");
        }
        if (request.reason() == ReportReason.OTHER && note == null) {
            throw ApiException.invalid("note", "A note is required when the reason is OTHER");
        }
        if (actor.is(publishedTargetAuthor(request.targetType(), request.targetId()))) {
            throw ApiException.badRequest("You cannot report your own content");
        }

        int inserted = reportRepository.insertIfAbsent(UUID.randomUUID(), actor.id(), request.targetType().name(),
                request.targetId(), request.reason().name(), note, Paging.now(clock));
        if (inserted > 0) {
            metrics.reportCreated(request.targetType(), request.reason());
            log.info("[SOCIAL_REPORT] target={}:{} reason={}", request.targetType(), request.targetId(), request.reason());
        }
    }

    private UUID publishedTargetAuthor(TargetType targetType, UUID targetId) {
        if (targetType == TargetType.POST) {
            return postService.requirePublished(targetId).getAuthorId();
        }
        Comment comment = commentRepository.findById(targetId).orElseThrow(ApiException::notFound);
        if (!commentService.isPubliclyVisible(comment)) {
            throw ApiException.notFound();
        }
        return comment.getAuthorId();
    }
}
