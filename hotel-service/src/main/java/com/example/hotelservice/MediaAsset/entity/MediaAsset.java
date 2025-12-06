package com.example.hotelservice.MediaAsset.entity;


import com.example.hotelservice.MediaAsset.enums.MediaTargetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private MediaTargetType targetType;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "is_thumbnail", nullable = false)
    private Boolean isThumbnail;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (isThumbnail == null) isThumbnail = false;
        if (sortOrder == null) sortOrder = 0;

        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}


