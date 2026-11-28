package com.example.travelsocialservice.service;

import com.example.travelsocialservice.domain.ModerationAction;
import com.example.travelsocialservice.domain.ReportReason;
import com.example.travelsocialservice.domain.TargetType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Business counters. Tags are low-cardinality enums only: never user ids, post ids or content. */
@Component
public class SocialMetrics {

    private final MeterRegistry registry;

    public SocialMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void mediaUpload(String result) {
        registry.counter("social.media.uploads", "result", result).increment();
    }

    public void reportCreated(TargetType targetType, ReportReason reason) {
        registry.counter("social.reports.created", "target", targetType.name(), "reason", reason.name()).increment();
    }

    public void moderationAction(ModerationAction action, TargetType targetType) {
        registry.counter("social.moderation.actions", "action", action.name(), "target", targetType.name()).increment();
    }

    /** result: verified, rejected, unavailable. */
    public void verifiedPost(String result) {
        registry.counter("social.verified_posts", "result", result).increment();
    }

    /** result: created, duplicate, ignored_status, not_completed, hotel_unpublished, lookup_failed. */
    public void stayInvitation(String result) {
        registry.counter("social.stay_invitations", "result", result).increment();
    }

    public void cleanupRemoved(String kind, int count) {
        if (count > 0) {
            registry.counter("social.cleanup.removed", "kind", kind).increment(count);
        }
    }
}
