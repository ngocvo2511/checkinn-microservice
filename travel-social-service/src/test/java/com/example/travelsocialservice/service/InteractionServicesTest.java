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
import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.ReportRepository;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.InteractionDtos.CloseReportsRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentView;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportGroupView;
import com.example.travelsocialservice.web.dto.InteractionDtos.ReportRequest;
import com.example.travelsocialservice.web.dto.PostDtos.AuthorView;
import com.example.travelsocialservice.web.dto.PostDtos.LikeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InteractionServicesTest {

    private PostRepository posts;
    private CommentRepository comments;
    private ReportRepository reports;
    private ModerationAuditRepository audits;
    private InteractionQueries interactions;
    private PostAssembler assembler;

    private PostService postService;
    private LikeService likeService;
    private CommentService commentService;
    private ReportService reportService;
    private ModerationService moderationService;

    private final Actor author = user();
    private final Actor reader = user();
    private final Actor admin = admin();

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        comments = mock(CommentRepository.class);
        reports = mock(ReportRepository.class);
        audits = mock(ModerationAuditRepository.class);
        interactions = mock(InteractionQueries.class);
        assembler = mock(PostAssembler.class);
        when(assembler.authorViews(any())).thenAnswer(call -> ((Collection<UUID>) call.getArgument(0)).stream().distinct()
                .collect(Collectors.toMap(id -> id, id -> new AuthorView(id, "An N.", null))));

        postService = new PostService(posts, mock(PostMediaRepository.class), audits, mock(PlaceClient.class), PROPERTIES, CLOCK);
        likeService = new LikeService(postService, interactions, CLOCK);
        commentService = new CommentService(comments, posts, audits, postService, assembler, PROPERTIES, CLOCK);
        reportService = new ReportService(reports, comments, postService, commentService, PROPERTIES, metrics(), CLOCK);
        moderationService = new ModerationService(reports, posts, comments, audits, metrics(), CLOCK);
    }

    private Post stored(ContentStatus status) {
        Post post = post(author.id(), status);
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    private Comment storedComment(Post post, UUID commentAuthor, ContentStatus status) {
        Comment comment = comment(post.getId(), commentAuthor, status);
        when(comments.findById(comment.getId())).thenReturn(Optional.of(comment));
        return comment;
    }

    @Test
    void likeAndUnlikeAreDelegatedOncePerRequestAndReturnTheStoredCount() {
        Post post = stored(ContentStatus.PUBLISHED);
        when(interactions.likeCounts(List.of(post.getId()))).thenReturn(Map.of(post.getId(), 3L));

        LikeView liked = likeService.like(reader, post.getId());
        LikeView unliked = likeService.unlike(reader, post.getId());

        assertThat(liked).isEqualTo(new LikeView(3, true));
        assertThat(unliked).isEqualTo(new LikeView(3, false));
        verify(interactions).like(post.getId(), reader.id(), NOW);
        verify(interactions).unlike(post.getId(), reader.id());
    }

    @Test
    void interactionsWithHiddenOrDeletedPostsLookLikeMissingPosts() {
        for (ContentStatus status : List.of(ContentStatus.HIDDEN, ContentStatus.DELETED)) {
            Post post = stored(status);
            assertApiError(() -> likeService.like(reader, post.getId()), HttpStatus.NOT_FOUND);
            assertApiError(() -> likeService.unlike(reader, post.getId()), HttpStatus.NOT_FOUND);
            assertApiError(() -> commentService.create(reader, post.getId(), new CommentRequest("hi")), HttpStatus.NOT_FOUND);
            assertApiError(() -> commentService.list(post.getId(), null, null, reader), HttpStatus.NOT_FOUND);
            assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.POST, post.getId(), ReportReason.SPAM, null)), HttpStatus.NOT_FOUND);
        }
        verify(interactions, never()).like(any(), any(), any());
        verify(comments, never()).save(any());
        verify(reports, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void commentIsStoredForTheActorWithTrimmedBody() {
        Post post = stored(ContentStatus.PUBLISHED);

        CommentView view = commentService.create(reader, post.getId(), new CommentRequest("  Thanks for sharing "));

        ArgumentCaptor<Comment> saved = ArgumentCaptor.forClass(Comment.class);
        verify(comments).save(saved.capture());
        assertThat(saved.getValue().getAuthorId()).isEqualTo(reader.id());
        assertThat(saved.getValue().getBody()).isEqualTo("Thanks for sharing");
        assertThat(saved.getValue().getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(view.author().id()).isEqualTo(reader.id());
        assertThat(view.postId()).isEqualTo(post.getId());
    }

    @Test
    void emptyOrOverlongCommentsAreRejected() {
        Post post = stored(ContentStatus.PUBLISHED);
        for (CommentRequest request : new CommentRequest[]{null, new CommentRequest(null), new CommentRequest("  "), new CommentRequest("x".repeat(1001))}) {
            assertApiError(() -> commentService.create(reader, post.getId(), request), HttpStatus.BAD_REQUEST);
        }
        verify(comments, never()).save(any());
    }

    @Test
    void commentDeletionIsLimitedToItsAuthorAndAdmins() {
        Post post = stored(ContentStatus.PUBLISHED);
        Comment own = storedComment(post, reader.id(), ContentStatus.PUBLISHED);
        Comment byAdmin = storedComment(post, reader.id(), ContentStatus.PUBLISHED);

        assertApiError(() -> commentService.delete(author, own.getId()), HttpStatus.FORBIDDEN);
        assertThat(own.getStatus()).isEqualTo(ContentStatus.PUBLISHED);

        commentService.delete(reader, own.getId());
        assertThat(own.getStatus()).isEqualTo(ContentStatus.DELETED);
        verifyNoInteractions(audits);
        assertApiError(() -> commentService.delete(reader, own.getId()), HttpStatus.NOT_FOUND);

        commentService.delete(admin, byAdmin.getId());
        assertThat(byAdmin.getStatus()).isEqualTo(ContentStatus.DELETED);
        verify(audits).save(any(ModerationAudit.class));
    }

    @Test
    void reportIsRecordedOnceAndRepeatsStillSucceed() {
        Post post = stored(ContentStatus.PUBLISHED);
        when(reports.insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(1, 0);
        ReportRequest request = new ReportRequest(TargetType.POST, post.getId(), ReportReason.SPAM, "  ");

        reportService.report(reader, request);
        reportService.report(reader, request);

        verify(reports, times(2)).insertIfAbsent(any(), eq(reader.id()), eq("POST"), eq(post.getId()), eq("SPAM"), eq(null), eq(NOW));
    }

    @Test
    void reportsValidateReasonNoteAndOwnership() {
        Post post = stored(ContentStatus.PUBLISHED);
        Comment ownComment = storedComment(post, reader.id(), ContentStatus.PUBLISHED);

        assertApiError(() -> reportService.report(reader, null), HttpStatus.BAD_REQUEST);
        assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.POST, post.getId(), null, null)), HttpStatus.BAD_REQUEST);
        assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.POST, post.getId(), ReportReason.OTHER, " ")), HttpStatus.BAD_REQUEST);
        assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.POST, post.getId(), ReportReason.SPAM, "x".repeat(501))), HttpStatus.BAD_REQUEST);
        assertApiError(() -> reportService.report(author, new ReportRequest(TargetType.POST, post.getId(), ReportReason.SPAM, null)), HttpStatus.BAD_REQUEST);
        assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.COMMENT, ownComment.getId(), ReportReason.SPAM, null)), HttpStatus.BAD_REQUEST);
        verify(reports, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aCommentOnAHiddenPostCannotBeReported() {
        Post hidden = stored(ContentStatus.HIDDEN);
        Comment comment = storedComment(hidden, author.id(), ContentStatus.PUBLISHED);

        assertApiError(() -> reportService.report(reader, new ReportRequest(TargetType.COMMENT, comment.getId(), ReportReason.HARASSMENT, null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void hideAndRestoreFollowTheStatusModelAndAreAudited() {
        Post post = stored(ContentStatus.PUBLISHED);

        assertThat(moderationService.hidePost(admin, post.getId(), " spam ").status()).isEqualTo(ContentStatus.HIDDEN);
        assertThat(post.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        assertApiError(() -> moderationService.hidePost(admin, post.getId(), null), HttpStatus.CONFLICT);

        assertThat(moderationService.restorePost(admin, post.getId(), null).status()).isEqualTo(ContentStatus.PUBLISHED);
        assertApiError(() -> moderationService.restorePost(admin, post.getId(), null), HttpStatus.CONFLICT);

        ArgumentCaptor<ModerationAudit> audit = ArgumentCaptor.forClass(ModerationAudit.class);
        verify(audits, times(2)).save(audit.capture());
        assertThat(audit.getAllValues()).extracting(ModerationAudit::getAction)
                .containsExactly(ModerationAction.HIDE, ModerationAction.RESTORE);
        assertThat(audit.getAllValues().get(0).getReason()).isEqualTo("spam");
        assertThat(audit.getAllValues().get(0).getActorId()).isEqualTo(admin.id());
    }

    @Test
    void deletedContentCannotBeHiddenOrRestored() {
        Post deleted = stored(ContentStatus.DELETED);
        Comment deletedComment = storedComment(stored(ContentStatus.PUBLISHED), reader.id(), ContentStatus.DELETED);

        assertApiError(() -> moderationService.hidePost(admin, deleted.getId(), null), HttpStatus.NOT_FOUND);
        assertApiError(() -> moderationService.restorePost(admin, deleted.getId(), null), HttpStatus.NOT_FOUND);
        assertApiError(() -> moderationService.hideComment(admin, deletedComment.getId(), null), HttpStatus.NOT_FOUND);
        verifyNoInteractions(audits);
    }

    @Test
    void commentsCanBeHiddenAndRestoredIndependentlyOfTheirPost() {
        Comment comment = storedComment(stored(ContentStatus.PUBLISHED), reader.id(), ContentStatus.PUBLISHED);

        moderationService.hideComment(admin, comment.getId(), null);
        assertThat(comment.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        moderationService.restoreComment(admin, comment.getId(), null);
        assertThat(comment.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        verify(audits, times(2)).save(any(ModerationAudit.class));
    }

    private Report openReport(TargetType type, UUID targetId, ReportReason reason, long secondsAgo) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporterId(UUID.randomUUID());
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(NOW.minusSeconds(secondsAgo));
        return report;
    }

    @Test
    void queueGroupsOpenReportsByTargetOldestFirst() {
        Post post = stored(ContentStatus.PUBLISHED);
        Comment comment = storedComment(post, reader.id(), ContentStatus.PUBLISHED);
        Report first = openReport(TargetType.POST, post.getId(), ReportReason.SPAM, 300);
        Report second = openReport(TargetType.COMMENT, comment.getId(), ReportReason.HARASSMENT, 200);
        Report third = openReport(TargetType.POST, post.getId(), ReportReason.SPAM, 100);
        when(reports.findByStatusOrderByCreatedAtAscIdAsc(eq(ReportStatus.OPEN), any())).thenReturn(List.of(first, second, third));

        List<ReportGroupView> queue = moderationService.openReportQueue(null);

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).targetId()).isEqualTo(post.getId());
        assertThat(queue.get(0).reportCount()).isEqualTo(2);
        assertThat(queue.get(0).reasons()).containsEntry(ReportReason.SPAM, 2L);
        assertThat(queue.get(0).targetAuthorId()).isEqualTo(author.id());
        assertThat(queue.get(1).targetType()).isEqualTo(TargetType.COMMENT);
        assertThat(queue.get(1).postId()).isEqualTo(post.getId());
        assertThat(moderationService.openReportQueue(1)).hasSize(1);
    }

    @Test
    void closingReportsMarksEveryOpenReportOnTheTargetAndAuditsOnce() {
        UUID targetId = UUID.randomUUID();
        Report first = openReport(TargetType.POST, targetId, ReportReason.SPAM, 300);
        Report second = openReport(TargetType.POST, targetId, ReportReason.MISLEADING, 100);
        when(reports.findByTargetTypeAndTargetIdAndStatus(TargetType.POST, targetId, ReportStatus.OPEN)).thenReturn(List.of(first, second));

        int closed = moderationService.closeReports(admin, new CloseReportsRequest(TargetType.POST, targetId, ReportStatus.DISMISSED, null));

        assertThat(closed).isEqualTo(2);
        assertThat(List.of(first, second)).allSatisfy(report -> {
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DISMISSED);
            assertThat(report.getClosedBy()).isEqualTo(admin.id());
            assertThat(report.getClosedAt()).isEqualTo(NOW);
        });
        ArgumentCaptor<ModerationAudit> audit = ArgumentCaptor.forClass(ModerationAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(ModerationAction.DISMISS_REPORTS);
    }

    @Test
    void closingReportsRequiresAValidOutcomeAndOpenReports() {
        UUID targetId = UUID.randomUUID();
        when(reports.findByTargetTypeAndTargetIdAndStatus(any(), any(), any())).thenReturn(List.of());

        assertApiError(() -> moderationService.closeReports(admin, null), HttpStatus.BAD_REQUEST);
        assertApiError(() -> moderationService.closeReports(admin, new CloseReportsRequest(TargetType.POST, targetId, ReportStatus.OPEN, null)), HttpStatus.BAD_REQUEST);
        assertApiError(() -> moderationService.closeReports(admin, new CloseReportsRequest(TargetType.POST, targetId, ReportStatus.RESOLVED, null)), HttpStatus.NOT_FOUND);
        verifyNoInteractions(audits);
    }
}
