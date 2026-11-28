package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.client.PlaceClient.PlaceRef;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.ModerationAction;
import com.example.travelsocialservice.domain.ModerationAudit;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.ModerationAuditRepository;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.repository.PostRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import com.example.travelsocialservice.web.dto.PostDtos.PostRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostServiceTest {

    private PostRepository posts;
    private PostMediaRepository media;
    private ModerationAuditRepository audits;
    private PlaceClient places;
    private PostService service;

    private final Actor author = user();
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        media = mock(PostMediaRepository.class);
        audits = mock(ModerationAuditRepository.class);
        places = mock(PlaceClient.class);
        when(places.require(placeId)).thenReturn(Optional.of(new PlaceRef(placeId, "Cầu Rồng", "ATTRACTION", "Đà Nẵng")));
        when(media.findAllById(any())).thenReturn(List.of());
        service = new PostService(posts, media, audits, places,
                mock(com.example.travelsocialservice.client.HotelClient.class),
                mock(com.example.travelsocialservice.client.CompletedStays.class),
                mock(com.example.travelsocialservice.repository.StayInvitationRepository.class), metrics(), PROPERTIES, CLOCK);
    }

    private Post stored(Post post) {
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void createPublishesAPlaceReviewForTheActorWithTrimmedBody() {
        Post post = service.create(author, new PostRequest("  Great bridge  ", placeId, 5, null));

        assertThat(post.getAuthorId()).isEqualTo(author.id());
        assertThat(post.getBody()).isEqualTo("Great bridge");
        assertThat(post.getPlaceId()).isEqualTo(placeId);
        assertThat(post.getRating()).isEqualTo(5);
        assertThat(post.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(post.getCreatedAt()).isEqualTo(NOW);
        verify(posts).save(post);
    }

    @Test
    void createAllowsAStoryWithoutPlaceOrRating() {
        Post post = service.create(author, new PostRequest("Three days on the road", null, null, List.of()));

        assertThat(post.getPlaceId()).isNull();
        assertThat(post.getRating()).isNull();
        verifyNoInteractions(places);
    }

    @Test
    void createRejectsInvalidContentWithoutSaving() {
        List<PostRequest> invalid = List.of(
                new PostRequest("   ", null, null, null),
                new PostRequest("x".repeat(5001), null, null, null),
                new PostRequest("ok", placeId, 0, null),
                new PostRequest("ok", placeId, 6, null),
                new PostRequest("ok", null, 4, null),
                new PostRequest("ok", null, null, Stream.generate(UUID::randomUUID).limit(6).toList()),
                new PostRequest("ok", null, null, Arrays.asList(UUID.randomUUID(), null)));
        invalid.forEach(request -> assertApiError(() -> service.create(author, request), HttpStatus.BAD_REQUEST));
        assertApiError(() -> service.create(author, null), HttpStatus.BAD_REQUEST);
        UUID duplicate = UUID.randomUUID();
        assertApiError(() -> service.create(author, new PostRequest("ok", null, null, List.of(duplicate, duplicate))), HttpStatus.BAD_REQUEST);
        verify(posts, never()).save(any());
    }

    @Test
    void createRejectsAnUnknownPlace() {
        UUID unknown = UUID.randomUUID();
        when(places.require(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertApiError(() -> service.create(author, new PostRequest("ok", unknown, null, null)), HttpStatus.BAD_REQUEST);

        assertThat(ex.getFields()).containsKey("placeId");
        verify(posts, never()).save(any());
    }

    @Test
    void createFailsWhenThePlaceCatalogIsUnreachable() {
        when(places.require(placeId)).thenThrow(ApiException.unavailable("PLACE_SERVICE_UNAVAILABLE", "down"));

        assertApiError(() -> service.create(author, new PostRequest("ok", placeId, null, null)), HttpStatus.SERVICE_UNAVAILABLE);
        verify(posts, never()).save(any());
    }

    @Test
    void createAttachesOwnPendingPhotosInTheRequestedOrder() {
        PostMedia first = pendingMedia(author.id());
        PostMedia second = pendingMedia(author.id());
        when(media.findAllById(any())).thenReturn(List.of(first, second));

        Post post = service.create(author, new PostRequest("ok", null, null, List.of(second.getId(), first.getId())));

        assertThat(second.getPostId()).isEqualTo(post.getId());
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(MediaStatus.ATTACHED);
    }

    @Test
    void createRejectsPhotosThatAreForeignAlreadyUsedOrUnknown() {
        PostMedia foreign = pendingMedia(UUID.randomUUID());
        PostMedia used = pendingMedia(author.id());
        used.attachTo(UUID.randomUUID(), 0);
        when(media.findAllById(List.of(foreign.getId()))).thenReturn(List.of(foreign));
        when(media.findAllById(List.of(used.getId()))).thenReturn(List.of(used));

        for (UUID mediaId : List.of(foreign.getId(), used.getId(), UUID.randomUUID())) {
            ApiException ex = assertApiError(() -> service.create(author, new PostRequest("ok", null, null, List.of(mediaId))), HttpStatus.BAD_REQUEST);
            assertThat(ex.getFields()).containsKey("mediaIds");
        }
        assertThat(foreign.getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    @Test
    void editingAHiddenPostKeepsItHidden() {
        Post hidden = stored(post(author.id(), ContentStatus.HIDDEN));

        Post updated = service.update(author, hidden.getId(), new PostRequest("Reworded", null, null, null));

        assertThat(updated.getBody()).isEqualTo("Reworded");
        assertThat(updated.getStatus()).isEqualTo(ContentStatus.HIDDEN);
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void editRevalidatesAPlaceOnlyWhenItChanges() {
        Post existing = post(author.id(), ContentStatus.PUBLISHED);
        existing.setPlaceId(placeId);
        stored(existing);

        service.update(author, existing.getId(), new PostRequest("Same place", placeId, 4, null));
        verifyNoInteractions(places);

        UUID gone = UUID.randomUUID();
        when(places.require(gone)).thenReturn(Optional.empty());
        assertApiError(() -> service.update(author, existing.getId(), new PostRequest("New place", gone, null, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void editKeepsReordersAndDetachesPhotos() {
        Post existing = stored(post(author.id(), ContentStatus.PUBLISHED));
        PostMedia kept = pendingMedia(author.id());
        PostMedia removed = pendingMedia(author.id());
        kept.attachTo(existing.getId(), 0);
        removed.attachTo(existing.getId(), 1);
        PostMedia added = pendingMedia(author.id());
        when(media.findByPostIdOrderByDisplayOrderAsc(existing.getId())).thenReturn(List.of(kept, removed));
        when(media.findAllById(any())).thenReturn(List.of(added, kept));

        service.update(author, existing.getId(), new PostRequest("ok", null, null, List.of(added.getId(), kept.getId())));

        assertThat(added.getPostId()).isEqualTo(existing.getId());
        assertThat(added.getDisplayOrder()).isZero();
        assertThat(kept.getDisplayOrder()).isEqualTo(1);
        assertThat(removed.getStatus()).isEqualTo(MediaStatus.PENDING);
        assertThat(removed.getPostId()).isNull();
    }

    @Test
    void strangersCannotEditOrDeleteAndCannotDetectHiddenPosts() {
        Actor stranger = user();
        Post published = stored(post(author.id(), ContentStatus.PUBLISHED));
        Post hidden = stored(post(author.id(), ContentStatus.HIDDEN));
        PostRequest edit = new PostRequest("hijack", null, null, null);

        assertApiError(() -> service.update(stranger, published.getId(), edit), HttpStatus.FORBIDDEN);
        assertApiError(() -> service.delete(stranger, published.getId()), HttpStatus.FORBIDDEN);
        assertApiError(() -> service.update(stranger, hidden.getId(), edit), HttpStatus.NOT_FOUND);
        assertApiError(() -> service.delete(stranger, hidden.getId()), HttpStatus.NOT_FOUND);
        assertThat(published.getBody()).isNotEqualTo("hijack");
        assertThat(published.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void authorDeleteIsASoftDeleteThatCannotBeRepeatedOrEdited() {
        Post existing = stored(post(author.id(), ContentStatus.PUBLISHED));

        service.delete(author, existing.getId());

        assertThat(existing.getStatus()).isEqualTo(ContentStatus.DELETED);
        assertThat(existing.getStatusChangedAt()).isEqualTo(NOW);
        verify(posts, never()).delete(any());
        verifyNoInteractions(audits);
        assertApiError(() -> service.delete(author, existing.getId()), HttpStatus.NOT_FOUND);
        assertApiError(() -> service.update(author, existing.getId(), new PostRequest("back", null, null, null)), HttpStatus.NOT_FOUND);
    }

    @Test
    void adminDeleteIsAudited() {
        Actor admin = admin();
        Post existing = stored(post(author.id(), ContentStatus.HIDDEN));

        service.delete(admin, existing.getId());

        assertThat(existing.getStatus()).isEqualTo(ContentStatus.DELETED);
        ArgumentCaptor<ModerationAudit> audit = ArgumentCaptor.forClass(ModerationAudit.class);
        verify(audits).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(ModerationAction.DELETE);
        assertThat(audit.getValue().getActorId()).isEqualTo(admin.id());
        assertThat(audit.getValue().getTargetId()).isEqualTo(existing.getId());
    }

    @Test
    void hiddenAndDeletedPostsAreVisibleOnlyToAdmins() {
        Post hidden = stored(post(author.id(), ContentStatus.HIDDEN));
        Post deleted = stored(post(author.id(), ContentStatus.DELETED));
        Post published = stored(post(author.id(), ContentStatus.PUBLISHED));

        for (Post unavailable : List.of(hidden, deleted)) {
            assertApiError(() -> service.requireVisible(null, unavailable.getId()), HttpStatus.NOT_FOUND);
            assertApiError(() -> service.requireVisible(user(), unavailable.getId()), HttpStatus.NOT_FOUND);
            assertApiError(() -> service.requireVisible(author, unavailable.getId()), HttpStatus.NOT_FOUND);
            assertApiError(() -> service.requirePublished(unavailable.getId()), HttpStatus.NOT_FOUND);
            assertThat(service.requireVisible(admin(), unavailable.getId())).isSameAs(unavailable);
        }
        assertThat(service.requireVisible(null, published.getId())).isSameAs(published);
        assertApiError(() -> service.requireVisible(null, UUID.randomUUID()), HttpStatus.NOT_FOUND);
    }

    @Test
    void requireOwnLetsTheAuthorLoadTheirHiddenPostButNobodyElse() {
        Post hidden = stored(post(author.id(), ContentStatus.HIDDEN));
        Post deleted = stored(post(author.id(), ContentStatus.DELETED));

        assertThat(service.requireOwn(author, hidden.getId())).isSameAs(hidden);
        assertApiError(() -> service.requireOwn(user(), hidden.getId()), HttpStatus.NOT_FOUND);
        assertApiError(() -> service.requireOwn(admin(), hidden.getId()), HttpStatus.NOT_FOUND);
        assertApiError(() -> service.requireOwn(author, deleted.getId()), HttpStatus.NOT_FOUND);
        assertApiError(() -> service.requireOwn(author, UUID.randomUUID()), HttpStatus.NOT_FOUND);
    }
}
