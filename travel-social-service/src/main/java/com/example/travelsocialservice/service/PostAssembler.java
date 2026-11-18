package com.example.travelsocialservice.service;

import com.example.travelsocialservice.client.AuthorClient;
import com.example.travelsocialservice.client.AuthorClient.Author;
import com.example.travelsocialservice.client.PlaceClient;
import com.example.travelsocialservice.client.PlaceClient.PlaceRef;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.repository.InteractionQueries;
import com.example.travelsocialservice.repository.PostMediaRepository;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.dto.PostDtos.AuthorView;
import com.example.travelsocialservice.web.dto.PostDtos.MediaView;
import com.example.travelsocialservice.web.dto.PostDtos.PlaceView;
import com.example.travelsocialservice.web.dto.PostDtos.PostView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns posts into responses with one batched lookup per related source, never one per post. */
@Component
@RequiredArgsConstructor
public class PostAssembler {

    public static final String SOURCE_COMMUNITY = "COMMUNITY";

    private final PostMediaRepository mediaRepository;
    private final InteractionQueries interactions;
    private final PlaceClient placeClient;
    private final AuthorClient authorClient;

    public PostView toView(Post post, Actor viewer) {
        return toViews(List.of(post), viewer).get(0);
    }

    public List<PostView> toViews(List<Post> posts, Actor viewer) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        Map<UUID, List<PostMedia>> mediaByPost = mediaRepository.findByPostIdInOrderByPostIdAscDisplayOrderAsc(postIds)
                .stream().collect(Collectors.groupingBy(PostMedia::getPostId));
        Map<UUID, Long> likeCounts = interactions.likeCounts(postIds);
        Map<UUID, Long> commentCounts = interactions.publishedCommentCounts(postIds);
        Set<UUID> likedByViewer = viewer == null ? null : interactions.likedBy(viewer.id(), postIds);
        Map<UUID, PlaceRef> places = placeClient.findAll(
                posts.stream().map(Post::getPlaceId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, Author> authors = authorClient.findAll(
                posts.stream().map(Post::getAuthorId).collect(Collectors.toSet()));

        return posts.stream().map(post -> new PostView(
                post.getId(),
                authorView(authors, post.getAuthorId()),
                post.getBody(),
                post.getRating(),
                SOURCE_COMMUNITY,
                false,
                post.getPlaceId(),
                placeView(places.get(post.getPlaceId())),
                mediaByPost.getOrDefault(post.getId(), List.of()).stream()
                        .map(media -> new MediaView(media.getId(), media.getUrl(), media.getDisplayOrder())).toList(),
                likeCounts.getOrDefault(post.getId(), 0L),
                likedByViewer == null ? null : likedByViewer.contains(post.getId()),
                commentCounts.getOrDefault(post.getId(), 0L),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                viewer != null && (viewer.admin() || viewer.is(post.getAuthorId())) ? post.getStatus() : null
        )).toList();
    }

    public Map<UUID, AuthorView> authorViews(Collection<UUID> authorIds) {
        Map<UUID, Author> authors = authorClient.findAll(authorIds);
        return authorIds.stream().distinct().collect(Collectors.toMap(id -> id, id -> authorView(authors, id)));
    }

    private static AuthorView authorView(Map<UUID, Author> authors, UUID authorId) {
        Author author = authors.get(authorId);
        return author == null ? new AuthorView(authorId, null, null)
                : new AuthorView(authorId, author.displayName(), author.avatarUrl());
    }

    private static PlaceView placeView(PlaceRef place) {
        return place == null ? null : new PlaceView(place.id(), place.name(), place.category(), place.provinceName());
    }
}
