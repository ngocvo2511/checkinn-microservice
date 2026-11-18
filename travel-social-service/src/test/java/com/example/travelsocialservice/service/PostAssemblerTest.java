package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.AuthorClient;
import com.example.travelsocialservice.client.AuthorClient.Author;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.client.PlaceClient.PlaceRef;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.PostDtos.PostView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.travelsocialservice.service.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PostAssemblerTest {

    private PostMediaRepository media;
    private InteractionQueries interactions;
    private PlaceClient places;
    private AuthorClient authors;
    private PostAssembler assembler;

    private final Actor author = user();
    private final UUID placeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        media = mock(PostMediaRepository.class);
        interactions = mock(InteractionQueries.class);
        places = mock(PlaceClient.class);
        authors = mock(AuthorClient.class);
        when(authors.findAll(any())).thenReturn(Map.of(author.id(), new Author(author.id(), "An N.", null)));
        assembler = new PostAssembler(media, interactions, places, authors);
    }

    private Post review() {
        Post post = post(author.id(), ContentStatus.PUBLISHED);
        post.setPlaceId(placeId);
        post.setRating(4);
        return post;
    }

    @Test
    void viewIsLabelledCommunityAndUnverifiedWithBatchedDisplayData() {
        Post post = review();
        PostMedia photo = pendingMedia(author.id());
        photo.attachTo(post.getId(), 0);
        when(media.findByPostIdInOrderByPostIdAscDisplayOrderAsc(List.of(post.getId()))).thenReturn(List.of(photo));
        when(interactions.likeCounts(List.of(post.getId()))).thenReturn(Map.of(post.getId(), 7L));
        when(interactions.publishedCommentCounts(List.of(post.getId()))).thenReturn(Map.of(post.getId(), 2L));
        when(places.findAll(Set.of(placeId))).thenReturn(Map.of(placeId, new PlaceRef(placeId, "Cầu Rồng", "ATTRACTION", "Đà Nẵng")));

        PostView view = assembler.toView(post, null);

        assertThat(view.source()).isEqualTo("COMMUNITY");
        assertThat(view.verified()).isFalse();
        assertThat(view.rating()).isEqualTo(4);
        assertThat(view.author().displayName()).isEqualTo("An N.");
        assertThat(view.place().name()).isEqualTo("Cầu Rồng");
        assertThat(view.media()).singleElement().satisfies(m -> assertThat(m.url()).isEqualTo(photo.getUrl()));
        assertThat(view.likeCount()).isEqualTo(7);
        assertThat(view.commentCount()).isEqualTo(2);
        assertThat(view.likedByMe()).isNull();
        assertThat(view.status()).isNull();
        verify(interactions, never()).likedBy(any(), any());
    }

    @Test
    void aMissingPlaceOrAuthorDoesNotHideThePost() {
        Post post = review();
        when(places.findAll(any())).thenReturn(Map.of());
        when(authors.findAll(any())).thenReturn(Map.of());

        PostView view = assembler.toView(post, null);

        assertThat(view.place()).isNull();
        assertThat(view.placeId()).isEqualTo(placeId);
        assertThat(view.author().id()).isEqualTo(author.id());
        assertThat(view.author().displayName()).isNull();
    }

    @Test
    void statusAndLikeStateDependOnTheViewer() {
        Post post = review();
        Actor reader = user();
        when(interactions.likedBy(reader.id(), List.of(post.getId()))).thenReturn(Set.of(post.getId()));
        when(interactions.likedBy(author.id(), List.of(post.getId()))).thenReturn(Set.of());

        assertThat(assembler.toView(post, reader).likedByMe()).isTrue();
        assertThat(assembler.toView(post, reader).status()).isNull();
        assertThat(assembler.toView(post, author).likedByMe()).isFalse();
        assertThat(assembler.toView(post, author).status()).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(assembler.toView(post, admin()).status()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void aPageUsesOneLookupPerSource() {
        List<Post> page = List.of(review(), review(), review());

        assertThat(assembler.toViews(page, null)).hasSize(3);

        verify(media, times(1)).findByPostIdInOrderByPostIdAscDisplayOrderAsc(any());
        verify(interactions, times(1)).likeCounts(any());
        verify(interactions, times(1)).publishedCommentCounts(any());
        verify(places, times(1)).findAll(any());
        verify(authors, times(1)).findAll(any());
        assertThat(assembler.toViews(List.of(), null)).isEmpty();
    }
}
