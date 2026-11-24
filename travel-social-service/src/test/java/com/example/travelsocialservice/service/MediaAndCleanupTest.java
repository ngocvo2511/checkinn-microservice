package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.MediaStorage;
import com.example.travelsocialservice.client.MediaStorage.MediaStorageException;
import com.example.travelsocialservice.client.MediaStorage.StoredObject;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.CommentRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.repository.ReportRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.InteractionDtos.MediaUploadView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediaAndCleanupTest {

    private PostMediaRepository mediaRepository;
    private MediaStorage storage;
    private MediaService mediaService;
    private final Actor uploader = user();

    @BeforeEach
    void setUp() {
        mediaRepository = mock(PostMediaRepository.class);
        storage = mock(MediaStorage.class);
        when(storage.upload(anyString(), anyString(), any()))
                .thenReturn(new StoredObject("https://storage.invalid/social/a.jpg", "social/a_photo.jpg"));
        mediaService = new MediaService(mediaRepository, storage, PROPERTIES, metrics(), CLOCK);
    }

    @Test
    void uploadStoresAPendingRecordOwnedByTheActorUsingTheDetectedType() {
        MediaUploadView view = mediaService.upload(uploader, jpeg());

        verify(storage).upload("photo.jpg", "image/jpeg", jpeg());
        ArgumentCaptor<PostMedia> saved = ArgumentCaptor.forClass(PostMedia.class);
        verify(mediaRepository).save(saved.capture());
        assertThat(saved.getValue().getUploaderId()).isEqualTo(uploader.id());
        assertThat(saved.getValue().getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(saved.getValue().getPostId()).isNull();
        assertThat(saved.getValue().getObjectName()).isEqualTo("social/a_photo.jpg");
        assertThat(saved.getValue().getPendingSince()).isEqualTo(NOW);
        assertThat(view.id()).isEqualTo(saved.getValue().getId());
        assertThat(view.url()).isEqualTo("https://storage.invalid/social/a.jpg");
    }

    @Test
    void uploadRejectsEmptyOversizedAndNonImageFilesBeforeTouchingStorage() {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(jpeg(), 0, oversized, 0, 4);
        assertApiError(() -> mediaService.upload(uploader, new byte[0]), HttpStatus.BAD_REQUEST);
        assertApiError(() -> mediaService.upload(uploader, null), HttpStatus.BAD_REQUEST);
        assertApiError(() -> mediaService.upload(uploader, oversized), HttpStatus.BAD_REQUEST);
        assertApiError(() -> mediaService.upload(uploader, "<html>not a photo</html>".getBytes(StandardCharsets.UTF_8)), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(storage);
    }

    @Test
    void uploadIsRefusedWhenTooManyPhotosAreStillUnattached() {
        when(mediaRepository.countByUploaderIdAndStatus(uploader.id(), MediaStatus.PENDING)).thenReturn(20L);

        assertApiError(() -> mediaService.upload(uploader, jpeg()), HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(storage);
    }

    @Test
    void storageFailureIsReportedAsUnavailableAndSavesNothing() {
        when(storage.upload(anyString(), anyString(), any())).thenThrow(new MediaStorageException("down", null));

        assertApiError(() -> mediaService.upload(uploader, jpeg()), HttpStatus.SERVICE_UNAVAILABLE);
        verify(mediaRepository, never()).save(any());
    }

    @Test
    void aFailedRecordSaveRemovesTheUploadedObject() {
        when(mediaRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> mediaService.upload(uploader, jpeg())).isInstanceOf(IllegalStateException.class);
        verify(storage).delete("social/a_photo.jpg");
    }

    private CleanupJob cleanupJob(PostRepository posts) {
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenReturn(0);
        return new CleanupJob(posts, mediaRepository, mock(CommentRepository.class), mock(ReportRepository.class),
                mock(com.example.travelsocialservice.repository.StayInvitationRepository.class),
                mediaService, PROPERTIES, metrics(), transactions, CLOCK);
    }

    @Test
    void cleanupRemovesReportsWhoseTargetWasPurgedAndCountsEveryKind() {
        PostRepository posts = mock(PostRepository.class);
        CommentRepository comments = mock(CommentRepository.class);
        ReportRepository reports = mock(ReportRepository.class);
        when(comments.deleteByStatusAndStatusChangedAtBefore(eq(ContentStatus.DELETED), any())).thenReturn(3);
        when(reports.deleteClosedBefore(any())).thenReturn(1);
        when(reports.deleteOrphaned()).thenReturn(2);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        com.example.travelsocialservice.repository.StayInvitationRepository invitations =
                mock(com.example.travelsocialservice.repository.StayInvitationRepository.class);
        when(invitations.expireOverdue(NOW)).thenReturn(4);
        when(invitations.deleteClosedBefore(NOW.minus(PROPERTIES.retention().deletedContent()))).thenReturn(5);

        new CleanupJob(posts, mediaRepository, comments, reports, invitations, mediaService, PROPERTIES,
                new SocialMetrics(registry), transactions, CLOCK).run();

        verify(reports).deleteOrphaned();
        assertThat(registry.counter("social.cleanup.removed", "kind", "deleted_comments").count()).isEqualTo(3);
        assertThat(registry.counter("social.cleanup.removed", "kind", "closed_reports").count()).isEqualTo(1);
        assertThat(registry.counter("social.cleanup.removed", "kind", "orphaned_reports").count()).isEqualTo(2);
        assertThat(registry.counter("social.cleanup.removed", "kind", "expired_invitations").count()).isEqualTo(4);
        assertThat(registry.counter("social.cleanup.removed", "kind", "closed_invitations").count()).isEqualTo(5);
    }

    @Test
    void aPhotoRemovedDuringAnEditGetsAFullCleanupWindow() {
        PostMedia photo = pendingMedia(uploader.id());
        photo.setCreatedAt(NOW.minus(Duration.ofDays(10)));
        photo.attachTo(java.util.UUID.randomUUID(), 0);
        assertThat(photo.getPendingSince()).isNull();

        photo.detach(NOW);

        assertThat(photo.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(photo.getPendingSince()).isEqualTo(NOW);
    }

    @Test
    void everyRejectedUploadIsCounted() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MediaService counted = new MediaService(mediaRepository, storage, PROPERTIES, new SocialMetrics(registry), CLOCK);

        assertApiError(() -> counted.upload(uploader, new byte[0]), HttpStatus.BAD_REQUEST);
        assertApiError(() -> counted.upload(uploader, new byte[5 * 1024 * 1024 + 1]), HttpStatus.BAD_REQUEST);
        assertApiError(() -> counted.upload(uploader, "text".getBytes(StandardCharsets.UTF_8)), HttpStatus.BAD_REQUEST);

        assertThat(registry.counter("social.media.uploads", "result", "rejected").count()).isEqualTo(3);
    }

    @Test
    void cleanupDeletesStaleUnattachedPhotosButKeepsRowsWhoseStorageDeleteFailed() {
        PostMedia removable = pendingMedia(uploader.id());
        PostMedia stuck = pendingMedia(uploader.id());
        when(mediaRepository.findByStatusAndPendingSinceBeforeOrderByPendingSinceAsc(eq(MediaStatus.PENDING),
                eq(NOW.minus(Duration.ofHours(24))), any())).thenReturn(List.of(removable, stuck));
        doThrow(new MediaStorageException("down", null)).when(storage).delete(stuck.getObjectName());

        int removed = cleanupJob(mock(PostRepository.class)).removeUnattachedMedia(NOW.minus(Duration.ofHours(24)));

        assertThat(removed).isEqualTo(1);
        verify(mediaRepository).deleteById(removable.getId());
        verify(mediaRepository, never()).deleteById(stuck.getId());
    }

    @Test
    void purgeRemovesStoredPhotosBeforeDeletingAnExpiredPost() {
        PostRepository posts = mock(PostRepository.class);
        Post expired = post(uploader.id(), ContentStatus.DELETED);
        Post blocked = post(uploader.id(), ContentStatus.DELETED);
        PostMedia photo = pendingMedia(uploader.id());
        PostMedia unreachable = pendingMedia(uploader.id());
        when(posts.findByStatusAndStatusChangedAtBeforeOrderByStatusChangedAtAsc(eq(ContentStatus.DELETED), any(), any()))
                .thenReturn(List.of(expired, blocked));
        when(mediaRepository.findByPostIdOrderByDisplayOrderAsc(expired.getId())).thenReturn(List.of(photo));
        when(mediaRepository.findByPostIdOrderByDisplayOrderAsc(blocked.getId())).thenReturn(List.of(unreachable));
        doThrow(new MediaStorageException("down", null)).when(storage).delete(unreachable.getObjectName());

        int purged = cleanupJob(posts).purgeDeletedPosts(NOW.minus(Duration.ofDays(30)));

        assertThat(purged).isEqualTo(1);
        verify(storage).delete(photo.getObjectName());
        verify(posts).deleteById(expired.getId());
        verify(posts, never()).deleteById(blocked.getId());
    }

    @Test
    void aFailingCleanupRunDoesNotPropagate() {
        PostRepository posts = mock(PostRepository.class);
        when(mediaRepository.findByStatusAndPendingSinceBeforeOrderByPendingSinceAsc(any(), any(), any()))
                .thenThrow(new IllegalStateException("db down"));

        cleanupJob(posts).run();

        verifyNoInteractions(posts);
    }
}
