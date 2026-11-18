package com.example.travelsocialservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_media")
@Getter
@Setter
@NoArgsConstructor
public class PostMedia {

    @Id
    private UUID id;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Column(name = "post_id")
    private UUID postId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String url;

    @Column(name = "object_name", nullable = false, updatable = false, columnDefinition = "text")
    private String objectName;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 64)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    /**
     * When the photo last became unattached: at upload, or when an edit removed it from a post. The
     * cleanup window is measured from here, so a photo removed during an edit gets the full window
     * rather than being collected on the next run because it was uploaded long ago.
     */
    @Column(name = "pending_since")
    private Instant pendingSince;

    public void attachTo(UUID postId, int displayOrder) {
        this.postId = postId;
        this.displayOrder = displayOrder;
        this.status = MediaStatus.ATTACHED;
        this.pendingSince = null;
    }

    public void detach(Instant at) {
        this.postId = null;
        this.displayOrder = 0;
        this.status = MediaStatus.PENDING;
        this.pendingSince = at;
    }
}
