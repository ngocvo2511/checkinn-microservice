package com.example.travelsocialservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
    }

    public void changeStatus(ContentStatus next, Instant at) {
        this.status = next;
        this.statusChangedAt = at;
    }
}
