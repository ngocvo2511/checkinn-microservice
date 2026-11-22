package com.example.travelsocialservice.service;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.Comment;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.ModerationAction;
import com.example.travelsocialservice.domain.ModerationAudit;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.TargetType;
import com.example.travelsocialservice.repository.CommentRepository;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentView;
import com.example.travelsocialservice.web.dto.PageResponse;
import com.example.travelsocialservice.web.dto.PostDtos.AuthorView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final ModerationAuditRepository auditRepository;
    private final PostService postService;
    private final PostAssembler assembler;
    private final SocialProperties properties;
    private final Clock clock;

    /** Oldest first. Comments of a hidden or deleted post are not returned to non-administrators. */
    @Transactional(readOnly = true)
    public PageResponse<CommentView> list(UUID postId, String cursor, Integer limit, Actor viewer) {
        postService.requireVisible(viewer, postId);
        int pageSize = Paging.limit(limit);
        KeysetCursor after = KeysetCursor.decode(cursor);
        PageRequest fetch = PageRequest.of(0, pageSize + 1);
        List<Comment> rows = after == null
                ? commentRepository.findPublishedByPost(postId, fetch)
                : commentRepository.findPublishedByPostAfter(postId, after.createdAt(), after.id(), fetch);
        return Paging.page(rows, pageSize, this::toViews,
                comment -> new KeysetCursor(comment.getCreatedAt(), comment.getId()));
    }

    @Transactional
    public CommentView create(Actor actor, UUID postId, CommentRequest request) {
        String body = request == null || request.body() == null ? "" : request.body().strip();
        if (body.isEmpty() || body.length() > properties.limits().commentBodyMax()) {
            throw ApiException.invalid("body", "body must be 1-" + properties.limits().commentBodyMax() + " characters");
        }
        postService.requirePublished(postId);

        Instant now = Paging.now(clock);
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setPostId(postId);
        comment.setAuthorId(actor.id());
        comment.setBody(body);
        comment.setStatus(ContentStatus.PUBLISHED);
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comment.setStatusChangedAt(now);
        commentRepository.save(comment);
        return toViews(List.of(comment)).get(0);
    }

    @Transactional
    public void delete(Actor actor, UUID commentId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(ApiException::notFound);
        if (comment.getStatus() == ContentStatus.DELETED) {
            throw ApiException.notFound();
        }
        boolean owner = actor.is(comment.getAuthorId());
        if (!owner && !actor.admin()) {
            throw isPubliclyVisible(comment) ? ApiException.forbidden() : ApiException.notFound();
        }
        Instant now = Paging.now(clock);
        comment.changeStatus(ContentStatus.DELETED, now);
        if (!owner) {
            auditRepository.save(new ModerationAudit(actor.id(), ModerationAction.DELETE, TargetType.COMMENT, commentId, null, now));
            log.info("[SOCIAL_MODERATION] action=DELETE target=COMMENT:{} actor={}", commentId, actor.id());
        }
    }

    public boolean isPubliclyVisible(Comment comment) {
        return comment.isPublished()
                && postRepository.findById(comment.getPostId()).map(Post::isPublished).orElse(false);
    }

    private List<CommentView> toViews(List<Comment> comments) {
        Map<UUID, AuthorView> authors = assembler.authorViews(comments.stream().map(Comment::getAuthorId).toList());
        return comments.stream().map(comment -> new CommentView(comment.getId(), comment.getPostId(),
                authors.get(comment.getAuthorId()), comment.getBody(), comment.getCreatedAt())).toList();
    }
}
