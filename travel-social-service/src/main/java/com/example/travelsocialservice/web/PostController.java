package com.example.travelsocialservice.web;

import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.service.CommentService;
import com.example.travelsocialservice.service.FeedService;
import com.example.travelsocialservice.service.LikeService;
import com.example.travelsocialservice.service.PostAssembler;
import com.example.travelsocialservice.service.PostService;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentRequest;
import com.example.travelsocialservice.web.dto.InteractionDtos.CommentView;
import com.example.travelsocialservice.web.dto.PageResponse;
import com.example.travelsocialservice.web.dto.PostDtos.LikeView;
import com.example.travelsocialservice.web.dto.PostDtos.PostRequest;
import com.example.travelsocialservice.web.dto.PostDtos.PostView;
import com.example.travelsocialservice.web.dto.PostDtos.RatingView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final FeedService feedService;
    private final LikeService likeService;
    private final CommentService commentService;
    private final PostAssembler assembler;

    @GetMapping("/posts")
    public PageResponse<PostView> recent(@RequestParam(required = false) UUID placeId,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit,
                                         @AuthenticationPrincipal Jwt jwt) {
        return feedService.recent(placeId, cursor, limit, Actor.optional(jwt));
    }

    @GetMapping("/posts/search")
    public PageResponse<PostView> search(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit,
                                         @AuthenticationPrincipal Jwt jwt) {
        return feedService.search(q, cursor, limit, Actor.optional(jwt));
    }

    /** One of the caller's own posts, including a hidden one, with its status. Used by the edit screen. */
    @GetMapping("/me/posts/{postId}")
    public PostView minePost(@PathVariable UUID postId, @AuthenticationPrincipal Jwt jwt) {
        Actor author = Actor.require(jwt);
        return assembler.toView(postService.requireOwn(author, postId), author);
    }

    @GetMapping("/me/posts")
    public PageResponse<PostView> mine(@RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit,
                                       @AuthenticationPrincipal Jwt jwt) {
        return feedService.ownedBy(Actor.require(jwt), cursor, limit);
    }

    @GetMapping("/places/{placeId}/rating")
    public RatingView placeRating(@PathVariable UUID placeId) {
        return feedService.placeRating(placeId);
    }

    @GetMapping("/posts/{postId}")
    public PostView detail(@PathVariable UUID postId, @AuthenticationPrincipal Jwt jwt) {
        Actor viewer = Actor.optional(jwt);
        return assembler.toView(postService.requireVisible(viewer, postId), viewer);
    }

    @PostMapping("/posts")
    public ResponseEntity<PostView> create(@RequestBody(required = false) PostRequest request, @AuthenticationPrincipal Jwt jwt) {
        Actor actor = Actor.require(jwt);
        Post post = postService.create(actor, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.toView(post, actor));
    }

    @PutMapping("/posts/{postId}")
    public PostView update(@PathVariable UUID postId, @RequestBody(required = false) PostRequest request,
                           @AuthenticationPrincipal Jwt jwt) {
        Actor actor = Actor.require(jwt);
        return assembler.toView(postService.update(actor, postId, request), actor);
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> delete(@PathVariable UUID postId, @AuthenticationPrincipal Jwt jwt) {
        postService.delete(Actor.require(jwt), postId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{postId}/like")
    public LikeView like(@PathVariable UUID postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.like(Actor.require(jwt), postId);
    }

    @DeleteMapping("/posts/{postId}/like")
    public LikeView unlike(@PathVariable UUID postId, @AuthenticationPrincipal Jwt jwt) {
        return likeService.unlike(Actor.require(jwt), postId);
    }

    @GetMapping("/posts/{postId}/comments")
    public PageResponse<CommentView> comments(@PathVariable UUID postId,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit,
                                              @AuthenticationPrincipal Jwt jwt) {
        return commentService.list(postId, cursor, limit, Actor.optional(jwt));
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentView> comment(@PathVariable UUID postId, @RequestBody(required = false) CommentRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(Actor.require(jwt), postId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId, @AuthenticationPrincipal Jwt jwt) {
        commentService.delete(Actor.require(jwt), commentId);
        return ResponseEntity.noContent().build();
    }
}
