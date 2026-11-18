package com.example.travelsocialservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_audit")
@Getter
@NoArgsConstructor
public class ModerationAudit {

    @Id
    private UUID id;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private ModerationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 16)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(updatable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ModerationAudit(UUID actorId, ModerationAction action, TargetType targetType, UUID targetId, String reason, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
