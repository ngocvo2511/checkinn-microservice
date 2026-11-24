package com.example.travelsocialservice.service;

import com.example.travelsocialservice.config.SocialProperties;
import com.example.travelsocialservice.domain.Comment;
import com.example.travelsocialservice.domain.ContentStatus;
import com.example.travelsocialservice.domain.MediaStatus;
import com.example.travelsocialservice.domain.Post;
import com.example.travelsocialservice.domain.PostMedia;
import com.example.travelsocialservice.security.Actor;
import com.example.travelsocialservice.web.ApiException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

final class Fixtures {

    static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    static final SocialProperties PROPERTIES = new SocialProperties(
            "http://place.invalid", "http://user.invalid", "http://hotel.invalid",
            new SocialProperties.Client(Duration.ofSeconds(2), Duration.ofSeconds(3), Duration.ofMinutes(5)),
            new SocialProperties.Limits(5000, 1000, 500, 5, 5 * 1024 * 1024, 20),
            new SocialProperties.Retention(Duration.ofHours(24), Duration.ofDays(30)),
            new SocialProperties.Cleanup(100),
            new SocialProperties.Invitations(Duration.ofDays(30), 20));

    private Fixtures() {
    }

    static SocialMetrics metrics() {
        return new SocialMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    static Actor user() {
        return new Actor(UUID.randomUUID(), false);
    }

    static Actor admin() {
        return new Actor(UUID.randomUUID(), true);
    }

    static Post post(UUID authorId, ContentStatus status) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setAuthorId(authorId);
        post.setBody("A quiet beach at sunrise");
        post.setStatus(status);
        post.setCreatedAt(NOW.minusSeconds(3600));
        post.setUpdatedAt(NOW.minusSeconds(3600));
        post.setStatusChangedAt(NOW.minusSeconds(3600));
        return post;
    }

    static Comment comment(UUID postId, UUID authorId, ContentStatus status) {
        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setBody("Lovely");
        comment.setStatus(status);
        comment.setCreatedAt(NOW.minusSeconds(60));
        comment.setUpdatedAt(NOW.minusSeconds(60));
        comment.setStatusChangedAt(NOW.minusSeconds(60));
        return comment;
    }

    static PostMedia pendingMedia(UUID uploaderId) {
        PostMedia media = new PostMedia();
        media.setId(UUID.randomUUID());
        media.setUploaderId(uploaderId);
        media.setUrl("https://storage.invalid/social/" + media.getId() + ".jpg");
        media.setObjectName("social/" + media.getId() + "_photo.jpg");
        media.setMimeType("image/jpeg");
        media.setSizeBytes(10);
        media.setStatus(MediaStatus.PENDING);
        media.setCreatedAt(NOW.minusSeconds(120));
        media.setPendingSince(media.getCreatedAt());
        return media;
    }

    static ApiException assertApiError(ThrowingCallable call, HttpStatus status) {
        ApiException ex = catchThrowableOfType(call, ApiException.class);
        assertThat(ex).as("expected ApiException " + status).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(status);
        return ex;
    }

    static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    }
}
