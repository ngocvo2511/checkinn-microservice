package com.example.travelsocialservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("social")
public record SocialProperties(
        String placeServiceUrl,
        String userServiceUrl,
        String hotelServiceUrl,
        Client client,
        Limits limits,
        Retention retention,
        Cleanup cleanup,
        Invitations invitations
) {
    public record Client(Duration connectTimeout, Duration readTimeout, Duration authorCacheTtl) {
    }

    public record Limits(int postBodyMax, int commentBodyMax, int reportNoteMax, int maxMediaPerPost,
                         long maxMediaBytes, int maxPendingMediaPerUser) {
    }

    public record Retention(Duration pendingMedia, Duration deletedContent) {
    }

    public record Cleanup(int batchSize) {
    }

    public record Invitations(Duration ttl, int listMax) {
    }
}
