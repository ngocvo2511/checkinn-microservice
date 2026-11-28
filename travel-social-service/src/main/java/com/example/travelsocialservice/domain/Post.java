package com.example.travelsocialservice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "place_id")
    private UUID placeId;

    private Integer rating;

    @Column(name = "hotel_id")
    private UUID hotelId;

    /** Private: never returned in a public response. */
    @Column(name = "stay_booking_id")
    private UUID stayBookingId;

    @Column(name = "verified_stay", nullable = false)
    private boolean verifiedStay;

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
